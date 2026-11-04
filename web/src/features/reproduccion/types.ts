export type TipoServicio = 'NATURAL' | 'INSEMINACION'

export type ResultadoTacto = 'PRENADA' | 'VACIA'

/** Un toro del servicio, con la caravana que tenía al registrarlo. */
export interface ToroServicio {
  id: string
  caravana: string
}

/**
 * Servicio (#19). La fecha del evento es el inicio. Se envían `toroIds`; `toros` lo completa el
 * servidor. `pajuela` solo en inseminación.
 */
export interface ServicioDatos {
  tipo: TipoServicio
  fechaFin?: string | null
  toroIds?: string[]
  toros?: ToroServicio[] | null
  pajuela?: string | null
  observaciones?: string | null
}

/** Tacto (#19). `fechaEstimadaParto` la calcula el servidor si está preñada. */
export interface TactoDatos {
  resultado: ResultadoTacto
  mesesGestacion?: number | null
  veterinario?: string | null
  observaciones?: string | null
  fechaEstimadaParto?: string | null
}

export type EstadoReproductivoCodigo =
  'SIN_DATOS' | 'EN_SERVICIO' | 'SERVIDA' | 'PRENADA' | 'VACIA' | 'PARIDA'

/** Sale del último servicio, tacto o parto (EstadoReproductivoService). Solo vacas y vaquillonas. */
export interface EstadoReproductivo {
  estado: EstadoReproductivoCodigo
  /** Fecha del evento que define el estado. */
  desde?: string | null
  /** Solo si está preñada. */
  fechaEstimadaParto?: string | null
  ultimoServicio?: {
    fecha: string
    fechaFin?: string | null
    tipo: TipoServicio
    toros?: string[] | null
    pajuela?: string | null
  } | null
  ultimoTacto?: {
    fecha: string
    resultado: ResultadoTacto
    mesesGestacion?: number | null
    veterinario?: string | null
    fechaEstimadaParto?: string | null
  } | null
  ultimoParto?: string | null
}
