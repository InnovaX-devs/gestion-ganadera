import { type ReactNode, useCallback, useEffect, useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { borrarTodo } from '@/features/offline/db'
import { esErrorDeRed } from '@/features/offline/red'
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
        authStorage.setUsuario(u)
        setUsuario(u)
        setStatus('authenticated')
      })
      .catch((error) => {
        if (!activo) return
        // Sin conexión no se puede validar la sesión: se sigue con el último usuario conocido (#30).
        // Cuando vuelva la red, cualquier pedido con un token vencido cierra la sesión como siempre.
        const guardado = authStorage.getUsuario<Usuario>()
        if (esErrorDeRed(error) && guardado) {
          setUsuario(guardado)
          setStatus('authenticated')
          return
        }
        // Sin red y sin usuario guardado no hay nada para mostrar; los tokens se conservan
        if (!esErrorDeRed(error)) authStorage.clear()
        setStatus('anonymous')
      })
    return () => {
      activo = false
    }
  }, [status])

  const iniciarSesion = useCallback((data: AuthResponse) => {
    authStorage.setTokens(data.accessToken, data.refreshToken)
    authStorage.setUsuario(data.usuario)
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
    // Los datos guardados para trabajar sin conexión son de este usuario (#30)
    borrarTodo().catch(() => undefined)
    // Borra los datos en caché para que el próximo usuario no vea los del anterior
    queryClient.clear()
    setUsuario(null)
    setStatus('anonymous')
  }, [queryClient])

  const value = useMemo<AuthContextValue>(
    () => ({ status, usuario, login, register, iniciarSesion, logout }),
    [status, usuario, login, register, iniciarSesion, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
