import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Alertas, Plan, PlanInput } from './types'

const base = (establecimientoId: string) => `/establecimientos/${establecimientoId}/plan-sanitario`

export function usePlanSanitario(
  establecimientoId: string | undefined,
  desde: string,
  hasta: string,
) {
  return useQuery({
    queryKey: ['plan-sanitario', establecimientoId, desde, hasta],
    queryFn: () =>
      api.get<Plan[]>(base(establecimientoId!), { params: { desde, hasta } }).then((r) => r.data),
    enabled: !!establecimientoId,
  })
}

export function useAlertas(establecimientoId: string | undefined) {
  return useQuery({
    queryKey: ['alertas', establecimientoId],
    queryFn: () =>
      api.get<Alertas>(`/establecimientos/${establecimientoId}/alertas`).then((r) => r.data),
    enabled: !!establecimientoId,
  })
}

/** El plan y las alertas van juntos: cualquier cambio en uno refresca los dos. */
function useInvalidar(establecimientoId: string) {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['plan-sanitario', establecimientoId] }),
      queryClient.invalidateQueries({ queryKey: ['alertas', establecimientoId] }),
    ])
}

export function useGuardarPlan(establecimientoId: string) {
  const invalidar = useInvalidar(establecimientoId)
  return useMutation({
    mutationFn: ({ id, data }: { id?: string; data: PlanInput }) =>
      id
        ? api.put<Plan>(`${base(establecimientoId)}/${id}`, data).then((r) => [r.data])
        : api.post<Plan[]>(base(establecimientoId), data).then((r) => r.data),
    onSuccess: invalidar,
  })
}

export type AccionPlan = 'realizado' | 'reabrir' | 'cancelar' | 'eliminar'

export function useAccionPlan(establecimientoId: string) {
  const invalidar = useInvalidar(establecimientoId)
  return useMutation({
    mutationFn: ({
      id,
      accion,
      operacionId,
    }: {
      id: string
      accion: AccionPlan
      operacionId?: string
    }) =>
      accion === 'eliminar'
        ? api.delete(`${base(establecimientoId)}/${id}`).then(() => undefined)
        : api
            .post<Plan>(
              `${base(establecimientoId)}/${id}/${accion}`,
              operacionId ? { operacionId } : {},
            )
            .then(() => undefined),
    onSuccess: invalidar,
  })
}

export function usePreferencias() {
  return useQuery({
    queryKey: ['preferencias'],
    queryFn: () => api.get<{ alertasEmail: boolean }>('/usuario/preferencias').then((r) => r.data),
  })
}

export function useGuardarPreferencias() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: { alertasEmail: boolean }) =>
      api.put<{ alertasEmail: boolean }>('/usuario/preferencias', data).then((r) => r.data),
    onSuccess: (data) => queryClient.setQueryData(['preferencias'], data),
  })
}
