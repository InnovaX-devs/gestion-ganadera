import { api } from '@/lib/api'
import type { AuthResponse, LoginData, RegisterData, Usuario } from './types'

export const authApi = {
  login: (data: LoginData) => api.post<AuthResponse>('/auth/login', data).then((r) => r.data),
  register: (data: RegisterData) =>
    api.post<AuthResponse>('/auth/register', data).then((r) => r.data),
  me: () => api.get<Usuario>('/auth/me').then((r) => r.data),
}
