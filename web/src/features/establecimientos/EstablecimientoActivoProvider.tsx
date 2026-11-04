import { type ReactNode, useCallback, useMemo, useState } from 'react'
import { useAuth } from '@/features/auth/useAuth'
import {
  EstablecimientoActivoContext,
  type EstablecimientoActivoValue,
} from './EstablecimientoActivoContext'
import { useEstablecimientos } from './queries'
import type { Establecimiento } from './types'

const SIN_DATOS: Establecimiento[] = []
const claveDe = (organizacionId: string) => `establecimiento_activo:${organizacionId}`

function leerGuardado(clave: string): string | null {
  try {
    return localStorage.getItem(clave)
  } catch {
    return null
  }
}

/**
 * Recuerda (por navegador y por organización) qué establecimiento eligió el usuario.
 * Si el guardado ya no existe (se dio de baja), usa el primero de la lista.
 */
export function EstablecimientoActivoProvider({ children }: { children: ReactNode }) {
  const { usuario } = useAuth()
  const clave = claveDe(usuario?.organizacionId ?? 'sin-organizacion')
  const { data: establecimientos = SIN_DATOS, isPending } = useEstablecimientos()
  const [elegidoId, setElegidoId] = useState(() => leerGuardado(clave))

  const seleccionar = useCallback(
    (id: string) => {
      setElegidoId(id)
      try {
        localStorage.setItem(clave, id)
      } catch {
        /* sin persistencia */
      }
    },
    [clave],
  )

  const activo = establecimientos.find((e) => e.id === elegidoId) ?? establecimientos[0] ?? null

  const value = useMemo<EstablecimientoActivoValue>(
    () => ({ activo, establecimientos, cargando: isPending, seleccionar }),
    [activo, establecimientos, isPending, seleccionar],
  )

  return (
    <EstablecimientoActivoContext.Provider value={value}>
      {children}
    </EstablecimientoActivoContext.Provider>
  )
}
