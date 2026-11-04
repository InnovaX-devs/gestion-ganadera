package com.innovax.ganadera.reporte.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.innovax.ganadera.animal.Categoria;
import com.innovax.ganadera.animal.Sexo;
import com.innovax.ganadera.stock.dto.StockResponse;

/**
 * Existencias para vacunación / SENASA (#32): los datos del establecimiento (con el RENSPA), el
 * stock por categoría y la planilla de animales activos, ordenada por lote y caravana.
 * Es una foto del momento en que se pide ({@code generadoEn}).
 */
public record ExistenciasResponse(
    Establecimiento establecimiento,
    Instant generadoEn,
    StockResponse stock,
    List<Animal> animales
) {

    public record Establecimiento(UUID id, String nombre, String renspa, String localidad, String provincia) {
    }

    public record Animal(UUID id, String caravana, String caravanaElectronica, Sexo sexo, Categoria categoria,
                         String lote, String rodeo) {
    }
}
