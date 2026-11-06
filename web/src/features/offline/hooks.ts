import { useEffect, useState, useSyncExternalStore } from 'react'
import type { ArchivoPendiente } from '@/features/adjuntos/types'
import { listarArchivosPendientes } from './archivos'
import { alCambiarLaCola, listarAvisos, listarCola } from './cola'
import { alCambiarEstadoSync, leerEstadoSync } from './sincronizar'
import type { AvisoSync, OperacionPendiente } from './tipos'

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

/** Avisos de la sincronización (ej: campos que no se aplicaron por un conflicto). */
export function useAvisosSync(): AvisoSync[] {
  const [avisos, setAvisos] = useState<AvisoSync[]>([])
  useEffect(() => {
    let activo = true
    const leer = () =>
      listarAvisos()
        .then((lista) => activo && setAvisos(lista))
        .catch(() => undefined)
    leer()
    const dejar = alCambiarLaCola(leer)
    return () => {
      activo = false
      dejar()
    }
  }, [])
  return avisos
}

/** Fotos y archivos guardados en el dispositivo que todavía no se subieron (#43). */
export function useArchivosPendientes(): ArchivoPendiente[] {
  const [archivos, setArchivos] = useState<ArchivoPendiente[]>([])
  useEffect(() => {
    let activo = true
    const leer = () =>
      listarArchivosPendientes()
        .then((lista) => activo && setArchivos(lista))
        .catch(() => undefined)
    leer()
    const dejar = alCambiarLaCola(leer)
    return () => {
      activo = false
      dejar()
    }
  }, [])
  return archivos
}
