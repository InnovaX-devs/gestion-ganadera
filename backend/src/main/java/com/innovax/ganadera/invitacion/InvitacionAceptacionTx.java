package com.innovax.ganadera.invitacion;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.innovax.ganadera.auth.AuthService;
import com.innovax.ganadera.auth.dto.AuthResponse;
import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.invitacion.dto.RegistroInvitadoRequest;
import com.innovax.ganadera.membresia.Membresia;
import com.innovax.ganadera.membresia.MembresiaRepository;
import com.innovax.ganadera.organizacion.Organizacion;
import com.innovax.ganadera.organizacion.OrganizacionRepository;
import com.innovax.ganadera.usuario.Usuario;
import com.innovax.ganadera.usuario.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Las dos transacciones de aceptar una invitación. Están en una clase aparte de
 * {@link InvitacionAceptacionService} porque la organización tiene que estar fijada <b>antes</b> de
 * abrir la transacción (ver {@code TenantContext.comoOrganizacion}), y un método
 * {@code @Transactional} no puede cambiar eso desde adentro de sí mismo.
 */
@Service
@RequiredArgsConstructor
public class InvitacionAceptacionTx {

    private final InvitacionRepository invitacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final OrganizacionRepository organizacionRepository;
    private final MembresiaRepository membresiaRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    /** Crea la cuenta del invitado en la organización de la invitación, lo suma al establecimiento y lo loguea. */
    @Transactional
    public AuthResponse registrar(UUID invitacionId, RegistroInvitadoRequest request) {
        Invitacion invitacion = pendiente(invitacionId);
        Organizacion organizacion = organizacionRepository.getReferenceById(invitacion.getOrganizacionId());
        Usuario usuario = usuarioRepository.save(new Usuario(organizacion, invitacion.getEmail(),
            passwordEncoder.encode(request.password()), request.nombre(), request.apellido()));
        membresiaRepository.save(new Membresia(usuario, invitacion.getEstablecimiento(), invitacion.getRol()));
        invitacion.aceptar(Instant.now());
        return authService.sesionPara(usuario);
    }

    /** Suma al establecimiento a un usuario que ya tenía cuenta y estaba logueado. Devuelve el id del establecimiento. */
    @Transactional
    public UUID aceptarExistente(UUID invitacionId, UUID usuarioId) {
        Invitacion invitacion = pendiente(invitacionId);
        Establecimiento establecimiento = invitacion.getEstablecimiento();
        // Si ya era miembro (ej: lo agregaron a mano después de invitarlo) solo se cierra la invitación
        if (!membresiaRepository.existsByUsuarioIdAndEstablecimientoId(usuarioId, establecimiento.getId())) {
            membresiaRepository.save(
                new Membresia(usuarioRepository.getReferenceById(usuarioId), establecimiento, invitacion.getRol()));
        }
        invitacion.aceptar(Instant.now());
        return establecimiento.getId();
    }

    /** Se vuelve a chequear adentro de la transacción: entre que se abrió el link y se aceptó, pudo cambiar. */
    private Invitacion pendiente(UUID invitacionId) {
        Invitacion invitacion = invitacionRepository.findById(invitacionId)
            .orElseThrow(() -> NotFoundException.of("Invitación", invitacionId));
        EstadoInvitacion estado = invitacion.estado(Instant.now());
        if (estado != EstadoInvitacion.PENDIENTE) {
            throw new BusinessException(estado.motivo());
        }
        return invitacion;
    }
}
