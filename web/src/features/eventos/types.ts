export type TipoEvento =
  | 'NACIMIENTO'
  | 'SERVICIO'
  | 'TACTO'
  | 'TRATAMIENTO'
  | 'PESAJE'
  | 'MOVIMIENTO'
  | 'VENTA'
  | 'BAJA'
  | 'OBSERVACION'

export interface Referencia {
  id: string
  nombre: string
}

/** Un evento del historial. Sin animal ni lote, es del establecimiento. */
export interface Evento<D = Record<string, unknown>> {
  id: string
  operacionId: string
  tipo: TipoEvento
  fecha: string
  animal?: Referencia | null
  lote?: Referencia | null
  datos: D
  registradoPor: Referencia
  createdAt: string
}

export interface EventoInput<D = Record<string, unknown>> {
  /** Opcional: si se reintenta con el mismo id, el backend no duplica. */
  operacionId?: string
  tipo: TipoEvento
  fecha: string
  animalIds?: string[]
  loteId?: string | null
  /** Para los tipos sobre animales (ej: tratamiento): aplica a todos los animales activos del rodeo. */
  rodeoId?: string | null
  datos: D
}

export interface Operacion {
  operacionId: string
  cantidad: number
  eventos: Evento[]
}

export interface FiltroEventos {
  tipo?: TipoEvento
  animalId?: string
  loteId?: string
  desde?: string
  hasta?: string
  pagina?: number
  tamanio?: number
}

export interface ObservacionDatos {
  texto: string
}

/**
 * Datos de un pesaje. Individual: solo `kg`. Grupal: se envían `pesoTotal` y `cantidad`, y el
 * backend guarda el promedio en `kg` (con `grupal: true`).
 */
export interface PesajeDatos {
  kg: number
  grupal?: boolean
  pesoTotal?: number
  cantidad?: number
}
