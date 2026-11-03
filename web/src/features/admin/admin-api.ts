import axios from 'axios'
import { sesionAdmin } from './sesion'

export const RUTA_LOGIN_ADMIN = '/admin/login'

/** Cliente de la API para el panel: lleva el token del panel, no el de la app. */
export const adminApi = axios.create({
  baseURL: `${import.meta.env.VITE_API_URL ?? 'http://localhost:8080'}/api/admin`,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
})

adminApi.interceptors.request.use((config) => {
  const sesion = sesionAdmin.leer()
  if (sesion && !config.url?.endsWith('/auth/login')) {
    config.headers.Authorization = `Bearer ${sesion.accessToken}`
  }
  return config
})

// Sesión vencida o inválida: de vuelta al login del panel
adminApi.interceptors.response.use(
  (response) => response,
  (error) => {
    if (
      axios.isAxiosError(error) &&
      error.response?.status === 401 &&
      !error.config?.url?.endsWith('/auth/login')
    ) {
      sesionAdmin.borrar()
      if (window.location.pathname !== RUTA_LOGIN_ADMIN) window.location.assign(RUTA_LOGIN_ADMIN)
    }
    return Promise.reject(error)
  },
)

export async function ingresarAlPanel(email: string, password: string) {
  const { data } = await adminApi.post<{ accessToken: string; email: string; expiresIn: number }>(
    '/auth/login',
    { email, password },
  )
  sesionAdmin.guardar({
    accessToken: data.accessToken,
    email: data.email,
    venceAt: Date.now() + data.expiresIn * 1000,
  })
}
