package com.innovax.ganadera.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuración de tokens: {@code app.jwt.*} en application.yml. */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
}
