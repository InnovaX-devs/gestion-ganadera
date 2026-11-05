import type { ArchivoPendiente } from '@/features/adjuntos/types'
import { avisarCambioDeCola, establecimientosConPendientes } from './cola'
import { db } from './db'

/**
 * Fotos y archivos que esperan señal para subirse (#43). Se guardan en IndexedDB, así sobreviven a
 * cerrar la app, y avisan cada cambio por el mismo canal que la cola de operaciones (#30), para
 * que el indicador de pendientes se actualice.
 */

export async function guardarArchivoPendiente(archivo: ArchivoPendiente) {
  await (await db()).put('archivos', archivo)
  avisarCambioDeCola()
}

/** De la más vieja a la más nueva. Sin establecimiento, todas. */
export async function listarArchivosPendientes(establecimientoId?: string) {
  const base = await db()
  const lista = establecimientoId
    ? await base.getAllFromIndex('archivos', 'est', establecimientoId)
    : await base.getAll('archivos')
  return lista.sort((a, b) => a.creadoEn.localeCompare(b.creadoEn))
}

export async function quitarArchivoPendiente(id: string) {
  await (await db()).delete('archivos', id)
  avisarCambioDeCola()
}

/** Establecimientos con algo para enviar: operaciones en la cola o archivos por subir. */
export async function establecimientosConEnvios() {
  const [operaciones, archivos] = await Promise.all([
    establecimientosConPendientes(),
    listarArchivosPendientes(),
  ])
  return [...new Set([...operaciones, ...archivos.map((a) => a.establecimientoId)])]
}
