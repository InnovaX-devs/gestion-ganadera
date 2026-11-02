import type { Referencia } from '@/features/animales/types'

export type TipoDestino = 'ANIMALES' | 'RODEO' | 'LOTE'

export interface Destino {
  tipo: TipoDestino
  animales: Referencia[]
  rodeoId: string
  loteId: string
}

export const DESTINO_VACIO: Destino = { tipo: 'ANIMALES', animales: [], rodeoId: '', loteId: '' }

export function errorDeDestino(d: Destino): string | null {
  if (d.tipo === 'ANIMALES' && d.animales.length === 0)
    return 'Elegí al menos una vaca o vaquillona'
  if (d.tipo === 'RODEO' && !d.rodeoId) return 'Elegí el rodeo'
  if (d.tipo === 'LOTE' && !d.loteId) return 'Elegí el lote'
  return null
}

export function destinoAPedido(d: Destino) {
  return {
    animalIds: d.tipo === 'ANIMALES' ? d.animales.map((a) => a.id) : [],
    rodeoId: d.tipo === 'RODEO' ? d.rodeoId : null,
    loteId: d.tipo === 'LOTE' ? d.loteId : null,
  }
}
