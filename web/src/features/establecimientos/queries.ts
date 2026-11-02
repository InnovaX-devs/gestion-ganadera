import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { conRespaldo } from '@/features/offline/conRespaldo'
import { establecimientosLocales, guardarEstablecimientos } from '@/features/offline/local'
import { api } from '@/lib/api'
import type { Establecimiento, EstablecimientoInput } from './types'

const KEY = ['establecimientos'] as const

export const establecimientosApi = {
  listar: () => api.get<Establecimiento[]>('/establecimientos').then((r) => r.data),
  crear: (data: EstablecimientoInput) =>
    api.post<Establecimiento>('/establecimientos', data).then((r) => r.data),
  actualizar: (id: string, data: EstablecimientoInput) =>
    api.put<Establecimiento>(`/establecimientos/${id}`, data).then((r) => r.data),
  eliminar: (id: string) => api.delete(`/establecimientos/${id}`),
}

/** La lista se guarda en el dispositivo cada vez que llega, para poder abrir la app sin conexión (#30). */
export function useEstablecimientos() {
  return useQuery({
    queryKey: KEY,
    queryFn: () =>
      conRespaldo(async () => {
        const lista = await establecimientosApi.listar()
        guardarEstablecimientos(lista).catch(() => undefined)
        return lista
      }, establecimientosLocales),
  })
}

/** Después de cualquier cambio se vuelve a pedir la lista (la usan la página y el selector del header). */
function useInvalidar() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: KEY })
}

export function useCrearEstablecimiento() {
  const invalidar = useInvalidar()
  return useMutation({ mutationFn: establecimientosApi.crear, onSuccess: invalidar })
}

export function useActualizarEstablecimiento() {
  const invalidar = useInvalidar()
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: EstablecimientoInput }) =>
      establecimientosApi.actualizar(id, data),
    onSuccess: invalidar,
  })
}

export function useEliminarEstablecimiento() {
  const invalidar = useInvalidar()
  return useMutation({ mutationFn: establecimientosApi.eliminar, onSuccess: invalidar })
}
