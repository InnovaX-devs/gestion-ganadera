package com.innovax.ganadera.animal.importacion.dto;

/**
 * Una fila de la planilla, tal como se leyó. Todo llega como texto: el backend lo interpreta y
 * devuelve un error por campo si algo no se entiende, en vez de rechazar todo el pedido.
 *
 * @param fila número de fila en la planilla (para mostrar los errores). Si falta se usa la posición.
 * @param sexo "Hembra", "Macho", "H" o "M". Si falta, se deduce de la categoría.
 * @param fechaNacimiento "2024-09-15" o "15/09/2024"
 * @param lote nombre del lote: si no existe se crea
 * @param madre caravana de la madre: puede estar en la misma planilla o ya cargada
 */
public record FilaImportacion(
    Integer fila,
    String caravana,
    String caravanaElectronica,
    String sexo,
    String categoria,
    String raza,
    String fechaNacimiento,
    String fechaNacimientoEstimada,
    String castrado,
    String lote,
    String rodeo,
    String madre,
    String padre,
    String observaciones
) {
}
