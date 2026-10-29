package com.innovax.ganadera.evento;

import java.util.EnumSet;
import java.util.Set;

import com.innovax.ganadera.common.persistence.EnumTextoConverter;
import com.innovax.ganadera.membresia.Permiso;
import com.innovax.ganadera.membresia.Rol;

/**
 * Tipos de evento. Cada uno define si necesita animales y qué permiso hace falta para registrarlo
 * o anularlo. Los datos propios y las reglas de cada tipo están en su {@link TipoEventoHandler}
 * (paquete {@code evento.tipos}); un tipo sin handler todavía no se puede registrar.
 */
public enum TipoEvento {

    NACIMIENTO("Nacimiento", true, Permiso.GESTIONAR_ANIMALES),
    SERVICIO("Servicio", true, Permiso.GESTIONAR_ANIMALES),
    TACTO("Tacto", true, Permiso.REGISTRAR_SANIDAD),
    TRATAMIENTO("Tratamiento", true, Permiso.REGISTRAR_SANIDAD),
    PESAJE("Pesaje", true, Permiso.GESTIONAR_ANIMALES),
    MOVIMIENTO("Movimiento", true, Permiso.GESTIONAR_ANIMALES),
    VENTA("Venta", true, Permiso.GESTIONAR_ANIMALES),
    BAJA("Baja", true, Permiso.GESTIONAR_ANIMALES),
    /** Puede ser de animales, de un lote o del establecimiento. La puede cargar también el veterinario. */
    OBSERVACION("Observación", false, Permiso.GESTIONAR_ANIMALES, Permiso.REGISTRAR_SANIDAD);

    private final String etiqueta;
    private final boolean requiereAnimales;
    private final Set<Permiso> permisos;

    TipoEvento(String etiqueta, boolean requiereAnimales, Permiso permiso, Permiso... otros) {
        this.etiqueta = etiqueta;
        this.requiereAnimales = requiereAnimales;
        this.permisos = EnumSet.of(permiso, otros);
    }

    public String etiqueta() {
        return etiqueta;
    }

    /** true: el evento es siempre sobre uno o más animales (no sobre un lote o el establecimiento). */
    public boolean requiereAnimales() {
        return requiereAnimales;
    }

    /** Alcanza con que el rol tenga alguno de los permisos del tipo. */
    public boolean permitidoPara(Rol rol) {
        return permisos.stream().anyMatch(rol::puede);
    }

    @jakarta.persistence.Converter
    public static class Converter extends EnumTextoConverter<TipoEvento> {
        public Converter() {
            super(TipoEvento.class);
        }
    }
}
