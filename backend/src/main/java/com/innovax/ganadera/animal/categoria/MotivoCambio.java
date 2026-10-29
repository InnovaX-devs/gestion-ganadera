package com.innovax.ganadera.animal.categoria;

import com.innovax.ganadera.common.persistence.EnumTextoConverter;

/** Por qué cambió la categoría de un animal. Se guarda en el historial. */
public enum MotivoCambio {
    DESTETE,
    PARTO,
    EDAD,
    PESO,
    /** El usuario editó la categoría a mano. */
    MANUAL;

    @jakarta.persistence.Converter
    public static class Converter extends EnumTextoConverter<MotivoCambio> {
        public Converter() {
            super(MotivoCambio.class);
        }
    }
}
