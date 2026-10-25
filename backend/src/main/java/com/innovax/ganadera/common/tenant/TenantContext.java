package com.innovax.ganadera.common.tenant;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Acceso al usuario y la organización del request actual. */
public final class TenantContext {

    private TenantContext() {
    }

    public static Optional<AuthenticatedUser> usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser usuario) {
            return Optional.of(usuario);
        }
        return Optional.empty();
    }

    public static Optional<UUID> organizacionIdActual() {
        return usuarioActual().map(AuthenticatedUser::organizacionId);
    }

    /** Para usar en servicios que sí o sí necesitan un usuario logueado. */
    public static AuthenticatedUser requerirUsuario() {
        return usuarioActual().orElseThrow(() -> new AccessDeniedException("No hay un usuario autenticado"));
    }
}
