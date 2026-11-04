import type { Rol } from '@/features/establecimientos/permisos'

export type EstadoInvitacion = 'PENDIENTE' | 'ACEPTADA' | 'VENCIDA' | 'REVOCADA'

export interface Invitacion {
  id: string
  email: string
  rol: Rol
  estado: EstadoInvitacion
  venceAt: string
  createdAt: string
  invitadoPorNombre: string
}

export interface InvitacionInput {
  email: string
  rol: Rol
}

/** Lo que ve quien abre el link (sin sesión). */
export interface InvitacionPublica {
  email: string
  establecimientoNombre: string
  organizacionNombre: string
  rol: Rol
  estado: EstadoInvitacion
  venceAt: string
  /** Ya hay una cuenta con ese email: tiene que iniciar sesión en vez de registrarse. */
  cuentaExistente: boolean
}

export interface RegistroInvitadoInput {
  nombre: string
  apellido?: string
  password: string
}

export const ETIQUETA_ESTADO: Record<EstadoInvitacion, string> = {
  PENDIENTE: 'Pendiente',
  ACEPTADA: 'Aceptada',
  VENCIDA: 'Vencida',
  REVOCADA: 'Revocada',
}

export const MENSAJE_ESTADO: Record<Exclude<EstadoInvitacion, 'PENDIENTE'>, string> = {
  ACEPTADA: 'Esta invitación ya fue aceptada.',
  VENCIDA: 'Esta invitación venció. Pedile a quien te invitó que te la reenvíe.',
  REVOCADA: 'Esta invitación fue cancelada.',
}
