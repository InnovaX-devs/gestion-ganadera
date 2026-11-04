package com.innovax.ganadera.support;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Vacía las tablas entre tests. Todos los tests comparten la misma base H2, así que hay que borrar
 * en orden (primero las tablas que referencian a otras).
 *
 * <p><b>Al crear una tabla nueva, agregarla acá</b>, arriba de las tablas a las que referencia.
 */
public final class DbCleaner {

    private static final List<String> TABLAS_EN_ORDEN = List.of(
        "alerta_enviada",
        "tratamiento_planificado",
        "sync_baja",
        "cotizacion",
        "evento",
        "cambio_categoria",
        "animal",
        "invitacion",
        "membresia",
        "lote",
        "rodeo",
        "producto_sanitario",
        "establecimiento",
        "activacion_cuenta",
        "restablecimiento_contrasena",
        "suscripcion",
        "usuario",
        "organizacion"
    );

    private DbCleaner() {
    }

    public static void limpiar(JdbcTemplate jdbc) {
        // animal se referencia a sí misma (madre/padre) y al evento que la creó (nacimiento):
        // primero se cortan esas relaciones
        jdbc.update("UPDATE animal SET madre_id = NULL, padre_id = NULL, evento_nacimiento_id = NULL");
        TABLAS_EN_ORDEN.forEach(tabla -> jdbc.update("DELETE FROM " + tabla));
        // Los planes son datos fijos (V17): solo se borran los que agregó algún test
        jdbc.update("DELETE FROM plan WHERE codigo NOT IN ('INICIAL', 'PRODUCTOR', 'EMPRESA')");
        jdbc.update("UPDATE plan SET precio_mensual = NULL");
    }
}
