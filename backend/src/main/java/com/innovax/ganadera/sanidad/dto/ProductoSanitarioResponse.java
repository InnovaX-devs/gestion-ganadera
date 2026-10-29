package com.innovax.ganadera.sanidad.dto;

import java.util.UUID;

import com.innovax.ganadera.sanidad.ProductoSanitario;
import com.innovax.ganadera.sanidad.TipoProducto;
import com.innovax.ganadera.sanidad.ViaAdministracion;

public record ProductoSanitarioResponse(
    UUID id,
    String nombre,
    TipoProducto tipo,
    int diasCarencia,
    String dosisHabitual,
    ViaAdministracion viaHabitual
) {

    public static ProductoSanitarioResponse from(ProductoSanitario p) {
        return new ProductoSanitarioResponse(p.getId(), p.getNombre(), p.getTipo(), p.getDiasCarencia(),
            p.getDosisHabitual(), p.getViaHabitual());
    }
}
