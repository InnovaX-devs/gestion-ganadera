import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { AuthResponse } from '@/features/auth/types'
import { api } from '@/lib/api'
import type { Invitacion, InvitacionInput, InvitacionPublica, RegistroInvitadoInput } from './types'

const KEY = ['invitaciones'] as const
const key = (establecimientoId: string) => [...KEY, establecimientoId] as const
const base = (establecimientoId: string) => `/establecimientos/${establecimientoId}/invitaciones`
const publica = (token: string) => `/invitaciones/${encodeURIComponent(token)}`

export const invitacionesApi = {
  listar: (establecimientoId: string) =>
    api.get<Invitacion[]>(base(establecimientoId)).then((r) => r.data),
  invitar: (establecimientoId: string, data: InvitacionInput) =>
    api.post<Invitacion>(base(establecimientoId), data).then((r) => r.data),
  reenviar: (establecimientoId: string, id: string) =>
    api.post<Invitacion>(`${base(establecimientoId)}/${id}/reenviar`).then((r) => r.data),
  revocar: (establecimientoId: string, id: string) =>
    api.delete(`${base(establecimientoId)}/${id}`),

  ver: (token: string) => api.get<InvitacionPublica>(publica(token)).then((r) => r.data),
  registrar: (token: string, data: RegistroInvitadoInput) =>
    api.post<AuthResponse>(`${publica(token)}/registro`, data).then((r) => r.data),
  aceptar: (token: string) =>
    api.post<{ establecimientoId: string }>(`${publica(token)}/aceptar`).then((r) => r.data),
}

/** `establecimientoId` null = todavía no hay establecimiento elegido: no pide nada. */
export function useInvitaciones(establecimientoId: string | null) {
  return useQuery({
    queryKey: key(establecimientoId ?? ''),
    queryFn: () => invitacionesApi.listar(establecimientoId!),
    enabled: establecimientoId !== null,
  })
}

function useInvalidar() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: KEY })
}

export function useInvitar() {
  const invalidar = useInvalidar()
  return useMutation({
    mutationFn: ({
      establecimientoId,
      data,
    }: {
      establecimientoId: string
      data: InvitacionInput
    }) => invitacionesApi.invitar(establecimientoId, data),
    onSuccess: invalidar,
  })
}

export function useReenviarInvitacion(establecimientoId: string) {
  const invalidar = useInvalidar()
  return useMutation({
    mutationFn: (id: string) => invitacionesApi.reenviar(establecimientoId, id),
    onSuccess: invalidar,
  })
}

export function useRevocarInvitacion(establecimientoId: string) {
  const invalidar = useInvalidar()
  return useMutation({
    mutationFn: (id: string) => invitacionesApi.revocar(establecimientoId, id),
    onSuccess: invalidar,
  })
}

export function useInvitacionPublica(token: string) {
  return useQuery({
    queryKey: ['invitacion-publica', token],
    queryFn: () => invitacionesApi.ver(token),
    retry: false,
  })
}

export function useAceptarInvitacion(token: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => invitacionesApi.aceptar(token),
    // Aparece un establecimiento nuevo en la lista del usuario
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['establecimientos'] }),
  })
}
