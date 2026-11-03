package com.innovax.ganadera.admin;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.admin.*}: acceso al panel de administración del equipo de Innovax ({@code /admin} en la
 * web). No usa las cuentas de los clientes ni se guarda en la base:
 *
 * <ul>
 *   <li>{@code emails} (ADMIN_EMAILS): quiénes pueden entrar, separados por coma.</li>
 *   <li>{@code password} (ADMIN_PASSWORD): la contraseña del panel. Vacía = panel cerrado.</li>
 *   <li>{@code sesion} (ADMIN_SESION): cuánto dura la sesión antes de pedir la contraseña de nuevo.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(List<String> emails, String password, Duration sesion) {

    public AdminProperties {
        emails = emails == null ? List.of()
            : emails.stream().map(e -> e.trim().toLowerCase()).filter(e -> !e.isEmpty()).toList();
        password = password == null ? "" : password;
        sesion = sesion == null ? Duration.ofHours(8) : sesion;
    }
}
