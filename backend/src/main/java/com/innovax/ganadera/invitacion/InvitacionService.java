package com.innovax.ganadera.invitacion;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.common.error.ConflictException;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.common.tenant.AuthenticatedUser;
import com.innovax.ganadera.common.tenant.TenantContext;
import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.establecimiento.EstablecimientoRepository;
import com.innovax.ganadera.invitacion.dto.InvitacionRequest;
import com.innovax.ganadera.invitacion.dto.InvitacionResponse;
import com.innovax.ganadera.membresia.AutorizacionService;
import com.innovax.ganadera.membresia.MembresiaRepository;
import com.innovax.ganadera.membresia.Permiso;
import com.innovax.ganadera.plan.PlanService;
import com.innovax.ganadera.usuario.Usuario;
import com.innovax.ganadera.usuario.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Gestión de invitaciones de un establecimiento: invitar, ver, reenviar y revocar. Solo quien tiene
 * el permiso {@link Permiso#GESTIONAR_MIEMBROS} (el dueño). Aceptar una invitación, que lo hace el
 * invitado, está en {@link InvitacionAceptacionService}.
 */
@Service
@RequiredArgsConstructor
public class InvitacionService {

    private final InvitacionRepository repository;
    private final EstablecimientoRepository establecimientoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MembresiaRepository membresiaRepository;
    private final AutorizacionService autorizacion;
    private final PlanService planService;
    private final InvitacionProperties properties;
    private final ApplicationEventPublisher eventos;

    @Transactional(readOnly = true)
    public List<InvitacionResponse> listar(UUID establecimientoId) {
        autorizacion.exigir(establecimientoId, Permiso.GESTIONAR_MIEMBROS);
        Instant ahora = Instant.now();
        return repository.findDelEstablecimiento(establecimientoId).stream()
            .map(i -> InvitacionResponse.from(i, ahora))
            .toList();
    }

    @Transactional
    public InvitacionResponse invitar(UUID establecimientoId, InvitacionRequest request) {
        autorizacion.exigir(establecimientoId, Permiso.GESTIONAR_MIEMBROS);
        Establecimiento establecimiento = establecimientoActivo(establecimientoId);
        AuthenticatedUser actual = TenantContext.requerirUsuario();
        String email = request.email().toLowerCase(Locale.ROOT);

        // Un usuario pertenece a una sola organización: si ya tiene cuenta en otra, no se lo puede sumar
        usuarioRepository.findByEmailIgnoreCase(email).ifPresent(existente -> {
            if (!existente.getOrganizacion().getId().equals(actual.organizacionId())) {
                throw new ConflictException(
                    "Ese email ya tiene una cuenta en otra organización, así que no se lo puede invitar");
            }
            if (membresiaRepository.existsByUsuarioIdAndEstablecimientoId(existente.getId(), establecimientoId)) {
                throw new ConflictException("Ese usuario ya forma parte de este establecimiento");
            }
        });

        boolean yaEsUsuario = usuarioRepository.findByEmailIgnoreCase(email).isPresent();
        if (!yaEsUsuario) planService.exigirLugarParaInvitar(email);

        Instant ahora = Instant.now();
        boolean hayPendiente = repository.findByEstablecimientoIdAndEmail(establecimientoId, email).stream()
            .anyMatch(i -> i.estado(ahora) == EstadoInvitacion.PENDIENTE);
        if (hayPendiente) {
            throw new ConflictException("Ya hay una invitación pendiente para ese email. Podés reenviarla desde la lista.");
        }

        String token = TokensInvitacion.generar();
        Usuario invitador = usuarioRepository.getReferenceById(actual.userId());
        Invitacion invitacion = repository.saveAndFlush(new Invitacion(establecimiento, invitador, email,
            request.rol(), TokensInvitacion.hash(token), ahora.plus(properties.ttl())));
        publicar(invitacion, token);
        return InvitacionResponse.from(invitacion, ahora);
    }

    /**
     * Genera un link nuevo (el anterior deja de funcionar), renueva el plazo y vuelve a mandar el
     * email. Sirve para invitaciones pendientes y vencidas.
     */
    @Transactional
    public InvitacionResponse reenviar(UUID establecimientoId, UUID id) {
        autorizacion.exigir(establecimientoId, Permiso.GESTIONAR_MIEMBROS);
        Invitacion invitacion = buscar(establecimientoId, id);
        Instant ahora = Instant.now();
        EstadoInvitacion estado = invitacion.estado(ahora);
        if (estado == EstadoInvitacion.ACEPTADA || estado == EstadoInvitacion.REVOCADA) {
            throw new BusinessException(estado == EstadoInvitacion.ACEPTADA
                ? "Esa invitación ya fue aceptada"
                : "Esa invitación fue revocada. Creá una nueva.");
        }
        String token = TokensInvitacion.generar();
        invitacion.renovar(TokensInvitacion.hash(token), ahora.plus(properties.ttl()));
        publicar(invitacion, token);
        return InvitacionResponse.from(invitacion, ahora);
    }

    /** El link deja de funcionar. Una invitación ya aceptada no se puede revocar (para eso se quita al miembro). */
    @Transactional
    public void revocar(UUID establecimientoId, UUID id) {
        autorizacion.exigir(establecimientoId, Permiso.GESTIONAR_MIEMBROS);
        Invitacion invitacion = buscar(establecimientoId, id);
        Instant ahora = Instant.now();
        EstadoInvitacion estado = invitacion.estado(ahora);
        if (estado == EstadoInvitacion.ACEPTADA) {
            throw new BusinessException("Esa invitación ya fue aceptada. Si querés sacarlo, quitalo del equipo.");
        }
        if (estado != EstadoInvitacion.REVOCADA) {
            invitacion.revocar(ahora);
        }
    }

    private void publicar(Invitacion invitacion, String token) {
        Usuario invitador = invitacion.getInvitadoPor();
        String nombre = invitador.getApellido() == null
            ? invitador.getNombre()
            : invitador.getNombre() + " " + invitador.getApellido();
        eventos.publishEvent(new InvitacionEnviadaEvent(invitacion.getEmail(), nombre,
            invitacion.getEstablecimiento().getNombre(), invitacion.getRol(), token, invitacion.getVenceAt()));
    }

    private Establecimiento establecimientoActivo(UUID id) {
        return establecimientoRepository.findByIdAndActivoTrue(id)
            .orElseThrow(() -> NotFoundException.of("Establecimiento", id));
    }

    private Invitacion buscar(UUID establecimientoId, UUID id) {
        establecimientoActivo(establecimientoId);
        return repository.findByIdAndEstablecimientoId(id, establecimientoId)
            .orElseThrow(() -> NotFoundException.of("Invitación", id));
    }
}
