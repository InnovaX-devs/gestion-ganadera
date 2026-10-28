package com.innovax.ganadera.membresia;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Guarda el rol como texto ("DUENO", "SOCIO"...). Se usa un converter en vez de {@code @Enumerated}
 * para que la columna sea siempre un VARCHAR, igual en PostgreSQL y en H2: así Hibernate no espera
 * un tipo {@code enum} propio de cada base y la validación del esquema no falla.
 */
@Converter
public class RolConverter implements AttributeConverter<Rol, String> {

    @Override
    public String convertToDatabaseColumn(Rol rol) {
        return rol == null ? null : rol.name();
    }

    @Override
    public Rol convertToEntityAttribute(String valor) {
        return valor == null ? null : Rol.valueOf(valor);
    }
}
