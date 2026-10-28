package com.innovax.ganadera.membresia;

import static com.innovax.ganadera.membresia.Permiso.EDITAR_ESTABLECIMIENTO;
import static com.innovax.ganadera.membresia.Permiso.ELIMINAR_ESTABLECIMIENTO;
import static com.innovax.ganadera.membresia.Permiso.GESTIONAR_AGRUPACIONES;
import static com.innovax.ganadera.membresia.Permiso.GESTIONAR_MIEMBROS;
import static com.innovax.ganadera.membresia.Permiso.REGISTRAR_SANIDAD;
import static com.innovax.ganadera.membresia.Permiso.VER;

import java.util.EnumSet;
import java.util.Set;

/**
 * Rol de un usuario en un establecimiento. Cada rol define qué permisos tiene: esta es la única
 * fuente de verdad de la matriz de permisos (docs/permisos.md la documenta).
 */
public enum Rol {

    DUENO("Dueño", EnumSet.allOf(Permiso.class)),
    ENCARGADO("Encargado", EnumSet.of(VER, EDITAR_ESTABLECIMIENTO, GESTIONAR_AGRUPACIONES, REGISTRAR_SANIDAD)),
    VETERINARIO("Veterinario", EnumSet.of(VER, REGISTRAR_SANIDAD)),
    /** Solo lectura. */
    SOCIO("Socio", EnumSet.of(VER));

    private final String etiqueta;
    private final Set<Permiso> permisos;

    Rol(String etiqueta, Set<Permiso> permisos) {
        this.etiqueta = etiqueta;
        this.permisos = permisos;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public boolean puede(Permiso permiso) {
        return permisos.contains(permiso);
    }
}
