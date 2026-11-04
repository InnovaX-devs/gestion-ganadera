import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { AuthResponse } from '@/features/auth/types'
import { authStorage } from './auth-storage'

/** Formato de error estándar que devuelve el backend (ver backend/README.md). */
export interface ApiError {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  fieldErrors?: { field: string; message: string }[]
  /** Para reconocer errores puntuales sin leer el mensaje (ej: LIMITE_PLAN, #35) */
  codigo?: string
}

export const api = axios.create({
  baseURL: `${import.meta.env.VITE_API_URL ?? 'http://localhost:8080'}/api`,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
})

// Endpoints que no llevan token (si se manda uno vencido, el backend respondería 401)
const RUTAS_PUBLICAS = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/olvide-contrasena']
// Ver y registrarse desde el link de una invitación (aceptar, en cambio, sí lleva el token)
const RUTA_INVITACION_PUBLICA = /^\/invitaciones\/[^/]+(\/registro)?$/
// Link para activar una cuenta creada desde el panel de administración (#35)
const RUTA_ACTIVACION = /^\/activacion\/[^/]+$/
// Link de "olvidé mi contraseña"
const RUTA_RESTABLECER = /^\/restablecer\/[^/]+$/
const esRutaPublica = (url?: string) =>
  !!url &&
  (RUTAS_PUBLICAS.some((ruta) => url.endsWith(ruta)) ||
    RUTA_INVITACION_PUBLICA.test(url) ||
    RUTA_ACTIVACION.test(url) ||
    RUTA_RESTABLECER.test(url))

api.interceptors.request.use((config) => {
  const token = authStorage.getAccessToken()
  if (token && !esRutaPublica(config.url)) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// ---------- Renovación automática del access token ----------

let onSessionExpired: (() => void) | null = null

/** Lo registra AuthProvider para enterarse cuando la sesión venció del todo. */
export function setOnSessionExpired(callback: (() => void) | null) {
  onSessionExpired = callback
}

let onPagoRequerido: (() => void) | null = null

/** Lo registra la app para enterarse cuando la cuenta queda en solo lectura por falta de pago (402). */
export function setOnPagoRequerido(callback: (() => void) | null) {
  onPagoRequerido = callback
}

// Si varios requests reciben 401 a la vez, se hace un solo refresh y todos esperan ese.
let refreshEnCurso: Promise<string | null> | null = null

function refrescarAccessToken(): Promise<string | null> {
  const refreshToken = authStorage.getRefreshToken()
  if (!refreshToken) return Promise.resolve(null)

  refreshEnCurso ??= api
    .post<AuthResponse>('/auth/refresh', { refreshToken })
    .then(({ data }) => {
      authStorage.setTokens(data.accessToken, data.refreshToken)
      return data.accessToken
    })
    .catch(() => null)
    .finally(() => {
      refreshEnCurso = null
    })
  return refreshEnCurso
}

type RequestReintentable = InternalAxiosRequestConfig & { _reintentado?: boolean }

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiError>) => {
    const original = error.config as RequestReintentable | undefined
    const puedeReintentar =
      error.response?.status === 401 &&
      original &&
      !original._reintentado &&
      !esRutaPublica(original.url)

    // Cuenta en solo lectura por falta de pago (#35): se refresca el plan para mostrar el aviso
    if (error.response?.data?.codigo === 'PAGO_REQUERIDO') onPagoRequerido?.()
    if (!puedeReintentar) return Promise.reject(error)

    original._reintentado = true
    const nuevoToken = await refrescarAccessToken()
    if (!nuevoToken) {
      authStorage.clear()
      onSessionExpired?.()
      return Promise.reject(error)
    }
    original.headers.Authorization = `Bearer ${nuevoToken}`
    return api(original)
  },
)

// ---------- Helpers de errores ----------

/** Error armado en el navegador con un mensaje pensado para el usuario (ej: sin conexión). */
export class ErrorConMensaje extends Error {}

/** Mensaje legible para mostrar al usuario a partir de cualquier error de la API. */
export function getErrorMessage(error: unknown): string {
  if (error instanceof ErrorConMensaje) return error.message
  if (axios.isAxiosError<ApiError>(error)) {
    if (error.response?.data?.message) return error.response.data.message
    if (!error.response) return 'No se pudo conectar con el servidor'
  }
  return 'Ocurrió un error inesperado'
}

/** Se llegó a un límite del plan (animales, establecimientos o usuarios, #35). */
export function esLimitePlan(error: unknown): boolean {
  return axios.isAxiosError<ApiError>(error) && error.response?.data?.codigo === 'LIMITE_PLAN'
}

/** Errores de validación por campo: { email: 'El email no es válido', ... } */
export function getFieldErrors(error: unknown): Record<string, string> {
  if (!axios.isAxiosError<ApiError>(error)) return {}
  const errores: Record<string, string> = {}
  for (const { field, message } of error.response?.data?.fieldErrors ?? []) {
    errores[field] ??= message
  }
  return errores
}
