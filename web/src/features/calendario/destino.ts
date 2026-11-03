import type { Plan } from './types'

/** "Lote Recría", "Rodeo Cría" o "Todo el establecimiento" */
export function textoDestino(p: Plan) {
  if (p.destino.tipo === 'LOTE') return `Lote ${p.destino.nombre}`
  if (p.destino.tipo === 'RODEO') return `Rodeo ${p.destino.nombre}`
  return 'Todo el establecimiento'
}
