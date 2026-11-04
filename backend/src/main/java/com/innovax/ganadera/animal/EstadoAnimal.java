package com.innovax.ganadera.animal;

import com.innovax.ganadera.common.persistence.EnumTextoConverter;

/**
 * Solo los ACTIVOS cuentan para el stock. Los otros estados los asignan los eventos de venta
 * (VENDIDO) y baja (MUERTO o PERDIDO), y se vuelven a ACTIVO si el evento se anula (#23).
 */
public enum EstadoAnimal {
    ACTIVO,
    VENDIDO,
    MUERTO,
    PERDIDO;

    @jakarta.persistence.Converter
    public static class Converter extends EnumTextoConverter<EstadoAnimal> {
        public Converter() {
            super(EstadoAnimal.class);
        }
    }
}
