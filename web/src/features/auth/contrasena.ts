import { useQuery } from '@tanstack/react-query'
import type { AuthResponse } from '@/features/auth/types'
import { api } from '@/lib/api'

/** "Olvidé mi contraseña": el backend responde igual exista o no el email. */
export const pedirRestablecimiento = (email: string) =>
  api.post('/auth/olvide-contrasena', { email: email.trim() }).then(() => undefined)

export interface Restablecimiento {
  email: string
  nombre: string
}

/** Lado del link del email (sin sesión). */
export function useRestablecimiento(token: string) {
  return useQuery({
    queryKey: ['restablecimiento', token],
    queryFn: () => api.get<Restablecimiento>(`/restablecer/${token}`).then((r) => r.data),
    retry: false,
  })
}

export const restablecerContrasena = (token: string, password: string) =>
  api.post<AuthResponse>(`/restablecer/${token}`, { password }).then((r) => r.data)
