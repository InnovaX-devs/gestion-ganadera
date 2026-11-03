/** Un límite que no viene es "sin límite"; sin precio (o 0), no se contrata online. */
export interface PlanInfo {
  codigo: string
  nombre: string
  maxAnimales?: number | null
  maxEstablecimientos?: number | null
  maxUsuarios?: number | null
  precioMensual?: number | null
}

export interface Plan {
  /** El que rige hoy (durante la prueba, el de prueba) */
  plan: PlanInfo
  /** El de la organización, que rige cuando termina la prueba */
  contratado: PlanInfo
  enPrueba: boolean
  pruebaHasta?: string | null
  diasDePrueba: number
  uso: {
    animales: number
    establecimientos: number
    usuarios: number
    /** Ocupan lugar de usuario hasta que se aceptan, vencen o se revocan */
    invitacionesPendientes: number
  }
  planes: PlanInfo[]
  cobro?: Cobro | null
}

export type EstadoSuscripcion = 'ACTIVA' | 'PAUSADA' | 'CANCELADA' | 'VENCIDA'

/** Suscripción mensual con Mercado Pago (#35). */
export interface Cobro {
  /** El pago online está configurado */
  habilitado: boolean
  /**
   * La cuenta tiene el uso pago (prueba, pago manual o suscripción al día). Si es false solo puede
   * entrar a Mi plan: ningún plan es gratis.
   */
  habilitada: boolean
  /** Pagó por fuera de Mercado Pago hasta este día (lo carga el panel de Innovax) */
  pagoManualHasta?: string | null
  /** Quien paga por transferencia paga del 1 a este día de cada mes */
  diaVencimientoManual?: number
  /** Días que sigue el plan si no se puede cobrar */
  diasGracia: number
  /** La que rige o rigió el plan */
  suscripcion?: {
    planCodigo: string
    planNombre: string
    monto: number
    estado: EstadoSuscripcion
    /** Último día pago (null: todavía no se aprobó ningún cobro) */
    pagadoHasta?: string | null
    /** Primer cobro si se suscribió durante la prueba (null: enseguida) */
    primerCobro?: string | null
    /** No se pudo cobrar y corren los días de gracia */
    enGracia: boolean
    /** Último día en que rige el plan si no se cobra más */
    hasta?: string | null
  } | null
  /** Creada pero sin pagar: link de Mercado Pago para terminar */
  pendiente?: {
    planCodigo: string
    planNombre: string
    monto: number
    url: string
    primerCobro?: string | null
  } | null
}

export type Recurso = 'animales' | 'establecimientos' | 'usuarios'
