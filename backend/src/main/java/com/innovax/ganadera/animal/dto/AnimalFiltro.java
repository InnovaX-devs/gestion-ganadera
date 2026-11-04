package com.innovax.ganadera.animal.dto;

import java.util.UUID;

import com.innovax.ganadera.animal.Categoria;
import com.innovax.ganadera.animal.EstadoAnimal;
import com.innovax.ganadera.animal.Sexo;

/**
 * Filtros del listado (query params). Todos opcionales.
 *
 * <pre>GET /animales?q=a-1&categoria=VACA&loteId=...&pagina=0&tamanio=50</pre>
 *
 * @param q          busca en la caravana visual y en la electrónica (contiene, sin importar mayúsculas)
 * @param estado     por defecto ACTIVO
 * @param sinLote    true: solo los que no tienen lote asignado
 * @param sinRodeo   true: solo los que no tienen rodeo asignado
 */
public record AnimalFiltro(
    String q,
    Categoria categoria,
    Sexo sexo,
    UUID loteId,
    UUID rodeoId,
    Boolean sinLote,
    Boolean sinRodeo,
    EstadoAnimal estado,
    Integer pagina,
    Integer tamanio
) {

    public static final int TAMANIO_MAXIMO = 200;
    private static final int TAMANIO_POR_DEFECTO = 50;

    public EstadoAnimal estadoOPorDefecto() {
        return estado == null ? EstadoAnimal.ACTIVO : estado;
    }

    public int paginaValida() {
        return pagina == null || pagina < 0 ? 0 : pagina;
    }

    public int tamanioValido() {
        if (tamanio == null || tamanio < 1) return TAMANIO_POR_DEFECTO;
        return Math.min(tamanio, TAMANIO_MAXIMO);
    }
}
