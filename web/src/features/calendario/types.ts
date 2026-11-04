export type EstadoPlan = 'PENDIENTE' | 'REALIZADO' | 'CANCELADO'

/** Tratamiento planificado (#34). */
export interface Plan {
  id: string
  fecha: string
  /** A quién se aplica: un lote, un rodeo o todo el establecimiento */
  destino: { tipo: 'LOTE' | 'RODEO' | 'ESTABLECIMIENTO'; id?: string; nombre?: string }
  producto?: { id: string; nombre: string }
  descripcion: string
  dosis?: string
  notas?: string
  avisarDiasAntes: number
  estado: EstadoPlan
  /** Pendiente con fecha anterior a hoy */
  vencido: boolean
  realizadoOperacionId?: string
  realizadoEn?: string
  creadoPor?: string
}

export interface PlanInput {
  fecha: string
  loteId?: string | null
  rodeoId?: string | null
  productoId?: string | null
  descripcion: string
  dosis?: string | null
  notas?: string | null
  avisarDiasAntes: number
  /** Solo al crear */
  repeticiones?: number
  cadaDias?: number
}

/** GET /establecimientos/{id}/alertas */
export interface Alertas {
  hoy: string
  diasProximos: number
  tratamientosVencidos: Plan[]
  tratamientosProximos: Plan[]
  carencias: {
    animalesEnCarencia: number
    salenProximamente: {
      animalId: string
      caravana: string
      libreDesde: string
      productos: string
    }[]
  }
}
