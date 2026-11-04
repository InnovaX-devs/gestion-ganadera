import { isAxiosError } from 'axios'

/**
 * Falló por falta de conexión (no hubo respuesta del servidor), no porque el servidor dijo que no.
 * Es lo que decide si algo se guarda para enviar después.
 */
export function esErrorDeRed(error: unknown) {
  if (typeof navigator !== 'undefined' && !navigator.onLine) return true
  return isAxiosError(error) && !error.response
}

export const enLinea = () => typeof navigator === 'undefined' || navigator.onLine

/**
 * Para el onSuccess de una mutación que funciona sin conexión: con red se espera a que se refresquen
 * los datos (así el formulario se cierra con todo actualizado). Sin red no se espera: TanStack Query
 * deja en pausa los reintentos hasta que vuelva la conexión, y el formulario quedaría cargando para
 * siempre aunque la carga ya esté guardada en el dispositivo.
 */
export function esperarSoloEnLinea(refresco: Promise<unknown>): Promise<unknown> | undefined {
  return enLinea() ? refresco : undefined
}

/** crypto.randomUUID solo existe en https o localhost; por las dudas, un respaldo. */
export function nuevoId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}
