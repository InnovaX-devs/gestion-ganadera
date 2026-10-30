package com.innovax.ganadera.mercado;

import com.innovax.ganadera.common.persistence.EnumTextoConverter;

/** Cómo se obtuvo el valor de una foto diaria. */
public enum EstadoCotizacion {
    /** Se leyó de la fuente en esta corrida. */
    ACTUAL,
    /** La fuente falló: se mantiene el último valor conocido (con su fecha_dato original). */
    ANTERIOR,
    /** La fuente falló y nunca hubo un valor para este producto. */
    SIN_DATO;

    @jakarta.persistence.Converter
    public static class Converter extends EnumTextoConverter<EstadoCotizacion> {
        public Converter() {
            super(EstadoCotizacion.class);
        }
    }
}
