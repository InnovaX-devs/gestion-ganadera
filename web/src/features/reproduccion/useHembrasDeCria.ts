import { useAnimales } from '@/features/animales/queries'

/**
 * Cuántas vacas y vaquillonas activas tiene un lote o rodeo: son las que reciben un servicio o
 * tacto cargado para el grupo entero. Dos consultas de un solo resultado, solo por el total.
 */
export function useHembrasDeCria(
  establecimientoId: string,
  grupo: { lote?: string; rodeo?: string },
) {
  const habilitado = !!(grupo.lote || grupo.rodeo)
  const id = habilitado ? establecimientoId : undefined
  const vacas = useAnimales(id, { ...grupo, categoria: 'VACA', tamanio: 1 })
  const vaquillonas = useAnimales(id, { ...grupo, categoria: 'VAQUILLONA', tamanio: 1 })
  if (!habilitado || !vacas.data || !vaquillonas.data) return undefined
  return { vacas: vacas.data.total, vaquillonas: vaquillonas.data.total }
}

/** "Se registra en las 34 vacas y 12 vaquillonas del rodeo (toros y terneros no)." */
export function textoHembras(
  cantidad: { vacas: number; vaquillonas: number } | undefined,
  grupo: 'lote' | 'rodeo',
) {
  if (!cantidad) return undefined
  const { vacas, vaquillonas } = cantidad
  if (vacas + vaquillonas === 0) return `No tiene vacas ni vaquillonas`
  const partes = [
    vacas > 0 ? `${vacas} ${vacas === 1 ? 'vaca' : 'vacas'}` : null,
    vaquillonas > 0 ? `${vaquillonas} ${vaquillonas === 1 ? 'vaquillona' : 'vaquillonas'}` : null,
  ].filter(Boolean)
  const total = vacas + vaquillonas
  return `Se registra en ${total === 1 ? 'la' : 'las'} ${partes.join(' y ')} del ${grupo}. El resto (toros, terneros) queda afuera.`
}
