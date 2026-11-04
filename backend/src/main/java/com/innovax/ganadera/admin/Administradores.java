package com.innovax.ganadera.admin;

import java.util.Locale;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.innovax.ganadera.usuario.Usuario;

import lombok.RequiredArgsConstructor;

/** Quién es del equipo de Innovax (administrador del sistema, no de una organización). */
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AdminProperties.class)
public class Administradores {

    private final AdminProperties properties;

    public boolean esAdministrador(String email) {
        return email != null && properties.emails().contains(email.trim().toLowerCase(Locale.ROOT));
    }

    static String nombreCompleto(Usuario u) {
        return u.getApellido() == null ? u.getNombre() : u.getNombre() + " " + u.getApellido();
    }

    /**
     * Email de quien entró al panel. La seguridad ya exige el rol ADMIN para {@code /api/admin/**};
     * esto es una segunda barrera por si algún día se llama desde otro lado.
     */
    public String exigir() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AdminAutenticado admin) {
            return admin.email();
        }
        throw new AccessDeniedException("Solo el equipo de Innovax puede entrar a la administración");
    }
}
