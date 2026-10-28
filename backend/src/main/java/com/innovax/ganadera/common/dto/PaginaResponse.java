package com.innovax.ganadera.common.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Página de resultados con un formato propio y estable (no se expone el {@code Page} de Spring,
 * que cambia entre versiones). Reutilizable para cualquier listado paginado.
 */
public record PaginaResponse<T>(
    List<T> contenido,
    long total,
    int pagina,
    int tamanio,
    int totalPaginas
) {

    public static <E, T> PaginaResponse<T> de(Page<E> page, Function<E, T> mapper) {
        return new PaginaResponse<>(
            page.getContent().stream().map(mapper).toList(),
            page.getTotalElements(),
            page.getNumber(),
            page.getSize(),
            page.getTotalPages());
    }
}
