package com.innovax.ganadera.membresia;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.common.error.ConflictException;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.common.tenant.TenantContext;
import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.establecimiento.EstablecimientoRepository;
import com.innovax.ganadera.membresia.dto.MiembroRequest;
import com.innovax.ganadera.membresia.dto.MiembroResponse;
import com.innovax.ganadera.membresia.dto.MiembroRolRequest;
import com.innovax.ganadera.usuario.Usuario;
import com.innovax.ganadera.usuario.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Equipo de un establecimiento: quién tiene acceso y con qué rol. Solo el dueño lo puede cambiar.
 *
 * <p>Por ahora solo se puede agregar a usuarios que ya pertenecen a la organización. Invitar a una
 * persona nueva a la organización es otro issue.
 */
@Service
@RequiredArgsConstructor
public class MembresiaService {

    private final MembresiaRepository repository;
    private final EstablecimientoRepository establecimientoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutorizacionService autorizacion;

    @Transactional(readOnly = true)
    public List<MiembroResponse> listar(UUID establecimientoId) {
        autorizacion.exigir(establecimientoId, Permiso.VER);
        return repository.findDelEstablecimiento(establecimientoId).stream()
            .map(MiembroResponse::from)
            .toList();
    }

    @Transactional
    public MiembroResponse agregar(UUID establecimientoId, MiembroRequest request) {
        autorizacion.exigir(establecimientoId, Permiso.GESTIONAR_MIEMBROS);
        Establecimiento establecimiento = establecimientoActivo(establecimientoId);
        Usuario usuario = buscarUsuarioDeLaOrganizacion(request.email());
        if (repository.existsByUsuarioIdAndEstablecimientoId(usuario.getId(), establecimientoId)) {
            throw new ConflictException("Ese usuario ya forma parte de este establecimiento");
        }
        return MiembroResponse.from(repository.save(new Membresia(usuario, establecimiento, request.rol())));
    }

    @Transactional
    public MiembroResponse cambiarRol(UUID establecimientoId, UUID membresiaId, MiembroRolRequest request) {
        autorizacion.exigir(establecimientoId, Permiso.GESTIONAR_MIEMBROS);
        Membresia membresia = buscar(establecimientoId, membresiaId);
        if (membresia.getRol() == Rol.DUENO && request.rol() != Rol.DUENO) {
            exigirOtroDueno(establecimientoId);
        }
        membresia.setRol(request.rol());
        return MiembroResponse.from(repository.saveAndFlush(membresia));
    }

    @Transactional
    public void quitar(UUID establecimientoId, UUID membresiaId) {
        autorizacion.exigir(establecimientoId, Permiso.GESTIONAR_MIEMBROS);
        Membresia membresia = buscar(establecimientoId, membresiaId);
        if (membresia.getRol() == Rol.DUENO) {
            exigirOtroDueno(establecimientoId);
        }
        repository.delete(membresia);
    }

    /** Un establecimiento nunca puede quedar sin dueño: nadie podría volver a administrarlo. */
    private void exigirOtroDueno(UUID establecimientoId) {
        if (repository.countByEstablecimientoIdAndRol(establecimientoId, Rol.DUENO) <= 1) {
            throw new BusinessException("El establecimiento tiene que tener al menos un dueño");
        }
    }

    private Establecimiento establecimientoActivo(UUID id) {
        return establecimientoRepository.findByIdAndActivoTrue(id)
            .orElseThrow(() -> NotFoundException.of("Establecimiento", id));
    }

    private Membresia buscar(UUID establecimientoId, UUID membresiaId) {
        establecimientoActivo(establecimientoId);
        return repository.findByIdAndEstablecimientoId(membresiaId, establecimientoId)
            .orElseThrow(() -> NotFoundException.of("Miembro", membresiaId));
    }

    /** El mensaje es el mismo si el email no existe o es de otra organización, para no filtrar cuentas ajenas. */
    private Usuario buscarUsuarioDeLaOrganizacion(String email) {
        UUID organizacionId = TenantContext.requerirUsuario().organizacionId();
        return usuarioRepository.findByEmailIgnoreCase(email)
            .filter(Usuario::isActivo)
            .filter(u -> u.getOrganizacion().getId().equals(organizacionId))
            .orElseThrow(() -> new NotFoundException("No hay ningún usuario con el email " + email + " en tu organización"));
    }
}
