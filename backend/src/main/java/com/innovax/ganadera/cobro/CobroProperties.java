package com.innovax.ganadera.cobro;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.cobro.*}: suscripciones con Mercado Pago (#35). Ver docs/planes.md.
 *
 * @param mpAccessToken      Access Token de la aplicación de Mercado Pago. Vacío = cobro online apagado.
 * @param mpWebhookSecret    clave secreta de los webhooks, para validar la firma. Vacía = no se valida
 *                           (igual cada aviso se confirma consultando a Mercado Pago).
 * @param mpApiUrl           API de Mercado Pago
 * @param backUrl            a dónde vuelve el cliente después de pagar
 * @param emailPagadorPrueba solo para probar: en modo prueba Mercado Pago exige que el pagador sea
 *                           un usuario de prueba, así que se usa este email en vez del del usuario
 * @param moneda             ARS
 * @param diasGracia         días que sigue el plan después de lo pago si no se pudo cobrar
 * @param timeout            de cada llamada a Mercado Pago
 * @param exigirPago         sin prueba, suscripción al día ni pago manual, la cuenta queda en solo lectura.
 *                           En false nadie se bloquea (para lanzar sin cortar a nadie).
 * @param diaVencimientoManual quien paga por transferencia paga del 1 a este día de cada mes: "Registrar
 *                           pago" lo deja pagado hasta este día del mes siguiente
 * @param cacheEstado        cuánto se recuerda si una cuenta está habilitada (se consulta en cada request)
 */
@ConfigurationProperties(prefix = "app.cobro")
public record CobroProperties(String mpAccessToken, String mpWebhookSecret, String mpApiUrl, String backUrl,
                              String emailPagadorPrueba, String moneda, int diasGracia, Duration timeout,
                              Boolean exigirPago, Duration cacheEstado, Integer diaVencimientoManual) {

    public CobroProperties {
        mpAccessToken = mpAccessToken == null ? "" : mpAccessToken.trim();
        mpWebhookSecret = mpWebhookSecret == null ? "" : mpWebhookSecret.trim();
        mpApiUrl = mpApiUrl == null || mpApiUrl.isBlank() ? "https://api.mercadopago.com" : mpApiUrl;
        emailPagadorPrueba = emailPagadorPrueba == null ? "" : emailPagadorPrueba.trim();
        moneda = moneda == null || moneda.isBlank() ? "ARS" : moneda;
        timeout = timeout == null ? Duration.ofSeconds(15) : timeout;
        exigirPago = exigirPago == null || exigirPago;
        cacheEstado = cacheEstado == null ? Duration.ofSeconds(30) : cacheEstado;
        diaVencimientoManual = diaVencimientoManual == null ? 10 : Math.max(1, Math.min(28, diaVencimientoManual));
    }

    public boolean habilitado() {
        return !mpAccessToken.isEmpty();
    }
}
