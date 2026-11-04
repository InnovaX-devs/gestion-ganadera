import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { Pagina } from '@/features/animales/types'
import type { Operacion } from '@/features/eventos/types'
import { online, registrarGrillaSinConexion } from '@/features/offline/escrituras'
import { esperarSoloEnLinea, nuevoId } from '@/features/offline/red'
import { api } from '@/lib/api'
import type { FiltroPesajes, GrillaInput, Pesaje } from './types'

const base = (establecimientoId: string) => `/establecimientos/${establecimientoId}/pesajes`

/** Pesajes con su ganancia diaria, del más nuevo al más viejo. Con `animalId`, la curva del animal. */
export function usePesajes(establecimientoId: string | undefined, filtro: FiltroPesajes) {
  return useQuery({
    queryKey: ['pesajes', establecimientoId, filtro],
    queryFn: () =>
      api.get<Pagina<Pesaje>>(base(establecimientoId!), { params: filtro }).then((r) => r.data),
    enabled: !!establecimientoId,
    placeholderData: keepPreviousData,
  })
}

/** Carga rápida: varios animales por caravana, cada uno con su peso. Todo o nada. */
export function useRegistrarGrilla(establecimientoId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    // Sin conexión queda pendiente y se envía al sincronizar (#30)
    mutationFn: (data: GrillaInput) => {
      const conId = { ...data, operacionId: data.operacionId ?? nuevoId() }
      return online(
        () => api.post<Operacion>(`${base(establecimientoId)}/grilla`, conId).then((r) => r.data),
        () => registrarGrillaSinConexion(establecimientoId, conId),
      )
    },
    onSuccess: () =>
      esperarSoloEnLinea(
        Promise.all([
          queryClient.invalidateQueries({ queryKey: ['pesajes', establecimientoId] }),
          queryClient.invalidateQueries({ queryKey: ['eventos', establecimientoId] }),
          // Un pesaje puede cambiar la categoría de un animal
          queryClient.invalidateQueries({ queryKey: ['animales', establecimientoId] }),
          queryClient.invalidateQueries({ queryKey: ['agrupaciones'] }),
        ]),
      ),
  })
}
