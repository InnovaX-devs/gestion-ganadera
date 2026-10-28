import { QueryClient } from '@tanstack/react-query'
import axios from 'axios'

/**
 * Caché de datos del servidor (TanStack Query).
 * - Los datos se consideran frescos 30 s: navegar entre páginas no repite requests.
 * - Los errores 4xx (validación, 404, 401...) no se reintentan; los de red o 5xx, una vez.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
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
