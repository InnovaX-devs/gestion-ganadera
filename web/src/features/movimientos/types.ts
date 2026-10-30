import type { Referencia } from '@/features/eventos/types'

/** Dónde está (o estaba) un animal. Lote o rodeo vacíos: sin asignar. */
export interface Ubicacion {
  establecimiento: Referencia
  lote?: Referencia | null
  rodeo?: Referencia | null
}

/**
 * Movimiento (#22). El cliente manda solo el destino; `desde` y `hacia` los completa el servidor
 * por animal y vienen en el historial.
 *
 * - Mismo establecimiento: cambian solo los campos indicados (`quitarLote` / `quitarRodeo` dejan
 *   al animal sin asignar).
 * - Otro establecimiento: el animal queda en el lote y rodeo indicados (del destino) o sin asignar.
 */
export interface MovimientoDatos {
  establecimientoDestinoId?: string | null
  loteDestinoId?: string | null
  rodeoDestinoId?: string | null
  quitarLote?: boolean
  quitarRodeo?: boolean
  desde?: Ubicacion
  hacia?: Ubicacion
}
