package com.innovax.ganadera.invitacion;

import java.time.Instant;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.innovax.ganadera.auth.dto.AuthResponse;
import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.common.error.ConflictException;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.common.tenant.AuthenticatedUser;
import com.innovax.ganadera.common.tenant.TenantContext;
import com.innovax.ganadera.invitacion.dto.AceptacionResponse;
import com.innovax.ganadera.invitacion.dto.InvitacionPublicaResponse;
import com.innovax.ganadera.invitacion.dto.RegistroInvitadoRequest;
import com.innovax.ganadera.usuario.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Lado del invitado: abre el link, y o bien se registra (no tenía cuenta) o bien acepta estando
 * logueado (ya tenía cuenta en la misma organización). Todo se identifica por el token del link.
 *
 * <p>Los métodos de esta clase no son {@code @Transactional} a propósito: las transacciones están
 * en {@link InvitacionAceptacionTx}.
 */
@Service
@RequiredArgsConstructor
public class InvitacionAceptacionService {

    private final InvitacionPublicaRepository publicaRepository;
    private final UsuarioRepository usuarioRepository;
    private final InvitacionAceptacionTx aceptacionTx;

    /** Datos de la invitación para mostrar en la pantalla del link. Funciona también con invitaciones vencidas o revocadas. */
    public InvitacionPublicaResponse ver(String token) {
        InvitacionPublica invitacion = buscar(token);
        return new InvitacionPublicaResponse(invitacion.email(), invitacion.establecimientoNombre(),
            invitacion.organizacionNombre(), invitacion.rol(), invitacion.estado(Instant.now()),
            invitacion.venceAt(), usuarioRepository.existsByEmailIgnoreCase(invitacion.email()));
    }

    /** El invitado no tiene cuenta: se la crea en la organización de la invitación y queda logueado. */
    public AuthResponse registrar(String token, RegistroInvitadoRequest request) {
        InvitacionPublica invitacion = pendiente(token);
        if (usuarioRepository.existsByEmailIgnoreCase(invitacion.email())) {
            throw new ConflictException("Ya existe una cuenta con ese email. Ingresá para aceptar la invitación.");
        }
        // La organización sale de la invitación (base de datos), no del request
        return TenantContext.comoOrganizacion(invitacion.organizacionId(),
            () -> aceptacionTx.registrar(invitacion.id(), request));
    }

    /** El invitado ya tiene cuenta y está logueado con ella. */
    public AceptacionResponse aceptar(String token) {
        AuthenticatedUser actual = TenantContext.requerirUsuario();
        InvitacionPublica invitacion = pendiente(token);
        if (!invitacion.email().equalsIgnoreCase(actual.email())) {
            throw new AccessDeniedException("La invitación es para otro email");
        }
        if (!invitacion.organizacionId().equals(actual.organizacionId())) {
            throw new ConflictException("Tu cuenta pertenece a otra organización, así que no podés unirte a este establecimiento");
        }
        return new AceptacionResponse(aceptacionTx.aceptarExistente(invitacion.id(), actual.userId()));
    }

    private InvitacionPublica buscar(String token) {
        return publicaRepository.buscarPorToken(token)
            .orElseThrow(() -> new NotFoundException("La invitación no existe o el enlace es incorrecto"));
    }

    private InvitacionPublica pendiente(String token) {
        InvitacionPublica invitacion = buscar(token);
        EstadoInvitacion estado = invitacion.estado(Instant.now());
        if (estado != EstadoInvitacion.PENDIENTE) {
            throw new BusinessException(estado.motivo());
        }
        return invitacion;
    }
}
