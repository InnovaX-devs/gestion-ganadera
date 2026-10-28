import { createContext } from 'react'
import type { Establecimiento } from './types'

export interface EstablecimientoActivoValue {
  /** Establecimiento sobre el que se trabaja. null si la organización todavía no creó ninguno. */
  activo: Establecimiento | null
  establecimientos: Establecimiento[]
  cargando: boolean
  seleccionar: (id: string) => void
}

export const EstablecimientoActivoContext = createContext<EstablecimientoActivoValue | null>(null)
