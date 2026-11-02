import { type QueryClient, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { stockKey } from '@/features/stock/queries'
import { conRespaldo } from '@/features/offline/conRespaldo'
import { agrupacionesLocales } from '@/features/offline/local'
import { api } from '@/lib/api'
import type { Agrupacion, AgrupacionInput, TipoAgrupacion } from './types'

const base = (tipo: TipoAgrupacion, establecimientoId: string) =>
  `/establecimientos/${establecimientoId}/${tipo}`

export const agrupacionesKey = (tipo: TipoAgrupacion, establecimientoId: string | undefined) =>
  ['agrupaciones', tipo, establecimientoId] as const

/** El stock muestra el nombre de cada lote y rodeo: también se refresca al cambiarlos. */
function invalidar(queryClient: QueryClient, tipo: TipoAgrupacion, establecimientoId: string) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: agrupacionesKey(tipo, establecimientoId) }),
    queryClient.invalidateQueries({ queryKey: stockKey(establecimientoId) }),
  ])
}

/** Lotes o rodeos del establecimiento. No pide nada mientras no haya establecimiento. */
export function useAgrupaciones(tipo: TipoAgrupacion, establecimientoId: string | undefined) {
  return useQuery({
    queryKey: agrupacionesKey(tipo, establecimientoId),
    queryFn: () =>
      conRespaldo(
        () => api.get<Agrupacion[]>(base(tipo, establecimientoId!)).then((r) => r.data),
        () => agrupacionesLocales(tipo, establecimientoId!),
      ),
    enabled: !!establecimientoId,
  })
}

export function useGuardarAgrupacion(tipo: TipoAgrupacion, establecimientoId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id?: string; data: AgrupacionInput }) =>
      (id
        ? api.put<Agrupacion>(`${base(tipo, establecimientoId)}/${id}`, data)
        : api.post<Agrupacion>(base(tipo, establecimientoId), data)
      ).then((r) => r.data),
    onSuccess: () => invalidar(queryClient, tipo, establecimientoId),
  })
}

export function useEliminarAgrupacion(tipo: TipoAgrupacion, establecimientoId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.delete(`${base(tipo, establecimientoId)}/${id}`),
    onSuccess: () => invalidar(queryClient, tipo, establecimientoId),
  })
}
