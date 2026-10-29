package com.innovax.ganadera.common.tenant;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Acceso al usuario y la organización del request actual.
 *
 * <p>Los procesos sin usuario (jobs programados) fijan la organización con
 * {@link #ejecutarComo(UUID, Supplier)}: mientras dura la tarea, Hibernate filtra y completa
 * {@code organizacion_id} con esa organización, igual que en un request.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> ORGANIZACION_FIJADA = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Optional<AuthenticatedUser> usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser usuario) {
            return Optional.of(usuario);
        }
        return Optional.empty();
    }

    /** La organización fijada por un proceso sin usuario o, si no hay, la del usuario logueado. */
    public static Optional<UUID> organizacionIdActual() {
        UUID fijada = ORGANIZACION_FIJADA.get();
        if (fijada != null) return Optional.of(fijada);
        return usuarioActual().map(AuthenticatedUser::organizacionId);
    }

    /** Para usar en servicios que sí o sí necesitan un usuario logueado. */
    public static AuthenticatedUser requerirUsuario() {
        return usuarioActual().orElseThrow(() -> new AccessDeniedException("No hay un usuario autenticado"));
    }

    /**
     * Ejecuta la tarea como si fuera de esa organización. Es para procesos sin usuario (jobs): la
     * transacción tiene que abrirse <b>dentro</b> de la tarea, porque Hibernate fija la organización
     * al abrir la sesión.
     *
     * <pre>
     * int cambios = TenantContext.ejecutarComo(organizacionId, () -&gt; servicio.recalcular(hoy));
     * </pre>
     *
     * @throws IllegalStateException si hay un usuario logueado de otra organización (sería una
     *                               forma de saltarse el aislamiento)
     */
    public static <T> T ejecutarComo(UUID organizacionId, Supplier<T> tarea) {
        Objects.requireNonNull(organizacionId, "organizacionId");
        usuarioActual().ifPresent(usuario -> {
            if (!usuario.organizacionId().equals(organizacionId)) {
                throw new IllegalStateException("No se puede operar sobre otra organización con un usuario logueado");
            }
        });
        UUID anterior = ORGANIZACION_FIJADA.get();
        ORGANIZACION_FIJADA.set(organizacionId);
        try {
            return tarea.get();
        } finally {
            if (anterior == null) {
                ORGANIZACION_FIJADA.remove();
            } else {
                ORGANIZACION_FIJADA.set(anterior);
            }
        }
    }

    /** Igual que {@link #ejecutarComo(UUID, Supplier)}, para tareas que no devuelven nada. */
    public static void ejecutarSinResultadoComo(UUID organizacionId, Runnable tarea) {
        ejecutarComo(organizacionId, () -> {
            tarea.run();
            return null;
        });
    }
}
