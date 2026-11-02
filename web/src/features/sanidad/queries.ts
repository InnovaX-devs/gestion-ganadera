import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { conRespaldo } from '@/features/offline/conRespaldo'
import { productosLocales } from '@/features/offline/local'
import { api } from '@/lib/api'
import type { ProductoInput, ProductoSanitario } from './types'

const base = (establecimientoId: string) =>
  `/establecimientos/${establecimientoId}/productos-sanitarios`

const key = (establecimientoId: string | undefined) => ['productos-sanitarios', establecimientoId]

/** Catálogo de productos activos, por nombre. */
export function useProductos(establecimientoId: string | undefined) {
  return useQuery({
    queryKey: key(establecimientoId),
    queryFn: () =>
      conRespaldo(
        () => api.get<ProductoSanitario[]>(base(establecimientoId!)).then((r) => r.data),
        () => productosLocales(establecimientoId!),
      ),
    enabled: !!establecimientoId,
  })
}

export function useGuardarProducto(establecimientoId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id?: string; data: ProductoInput }) =>
      (id
        ? api.put<ProductoSanitario>(`${base(establecimientoId)}/${id}`, data)
        : api.post<ProductoSanitario>(base(establecimientoId), data)
      ).then((r) => r.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key(establecimientoId) }),
  })
}

export function useEliminarProducto(establecimientoId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.delete(`${base(establecimientoId)}/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key(establecimientoId) }),
  })
}
