package com.innovax.ganadera.sanidad;

import com.innovax.ganadera.common.persistence.EnumTextoConverter;

/** Qué clase de producto sanitario es. */
public enum TipoProducto {
    VACUNA("Vacuna"),
    ANTIPARASITARIO("Antiparasitario"),
    ANTIBIOTICO("Antibiótico"),
    OTRO("Otro");

    private final String etiqueta;

    TipoProducto(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    @jakarta.persistence.Converter
    public static class Converter extends EnumTextoConverter<TipoProducto> {
        public Converter() {
            super(TipoProducto.class);
        }
    }
}
