package com.innovax.ganadera.mercado.fuente;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.innovax.ganadera.mercado.MercadoProperties;
import com.innovax.ganadera.mercado.Producto;

import lombok.extern.slf4j.Slf4j;

/**
 * Dólar oficial y blue desde DolarApi (https://dolarapi.com): API pública, gratuita y sin clave,
 * de código abierto (licencia MIT). Se guarda el valor de venta. Ver docs/mercado.md.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.mercado.fuentes-externas", havingValue = "true", matchIfMissing = true)
public class DolarApiFuente implements FuenteCotizacion {

    /** Producto -> "casa" en la URL de DolarApi (/v1/dolares/{casa}). */
    private static final Map<Producto, String> CASAS = Map.of(
        Producto.DOLAR_OFICIAL, "oficial",
        Producto.DOLAR_BLUE, "blue");

    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    private final RestClient http;
    private final String url;

    public DolarApiFuente(@Qualifier("mercadoRestClient") RestClient http, MercadoProperties propiedades) {
        this.http = http;
        this.url = propiedades.dolarApiUrl();
    }

    /** Respuesta de DolarApi. Trae más campos (casa, nombre, moneda): se ignoran. */
    public record RespuestaDolarApi(BigDecimal compra, BigDecimal venta, String fechaActualizacion) {
    }

    @Override
    public String nombre() {
        return "DolarApi";
    }

    @Override
    public Set<Producto> productos() {
        return CASAS.keySet();
    }

    /** Cada dólar se pide por separado: si falla uno, el otro se guarda igual. */
    @Override
    public List<Lectura> leer() {
        List<Lectura> lecturas = new ArrayList<>();
        for (var entrada : CASAS.entrySet()) {
            Producto producto = entrada.getKey();
            try {
                RespuestaDolarApi c = http.get().uri(url + "/dolares/{casa}", entrada.getValue())
                    .retrieve().body(RespuestaDolarApi.class);
                lecturas.add(convertir(producto, c));
            } catch (RuntimeException e) {
                log.warn("DolarApi: no se pudo leer {}", producto, e);
                lecturas.add(Lectura.fallo(producto, e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        return lecturas;
    }

    static Lectura convertir(Producto producto, RespuestaDolarApi c) {
        if (c == null || c.venta() == null || c.venta().signum() <= 0) {
            return Lectura.fallo(producto, "DolarApi devolvió una respuesta sin valor de venta");
        }
        return Lectura.ok(producto, c.venta(), fecha(c.fechaActualizacion()), null);
    }

    /** "2026-10-29T18:05:00.000Z" -> 29/10 en hora argentina. Sin fecha válida, null. */
    private static LocalDate fecha(String instante) {
        try {
            return instante == null ? null : Instant.parse(instante).atZone(ARGENTINA).toLocalDate();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
