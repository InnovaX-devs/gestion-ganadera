package com.innovax.ganadera.sync;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.sync.*} en application.yml.
 *
 * @param margen el pull no entrega filas modificadas en los últimos {@code margen}: así una
 *     transacción que todavía no terminó no queda atrás del cursor (ver docs/sync.md)
 * @param limitePorDefecto filas de cambios por respuesta si el cliente no pide otro
 */
@ConfigurationProperties(prefix = "app.sync")
public record SyncProperties(Duration margen, int limitePorDefecto) {

    Duration margenValido() {
        return margen == null || margen.isNegative() ? Duration.ofSeconds(10) : margen;
    }

    int limiteValido() {
        return limitePorDefecto >= 1 && limitePorDefecto <= 1000 ? limitePorDefecto : 500;
    }
}
