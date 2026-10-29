import type { Categoria, Sexo } from '@/features/animales/types'

/**
 * Stock de un lote o rodeo. Sin `id` ni `nombre` es el grupo de los animales que no tienen lote (o
 * rodeo) asignado. Los mapas traen siempre todas las claves, en 0 si no hay.
 */
export interface StockGrupo {
  id?: string
  nombre?: string
  total: number
  porSexo: Record<Sexo, number>
  porCategoria: Record<Categoria, number>
}

/** GET /stock?establecimientoId=: animales activos, calculado en el momento por el backend. */
export interface Stock {
  establecimientoId: string
  total: number
  porSexo: Record<Sexo, number>
  porCategoria: Record<Categoria, number>
  /** Todos los lotes activos (también los vacíos) por nombre; al final, los sin lote si hay. */
  porLote: StockGrupo[]
  porRodeo: StockGrupo[]
}
