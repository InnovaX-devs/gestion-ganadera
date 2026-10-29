package com.innovax.ganadera.stock.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.innovax.ganadera.animal.Categoria;
import com.innovax.ganadera.animal.Sexo;

/**
 * Stock del establecimiento: animales activos, calculado en el momento (nunca se guarda ni se edita).
 *
 * <ul>
 *   <li>{@code porSexo} y {@code porCategoria} traen siempre todas las claves, en 0 si no hay.</li>
 *   <li>{@code porLote} / {@code porRodeo}: todos los lotes y rodeos activos (también los vacíos),
 *       ordenados por nombre, y al final el grupo sin asignar si hay animales en él. La suma de
 *       {@code total} de cada lista es igual al {@code total} general.</li>
 * </ul>
 */
public record StockResponse(
    UUID establecimientoId,
    long total,
    Map<Sexo, Long> porSexo,
    Map<Categoria, Long> porCategoria,
    List<StockGrupoResponse> porLote,
    List<StockGrupoResponse> porRodeo
) {
}
