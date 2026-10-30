package com.innovax.ganadera.mercado;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.mercado.*} en application.yml.
 *
 * @param fuentesExternas false: no se consulta ninguna fuente real (los tests usan fuentes falsas)
 * @param timeout tiempo máximo de conexión y de lectura de cada fuente
 */
@ConfigurationProperties(prefix = "app.mercado")
public record MercadoProperties(
    boolean fuentesExternas,
    String dolarApiUrl,
    String pizarraRosarioUrl,
    Duration timeout
) {
}
