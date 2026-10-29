import type { Referencia } from '@/features/eventos/types'

/**
 * Un pesaje con su variación respecto al anterior del mismo animal. Si es el primero del animal,
 * los campos "anterior" y la ganancia no vienen.
 */
export interface Pesaje {
  id: string
  operacionId: string
  fecha: string
  animal?: Referencia | null
  /** Peso del animal; en un pesaje grupal, el promedio del grupo. */
  kg: number
  grupal: boolean
  pesoTotal?: number | null
  cantidad?: number | null
  fechaAnterior?: string | null
  kgAnterior?: number | null
  variacionKg?: number | null
  diasDesdeAnterior?: number | null
  /** kg por día entre este pesaje y el anterior. */
  gananciaDiaria?: number | null
  registradoPor: Referencia
}

export interface FiltroPesajes {
  animalId?: string
  desde?: string
  hasta?: string
  pagina?: number
  tamanio?: number
}

export interface GrillaInput {
  /** Si se reintenta con el mismo id, el backend no duplica. */
  operacionId?: string
  fecha: string
  pesajes: { caravana: string; kg: number }[]
}
