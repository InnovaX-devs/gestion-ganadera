import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { stockKey } from '@/features/stock/queries'
import type { Animal, AnimalInput, FiltroAnimales, Pagina } from './types'

const base = (establecimientoId: string) => `/establecimientos/${establecimientoId}/animales`

/** Convierte los filtros de la pantalla en los query params que espera el backend. */
function params(filtro: FiltroAnimales) {
  return {
    q: filtro.q || undefined,
    categoria: filtro.categoria,
    sexo: filtro.sexo,
    loteId: filtro.lote && filtro.lote !== 'SIN' ? filtro.lote : undefined,
    sinLote: filtro.lote === 'SIN' ? true : undefined,
    rodeoId: filtro.rodeo && filtro.rodeo !== 'SIN' ? filtro.rodeo : undefined,
    sinRodeo: filtro.rodeo === 'SIN' ? true : undefined,
    pagina: filtro.pagina,
    tamanio: filtro.tamanio,
  }
}

export function useAnimales(establecimientoId: string | undefined, filtro: FiltroAnimales) {
  return useQuery({
    queryKey: ['animales', establecimientoId, 'lista', filtro],
    queryFn: () =>
      api
        .get<Pagina<Animal>>(base(establecimientoId!), { params: params(filtro) })
        .then((r) => r.data),
    enabled: !!establecimientoId,
    // Al cambiar de página o filtro se sigue mostrando lo anterior hasta que llegan los datos nuevos
    placeholderData: keepPreviousData,
  })
}

export function useAnimal(establecimientoId: string | undefined, id: string | undefined) {
  return useQuery({
    queryKey: ['animales', establecimientoId, 'ficha', id],
    queryFn: () => api.get<Animal>(`${base(establecimientoId!)}/${id}`).then((r) => r.data),
    enabled: !!establecimientoId && !!id,
    // Un 404 no se arregla reintentando
    retry: false,
  })
}

/** Hijos del animal (como madre o padre), incluidos vendidos y muertos. */
export function useCrias(establecimientoId: string | undefined, id: string | undefined) {
  return useQuery({
    queryKey: ['animales', establecimientoId, 'crias', id],
    queryFn: () => api.get<Animal[]>(`${base(establecimientoId!)}/${id}/crias`).then((r) => r.data),
    enabled: !!establecimientoId && !!id,
  })
}

/**
 * Cualquier cambio en animales refresca los listados, el stock y también lotes y rodeos
 * (muestran cuántos animales tienen).
 */
function useInvalidar(establecimientoId: string) {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['animales', establecimientoId] }),
      queryClient.invalidateQueries({ queryKey: stockKey(establecimientoId) }),
      queryClient.invalidateQueries({ queryKey: ['agrupaciones'] }),
    ])
}

export function useGuardarAnimal(establecimientoId: string) {
  const invalidar = useInvalidar(establecimientoId)
  return useMutation({
    mutationFn: ({ id, data }: { id?: string; data: AnimalInput }) =>
      (id
        ? api.put<Animal>(`${base(establecimientoId)}/${id}`, data)
        : api.post<Animal>(base(establecimientoId), data)
      ).then((r) => r.data),
    onSuccess: invalidar,
  })
}

export function useEliminarAnimal(establecimientoId: string) {
  const invalidar = useInvalidar(establecimientoId)
  return useMutation({
    mutationFn: (id: string) => api.delete(`${base(establecimientoId)}/${id}`),
    onSuccess: invalidar,
  })
}

export function useAsignarAnimales(establecimientoId: string) {
  const invalidar = useInvalidar(establecimientoId)
  return useMutation({
    mutationFn: (data: {
      animalIds: string[]
      campo: 'LOTE' | 'RODEO'
      destinoId: string | null
    }) =>
      api
        .post<{ actualizados: number }>(`${base(establecimientoId)}/asignar`, data)
        .then((r) => r.data),
    onSuccess: invalidar,
  })
}
