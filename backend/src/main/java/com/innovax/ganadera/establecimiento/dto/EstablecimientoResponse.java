package com.innovax.ganadera.establecimiento.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.membresia.Rol;

public record EstablecimientoResponse(
    UUID id,
    String nombre,
    String localidad,
    String provincia,
    BigDecimal superficieHa,
    String renspa,
    BigDecimal latitud,
    BigDecimal longitud,
    Instant createdAt,
    /** Rol del usuario logueado en este establecimiento. */
    Rol rol
) {

    public static EstablecimientoResponse from(Establecimiento e, Rol rol) {
        return new EstablecimientoResponse(e.getId(), e.getNombre(), e.getLocalidad(), e.getProvincia(),
            e.getSuperficieHa(), e.getRenspa(), e.getLatitud(), e.getLongitud(), e.getCreatedAt(), rol);
    }
}
