package com.innovax.ganadera.animal;

import com.innovax.ganadera.common.persistence.EnumTextoConverter;

/**
 * Categorías de bovinos. Cada una corresponde a un sexo, y en los machos define si está castrado:
 * novillito y novillo son castrados; torito y toro, enteros. El ternero puede ser cualquiera de los dos.
 */
public enum Categoria {
    VACA(Sexo.HEMBRA, Castracion.NO_APLICA),
    VAQUILLONA(Sexo.HEMBRA, Castracion.NO_APLICA),
    TERNERA(Sexo.HEMBRA, Castracion.NO_APLICA),
    TERNERO(Sexo.MACHO, Castracion.CUALQUIERA),
    NOVILLITO(Sexo.MACHO, Castracion.CASTRADO),
    NOVILLO(Sexo.MACHO, Castracion.CASTRADO),
    TORITO(Sexo.MACHO, Castracion.ENTERO),
    TORO(Sexo.MACHO, Castracion.ENTERO);

    private enum Castracion { NO_APLICA, CUALQUIERA, CASTRADO, ENTERO }

    private final Sexo sexo;
    private final Castracion castracion;

    Categoria(Sexo sexo, Castracion castracion) {
        this.sexo = sexo;
        this.castracion = castracion;
    }

    public Sexo sexo() {
        return sexo;
    }

    /**
     * Si el animal está castrado según su categoría. Para el ternero respeta lo que se indicó; para
     * las hembras siempre es false.
     */
    public boolean castrado(boolean indicado) {
        return switch (castracion) {
            case NO_APLICA, ENTERO -> false;
            case CASTRADO -> true;
            case CUALQUIERA -> indicado;
        };
    }

    @jakarta.persistence.Converter
    public static class Converter extends EnumTextoConverter<Categoria> {
        public Converter() {
            super(Categoria.class);
        }
    }
}
