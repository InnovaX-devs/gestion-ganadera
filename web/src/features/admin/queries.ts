import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { AuthResponse } from '@/features/auth/types'
import type { Plan, PlanInfo } from '@/features/plan/types'
import { api } from '@/lib/api'
import { adminApi } from './admin-api'

export interface OrganizacionAdmin {
  id: string
  nombre: string
  duenoNombre?: string | null
  duenoEmail?: string | null
  /** Creada desde el panel y el dueño todavía no eligió su contraseña */
  duenoPendiente: boolean
  suspendida: boolean
  createdAt: string
  plan: Plan
}

const KEY = ['admin', 'organizaciones'] as const

export function useOrganizacionesAdmin() {
  return useQuery({
    queryKey: KEY,
    queryFn: () => adminApi.get<OrganizacionAdmin[]>('/organizaciones').then((r) => r.data),
  })
}

export function useCambiarPlan() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      id,
      planCodigo,
      pruebaHasta,
      pagoManualHasta,
    }: {
      id: string
      planCodigo: string
      pruebaHasta: string | null
      /** Pagó por fuera de Mercado Pago hasta ese día; null lo quita */
      pagoManualHasta: string | null
    }) =>
      adminApi
        .put<OrganizacionAdmin>(`/organizaciones/${id}/plan`, {
          planCodigo,
          pruebaHasta,
          pagoManualHasta,
        })
        .then((r) => r.data),
    onSuccess: (actualizada) =>
      queryClient.setQueryData<OrganizacionAdmin[]>(KEY, (lista) =>
        lista?.map((o) => (o.id === actualizada.id ? actualizada : o)),
      ),
  })
}

export interface CuentaNueva {
  nombreOrganizacion: string
  nombre: string
  apellido: string | null
  email: string
  planCodigo: string
  pruebaHasta: string | null
}

export function useCrearCuenta() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CuentaNueva) =>
      adminApi.post<OrganizacionAdmin>('/organizaciones', data).then((r) => r.data),
    onSuccess: (nueva) =>
      queryClient.setQueryData<OrganizacionAdmin[]>(KEY, (lista) => [nueva, ...(lista ?? [])]),
  })
}

/** Pago por transferencia del mes: queda pagada hasta el vencimiento del mes que viene. */
export function useRegistrarPago() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      adminApi.post<OrganizacionAdmin>(`/organizaciones/${id}/registrar-pago`).then((r) => r.data),
    onSuccess: (actualizada) =>
      queryClient.setQueryData<OrganizacionAdmin[]>(KEY, (lista) =>
        lista?.map((o) => (o.id === actualizada.id ? actualizada : o)),
      ),
  })
}

export function useReenviarActivacion() {
  return useMutation({
    mutationFn: (id: string) => adminApi.post(`/organizaciones/${id}/reenviar-activacion`),
  })
}

/** Link para que el dueño elija una contraseña nueva. Devuelve a qué email se mandó. */
export function useRestablecerContrasena() {
  return useMutation({
    mutationFn: (id: string) =>
      adminApi
        .post<{ email: string }>(`/organizaciones/${id}/restablecer-contrasena`)
        .then((r) => r.data),
  })
}

export function useSuspender() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, suspendida }: { id: string; suspendida: boolean }) =>
      adminApi
        .put<OrganizacionAdmin>(`/organizaciones/${id}/suspension`, { suspendida })
        .then((r) => r.data),
    onSuccess: (actualizada) =>
      queryClient.setQueryData<OrganizacionAdmin[]>(KEY, (lista) =>
        lista?.map((o) => (o.id === actualizada.id ? actualizada : o)),
      ),
  })
}

const PLANES_KEY = ['admin', 'planes'] as const

export function usePlanesAdmin() {
  return useQuery({
    queryKey: PLANES_KEY,
    queryFn: () => adminApi.get<PlanInfo[]>('/planes').then((r) => r.data),
  })
}

/** Precio por mes de un plan; null = no se contrata online. */
export function useCambiarPrecio() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ codigo, precioMensual }: { codigo: string; precioMensual: number | null }) =>
      adminApi.put<PlanInfo>(`/planes/${codigo}`, { precioMensual }).then((r) => r.data),
    // Los precios también vienen dentro del plan de cada cuenta
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin'] }),
  })
}

/** Lado del dueño: ver y usar el link de activación (sin sesión). */
export interface Activacion {
  email: string
  nombre: string
  organizacionNombre: string
}

export function useActivacion(token: string) {
  return useQuery({
    queryKey: ['activacion', token],
    queryFn: () => api.get<Activacion>(`/activacion/${token}`).then((r) => r.data),
    retry: false,
  })
}

export const activarCuenta = (token: string, password: string) =>
  api.post<AuthResponse>(`/activacion/${token}`, { password }).then((r) => r.data)
