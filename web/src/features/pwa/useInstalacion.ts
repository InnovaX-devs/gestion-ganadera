import { useSyncExternalStore } from 'react'
import { alCambiarInstalacion, leerEstadoInstalacion } from './instalacion'

/** Si la app se puede instalar en este navegador, y cómo. */
export function useInstalacion() {
  return useSyncExternalStore(alCambiarInstalacion, leerEstadoInstalacion)
}
