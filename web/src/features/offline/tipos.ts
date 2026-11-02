import type { Categoria, EstadoAnimal, Sexo } from '@/features/animales/types'
import type { Rol } from '@/features/establecimientos/permisos'
import type { EventoInput, TipoEvento } from '@/features/eventos/types'
import type { TipoProducto, ViaAdministracion } from '@/features/sanidad/types'

/** Tipos de POST /api/sync (ver docs/sync.md). */

export type TipoOperacion =
  'REGISTRAR_EVENTO' | 'REGISTRAR_EVENTOS_POR_ANIMAL' | 'ANULAR_EVENTO' | 'ACTUALIZAR_ANIMAL'

export interface OperacionSync {
  /** Lo genera el celular. Reenviarla no la repite. */
  id: string
  tipo: TipoOperacion
  evento?: Omit<EventoInput, 'operacionId'>
  eventoTipo?: TipoEvento
  fecha?: string
  items?: { animalId: string; datos: Record<string, unknown> }[]
  eventoId?: string
  animalId?: string
  cambios?: Record<string, unknown>
  modificadoEn?: string
}

export interface ResultadoOperacion {
  id: string
  estado: 'APLICADO' | 'RECHAZADO' | 'ERROR'
  mensaje?: string
  errores?: { field: string; message: string }[]
  operacionId?: string
  aplicados?: string[]
  descartados?: string[]
}

export interface AnimalSync {
  id: string
  caravana: string
  caravanaElectronica?: string
  sexo: Sexo
  categoria: Categoria
  raza?: string
  fechaNacimiento?: string
  fechaNacimientoEstimada: boolean
  castrado: boolean
  loteId?: string
  rodeoId?: string
  madreId?: string
  padreId?: string
  estado: EstadoAnimal
  observaciones?: string
  updatedAt: string
}

export interface AgrupacionSync {
  id: string
  nombre: string
  descripcion?: string
  activo: boolean
  updatedAt: string
}

export interface ProductoSync {
  id: string
  nombre: string
  tipo: TipoProducto
  diasCarencia: number
  dosisHabitual?: string
  viaHabitual?: ViaAdministracion
  activo: boolean
  updatedAt: string
}

export interface EventoSync {
  id: string
  operacionId: string
  tipo: TipoEvento
  fecha: string
  animalId?: string
  loteId?: string
  datos: Record<string, unknown>
  registradoPor: string
  createdAt: string
  anulado: boolean
  updatedAt: string
}

export interface SyncResponse {
  cursor: string
  hayMas: boolean
  servidorEn: string
  resultados: ResultadoOperacion[]
  establecimiento: { id: string; nombre: string; localidad?: string; provincia?: string; rol: Rol }
  cambios: {
    lotes: AgrupacionSync[]
    rodeos: AgrupacionSync[]
    productos: ProductoSync[]
    animales: AnimalSync[]
    eventos: EventoSync[]
  }
  bajas: { entidad: 'ANIMAL'; id: string; en: string }[]
}

/** Algo hecho sin conexión que todavía no llegó al servidor. */
export interface OperacionPendiente {
  id: string
  establecimientoId: string
  operacion: OperacionSync
  /** Para mostrarla: "Observación · V-214" */
  descripcion: string
  creadaEn: string
  /**
   * PENDIENTE: todavía no se envió. ERROR: falló el servidor, se reintenta. RECHAZADO: el servidor
   * no la acepta (hay que corregirla o descartarla).
   */
  estado: 'PENDIENTE' | 'ERROR' | 'RECHAZADO'
  mensaje?: string
  errores?: { field: string; message: string }[]
  intentos: number
}
