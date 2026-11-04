package com.innovax.ganadera.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.innovax.ganadera.admin.dto.CambioPlanRequest;
import com.innovax.ganadera.admin.dto.OrganizacionAdminResponse;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.common.tenant.TenantContext;
import com.innovax.ganadera.contrasena.RestablecimientoService;
import com.innovax.ganadera.cuenta.CuentaService;
import com.innovax.ganadera.cuenta.dto.CrearCuentaRequest;
import com.innovax.ganadera.organizacion.Organizacion;
import com.innovax.ganadera.organizacion.OrganizacionRepository;
import com.innovax.ganadera.plan.PlanService;
import com.innovax.ganadera.usuario.Usuario;
import com.innovax.ganadera.usuario.UsuarioRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Panel de administración del equipo de Innovax: ver todas las cuentas y cambiarles el plan o la
 * prueba sin tocar la base (#35). Solo para los emails de {@code app.admin.emails}.
 *
 * <p>Ve datos de todas las organizaciones: cada una se consulta dentro de
 * {@link TenantContext#comoOrganizacion}, en su propia transacción (ver docs/decisiones/0003). Por
 * eso los métodos de acá <b>no</b> son {@code @Transactional}: la transacción la abre
 * {@link PlanService#resumen} ya con la organización fijada.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final Administradores administradores;
    private final OrganizacionRepository organizacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final PlanService planService;
    private final CuentaService cuentaService;
    private final RestablecimientoService restablecimientoService;

    public List<OrganizacionAdminResponse> organizaciones() {
        administradores.exigir();
        return organizacionRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
            .map(this::detalle)
            .toList();
    }

    /** Cuenta nueva con su dueño; al dueño le llega un email para elegir la contraseña. */
    public OrganizacionAdminResponse crear(CrearCuentaRequest request) {
        String admin = administradores.exigir();
        UUID id = cuentaService.crear(request);
        log.info("Administración: {} creó la cuenta \"{}\" ({}) para {}", admin, request.nombreOrganizacion(), id,
            request.email());
        return detalle(buscar(id));
    }

    public void reenviarActivacion(UUID organizacionId) {
        administradores.exigir();
        cuentaService.reenviarActivacion(organizacionId);
    }

    /** Link para que el dueño elija una contraseña nueva. Devuelve a qué email se mandó. */
    public String restablecerContrasena(UUID organizacionId) {
        String admin = administradores.exigir();
        String email = restablecimientoService.enviarAlDueno(organizacionId);
        log.info("Administración: {} mandó un enlace para cambiar la contraseña a {} ({})", admin, email,
            organizacionId);
        return email;
    }

    public OrganizacionAdminResponse suspender(UUID organizacionId, boolean suspendida) {
        String admin = administradores.exigir();
        cuentaService.suspender(organizacionId, suspendida);
        Organizacion organizacion = buscar(organizacionId);
        log.info("Administración: {} {} la cuenta \"{}\" ({})", admin, suspendida ? "suspendió" : "reactivó",
            organizacion.getNombre(), organizacionId);
        return detalle(organizacion);
    }

    public OrganizacionAdminResponse cambiarPlan(UUID organizacionId, CambioPlanRequest request) {
        String admin = administradores.exigir();
        planService.asignar(organizacionId, request.planCodigo(), request.pruebaHasta());
        planService.pagoManual(organizacionId, request.pagoManualHasta());
        Organizacion organizacion = buscar(organizacionId);
        log.info("Administración: {} puso a \"{}\" ({}) en plan {} con prueba hasta {} y pago manual hasta {}", admin,
            organizacion.getNombre(), organizacionId, organizacion.getPlanCodigo(), organizacion.getPruebaHasta(),
            organizacion.getPagoManualHasta());
        return detalle(organizacion);
    }

    /** Pago por transferencia de este mes: queda pagada hasta el vencimiento del mes que viene. */
    public OrganizacionAdminResponse registrarPago(UUID organizacionId) {
        String admin = administradores.exigir();
        var hasta = planService.registrarPagoManual(organizacionId);
        Organizacion organizacion = buscar(organizacionId);
        log.info("Administración: {} registró un pago de \"{}\" ({}): pagada hasta {}", admin,
            organizacion.getNombre(), organizacionId, hasta);
        return detalle(organizacion);
    }

    private OrganizacionAdminResponse detalle(Organizacion organizacion) {
        UUID id = organizacion.getId();
        var dueno = usuarioRepository.findFirstByOrganizacionIdOrderByCreatedAtAsc(id);
        var plan = TenantContext.comoOrganizacion(id, () -> planService.resumen(id));
        boolean pendiente = dueno.map(u -> cuentaService.pendienteDeActivacion(u.getId())).orElse(false);
        return new OrganizacionAdminResponse(id, organizacion.getNombre(),
            dueno.map(Administradores::nombreCompleto).orElse(null), dueno.map(Usuario::getEmail).orElse(null),
            pendiente, organizacion.isSuspendida(), organizacion.getCreatedAt(), plan);
    }

    private Organizacion buscar(UUID id) {
        return organizacionRepository.findById(id).orElseThrow(() -> NotFoundException.of("Organización", id));
    }
}
