package com.innovax.ganadera.sanidad;

import com.innovax.ganadera.common.persistence.EnumTextoConverter;

/** Por dónde se aplica un producto. */
public enum ViaAdministracion {
    SUBCUTANEA("Subcutánea"),
    INTRAMUSCULAR("Intramuscular"),
    INTRAVENOSA("Intravenosa"),
    ORAL("Oral"),
    TOPICA("Tópica (pour-on)"),
    INTRAMAMARIA("Intramamaria"),
    OTRA("Otra");

    private final String etiqueta;

    ViaAdministracion(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    @jakarta.persistence.Converter
    public static class Converter extends EnumTextoConverter<ViaAdministracion> {
        public Converter() {
            super(ViaAdministracion.class);
        }
    }
}
