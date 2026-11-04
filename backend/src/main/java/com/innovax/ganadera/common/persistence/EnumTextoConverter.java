package com.innovax.ganadera.common.persistence;

import jakarta.persistence.AttributeConverter;

/**
 * Base para guardar un enum como texto (su {@code name()}). Misma idea que {@code RolConverter}: la
 * columna es siempre VARCHAR, en PostgreSQL y en H2, y la validación del esquema no falla.
 *
 * <pre>
 * &#64;Converter
 * public class SexoConverter extends EnumTextoConverter&lt;Sexo&gt; {
 *     public SexoConverter() { super(Sexo.class); }
 * }
 * </pre>
 */
public abstract class EnumTextoConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> tipo;

    protected EnumTextoConverter(Class<E> tipo) {
        this.tipo = tipo;
    }

    @Override
    public String convertToDatabaseColumn(E valor) {
        return valor == null ? null : valor.name();
    }

    @Override
    public E convertToEntityAttribute(String valor) {
        return valor == null ? null : Enum.valueOf(tipo, valor);
    }
}
