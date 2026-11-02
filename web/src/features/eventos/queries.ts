import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { Pagina } from '@/features/animales/types'
import { stockKey } from '@/features/stock/queries'
import { conRespaldo } from '@/features/offline/conRespaldo'
import {
  anularEventoSinConexion,
  online,
  registrarEventoSinConexion,
} from '@/features/offline/escrituras'
import { eventosLocales } from '@/features/offline/local'
import { nuevoId } from '@/features/offline/red'
import { api } from '@/lib/api'
import type { Evento, EventoInput, FiltroEventos, Operacion } from './types'

const base = (establecimientoId: string) => `/establecimientos/${establecimientoId}/eventos`

export function useEventos<D = Record<string, unknown>>(
  establecimientoId: string | undefined,
  filtro: FiltroEventos,
) {
  return useQuery({
    queryKey: ['eventos', establecimientoId, filtro],
    // Sin conexión: el historial guardado en el dispositivo (#30)
    queryFn: () =>
      conRespaldo(
        () =>
          api
            .get<Pagina<Evento<D>>>(base(establecimientoId!), { params: filtro })
            .then((r) => r.data),
        () => eventosLocales(establecimientoId!, filtro) as Promise<Pagina<Evento<D>> | undefined>,
      ),
    enabled: !!establecimientoId,
    placeholderData: keepPreviousData,
  })
}

/**
 * Un evento puede cambiar animales (estado, lote, categoría, crías nuevas o, en un movimiento,
 * de establecimiento), así que al registrar o anular se refrescan los listados de animales, el
 * stock y los lotes/rodeos. No se limita al establecimiento activo: un movimiento también cambia
 * los del destino.
 */
function useInvalidar(establecimientoId: string) {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['eventos'] }),
      queryClient.invalidateQueries({ queryKey: ['animales'] }),
      queryClient.invalidateQueries({ queryKey: ['stock'] }),
      queryClient.invalidateQueries({ queryKey: ['agrupaciones'] }),
      queryClient.invalidateQueries({ queryKey: stockKey(establecimientoId) }),
    ])
}

/**
 * Sin conexión (o si se corta a mitad), el evento queda guardado en el dispositivo con
 * `pendiente: true` y se envía en la próxima sincronización (#30). El mismo `operacionId` sirve
 * para los dos caminos, así nunca se registra dos veces.
 */
export function useRegistrarEvento<D>(establecimientoId: string) {
  const invalidar = useInvalidar(establecimientoId)
  return useMutation({
    mutationFn: (data: EventoInput<D>) => {
      const conId = { ...data, operacionId: data.operacionId ?? nuevoId() }
      return online(
        () => api.post<Operacion>(base(establecimientoId), conId).then((r) => r.data),
        () => registrarEventoSinConexion(establecimientoId, conId),
      )
    },
    onSuccess: invalidar,
  })
}

export function useAnularEvento(establecimientoId: string) {
  const invalidar = useInvalidar(establecimientoId)
  return useMutation({
    mutationFn: (id: string) =>
      online(
        () => api.delete(`${base(establecimientoId)}/${id}`).then(() => undefined),
        () => anularEventoSinConexion(establecimientoId, id),
      ),
    onSuccess: invalidar,
  })
}
