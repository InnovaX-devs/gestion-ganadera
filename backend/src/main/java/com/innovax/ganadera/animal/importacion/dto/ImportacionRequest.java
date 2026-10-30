package com.innovax.ganadera.animal.importacion.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record ImportacionRequest(
    @NotEmpty(message = "La planilla no tiene filas")
    @Size(max = ImportacionRequest.MAXIMO_FILAS, message = "Se pueden importar hasta 2000 animales por vez")
    List<FilaImportacion> filas
) {

    public static final int MAXIMO_FILAS = 2000;
}
