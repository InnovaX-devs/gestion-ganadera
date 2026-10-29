package com.innovax.ganadera.stock.dto;

import java.util.Map;
import java.util.UUID;

import com.innovax.ganadera.animal.Categoria;
import com.innovax.ganadera.animal.Sexo;

/**
 * Stock de un lote o un rodeo. Sin {@code id} ni {@code nombre}: son los animales que no tienen
 * lote (o rodeo) asignado. Los mapas traen siempre todas las categorías y los dos sexos, en 0 si no hay.
 */
public record StockGrupoResponse(
    UUID id,
    String nombre,
    long total,
    Map<Sexo, Long> porSexo,
    Map<Categoria, Long> porCategoria
) {
}
