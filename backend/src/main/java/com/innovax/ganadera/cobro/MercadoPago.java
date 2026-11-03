package com.innovax.ganadera.cobro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Lo que usamos de la API de suscripciones de Mercado Pago. La implementación real es
 * {@link MercadoPagoApi}; los tests usan una falsa.
 *
 * <p>Errores de comunicación: {@link MercadoPagoException}.
 */
public interface MercadoPago {

    /**
     * Datos para crear una suscripción "con pago pendiente": el cliente la paga en {@code initPoint}.
     *
     * @param primerCobro día del primer cobro (al terminar la prueba); null = enseguida
     */
    record NuevaSuscripcion(String descripcion, String referencia, String emailPagador, BigDecimal monto,
                            String moneda, String urlRetorno, LocalDate primerCobro) {
    }

    /**
     * @param status     pending, authorized, paused o cancelled
     * @param referencia nuestro id de suscripción (external_reference)
     */
    record Preapproval(String id, String status, String referencia, String initPoint) {
    }

    /** Un cobro mensual de la suscripción ("authorized payment"). */
    record Cuota(String id, String preapprovalId, LocalDate fecha, boolean aprobada) {
    }

    Preapproval crear(NuevaSuscripcion suscripcion);

    Preapproval obtener(String preapprovalId);

    void cancelar(String preapprovalId);

    Cuota cuota(String cuotaId);

    List<Cuota> cuotas(String preapprovalId);
}
