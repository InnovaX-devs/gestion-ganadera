package com.innovax.ganadera.cobro;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.innovax.ganadera.cobro.MercadoPago.Cuota;
import com.innovax.ganadera.cobro.MercadoPago.NuevaSuscripcion;
import com.innovax.ganadera.cobro.MercadoPago.Preapproval;
import com.innovax.ganadera.cobro.dto.SuscribirResponse;
import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.common.tenant.AuthenticatedUser;
import com.innovax.ganadera.common.tenant.TenantContext;
import com.innovax.ganadera.invitacion.InvitacionProperties;
import com.innovax.ganadera.organizacion.OrganizacionRepository;
import com.innovax.ganadera.plan.Plan;
import com.innovax.ganadera.plan.PlanRepository;
import com.innovax.ganadera.plan.PlanService;
import com.innovax.ganadera.usuario.Usuario;
import com.innovax.ganadera.usuario.UsuarioRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Suscripciones mensuales con Mercado Pago (#35).
 *
 * <ol>
 *   <li>El dueño de la cuenta elige un plan en "Mi plan": se crea la suscripción en Mercado Pago y
 *       el cliente la paga allá ({@link #suscribir}).</li>
 *   <li>Mercado Pago avisa por webhook cada cambio y cada cobro; también se puede pedir a mano
 *       ({@link #sincronizarOrganizacion}). Siempre se consulta el estado real a Mercado Pago: el
 *       aviso solo dice qué cambió.</li>
 *   <li>Autorizada: el plan contratado pasa a ser el de la suscripción. Cada cobro aprobado paga un
 *       mes más.</li>
 *   <li>Si se termina lo pago y la gracia (o lo pago, si la canceló), la suscripción vence y la
 *       cuenta queda en solo lectura hasta que pague ({@link EstadoPago}). No se borra nada.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuscripcionService {

    private static final EnumSet<EstadoSuscripcion> A_REEMPLAZAR = EnumSet.of(EstadoSuscripcion.PENDIENTE,
        EstadoSuscripcion.ACTIVA, EstadoSuscripcion.PAUSADA, EstadoSuscripcion.CANCELADA, EstadoSuscripcion.VENCIDA);

    private final SuscripcionRepository repository;
    private final PlanRepository planRepository;
    private final PlanService planService;
    private final UsuarioRepository usuarioRepository;
    private final OrganizacionRepository organizacionRepository;
    private final MercadoPago mercadoPago;
    private final EstadoPago estadoPago;
    private final CobroProperties propiedades;
    private final InvitacionProperties web;
    private final Clock reloj;

    // ---------- Lado del cliente ----------

    /** Crea la suscripción en Mercado Pago y devuelve el link para pagarla. */
    @Transactional
    public SuscribirResponse suscribir(String planCodigo) {
        AuthenticatedUser usuario = exigirDueno();
        if (!propiedades.habilitado()) {
            throw new BusinessException("El pago online todavía no está disponible. Escribinos a Innovax para cambiar de plan.");
        }
        Plan plan = planRepository.findById(planCodigo.trim().toUpperCase(Locale.ROOT))
            .orElseThrow(() -> new BusinessException("El plan " + planCodigo + " no existe"));
        BigDecimal precio = plan.getPrecioMensual();
        if (precio == null || precio.signum() <= 0) {
            throw new BusinessException("El plan " + plan.getNombre() + " no se puede contratar online.");
        }
        UUID organizacionId = usuario.organizacionId();
        List<Suscripcion> previas = repository.findByOrganizacionIdAndEstadoInOrderByCreatedAtDesc(organizacionId,
            EnumSet.of(EstadoSuscripcion.PENDIENTE, EstadoSuscripcion.ACTIVA, EstadoSuscripcion.PAUSADA));
        if (previas.stream().anyMatch(s -> s.getEstado() != EstadoSuscripcion.PENDIENTE
            && s.getPlanCodigo().equals(plan.getCodigo()))) {
            throw new BusinessException("Ya tenés una suscripción al plan " + plan.getNombre() + ".");
        }
        // Un link anterior sin pagar deja de valer: se paga solo el último plan elegido
        previas.stream().filter(s -> s.getEstado() == EstadoSuscripcion.PENDIENTE).forEach(s -> {
            s.setEstado(EstadoSuscripcion.REEMPLAZADA);
            cancelarEnMercadoPago(s);
        });

        Suscripcion suscripcion = repository.save(new Suscripcion(organizacionId, plan.getCodigo(), precio));
        suscripcion.setPrimerCobro(primerCobro(organizacionId));
        String email = propiedades.emailPagadorPrueba().isEmpty() ? usuario.email() : propiedades.emailPagadorPrueba();
        Preapproval creada = mercadoPago.crear(new NuevaSuscripcion("Gestión Ganadera - Plan " + plan.getNombre(),
            suscripcion.getId().toString(), email, precio, propiedades.moneda(), urlRetorno(),
            suscripcion.getPrimerCobro()));
        suscripcion.setMpId(creada.id());
        suscripcion.setInitPoint(creada.initPoint());
        log.info("Cobro: la organización {} empezó a suscribirse al plan {} ({} {})", organizacionId,
            plan.getCodigo(), precio, propiedades.moneda());
        return new SuscribirResponse(creada.initPoint());
    }

    /** Cancela la suscripción: no se cobra más y el plan sigue hasta el último día pago. */
    @Transactional
    public void cancelar() {
        UUID organizacionId = exigirDueno().organizacionId();
        Suscripcion suscripcion = repository.findByOrganizacionIdAndEstadoInOrderByCreatedAtDesc(organizacionId,
                EnumSet.of(EstadoSuscripcion.ACTIVA, EstadoSuscripcion.PAUSADA)).stream().findFirst()
            .orElseThrow(() -> new BusinessException("No tenés una suscripción activa."));
        mercadoPago.cancelar(suscripcion.getMpId());
        suscripcion.setEstado(EstadoSuscripcion.CANCELADA);
        estadoPago.olvidar(organizacionId);
        log.info("Cobro: la organización {} canceló su suscripción al plan {}", organizacionId,
            suscripcion.getPlanCodigo());
    }

    /**
     * Consulta a Mercado Pago las suscripciones de la organización del usuario. La web lo llama al
     * volver de pagar, por si el aviso de Mercado Pago todavía no llegó (o no puede llegar, como en
     * desarrollo).
     */
    @Transactional
    public void sincronizarOrganizacion() {
        if (!propiedades.habilitado()) return;
        UUID organizacionId = TenantContext.requerirUsuario().organizacionId();
        repository.findByOrganizacionIdAndEstadoInOrderByCreatedAtDesc(organizacionId,
                EnumSet.of(EstadoSuscripcion.PENDIENTE, EstadoSuscripcion.ACTIVA, EstadoSuscripcion.PAUSADA))
            .stream().filter(s -> s.getMpId() != null).forEach(this::sincronizar);
    }

    // ---------- Avisos de Mercado Pago ----------

    /**
     * Aviso (webhook) de Mercado Pago. {@code tipo}: subscription_preapproval (cambió la
     * suscripción) o subscription_authorized_payment (hubo un cobro). Los demás se ignoran.
     *
     * @return si el aviso era de una suscripción nuestra
     */
    @Transactional
    public boolean procesarAviso(String tipo, String id) {
        if (tipo == null || id == null || !propiedades.habilitado()) return false;
        Optional<Suscripcion> suscripcion = switch (tipo) {
            case "subscription_preapproval", "preapproval" -> porPreapproval(id);
            case "subscription_authorized_payment", "authorized_payment" ->
                Optional.ofNullable(mercadoPago.cuota(id).preapprovalId()).flatMap(this::porPreapproval);
            default -> Optional.empty();
        };
        suscripcion.ifPresent(this::sincronizar);
        return suscripcion.isPresent();
    }

    private Optional<Suscripcion> porPreapproval(String preapprovalId) {
        Optional<Suscripcion> conocida = repository.findByMpId(preapprovalId);
        if (conocida.isPresent()) return conocida;
        // Por si el aviso llegó antes de guardar el id: se busca por nuestra referencia
        Preapproval p = mercadoPago.obtener(preapprovalId);
        try {
            return Optional.ofNullable(p.referencia()).map(UUID::fromString).flatMap(repository::findById)
                .map(s -> {
                    s.setMpId(preapprovalId);
                    return s;
                });
        } catch (IllegalArgumentException e) {
            return Optional.empty(); // no es de este sistema
        }
    }

    /** Trae de Mercado Pago el estado y los cobros de la suscripción y los aplica. */
    void sincronizar(Suscripcion s) {
        Preapproval p = mercadoPago.obtener(s.getMpId());
        LocalDate pagoHasta = mercadoPago.cuotas(s.getMpId()).stream()
            .filter(Cuota::aprobada)
            .map(Cuota::fecha)
            .filter(f -> f != null)
            .max(LocalDate::compareTo)
            .map(f -> f.plusMonths(1))
            .orElse(null);
        aplicar(s, EstadoSuscripcion.deMercadoPago(p.status()), pagoHasta);
    }

    private void aplicar(Suscripcion s, EstadoSuscripcion enMercadoPago, LocalDate pagoHasta) {
        if (s.getEstado() == EstadoSuscripcion.REEMPLAZADA) return;
        LocalDate hoy = LocalDate.now(reloj);
        EstadoSuscripcion antes = s.getEstado();
        s.pagadoHasta(pagoHasta);
        switch (enMercadoPago) {
            case ACTIVA -> {
                if (s.getActivadaEl() == null) s.setActivadaEl(hoy);
                boolean volvio = antes == EstadoSuscripcion.VENCIDA && s.getPagadoHasta() != null
                    && !s.getPagadoHasta().isBefore(hoy);
                if (antes == EstadoSuscripcion.PENDIENTE || volvio) {
                    s.setEstado(EstadoSuscripcion.ACTIVA);
                    activarPlan(s);
                } else if (antes == EstadoSuscripcion.PAUSADA) {
                    s.setEstado(EstadoSuscripcion.ACTIVA);
                }
            }
            case PAUSADA -> {
                if (antes == EstadoSuscripcion.ACTIVA) s.setEstado(EstadoSuscripcion.PAUSADA);
            }
            case CANCELADA -> {
                if (antes == EstadoSuscripcion.ACTIVA || antes == EstadoSuscripcion.PAUSADA
                    || antes == EstadoSuscripcion.PENDIENTE) {
                    s.setEstado(EstadoSuscripcion.CANCELADA);
                }
            }
            default -> {
                // Sigue pendiente de pago
            }
        }
        estadoPago.olvidar(s.getOrganizacionId());
        if (antes != s.getEstado()) {
            log.info("Cobro: suscripción {} de la organización {} pasó de {} a {} (pagado hasta {})", s.getId(),
                s.getOrganizacionId(), antes, s.getEstado(), s.getPagadoHasta());
        }
    }

    /** Rige el plan de la suscripción; las demás de la organización quedan reemplazadas. */
    private void activarPlan(Suscripcion s) {
        planService.cambiarContratado(s.getOrganizacionId(), s.getPlanCodigo());
        repository.findByOrganizacionIdAndEstadoInOrderByCreatedAtDesc(s.getOrganizacionId(), A_REEMPLAZAR).stream()
            .filter(otra -> !otra.getId().equals(s.getId()))
            .forEach(otra -> {
                boolean cobrable = otra.getEstado() == EstadoSuscripcion.ACTIVA
                    || otra.getEstado() == EstadoSuscripcion.PAUSADA || otra.getEstado() == EstadoSuscripcion.PENDIENTE;
                otra.setEstado(EstadoSuscripcion.REEMPLAZADA);
                if (cobrable) cancelarEnMercadoPago(otra);
            });
    }

    private void cancelarEnMercadoPago(Suscripcion s) {
        if (s.getMpId() == null) return;
        try {
            mercadoPago.cancelar(s.getMpId());
        } catch (MercadoPagoException e) {
            // Se registra y se sigue: el cliente puede cancelarla también desde su cuenta de Mercado Pago
            log.warn("Cobro: no se pudo cancelar en Mercado Pago la suscripción {} ({})", s.getId(), s.getMpId());
        }
    }

    // ---------- Vencimientos (job diario) ----------

    /**
     * Las suscripciones que se quedaron sin días pagos (ni de gracia) pasan a VENCIDA: la cuenta
     * queda sin uso pago (solo lectura). Antes de vencer una se consulta a Mercado Pago, por si
     * un cobro aprobado no llegó a avisarse.
     *
     * @return cuántas vencieron
     */
    @Transactional
    public int vencer(LocalDate hoy) {
        int gracia = propiedades.diasGracia();
        int vencidas = 0;
        for (Suscripcion s : repository.findByEstadoIn(EnumSet.of(EstadoSuscripcion.ACTIVA,
            EstadoSuscripcion.PAUSADA, EstadoSuscripcion.CANCELADA))) {
            if (!s.vencida(hoy, gracia)) continue;
            if (s.getEstado() != EstadoSuscripcion.CANCELADA && s.getMpId() != null && propiedades.habilitado()) {
                try {
                    sincronizar(s);
                } catch (MercadoPagoException e) {
                    log.warn("Cobro: no se pudo consultar la suscripción {} antes de vencerla", s.getId());
                }
                if (!s.vencida(hoy, gracia)) continue;
            }
            s.setEstado(EstadoSuscripcion.VENCIDA);
            estadoPago.olvidar(s.getOrganizacionId());
            vencidas++;
            log.info("Cobro: venció la suscripción {} de la organización {} (pagado hasta {}): queda sin uso pago",
                s.getId(), s.getOrganizacionId(), s.getPagadoHasta());
        }
        return vencidas;
    }

    // ---------- Ayudas ----------

    /**
     * Si todavía tiene la prueba gratis (o un pago manual) por delante, el primer cobro es el día
     * después de que termina: no paga dos veces por los mismos días. Null: se cobra enseguida.
     */
    private LocalDate primerCobro(UUID organizacionId) {
        LocalDate hoy = LocalDate.now(reloj);
        return organizacionRepository.findById(organizacionId)
            .map(o -> Stream.of(o.getPruebaHasta(), o.getPagoManualHasta())
                .filter(d -> d != null && !d.isBefore(hoy))
                .max(LocalDate::compareTo)
                .map(d -> d.plusDays(1))
                .orElse(null))
            .orElse(null);
    }

    /** MP_BACK_URL, o la página Mi plan de la web si quedó vacía. */
    private String urlRetorno() {
        String url = propiedades.backUrl();
        return url == null || url.isBlank() ? web.webUrl() + "/plan" : url;
    }

    /** La suscripción la maneja el dueño de la cuenta (quien la creó), igual que en el panel. */
    private AuthenticatedUser exigirDueno() {
        AuthenticatedUser usuario = TenantContext.requerirUsuario();
        Optional<Usuario> dueno = usuarioRepository.findFirstByOrganizacionIdOrderByCreatedAtAsc(usuario.organizacionId());
        if (dueno.isEmpty() || !dueno.get().getId().equals(usuario.userId())) {
            throw new BusinessException("Solo el dueño de la cuenta"
                + dueno.map(d -> " (" + d.getEmail() + ")").orElse("") + " puede manejar la suscripción.");
        }
        return usuario;
    }
}
