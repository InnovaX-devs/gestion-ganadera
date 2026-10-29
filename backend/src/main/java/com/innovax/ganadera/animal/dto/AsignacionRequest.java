package com.innovax.ganadera.animal.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Asigna el mismo lote o rodeo a varios animales de una vez.
 *
 * @param destinoId id del lote o rodeo; null para quitárselo
 */
public record AsignacionRequest(
    @NotEmpty(message = "Elegí al menos un animal")
    @Size(max = 500, message = "Se pueden asignar hasta 500 animales por vez")
    List<UUID> animalIds,

    @NotNull(message = "Indicá si se asigna el lote o el rodeo")
    Campo campo,

    UUID destinoId
) {

    public enum Campo { LOTE, RODEO }
}
