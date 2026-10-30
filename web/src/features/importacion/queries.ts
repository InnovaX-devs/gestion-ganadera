import { useMutation, useQueryClient } from '@tanstack/react-query'
import { agrupacionesKey } from '@/features/agrupaciones/queries'
import { stockKey } from '@/features/stock/queries'
import { api } from '@/lib/api'
import type { FilaImportacion, ResultadoImportacion } from './types'

/**
 * `confirmar: false` solo revisa la planilla (vista previa); `true` guarda todo, o nada si hay
 * algún error.
 */
export function useImportarAnimales(establecimientoId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ filas, confirmar }: { filas: FilaImportacion[]; confirmar: boolean }) =>
      api
        .post<ResultadoImportacion>(
          `/establecimientos/${establecimientoId}/animales/importar`,
          { filas },
          { params: { confirmar } },
        )
        .then((r) => r.data),
    onSuccess: (resultado) => {
      if (resultado.creados === 0) return
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: ['animales', establecimientoId] }),
        queryClient.invalidateQueries({ queryKey: agrupacionesKey('lotes', establecimientoId) }),
        queryClient.invalidateQueries({ queryKey: agrupacionesKey('rodeos', establecimientoId) }),
        queryClient.invalidateQueries({ queryKey: stockKey(establecimientoId) }),
      ])
    },
  })
}
