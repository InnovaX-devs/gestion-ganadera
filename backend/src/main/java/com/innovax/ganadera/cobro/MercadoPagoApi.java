package com.innovax.ganadera.cobro;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.innovax.ganadera.animal.categoria.CategoriaConfig;
import com.innovax.ganadera.common.error.BusinessException;

import lombok.extern.slf4j.Slf4j;

/**
 * API de suscripciones de Mercado Pago (https://api.mercadopago.com): {@code /preapproval} y
 * {@code /authorized_payments}. El Access Token va siempre por header.
 */
@Slf4j
@Component
public class MercadoPagoApi implements MercadoPago {

    private static final ZoneId ZONA = ZoneId.of(CategoriaConfig.ZONA_HORARIA);
    private static final DateTimeFormatter FECHA_MP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    /** Lo que responde Mercado Pago si quien paga es el mismo usuario que cobra. */
    static final String MISMO_USUARIO = "Payer and collector cannot be the same user";

    private final RestClient http;
    private final CobroProperties propiedades;

    public MercadoPagoApi(@Qualifier("mercadoPagoRestClient") RestClient http, CobroProperties propiedades) {
        this.http = http;
        this.propiedades = propiedades;
    }

    // ---------- Respuestas de Mercado Pago (solo los campos que se usan) ----------

    public record PreapprovalMP(String id, String status, @JsonProperty("external_reference") String externalReference,
                         @JsonProperty("init_point") String initPoint) {
    }

    public record CuotaMP(String id, @JsonProperty("preapproval_id") String preapprovalId,
                   @JsonProperty("debit_date") String debitDate, @JsonProperty("date_created") String dateCreated,
                   PagoMP payment) {
    }

    public record PagoMP(String id, String status) {
    }

    public record BusquedaCuotasMP(List<CuotaMP> results) {
    }

    // ---------- Operaciones ----------

    @Override
    public Preapproval crear(NuevaSuscripcion s) {
        Map<String, Object> recurrencia = new LinkedHashMap<>();
        recurrencia.put("frequency", 1);
        recurrencia.put("frequency_type", "months");
        recurrencia.put("transaction_amount", s.monto());
        recurrencia.put("currency_id", s.moneda());
        if (s.primerCobro() != null) {
            recurrencia.put("start_date", s.primerCobro().atTime(9, 0).atZone(ZONA).format(FECHA_MP));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", s.descripcion());
        body.put("external_reference", s.referencia());
        body.put("payer_email", s.emailPagador());
        body.put("auto_recurring", recurrencia);
        body.put("back_url", s.urlRetorno());
        body.put("status", "pending");
        return llamar("crear la suscripción", () -> aPreapproval(http.post().uri("/preapproval")
            .headers(h -> h.setBearerAuth(propiedades.mpAccessToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(PreapprovalMP.class)));
    }

    @Override
    public Preapproval obtener(String preapprovalId) {
        return llamar("consultar la suscripción", () -> aPreapproval(http.get().uri("/preapproval/{id}", preapprovalId)
            .headers(h -> h.setBearerAuth(propiedades.mpAccessToken()))
            .retrieve()
            .body(PreapprovalMP.class)));
    }

    @Override
    public void cancelar(String preapprovalId) {
        llamar("cancelar la suscripción", () -> http.put().uri("/preapproval/{id}", preapprovalId)
            .headers(h -> h.setBearerAuth(propiedades.mpAccessToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("status", "cancelled"))
            .retrieve()
            .toBodilessEntity());
    }

    @Override
    public Cuota cuota(String cuotaId) {
        return llamar("consultar el cobro", () -> aCuota(http.get().uri("/authorized_payments/{id}", cuotaId)
            .headers(h -> h.setBearerAuth(propiedades.mpAccessToken()))
            .retrieve()
            .body(CuotaMP.class)));
    }

    @Override
    public List<Cuota> cuotas(String preapprovalId) {
        BusquedaCuotasMP busqueda = llamar("consultar los cobros", () -> http.get()
            .uri(u -> u.path("/authorized_payments/search").queryParam("preapproval_id", preapprovalId).build())
            .headers(h -> h.setBearerAuth(propiedades.mpAccessToken()))
            .retrieve()
            .body(BusquedaCuotasMP.class));
        if (busqueda == null || busqueda.results() == null) return List.of();
        return busqueda.results().stream().map(MercadoPagoApi::aCuota).toList();
    }

    // ---------- Conversión ----------

    private static Preapproval aPreapproval(PreapprovalMP p) {
        if (p == null) throw new MercadoPagoException("Mercado Pago respondió vacío", null);
        return new Preapproval(p.id(), p.status(), p.externalReference(), p.initPoint());
    }

    private static Cuota aCuota(CuotaMP c) {
        if (c == null) throw new MercadoPagoException("Mercado Pago respondió vacío", null);
        String fecha = c.debitDate() != null ? c.debitDate() : c.dateCreated();
        boolean aprobada = c.payment() != null && "approved".equals(c.payment().status());
        return new Cuota(c.id(), c.preapprovalId(), aFecha(fecha), aprobada);
    }

    static LocalDate aFecha(String texto) {
        if (texto == null || texto.isBlank()) return null;
        return OffsetDateTime.parse(texto).atZoneSameInstant(ZONA).toLocalDate();
    }

    private <T> T llamar(String que, Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (RestClientResponseException e) {
            String respuesta = e.getResponseBodyAsString();
            log.error("Mercado Pago: no se pudo {} ({}): {}", que, e.getStatusCode(), respuesta);
            if (respuesta != null && respuesta.contains(MISMO_USUARIO)) {
                // No es una falla de comunicación: el email de la cuenta es el de quien cobra
                throw new BusinessException("No se puede pagar con el email de la cuenta de Mercado Pago que "
                    + "cobra. El dueño de esta cuenta tiene que usar otro email.");
            }
            throw new MercadoPagoException("Mercado Pago rechazó el pedido al " + que, e);
        } catch (RestClientException e) {
            log.error("Mercado Pago: no se pudo {}", que, e);
            throw new MercadoPagoException("No pudimos comunicarnos con Mercado Pago", e);
        }
    }
}
