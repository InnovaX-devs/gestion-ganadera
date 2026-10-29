export type Rol = 'DUENO' | 'ENCARGADO' | 'VETERINARIO' | 'SOCIO'

export type Permiso =
  | 'EDITAR_ESTABLECIMIENTO'
  | 'ELIMINAR_ESTABLECIMIENTO'
  | 'GESTIONAR_AGRUPACIONES'
  | 'GESTIONAR_ANIMALES'
  | 'GESTIONAR_MIEMBROS'

export const ETIQUETA_ROL: Record<Rol, string> = {
  DUENO: 'Dueño',
  ENCARGADO: 'Encargado',
  VETERINARIO: 'Veterinario',
  SOCIO: 'Socio',
}

/**
 * Copia de la matriz de docs/permisos.md. El backend es quien manda (responde 403 si no
 * corresponde): esto solo sirve para no mostrar botones que no se pueden usar.
 */
const PERMISOS: Record<Rol, readonly Permiso[]> = {
  DUENO: [
    'EDITAR_ESTABLECIMIENTO',
    'ELIMINAR_ESTABLECIMIENTO',
    'GESTIONAR_AGRUPACIONES',
    'GESTIONAR_ANIMALES',
    'GESTIONAR_MIEMBROS',
  ],
  ENCARGADO: ['EDITAR_ESTABLECIMIENTO', 'GESTIONAR_AGRUPACIONES', 'GESTIONAR_ANIMALES'],
  VETERINARIO: [],
  SOCIO: [],
}

export function puede(rol: Rol, permiso: Permiso): boolean {
  return PERMISOS[rol].includes(permiso)
}
