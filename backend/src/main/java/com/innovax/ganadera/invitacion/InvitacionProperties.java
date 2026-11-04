package com.innovax.ganadera.invitacion;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code app.invitaciones.*} en application.yml. */
@ConfigurationProperties(prefix = "app.invitaciones")
public record InvitacionProperties(String webUrl, Duration ttl) {
}
