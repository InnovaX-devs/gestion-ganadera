package com.innovax.ganadera.membresia;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.common.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/**
 * Decide si el usuario logueado puede hacer algo en un establecimiento. Los servicios lo llaman al
 * empezar cada operación:
 *
 * <pre>
 * autorizacion.exigir(establecimientoId, Permiso.GESTIONAR_AGRUPACIONES);
 * </pre>
 *
 * <ul>
 *   <li>Sin membresía en el establecimiento: 404. Es lo mismo que pasa con un establecimiento de otra
 *       organización, así no se puede averiguar si un id existe.</li>
 *   <li>Con membresía pero sin el permiso: 403.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AutorizacionService {

    private final MembresiaRepository membresiaRepository;

    /** Devuelve el rol del usuario en el establecimiento, o 404 si no es miembro. */
    @Transactional(readOnly = true)
    public Rol rolEn(UUID establecimientoId) {
        UUID usuarioId = TenantContext.requerirUsuario().userId();
        return membresiaRepository.findByUsuarioIdAndEstablecimientoId(usuarioId, establecimientoId)
            .map(Membresia::getRol)
            .orElseThrow(() -> NotFoundException.of("Establecimiento", establecimientoId));
    }

    /** Corta con 404 o 403 si no corresponde. Si corresponde, devuelve el rol por si el llamador lo necesita. */
    @Transactional(readOnly = true)
    public Rol exigir(UUID establecimientoId, Permiso permiso) {
        Rol rol = rolEn(establecimientoId);
        if (!rol.puede(permiso)) {
            throw new AccessDeniedException("El rol " + rol + " no tiene el permiso " + permiso);
        }
        return rol;
    }
}
