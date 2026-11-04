export type ModalidadPrecio = 'POR_KG' | 'POR_CABEZA'

export type MotivoBaja = 'MUERTE' | 'PERDIDA'

/**
 * Venta (#23). El cliente manda comprador, modalidad, precio, el peso (`kg` si es un animal,
 * `pesoTotal` si son varios: cada uno queda con el promedio) y, si hace falta, `confirmarCarencia`.
 * El servidor completa `kg`, `cantidad` e `importe` (por animal).
 */
export interface VentaDatos {
  comprador: string
  modalidad: ModalidadPrecio
  precio: number
  kg?: number | null
  pesoTotal?: number | null
  cantidad?: number | null
  /** DT-e o guía, texto libre. */
  documento?: string | null
  confirmarCarencia?: boolean | null
  importe?: number | null
}

/** Baja (#23). La causa es obligatoria si es muerte. */
export interface BajaDatos {
  motivo: MotivoBaja
  causa?: string | null
}
