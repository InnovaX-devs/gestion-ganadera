package com.innovax.ganadera.mercado;

import com.innovax.ganadera.common.persistence.EnumTextoConverter;

/**
 * Lo que se cotiza (#27). El orden es el que muestra la API: invernada, consumo, granos y dólar.
 */
public enum Producto {

    TERNERO(Grupo.INVERNADA, "$/kg vivo"),
    TERNERA(Grupo.INVERNADA, "$/kg vivo"),
    NOVILLO(Grupo.CONSUMO, "$/kg vivo"),
    NOVILLITO(Grupo.CONSUMO, "$/kg vivo"),
    MAIZ(Grupo.GRANOS, "$/tn"),
    SOJA(Grupo.GRANOS, "$/tn"),
    TRIGO(Grupo.GRANOS, "$/tn"),
    DOLAR_OFICIAL(Grupo.DOLAR, "$/US$"),
    DOLAR_BLUE(Grupo.DOLAR, "$/US$");

    public enum Grupo {
        INVERNADA, CONSUMO, GRANOS, DOLAR
    }

    private final Grupo grupo;
    private final String unidad;

    Producto(Grupo grupo, String unidad) {
        this.grupo = grupo;
        this.unidad = unidad;
    }

    public Grupo grupo() {
        return grupo;
    }

    public String unidad() {
        return unidad;
    }

    @jakarta.persistence.Converter
    public static class Converter extends EnumTextoConverter<Producto> {
        public Converter() {
            super(Producto.class);
        }
    }
}
