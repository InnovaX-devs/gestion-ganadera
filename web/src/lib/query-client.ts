import { MutationCache, QueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { esperarSoloEnLinea } from '@/features/offline/red'
import { setOnPagoRequerido } from './api'

/**
 * Caché de datos del servidor (TanStack Query).
 * - Los datos se consideran frescos 30 s: navegar entre páginas no repite requests.
 * - Los errores 4xx (validación, 404, 401...) no se reintentan; los de red o 5xx, una vez.
 * - `offlineFirst`: sin conexión las consultas y mutaciones se ejecutan igual (no quedan en pausa),
 *   porque saben responder con lo guardado en el dispositivo (#30, src/features/offline).
 */
export const queryClient: QueryClient = new QueryClient({
  // Cualquier carga puede cambiar el uso del plan (#35): se refresca, y si alguna chocó con un
  // límite, el aviso del plan aparece enseguida. Solo se vuelve a pedir si hay una pantalla usándolo.
  mutationCache: new MutationCache({
    // Sin conexión no se espera: el refresco queda en pausa hasta que vuelva la red (ver esperarSoloEnLinea)
    onSettled: () => esperarSoloEnLinea(queryClient.invalidateQueries({ queryKey: ['plan'] })),
  }),
  defaultOptions: {
    mutations: {
      networkMode: 'offlineFirst',
    },
    queries: {
      networkMode: 'offlineFirst',
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      retry: (intentos, error) => {
        const status = axios.isAxiosError(error) ? error.response?.status : undefined
        if (status && status >= 400 && status < 500) return false
        return intentos < 1
      },
    },
  },
})

// Una cuenta que queda en solo lectura por falta de pago (402, #35) recarga el plan: aparece el aviso fijo
setOnPagoRequerido(() => void queryClient.invalidateQueries({ queryKey: ['plan'] }))
