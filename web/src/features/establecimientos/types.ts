import type { Rol } from './permisos'

export interface Establecimiento {
  id: string
  nombre: string
  localidad: string | null
  provincia: string | null
  superficieHa: number | null
  renspa: string | null
  latitud: number | null
  longitud: number | null
  createdAt: string
  /** Rol del usuario logueado en este establecimiento. */
  rol: Rol
}

export interface EstablecimientoInput {
  nombre: string
  localidad: string | null
  provincia: string | null
  superficieHa: number | null
  renspa: string | null
  latitud: number | null
  longitud: number | null
}
