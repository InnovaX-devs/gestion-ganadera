import type { EstadoReproductivo } from '@/features/reproduccion/types'
import type { Carencia } from '@/features/sanidad/types'

export type Sexo = 'HEMBRA' | 'MACHO'

export type Categoria =
  'VACA' | 'VAQUILLONA' | 'TERNERA' | 'TERNERO' | 'NOVILLITO' | 'NOVILLO' | 'TORITO' | 'TORO'

export type EstadoAnimal = 'ACTIVO' | 'VENDIDO' | 'MUERTO' | 'PERDIDO'

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
  /** Solo si está en período de carencia por un tratamiento (#20). */
  carencia?: Carencia | null
  /** Solo en vacas y vaquillonas (#19). Sin conexión no viene: sale de los eventos en el servidor. */
  estadoReproductivo?: EstadoReproductivo | null
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

/** Para abrir el listado ya filtrado desde otra pantalla: `<Link to="/animales" state={{ filtros }}>`. */
export interface AnimalesLocationState {
  filtros?: Omit<FiltroAnimales, 'pagina' | 'tamanio'>
}
