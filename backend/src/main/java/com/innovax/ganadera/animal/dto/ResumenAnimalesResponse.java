package com.innovax.ganadera.animal.dto;

import java.util.Map;

import com.innovax.ganadera.animal.Categoria;

/** Stock del establecimiento: animales activos en total, por sexo y por categoría (siempre trae las 8). */
public record ResumenAnimalesResponse(
    long total,
    long hembras,
    long machos,
    Map<Categoria, Long> porCategoria
) {
}
