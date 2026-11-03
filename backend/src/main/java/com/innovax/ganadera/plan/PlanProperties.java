package com.innovax.ganadera.plan;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.planes.*} en application.yml.
 *
 * @param diasPrueba días de prueba gratis de una organización nueva
 * @param planPrueba código del plan cuyos límites rigen durante la prueba
 */
@ConfigurationProperties(prefix = "app.planes")
public record PlanProperties(int diasPrueba, String planPrueba) {
}
