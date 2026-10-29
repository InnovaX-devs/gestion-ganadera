import { createContext } from 'react'
import type { AuthResponse, AuthStatus, LoginData, RegisterData, Usuario } from './types'

export interface AuthContextValue {
  status: AuthStatus
  usuario: Usuario | null
  login: (data: LoginData) => Promise<void>
  register: (data: RegisterData) => Promise<void>
  /** Guarda la sesión de una respuesta de auth (login, registro, registro por invitación). */
  iniciarSesion: (data: AuthResponse) => void
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
