import axios, { AxiosError } from 'axios'
import { authStorage } from './auth-storage'

/** Formato de error estándar que devuelve el backend (ver backend/README.md). */
export interface ApiError {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  fieldErrors?: { field: string; message: string }[]
}

export const api = axios.create({
  baseURL: `${import.meta.env.VITE_API_URL ?? 'http://localhost:8080'}/api`,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
})

// Agrega el token a cada request
api.interceptors.request.use((config) => {
  const token = authStorage.getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Sesión vencida: se borra el token. La redirección al login se agrega en el issue de auth.
api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    if (error.response?.status === 401) {
      authStorage.clear()
    }
    return Promise.reject(error)
  },
)

/** Mensaje legible para mostrar al usuario a partir de cualquier error de la API. */
export function getErrorMessage(error: unknown): string {
  if (axios.isAxiosError<ApiError>(error)) {
    if (error.response?.data?.message) return error.response.data.message
    if (!error.response) return 'No se pudo conectar con el servidor'
  }
  return 'Ocurrió un error inesperado'
}
