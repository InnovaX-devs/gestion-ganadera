package com.innovax.ganadera.common.tenant;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Acceso al usuario y la organización del request actual. */
public final class TenantContext {

    /** Organización fijada a mano para procesos sin usuario logueado (ver {@link #comoOrganizacion}). */
    private static final ThreadLocal<UUID> ORGANIZACION_FIJADA = new ThreadLocal<>();

    private TenantContext() {
    }

    /**
     * Ejecuta la acción "como si" fuera de esa organización, para los casos sin usuario logueado
     * (ej: aceptar una invitación desde el link). La organización tiene que salir de un dato
     * confiable de la base (la invitación), nunca de un parámetro del request.
     *
     * <p>Hibernate toma la organización cuando abre la sesión, o sea al empezar la transacción:
     * hay que llamar a este método <b>antes</b> de entrar a un método {@code @Transactional}.
     */
    public static <T> T comoOrganizacion(UUID organizacionId, Supplier<T> accion) {
        UUID anterior = ORGANIZACION_FIJADA.get();
        ORGANIZACION_FIJADA.set(organizacionId);
        try {
            return accion.get();
        } finally {
            if (anterior == null) {
                ORGANIZACION_FIJADA.remove();
            } else {
                ORGANIZACION_FIJADA.set(anterior);
            }
        }
    }

    public static Optional<AuthenticatedUser> usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser usuario) {
            return Optional.of(usuario);
        }
        return Optional.empty();
    }

    public static Optional<UUID> organizacionIdActual() {
        return Optional.ofNullable(ORGANIZACION_FIJADA.get())
            .or(() -> usuarioActual().map(AuthenticatedUser::organizacionId));
    }

    /** Para usar en servicios que sí o sí necesitan un usuario logueado. */
    public static AuthenticatedUser requerirUsuario() {
        return usuarioActual().orElseThrow(() -> new AccessDeniedException("No hay un usuario autenticado"));
    }
}
