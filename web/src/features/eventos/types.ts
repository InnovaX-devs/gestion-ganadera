import type { Adjunto } from '@/features/adjuntos/types'
import type { Sexo } from '@/features/animales/types'

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
  /** Donde se registró. En el historial de un animal que cambió de campo puede ser otro que el actual. */
  establecimiento?: Referencia | null
  tipo: TipoEvento
  fecha: string
  animal?: Referencia | null
  lote?: Referencia | null
  datos: D
  registradoPor: Referencia
  createdAt: string
  /**
   * Fotos y archivos de la operación (#43): en un evento grupal los ven todos. Faltan en lo guardado
   * en el dispositivo para trabajar sin conexión (se ven al volver la señal).
   */
  adjuntos?: Adjunto[]
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
  /** Se guardó sin conexión y se va a enviar al volver la señal (#30). Sin `eventos` todavía. */
  pendiente?: boolean
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

export type TipoParto = 'NORMAL' | 'ASISTIDO' | 'CESAREA'

/** Una cría del parto. Las nacidas muertas no llevan caravana ni se crean como animales. */
export interface CriaNacimiento {
  sexo: Sexo
  nacidoMuerto: boolean
  caravana?: string | null
  pesoKg?: number | null
}

/** Nacimiento (#18): va sobre la madre. Mellizos = dos crías en el mismo nacimiento. */
export interface NacimientoDatos {
  tipoParto: TipoParto
  crias: CriaNacimiento[]
  observaciones?: string | null
}
