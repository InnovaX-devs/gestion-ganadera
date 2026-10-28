export type TipoAgrupacion = 'lotes' | 'rodeos'

export interface Agrupacion {
  id: string
  establecimientoId: string
  nombre: string
  descripcion: string | null
  cantidadAnimales: number
  createdAt: string
}

export interface AgrupacionInput {
  nombre: string
  descripcion: string | null
}
