import { createContext } from 'react'
import type { AuthStatus, LoginData, RegisterData, Usuario } from './types'

export interface AuthContextValue {
  status: AuthStatus
  usuario: Usuario | null
  login: (data: LoginData) => Promise<void>
  register: (data: RegisterData) => Promise<void>
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
