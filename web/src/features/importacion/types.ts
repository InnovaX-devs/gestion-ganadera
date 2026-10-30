/** Una fila de la planilla tal como se leyó: todo texto. El backend la interpreta y la valida. */
export interface FilaImportacion {
  /** Número de fila en la planilla, para mostrar los errores donde el usuario los busca */
  fila: number
  caravana?: string
  caravanaElectronica?: string
  sexo?: string
  categoria?: string
  raza?: string
  fechaNacimiento?: string
  fechaNacimientoEstimada?: string
  castrado?: string
  lote?: string
  rodeo?: string
  madre?: string
  padre?: string
  observaciones?: string
}

export interface ErrorFila {
  fila: number
  campo: string | null
  mensaje: string
}

export interface ResultadoImportacion {
  total: number
  validas: number
  creados: number
  errores: ErrorFila[]
  lotesNuevos: string[]
  rodeosNuevos: string[]
}
