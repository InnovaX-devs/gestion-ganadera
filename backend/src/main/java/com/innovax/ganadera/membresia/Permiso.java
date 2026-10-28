package com.innovax.ganadera.membresia;

/**
 * Acciones que se pueden permitir o negar según el rol. Qué rol tiene cada permiso está en
 * {@link Rol}; la matriz completa, en docs/permisos.md.
 */
public enum Permiso {
    /** Ver el establecimiento y todo lo que contiene. */
    VER,
    /** Editar los datos del establecimiento (nombre, ubicación, superficie, RENSPA). */
    EDITAR_ESTABLECIMIENTO,
    /** Dar de baja el establecimiento. */
    ELIMINAR_ESTABLECIMIENTO,
    /** Crear, editar y eliminar lotes y rodeos. */
    GESTIONAR_AGRUPACIONES,
    /** Agregar usuarios al establecimiento, cambiarles el rol o quitarlos. */
    GESTIONAR_MIEMBROS,
    /** Registrar eventos sanitarios (tratamientos, tactos, vacunas). Se usa cuando existan esos módulos. */
    REGISTRAR_SANIDAD
}
