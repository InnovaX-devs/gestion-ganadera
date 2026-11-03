package com.innovax.ganadera.calendario;

import com.innovax.ganadera.common.persistence.EnumTextoConverter;

/** Estado de un tratamiento planificado. */
public enum EstadoPlan {
    PENDIENTE,
    REALIZADO,
    CANCELADO;

    @jakarta.persistence.Converter
    public static class Converter extends EnumTextoConverter<EstadoPlan> {
        public Converter() {
            super(EstadoPlan.class);
        }
    }
}
