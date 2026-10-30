package com.innovax.ganadera.clima;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.clima.*} en application.yml.
 *
 * @param apiKey solo para el plan comercial de Open-Meteo (con {@code url} apuntando a
 *     customer-api.open-meteo.com). Vacía = API gratuita. Nunca llega al frontend.
 * @param cache cuánto se reutiliza un pronóstico antes de volver a pedirlo
 */
@ConfigurationProperties(prefix = "app.clima")
public record ClimaProperties(
    String url,
    String geocodingUrl,
    String apiKey,
    int dias,
    Duration cache,
    Duration timeout
) {
}
