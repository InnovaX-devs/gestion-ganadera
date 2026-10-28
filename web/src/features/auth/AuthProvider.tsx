import { type ReactNode, useCallback, useEffect, useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { setOnSessionExpired } from '@/lib/api'
import { authStorage } from '@/lib/auth-storage'
import { authApi } from './auth-api'
import { AuthContext, type AuthContextValue } from './AuthContext'
import type { AuthResponse, AuthStatus, LoginData, RegisterData, Usuario } from './types'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [usuario, setUsuario] = useState<Usuario | null>(null)
  // Si hay tokens guardados, se valida la sesión contra el backend antes de mostrar la app
  const [status, setStatus] = useState<AuthStatus>(() =>
    authStorage.hasSession() ? 'loading' : 'anonymous',
  )

  // El cliente HTTP avisa cuando el refresh token también venció
  useEffect(() => {
    setOnSessionExpired(() => {
      queryClient.clear()
      setUsuario(null)
      setStatus('anonymous')
    })
    return () => setOnSessionExpired(null)
  }, [queryClient])

  useEffect(() => {
    if (status !== 'loading') return
    let activo = true
    authApi
      .me()
      .then((u) => {
        if (!activo) return
        setUsuario(u)
        setStatus('authenticated')
      })
      .catch(() => {
        if (!activo) return
        authStorage.clear()
        setStatus('anonymous')
      })
    return () => {
      activo = false
    }
  }, [status])

  const iniciarSesion = useCallback((data: AuthResponse) => {
    authStorage.setTokens(data.accessToken, data.refreshToken)
    setUsuario(data.usuario)
    setStatus('authenticated')
  }, [])

  const login = useCallback(
    async (data: LoginData) => iniciarSesion(await authApi.login(data)),
    [iniciarSesion],
  )

  const register = useCallback(
    async (data: RegisterData) => iniciarSesion(await authApi.register(data)),
    [iniciarSesion],
  )

  const logout = useCallback(() => {
    authStorage.clear()
    // Borra los datos en caché para que el próximo usuario no vea los del anterior
    queryClient.clear()
    setUsuario(null)
    setStatus('anonymous')
  }, [queryClient])

  const value = useMemo<AuthContextValue>(
    () => ({ status, usuario, login, register, logout }),
    [status, usuario, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
