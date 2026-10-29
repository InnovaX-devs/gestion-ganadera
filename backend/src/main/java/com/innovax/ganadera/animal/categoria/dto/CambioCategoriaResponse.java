package com.innovax.ganadera.animal.categoria.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.innovax.ganadera.animal.Categoria;
import com.innovax.ganadera.animal.categoria.CambioCategoria;
import com.innovax.ganadera.animal.categoria.MotivoCambio;

public record CambioCategoriaResponse(
    UUID id,
    Categoria categoriaAnterior,
    Categoria categoriaNueva,
    MotivoCambio motivo,
    LocalDate fecha,
    boolean automatico,
    Instant createdAt
) {

    public static CambioCategoriaResponse from(CambioCategoria c) {
        return new CambioCategoriaResponse(c.getId(), c.getCategoriaAnterior(), c.getCategoriaNueva(),
            c.getMotivo(), c.getFecha(), c.getMotivo() != MotivoCambio.MANUAL, c.getCreatedAt());
    }
}
