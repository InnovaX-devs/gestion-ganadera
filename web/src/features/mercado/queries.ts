import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Historico, MercadoHoy, Producto } from './types'

// Las cotizaciones cambian una vez por día: no hace falta pedirlas seguido
const DIEZ_MINUTOS = 10 * 60 * 1000

/** Cotizaciones del día (#27). Son las mismas para todos los establecimientos. */
export function useMercadoHoy() {
  return useQuery({
    queryKey: ['mercado', 'hoy'],
    queryFn: () => api.get<MercadoHoy>('/mercado/hoy').then((r) => r.data),
    staleTime: DIEZ_MINUTOS,
  })
}

/**
 * Últimos 30 días de esos productos (más la relación de reposición, que viene siempre). Se pide
 * recién cuando `habilitado`: el gráfico se abre al hacer clic.
 */
export function useHistoricoMercado(productos: Producto[], habilitado: boolean) {
  return useQuery({
    queryKey: ['mercado', 'historico', productos],
    queryFn: () =>
      api
        .get<Historico>('/mercado/historico', {
          params: { producto: productos },
          // producto=A&producto=B (no producto[]=A), que es lo que espera Spring
          paramsSerializer: { indexes: null },
        })
        .then((r) => r.data),
    enabled: habilitado,
    staleTime: DIEZ_MINUTOS,
  })
}
