import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Stock } from './types'

export const stockKey = (establecimientoId: string | undefined) =>
  ['stock', establecimientoId] as const

/**
 * Stock del establecimiento. Se refresca solo cuando cambian animales, lotes o rodeos (ver las
 * invalidaciones en animales/queries.ts y agrupaciones/queries.ts).
 */
export function useStock(establecimientoId: string | undefined) {
  return useQuery({
    queryKey: stockKey(establecimientoId),
    queryFn: () => api.get<Stock>('/stock', { params: { establecimientoId } }).then((r) => r.data),
    enabled: !!establecimientoId,
  })
}
