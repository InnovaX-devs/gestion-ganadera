import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { Pagina } from '@/features/animales/types'
import { stockKey } from '@/features/stock/queries'
import { api } from '@/lib/api'
import type { Evento, EventoInput, FiltroEventos, Operacion } from './types'

const base = (establecimientoId: string) => `/establecimientos/${establecimientoId}/eventos`

export function useEventos<D = Record<string, unknown>>(
  establecimientoId: string | undefined,
  filtro: FiltroEventos,
) {
  return useQuery({
    queryKey: ['eventos', establecimientoId, filtro],
    queryFn: () =>
      api.get<Pagina<Evento<D>>>(base(establecimientoId!), { params: filtro }).then((r) => r.data),
    enabled: !!establecimientoId,
    placeholderData: keepPreviousData,
  })
}

/**
 * Un evento puede cambiar animales (estado, lote, categoría, crías nuevas), así que al registrar o
 * anular se refrescan también los listados de animales, de lotes/rodeos y el stock.
 */
function useInvalidar(establecimientoId: string) {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['eventos', establecimientoId] }),
      queryClient.invalidateQueries({ queryKey: ['animales', establecimientoId] }),
      queryClient.invalidateQueries({ queryKey: ['agrupaciones'] }),
      queryClient.invalidateQueries({ queryKey: stockKey(establecimientoId) }),
    ])
}

export function useRegistrarEvento<D>(establecimientoId: string) {
  const invalidar = useInvalidar(establecimientoId)
  return useMutation({
    mutationFn: (data: EventoInput<D>) =>
      api.post<Operacion>(base(establecimientoId), data).then((r) => r.data),
    onSuccess: invalidar,
  })
}

export function useAnularEvento(establecimientoId: string) {
  const invalidar = useInvalidar(establecimientoId)
  return useMutation({
    mutationFn: (id: string) => api.delete(`${base(establecimientoId)}/${id}`),
    onSuccess: invalidar,
  })
}
