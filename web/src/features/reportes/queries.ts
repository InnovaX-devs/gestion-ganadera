import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Existencias, FiltroPesos, ReportePesos } from './types'

const base = (establecimientoId: string) => `/establecimientos/${establecimientoId}/reportes`

export function useReportePesos(
  establecimientoId: string | undefined,
  filtro: FiltroPesos,
  habilitado: boolean,
) {
  return useQuery({
    queryKey: ['reportes', establecimientoId, 'pesos', filtro],
    queryFn: () =>
      api
        .get<ReportePesos>(`${base(establecimientoId!)}/pesos`, { params: filtro })
        .then((r) => r.data),
    enabled: !!establecimientoId && habilitado,
    // Al cambiar un filtro se mantiene el reporte anterior hasta que llega el nuevo
    placeholderData: keepPreviousData,
  })
}

export function useExistencias(establecimientoId: string | undefined, habilitado: boolean) {
  return useQuery({
    queryKey: ['reportes', establecimientoId, 'existencias'],
    queryFn: () =>
      api.get<Existencias>(`${base(establecimientoId!)}/existencias`).then((r) => r.data),
    enabled: !!establecimientoId && habilitado,
  })
}
