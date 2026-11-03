package com.innovax.ganadera.plan;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.innovax.ganadera.animal.AnimalRepository;
import com.innovax.ganadera.animal.EstadoAnimal;
import com.innovax.ganadera.cobro.CobroProperties;
import com.innovax.ganadera.cobro.EstadoPago;
import com.innovax.ganadera.cobro.PagoManual;
import com.innovax.ganadera.cobro.ResumenCobro;
import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.common.error.LimitePlanException;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.common.tenant.TenantContext;
import com.innovax.ganadera.establecimiento.EstablecimientoRepository;
import com.innovax.ganadera.invitacion.InvitacionRepository;
import com.innovax.ganadera.organizacion.Organizacion;
import com.innovax.ganadera.organizacion.OrganizacionRepository;
import com.innovax.ganadera.plan.dto.PlanResponse;
import com.innovax.ganadera.plan.dto.PlanResponse.PlanInfo;
import com.innovax.ganadera.usuario.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Planes y límites (#35). Una organización nueva arranca con una prueba gratis: durante la prueba
 * rigen los límites del plan de prueba ({@code app.planes.plan-prueba}); después, los del plan
 * contratado (Inicial si no contrató otro).
 *
 * <p>El bloqueo es "suave": se frena lo que agrega (alta e importación de animales, nuevos
 * establecimientos, invitaciones), pero nunca lo que viene del campo. Un nacimiento o una carga
 * sin conexión que se sincroniza se guardan aunque pasen el límite, y todo lo cargado se puede
 * seguir viendo y editando.
 */
@Service
@RequiredArgsConstructor
public class PlanService {

    private final PlanRepository planRepository;
    private final OrganizacionRepository organizacionRepository;
    private final AnimalRepository animalRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final UsuarioRepository usuarioRepository;
    private final InvitacionRepository invitacionRepository;
    private final PlanProperties properties;
    private final ResumenCobro resumenCobro;
    private final EstadoPago estadoPago;
    private final CobroProperties cobroProperties;
    private final Clock reloj;

    /** Al registrar una organización: arranca la prueba gratis. */
    public void iniciarPrueba(Organizacion organizacion) {
        organizacion.setPruebaHasta(LocalDate.now(reloj).plusDays(properties.diasPrueba() - 1L));
    }

    @Transactional(readOnly = true)
    public PlanResponse actual() {
        return resumen(TenantContext.requerirUsuario().organizacionId());
    }

    /**
     * Plan y uso de una organización. Los conteos de animales, establecimientos e invitaciones se
     * filtran por la organización del contexto: el panel de administración lo llama dentro de
     * {@code TenantContext.comoOrganizacion} (ver docs/decisiones/0003).
     */
    @Transactional(readOnly = true)
    public PlanResponse resumen(UUID organizacionId) {
        Vigente v = vigente(organizacionId);
        LocalDate hoy = LocalDate.now(reloj);
        int dias = v.enPrueba() ? (int) ChronoUnit.DAYS.between(hoy, v.pruebaHasta()) + 1 : 0;
        List<PlanInfo> planes = planRepository.findAllByOrderByOrdenAsc().stream().map(PlanInfo::de).toList();
        var uso = new PlanResponse.Uso(
            animalRepository.countByEstado(EstadoAnimal.ACTIVO),
            establecimientoRepository.countByActivoTrue(),
            usuarioRepository.countByOrganizacionIdAndActivoTrue(organizacionId),
            invitacionRepository.emailsPendientes(Instant.now(reloj), organizacionId).size());
        return new PlanResponse(PlanInfo.de(v.plan()), PlanInfo.de(v.contratado()), v.enPrueba(),
            v.pruebaHasta(), dias, uso, planes, resumenCobro.de(organizacionId));
    }

    /**
     * Cambia el plan contratado y el fin de la prueba de una organización (panel de administración).
     * {@code pruebaHasta} null: sin prueba.
     */
    @Transactional
    public void asignar(UUID organizacionId, String planCodigo, LocalDate pruebaHasta) {
        Organizacion organizacion = organizacionRepository.findById(organizacionId)
            .orElseThrow(() -> NotFoundException.of("Organización", organizacionId));
        organizacion.setPlanCodigo(plan(planCodigo).getCodigo());
        organizacion.setPruebaHasta(pruebaHasta);
        estadoPago.olvidar(organizacionId);
    }

    /** Pago por fuera de Mercado Pago hasta ese día inclusive (panel); null lo quita. */
    @Transactional
    public void pagoManual(UUID organizacionId, LocalDate hasta) {
        Organizacion organizacion = organizacionRepository.findById(organizacionId)
            .orElseThrow(() -> NotFoundException.of("Organización", organizacionId));
        organizacion.setPagoManualHasta(hasta);
        estadoPago.olvidar(organizacionId);
    }

    /**
     * Registra un pago por transferencia (panel): queda pagada hasta el vencimiento del mes que viene,
     * o un mes más si ya estaba al día. Ver {@link PagoManual}.
     *
     * @return hasta qué día quedó pagada
     */
    @Transactional
    public LocalDate registrarPagoManual(UUID organizacionId) {
        Organizacion organizacion = organizacionRepository.findById(organizacionId)
            .orElseThrow(() -> NotFoundException.of("Organización", organizacionId));
        LocalDate hasta = PagoManual.siguienteVencimiento(organizacion.getPagoManualHasta(), LocalDate.now(reloj),
            cobroProperties.diaVencimientoManual());
        organizacion.setPagoManualHasta(hasta);
        estadoPago.olvidar(organizacionId);
        return hasta;
    }

    /** Cambia solo el plan contratado, sin tocar la prueba (suscripción con Mercado Pago). */
    @Transactional
    public void cambiarContratado(UUID organizacionId, String planCodigo) {
        Organizacion organizacion = organizacionRepository.findById(organizacionId)
            .orElseThrow(() -> NotFoundException.of("Organización", organizacionId));
        organizacion.setPlanCodigo(plan(planCodigo).getCodigo());
    }

    /** Todos los planes, de menor a mayor. */
    @Transactional(readOnly = true)
    public List<PlanInfo> planes() {
        return planRepository.findAllByOrderByOrdenAsc().stream().map(PlanInfo::de).toList();
    }

    /** Precio mensual de un plan (panel de administración). Null: no se contrata online. */
    @Transactional
    public PlanInfo cambiarPrecio(String planCodigo, BigDecimal precioMensual) {
        Plan plan = plan(planCodigo);
        plan.setPrecioMensual(precioMensual);
        return PlanInfo.de(plan);
    }

    // ---------- Límites ----------

    /** Antes de dar de alta animales a mano o por planilla. */
    @Transactional(readOnly = true)
    public void exigirLugarParaAnimales(int nuevos) {
        Plan plan = vigente(TenantContext.requerirUsuario().organizacionId()).plan();
        Integer maximo = plan.getMaxAnimales();
        if (maximo == null) return;
        long actuales = animalRepository.countByEstado(EstadoAnimal.ACTIVO);
        if (actuales + nuevos <= maximo) return;
        long libres = Math.max(0, maximo - actuales);
        if (nuevos == 1 || libres == 0) {
            throw new LimitePlanException("Llegaste al límite de " + maximo + " animales del plan "
                + plan.getNombre() + ". Para cargar más, pasá a un plan mayor desde Mi plan.");
        }
        throw new LimitePlanException("La planilla tiene " + nuevos + " animales y el plan " + plan.getNombre()
            + " permite " + maximo + " (ya tenés " + actuales + "): te quedan " + libres
            + " lugares. Pasá a un plan mayor desde Mi plan, o importá menos animales.");
    }

    /** Antes de crear un establecimiento. */
    @Transactional(readOnly = true)
    public void exigirLugarParaEstablecimiento() {
        Plan plan = vigente(TenantContext.requerirUsuario().organizacionId()).plan();
        Integer maximo = plan.getMaxEstablecimientos();
        if (maximo == null || establecimientoRepository.countByActivoTrue() < maximo) return;
        throw new LimitePlanException("El plan " + plan.getNombre() + " permite " + maximo
            + (maximo == 1 ? " establecimiento" : " establecimientos")
            + ". Para agregar otro, pasá a un plan mayor desde Mi plan.");
    }

    /**
     * Antes de invitar a alguien: si no es usuario todavía, va a ocupar un lugar. Las invitaciones
     * pendientes ya lo reservan, así que no se puede invitar más gente que lugares libres.
     */
    @Transactional(readOnly = true)
    public void exigirLugarParaInvitar(String email) {
        UUID organizacionId = TenantContext.requerirUsuario().organizacionId();
        Plan plan = vigente(organizacionId).plan();
        Integer maximo = plan.getMaxUsuarios();
        if (maximo == null) return;
        List<String> pendientes = invitacionRepository.emailsPendientes(Instant.now(reloj), organizacionId);
        if (pendientes.contains(email)) return; // ya tiene su lugar reservado
        long ocupados = usuarioRepository.countByOrganizacionIdAndActivoTrue(organizacionId) + pendientes.size();
        if (ocupados < maximo) return;
        throw new LimitePlanException("El plan " + plan.getNombre() + " permite " + maximo + " usuarios"
            + (pendientes.isEmpty() ? "" : ", contando las invitaciones pendientes")
            + ". Para sumar a alguien más, pasá a un plan mayor desde Mi plan.");
    }

    /**
     * Al aceptar una invitación creando la cuenta. Normalmente hay lugar (la invitación lo
     * reservó), salvo que el plan haya bajado entre que se invitó y se aceptó. No hay usuario
     * logueado: la organización va explícita.
     */
    @Transactional(readOnly = true)
    public void exigirLugarParaUsuarioNuevo(UUID organizacionId) {
        Plan plan = vigente(organizacionId).plan();
        Integer maximo = plan.getMaxUsuarios();
        if (maximo == null || usuarioRepository.countByOrganizacionIdAndActivoTrue(organizacionId) < maximo) return;
        throw new LimitePlanException("La cuenta ya tiene los " + maximo + " usuarios que permite su plan. "
            + "Pedile a quien te invitó que pase a un plan mayor.");
    }

    // ---------- Plan vigente ----------

    private record Vigente(Plan plan, Plan contratado, boolean enPrueba, LocalDate pruebaHasta) {
    }

    private Vigente vigente(UUID organizacionId) {
        Organizacion organizacion = organizacionRepository.findById(organizacionId)
            .orElseThrow(() -> NotFoundException.of("Organización", organizacionId));
        Plan contratado = plan(organizacion.getPlanCodigo());
        LocalDate hasta = organizacion.getPruebaHasta();
        boolean enPrueba = hasta != null && !LocalDate.now(reloj).isAfter(hasta);
        if (!enPrueba) return new Vigente(contratado, contratado, false, hasta);
        // Si contrató un plan mayor al de prueba antes de que termine, rige el contratado
        Plan prueba = plan(properties.planPrueba());
        Plan rige = prueba.getOrden() > contratado.getOrden() ? prueba : contratado;
        return new Vigente(rige, contratado, true, hasta);
    }

    private Plan plan(String codigo) {
        return planRepository.findById(codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT))
            .orElseThrow(() -> new BusinessException("El plan " + codigo + " no existe"));
    }
}
