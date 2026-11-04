import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { planKey } from './queries'
import type { Plan } from './types'

export { contratable, diaSiguiente, motivoPrimerCobro, pesos } from './cobro'

/** Crea la suscripción y lleva al cliente a pagarla en Mercado Pago. */
export function useSuscribir() {
  return useMutation({
    mutationFn: (planCodigo: string) =>
      api.post<{ url: string }>('/suscripcion', { planCodigo }).then((r) => r.data),
    onSuccess: ({ url }) => window.location.assign(url),
  })
}

/** Deja de cobrarse; el plan sigue hasta el último día pago. */
export function useCancelarSuscripcion() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => api.delete('/suscripcion'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: planKey }),
  })
}

/** Consulta a Mercado Pago al volver de pagar (sin esperar su aviso). */
export function useSincronizarSuscripcion() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => api.post<Plan>('/suscripcion/sincronizar').then((r) => r.data),
    onSuccess: (plan) => queryClient.setQueryData(planKey, plan),
  })
}
