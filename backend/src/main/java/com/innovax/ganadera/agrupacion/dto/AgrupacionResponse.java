package com.innovax.ganadera.agrupacion.dto;

import java.time.Instant;
import java.util.UUID;

import com.innovax.ganadera.agrupacion.Agrupacion;

public record AgrupacionResponse(
    UUID id,
    UUID establecimientoId,
    String nombre,
    String descripcion,
    long cantidadAnimales,
    Instant createdAt
) {

    public static AgrupacionResponse from(Agrupacion a, long cantidadAnimales) {
        return new AgrupacionResponse(a.getId(), a.getEstablecimiento().getId(), a.getNombre(),
            a.getDescripcion(), cantidadAnimales, a.getCreatedAt());
    }
}
