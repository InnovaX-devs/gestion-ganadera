export type Sexo = 'HEMBRA' | 'MACHO'

export type Categoria =
  'VACA' | 'VAQUILLONA' | 'TERNERA' | 'TERNERO' | 'NOVILLITO' | 'NOVILLO' | 'TORITO' | 'TORO'

export type EstadoAnimal = 'ACTIVO' | 'VENDIDO' | 'MUERTO'

/** Dato relacionado: lote, rodeo, madre o padre (para animales, `nombre` es la caravana). */
export interface Referencia {
  id: string
  nombre: string
}

/** El backend omite los campos vacíos: por eso los opcionales pueden faltar. */
export interface Animal {
  id: string
  establecimientoId: string
  caravana: string
  caravanaElectronica?: string | null
  sexo: Sexo
  categoria: Categoria
  raza?: string | null
  fechaNacimiento?: string | null
  fechaNacimientoEstimada: boolean
  castrado: boolean
  estado: EstadoAnimal
  lote?: Referencia | null
  rodeo?: Referencia | null
  madre?: Referencia | null
  padre?: Referencia | null
  observaciones?: string | null
  createdAt: string
}

export interface AnimalInput {
  caravana: string
  caravanaElectronica: string | null
  sexo: Sexo
  categoria: Categoria
  raza: string | null
  fechaNacimiento: string | null
  fechaNacimientoEstimada: boolean
  castrado: boolean
  loteId: string | null
  rodeoId: string | null
  madreId: string | null
  padreId: string | null
  observaciones: string | null
}

export interface FiltroAnimales {
  q?: string
  categoria?: Categoria
  sexo?: Sexo
  /** id de lote, o 'SIN' para los que no tienen lote */
  lote?: string
  rodeo?: string
  pagina?: number
  tamanio?: number
}

export interface Pagina<T> {
  contenido: T[]
  total: number
  pagina: number
  tamanio: number
  totalPaginas: number
}

export interface ResumenAnimales {
  total: number
  hembras: number
  machos: number
  porCategoria: Record<Categoria, number>
}
