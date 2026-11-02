import { useQuery } from '@tanstack/react-query'
import { conRespaldo } from '@/features/offline/conRespaldo'
import { stockLocal } from '@/features/offline/local'
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
    // Sin conexión se calcula con los animales guardados en el dispositivo (#30)
    queryFn: () =>
      conRespaldo(
        () => api.get<Stock>('/stock', { params: { establecimientoId } }).then((r) => r.data),
        () => stockLocal(establecimientoId!),
      ),
    enabled: !!establecimientoId,
  })
}
