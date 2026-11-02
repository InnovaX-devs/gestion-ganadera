import { useEffect, useState, useSyncExternalStore } from 'react'
import { alCambiarLaCola, listarCola } from './cola'
import { alCambiarEstadoSync, leerEstadoSync } from './sincronizar'
import type { OperacionPendiente } from './tipos'

function suscribirRed(oyente: () => void) {
  window.addEventListener('online', oyente)
  window.addEventListener('offline', oyente)
  return () => {
    window.removeEventListener('online', oyente)
    window.removeEventListener('offline', oyente)
  }
}

/** Si el dispositivo tiene conexión (según el navegador). */
export function useEnLinea() {
  return useSyncExternalStore(suscribirRed, () => navigator.onLine)
}

export function useEstadoSync() {
  return useSyncExternalStore(alCambiarEstadoSync, leerEstadoSync)
}

/** Operaciones guardadas en el dispositivo que todavía no llegaron al servidor. */
export function usePendientes(): OperacionPendiente[] {
  const [pendientes, setPendientes] = useState<OperacionPendiente[]>([])
  useEffect(() => {
    let activo = true
    const leer = () =>
      listarCola()
        .then((lista) => activo && setPendientes(lista))
        .catch(() => undefined)
    leer()
    const dejar = alCambiarLaCola(leer)
    return () => {
      activo = false
      dejar()
    }
  }, [])
  return pendientes
}
