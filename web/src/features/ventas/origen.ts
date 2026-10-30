import type { Referencia } from '@/features/animales/types'

export type Grupo = 'ANIMALES' | 'LOTE' | 'RODEO'

/** Qué animales se eligieron: algunos puntuales, o todo un lote o rodeo. */
export interface Origen {
  grupo: Grupo
  elegidos: Referencia[]
  loteId: string
  rodeoId: string
}

export const ORIGEN_VACIO: Origen = { grupo: 'ANIMALES', elegidos: [], loteId: '', rodeoId: '' }

/** Lo que va en el pedido para esos animales. */
export function origenAPedido(o: Origen) {
  return {
    animalIds: o.grupo === 'ANIMALES' ? o.elegidos.map((a) => a.id) : [],
    loteId: o.grupo === 'LOTE' ? o.loteId : null,
    rodeoId: o.grupo === 'RODEO' ? o.rodeoId : null,
  }
}

/** Mensaje si falta elegir algo, o null si está bien. */
export function errorDeOrigen(o: Origen): string | null {
  if (o.grupo === 'ANIMALES' && o.elegidos.length === 0) return 'Elegí al menos un animal'
  if (o.grupo === 'LOTE' && !o.loteId) return 'Elegí el lote'
  if (o.grupo === 'RODEO' && !o.rodeoId) return 'Elegí el rodeo'
  return null
}

/** Cuántos animales hay en el origen elegido (null si todavía no se sabe). */
export function cantidadDeOrigen(
  o: Origen,
  lotes?: { id: string; cantidadAnimales: number }[],
  rodeos?: { id: string; cantidadAnimales: number }[],
): number | null {
  if (o.grupo === 'ANIMALES') return o.elegidos.length
  const grupo =
    o.grupo === 'LOTE'
      ? lotes?.find((l) => l.id === o.loteId)
      : rodeos?.find((r) => r.id === o.rodeoId)
  return grupo ? grupo.cantidadAnimales : null
}
