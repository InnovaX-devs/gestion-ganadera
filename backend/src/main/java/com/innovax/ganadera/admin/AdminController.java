package com.innovax.ganadera.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.innovax.ganadera.admin.dto.CambioPlanRequest;
import com.innovax.ganadera.admin.dto.OrganizacionAdminResponse;
import com.innovax.ganadera.cuenta.dto.CrearCuentaRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Panel del equipo de Innovax. 403 para cualquier otro usuario. */
@RestController
@RequestMapping("/api/admin/organizaciones")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService service;

    /** Todas las cuentas, la más nueva primero, con su plan y uso. */
    @GetMapping
    public List<OrganizacionAdminResponse> listar() {
        return service.organizaciones();
    }

    /** Crea una cuenta y le manda al dueño el link para elegir su contraseña. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizacionAdminResponse crear(@Valid @RequestBody CrearCuentaRequest request) {
        return service.crear(request);
    }

    /**
     * Pago por transferencia (o efectivo) del mes: queda pagada hasta el día de vencimiento del mes
     * que viene, o un mes más si ya estaba al día.
     */
    @PostMapping("/{id}/registrar-pago")
    public OrganizacionAdminResponse registrarPago(@PathVariable UUID id) {
        return service.registrarPago(id);
    }

    /** Vuelve a mandar el link de activación (el anterior deja de funcionar). */
    @PostMapping("/{id}/reenviar-activacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reenviarActivacion(@PathVariable UUID id) {
        service.reenviarActivacion(id);
    }

    /** Le manda al dueño un link para elegir una contraseña nueva (la elige él, nunca el panel). */
    @PostMapping("/{id}/restablecer-contrasena")
    public EnlaceEnviado restablecerContrasena(@PathVariable UUID id) {
        return new EnlaceEnviado(service.restablecerContrasena(id));
    }

    public record EnlaceEnviado(String email) {
    }

    /**
     * Borra la cuenta con todos sus datos (no se puede deshacer). Hay que mandar el nombre de la
     * cuenta como confirmación: {@code { "confirmacion": "Estancia San Juan" }}.
     */
    @PostMapping("/{id}/eliminar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID id, @RequestBody Eliminacion request) {
        service.eliminar(id, request.confirmacion());
    }

    public record Eliminacion(String confirmacion) {
    }

    /** Suspende o reactiva la cuenta: {@code { "suspendida": true }}. */
    @PutMapping("/{id}/suspension")
    public OrganizacionAdminResponse suspender(@PathVariable UUID id, @RequestBody Suspension request) {
        return service.suspender(id, request.suspendida());
    }

    public record Suspension(boolean suspendida) {
    }

    /** Cambia el plan contratado y el fin de la prueba. */
    @PutMapping("/{id}/plan")
    public OrganizacionAdminResponse cambiarPlan(@PathVariable UUID id, @Valid @RequestBody CambioPlanRequest request) {
        return service.cambiarPlan(id, request);
    }
}
