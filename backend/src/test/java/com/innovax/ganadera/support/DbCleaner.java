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
        "membresia",
        "lote",
        "rodeo",
        "establecimiento",
        "usuario",
        "organizacion"
    );

    private DbCleaner() {
    }

    public static void limpiar(JdbcTemplate jdbc) {
        TABLAS_EN_ORDEN.forEach(tabla -> jdbc.update("DELETE FROM " + tabla));
    }
}
