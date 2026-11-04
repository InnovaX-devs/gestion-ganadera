package com.innovax.ganadera.stock;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import com.innovax.ganadera.animal.Categoria;
import com.innovax.ganadera.animal.Sexo;
import com.innovax.ganadera.stock.dto.StockGrupoResponse;

/** Acumula filas de {@link StockFila}: total, por sexo y por categoría (arranca todo en 0). */
final class Conteo {

    private final Map<Categoria, Long> porCategoria = ceros(Categoria.class);
    private final Map<Sexo, Long> porSexo = ceros(Sexo.class);
    private long total;

    void sumar(StockFila fila) {
        long cantidad = fila.cantidad();
        porCategoria.merge(fila.categoria(), cantidad, Long::sum);
        porSexo.merge(fila.sexo(), cantidad, Long::sum);
        total += cantidad;
    }

    long total() {
        return total;
    }

    Map<Sexo, Long> porSexo() {
        return Collections.unmodifiableMap(porSexo);
    }

    Map<Categoria, Long> porCategoria() {
        return Collections.unmodifiableMap(porCategoria);
    }

    StockGrupoResponse grupo(UUID id, String nombre) {
        return new StockGrupoResponse(id, nombre, total, porSexo(), porCategoria());
    }

    private static <E extends Enum<E>> Map<E, Long> ceros(Class<E> tipo) {
        Map<E, Long> mapa = new EnumMap<>(tipo);
        for (E valor : tipo.getEnumConstants()) mapa.put(valor, 0L);
        return mapa;
    }
}
