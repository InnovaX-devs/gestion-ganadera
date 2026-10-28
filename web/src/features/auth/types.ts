export interface Usuario {
  id: string
  email: string
  nombre: string
  apellido: string | null
  organizacionId: string
  organizacionNombre: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: 'Bearer'
  expiresIn: number
  usuario: Usuario
}

export interface LoginData {
  email: string
  password: string
}

export interface RegisterData {
  nombreOrganizacion: string
  nombre: string
  apellido?: string
  email: string
  password: string
}

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous'
