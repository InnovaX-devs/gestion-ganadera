package com.innovax.ganadera.stock;

import java.util.UUID;

import com.innovax.ganadera.animal.Categoria;
import com.innovax.ganadera.animal.Sexo;

/**
 * Una fila de la consulta agregada: cuántos animales activos hay con esa combinación de categoría,
 * sexo, lote y rodeo. {@code loteId} o {@code rodeoId} en null = sin asignar.
 */
public record StockFila(Categoria categoria, Sexo sexo, UUID loteId, UUID rodeoId, Long cantidad) {
}
