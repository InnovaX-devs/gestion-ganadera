package com.innovax.ganadera.cuenta;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.innovax.ganadera.cobro.EstadoSuscripcion;
import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.organizacion.Organizacion;
import com.innovax.ganadera.organizacion.OrganizacionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Borra una cuenta entera desde el panel de Innovax: la organización, sus usuarios y todos sus datos.
 * No se puede deshacer. Se hace con SQL directo (sin el filtro de tenant de Hibernate), en una sola
 * transacción: si algo falla, no se borra nada.
 *
 * <p><b>Al crear una tabla nueva con organizacion_id o usuario_id, agregarla acá</b> (igual que en
 * DbCleaner de los tests), antes de las tablas a las que referencia.
 */
@Service
@RequiredArgsConstructor
public class EliminacionCuenta {

    /** Datos de la organización, en orden: primero lo que referencia a otras tablas. */
    private static final List<String> TABLAS_DE_LA_ORGANIZACION = List.of(
        "alerta_enviada",
        "tratamiento_planificado",
        "sync_baja",
        "evento",
        "cambio_categoria",
        "animal",
        "invitacion",
        "membresia",
        "lote",
        "rodeo",
        "producto_sanitario",
        "establecimiento",
        "suscripcion");

    /** Lo de sus usuarios que no tiene organizacion_id (o que está en otras cuentas, ej: membresías). */
    private static final List<String> TABLAS_DE_SUS_USUARIOS = List.of(
        "activacion_cuenta",
        "restablecimiento_contrasena",
        "alerta_enviada",
        "membresia");

    private final OrganizacionRepository organizacionRepository;
    private final EstadoCuentas estadoCuentas;
    private final JdbcTemplate jdbc;

    /**
     * @param confirmacion el nombre de la cuenta, escrito a mano en el panel
     * @return el nombre de la cuenta borrada
     */
    @Transactional
    public String eliminar(UUID organizacionId, String confirmacion) {
        Organizacion organizacion = organizacionRepository.findById(organizacionId)
            .orElseThrow(() -> NotFoundException.of("Organización", organizacionId));
        String nombre = organizacion.getNombre();
        if (confirmacion == null || !normalizar(confirmacion).equals(normalizar(nombre))) {
            throw new BusinessException("Para confirmar, escribí el nombre de la cuenta: " + nombre);
        }
        Integer cobrando = jdbc.queryForObject(
            "SELECT COUNT(*) FROM suscripcion WHERE organizacion_id = ? AND estado IN (?, ?)", Integer.class,
            organizacionId, EstadoSuscripcion.ACTIVA.name(), EstadoSuscripcion.PAUSADA.name());
        if (cobrando != null && cobrando > 0) {
            throw new BusinessException(
                "La cuenta tiene una suscripción de Mercado Pago en curso: cancelala antes de eliminarla");
        }
        try {
            jdbc.update("""
                UPDATE animal SET madre_id = NULL, padre_id = NULL, evento_nacimiento_id = NULL
                WHERE organizacion_id = ?""", organizacionId);
            TABLAS_DE_LA_ORGANIZACION.forEach(tabla ->
                jdbc.update("DELETE FROM " + tabla + " WHERE organizacion_id = ?", organizacionId));
            TABLAS_DE_SUS_USUARIOS.forEach(tabla -> jdbc.update("DELETE FROM " + tabla
                + " WHERE usuario_id IN (SELECT id FROM usuario WHERE organizacion_id = ?)", organizacionId));
            jdbc.update("DELETE FROM usuario WHERE organizacion_id = ?", organizacionId);
            jdbc.update("DELETE FROM organizacion WHERE id = ?", organizacionId);
        } catch (DataIntegrityViolationException e) {
            // Ej: un usuario de esta cuenta cargó eventos en un establecimiento de otra cuenta
            throw new BusinessException("No se puede eliminar: algún usuario de esta cuenta tiene cargas en "
                + "establecimientos de otra cuenta. Suspendela en su lugar.");
        }
        estadoCuentas.olvidar(organizacionId);
        return nombre;
    }

    private static String normalizar(String texto) {
        return texto.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
