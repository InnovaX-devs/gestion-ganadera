package com.innovax.ganadera.cuenta;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.innovax.ganadera.organizacion.Organizacion;
import com.innovax.ganadera.organizacion.OrganizacionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Si una organización está suspendida (#35). Se consulta en cada request autenticado, así que se
 * guarda un rato en memoria; al suspender o reactivar desde el panel se borra al instante.
 *
 * <p>Con más de una instancia del backend, en las otras el cambio tarda hasta {@link #VIGENCIA}.
 */
@Component
@RequiredArgsConstructor
public class EstadoCuentas {

    static final Duration VIGENCIA = Duration.ofSeconds(30);

    private record Leido(boolean suspendida, Instant hasta) {
    }

    private final OrganizacionRepository organizacionRepository;
    private final Clock reloj;
    private final Map<UUID, Leido> cache = new ConcurrentHashMap<>();

    public boolean suspendida(UUID organizacionId) {
        Instant ahora = reloj.instant();
        Leido leido = cache.get(organizacionId);
        if (leido == null || ahora.isAfter(leido.hasta())) {
            boolean suspendida = organizacionRepository.findById(organizacionId)
                .map(Organizacion::isSuspendida).orElse(false);
            leido = new Leido(suspendida, ahora.plus(VIGENCIA));
            cache.put(organizacionId, leido);
        }
        return leido.suspendida();
    }

    void olvidar(UUID organizacionId) {
        cache.remove(organizacionId);
    }
}
