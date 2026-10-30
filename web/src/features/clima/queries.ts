import { useQuery } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { api } from '@/lib/api'
import type { Clima } from './types'

/**
 * Clima del establecimiento (#29). El backend consulta a Open-Meteo y guarda el pronóstico 30
 * minutos: no tiene sentido pedirlo más seguido.
 */
export function useClima(establecimientoId: string) {
  return useQuery({
    queryKey: ['clima', establecimientoId],
    queryFn: () =>
      api.get<Clima>(`/establecimientos/${establecimientoId}/clima`).then((r) => r.data),
    staleTime: 15 * 60 * 1000,
    // Sin ubicación (422) o sin acceso (404) no se arregla reintentando
    retry: (intentos, error) => !esSinUbicacion(error) && estado(error) !== 404 && intentos < 2,
  })
}

const estado = (error: unknown) => (isAxiosError(error) ? error.response?.status : undefined)

/** 422: el establecimiento no tiene ubicación o no se encontró su localidad. */
export const esSinUbicacion = (error: unknown) => estado(error) === 422
