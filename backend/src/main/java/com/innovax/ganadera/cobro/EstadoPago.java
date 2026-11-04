package com.innovax.ganadera.cobro;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.innovax.ganadera.organizacion.Organizacion;
import com.innovax.ganadera.organizacion.OrganizacionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Si una cuenta tiene el uso pago (#35). Ningún plan es gratis: está habilitada si tiene la prueba
 * gratis vigente, un pago manual vigente (cargado desde el panel) o una suscripción al día (activa
 * o pausada hasta lo pago + la gracia; cancelada hasta lo pago). Si no, queda en solo lectura
 * hasta que pague: ver {@link PagoRequeridoInterceptor}.
 *
 * <p>Se consulta en cada request, así que se recuerda unos segundos ({@code app.cobro.cache-estado}).
 * Lo que cambia el estado llama a {@link #olvidar}.
 */
@Component
@RequiredArgsConstructor
public class EstadoPago {

    private static final EnumSet<EstadoSuscripcion> VIGENTES = EnumSet.of(EstadoSuscripcion.ACTIVA,
        EstadoSuscripcion.PAUSADA, EstadoSuscripcion.CANCELADA);

    private final OrganizacionRepository organizacionRepository;
    private final SuscripcionRepository suscripcionRepository;
    private final CobroProperties propiedades;
    private final Clock reloj;

    private record Leido(boolean habilitada, Instant vence) {
    }

    private final Map<UUID, Leido> cache = new ConcurrentHashMap<>();

    /** Con caché: para el control de cada request. */
    public boolean habilitada(UUID organizacionId) {
        if (!propiedades.exigirPago()) return true;
        Instant ahora = reloj.instant();
        Leido leido = cache.get(organizacionId);
        if (leido == null || !ahora.isBefore(leido.vence())) {
            leido = new Leido(calcular(organizacionId), ahora.plus(propiedades.cacheEstado()));
            cache.put(organizacionId, leido);
        }
        return leido.habilitada();
    }

    /** Sin caché: para mostrar el estado en Mi plan y el panel. */
    public boolean calcular(UUID organizacionId) {
        if (!propiedades.exigirPago()) return true;
        Organizacion organizacion = organizacionRepository.findById(organizacionId).orElse(null);
        if (organizacion == null) return true;
        LocalDate hoy = LocalDate.now(reloj);
        if (vigente(organizacion.getPruebaHasta(), hoy) || vigente(organizacion.getPagoManualHasta(), hoy)) {
            return true;
        }
        int gracia = propiedades.diasGracia();
        return suscripcionRepository.findByOrganizacionIdAndEstadoInOrderByCreatedAtDesc(organizacionId, VIGENTES)
            .stream().anyMatch(s -> vigente(s.limite(gracia), hoy));
    }

    public void olvidar(UUID organizacionId) {
        cache.remove(organizacionId);
    }

    private static boolean vigente(LocalDate hasta, LocalDate hoy) {
        return hasta != null && !hoy.isAfter(hasta);
    }
}
