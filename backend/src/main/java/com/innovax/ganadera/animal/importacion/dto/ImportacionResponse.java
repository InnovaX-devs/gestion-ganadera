package com.innovax.ganadera.animal.importacion.dto;

import java.util.List;

/**
 * Resultado de revisar (o importar) una planilla.
 *
 * @param validas     filas sin errores
 * @param creados     animales guardados: 0 en la vista previa
 * @param lotesNuevos lotes que no existen y se van a crear (o se crearon)
 */
public record ImportacionResponse(
    int total,
    int validas,
    int creados,
    List<ErrorFila> errores,
    List<String> lotesNuevos,
    List<String> rodeosNuevos
) {

    /** @param campo nombre de la columna (caravana, sexo...) o null si es de toda la fila */
    public record ErrorFila(int fila, String campo, String mensaje) {
    }
}
