package com.innovax.ganadera.animal;

import com.innovax.ganadera.common.persistence.EnumTextoConverter;

public enum Sexo {
    HEMBRA,
    MACHO;

    @jakarta.persistence.Converter
    public static class Converter extends EnumTextoConverter<Sexo> {
        public Converter() {
            super(Sexo.class);
        }
    }
}
