package com.innovax.ganadera.evento.dto;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;

import com.innovax.ganadera.evento.TipoEvento;

/**
 * Filtros del listado (query params, todos opcionales). Con {@code animalId} es el historial del
 * animal, que usa la ficha (#15).
 *
 * <pre>GET /eventos?tipo=OBSERVACION&animalId=...&desde=2026-01-01&hasta=2026-12-31&pagina=0</pre>
 */
public record EventoFiltro(
    TipoEvento tipo,
    UUID animalId,
    UUID loteId,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
    Integer pagina,
    Integer tamanio
) {

    public int paginaValida() {
        return pagina == null || pagina < 0 ? 0 : pagina;
    }

    public int tamanioValido() {
        if (tamanio == null || tamanio < 1) return 50;
        return Math.min(tamanio, 200);
    }
}
