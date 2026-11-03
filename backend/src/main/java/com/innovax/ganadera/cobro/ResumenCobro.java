package com.innovax.ganadera.cobro;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.innovax.ganadera.cobro.dto.CobroResponse;
import com.innovax.ganadera.organizacion.Organizacion;
import com.innovax.ganadera.organizacion.OrganizacionRepository;
import com.innovax.ganadera.plan.Plan;
import com.innovax.ganadera.plan.PlanRepository;

import lombok.RequiredArgsConstructor;

/** Arma el {@link CobroResponse} de una organización para "Mi plan" y el panel. */
@Component
@RequiredArgsConstructor
public class ResumenCobro {

    private static final EnumSet<EstadoSuscripcion> MOSTRABLES = EnumSet.of(EstadoSuscripcion.ACTIVA,
        EstadoSuscripcion.PAUSADA, EstadoSuscripcion.CANCELADA, EstadoSuscripcion.VENCIDA);

    private final SuscripcionRepository repository;
    private final EstadoPago estadoPago;
    private final OrganizacionRepository organizacionRepository;
    private final PlanRepository planRepository;
    private final CobroProperties propiedades;
    private final Clock reloj;

    /** Requiere una transacción abierta. */
    public CobroResponse de(UUID organizacionId) {
        LocalDate hoy = LocalDate.now(reloj);
        int gracia = propiedades.diasGracia();
        List<Suscripcion> todas = repository.findByOrganizacionIdAndEstadoInOrderByCreatedAtDesc(organizacionId,
            EnumSet.complementOf(EnumSet.of(EstadoSuscripcion.REEMPLAZADA)));
        CobroResponse.Suscripcion actual = todas.stream()
            .filter(s -> MOSTRABLES.contains(s.getEstado()))
            .findFirst()
            .map(s -> new CobroResponse.Suscripcion(s.getPlanCodigo(), nombre(s.getPlanCodigo()), s.getMonto(),
                s.getEstado().name(), s.getPagadoHasta(), s.getPrimerCobro(), s.enGracia(hoy, gracia), s.limite(gracia)))
            .orElse(null);
        CobroResponse.Pendiente pendiente = todas.stream()
            .filter(s -> s.getEstado() == EstadoSuscripcion.PENDIENTE && s.getInitPoint() != null)
            .findFirst()
            .map(s -> new CobroResponse.Pendiente(s.getPlanCodigo(), nombre(s.getPlanCodigo()), s.getMonto(),
                s.getInitPoint(), s.getPrimerCobro()))
            .orElse(null);
        LocalDate pagoManual = organizacionRepository.findById(organizacionId)
            .map(Organizacion::getPagoManualHasta).orElse(null);
        return new CobroResponse(propiedades.habilitado(), estadoPago.calcular(organizacionId), pagoManual,
            propiedades.diaVencimientoManual(), gracia,
            actual, pendiente);
    }

    private String nombre(String codigo) {
        return planRepository.findById(codigo).map(Plan::getNombre).orElse(codigo);
    }
}
