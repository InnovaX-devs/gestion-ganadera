import { isAxiosError } from 'axios'
import {
  guardarArchivoPendiente,
  listarArchivosPendientes,
  quitarArchivoPendiente,
} from '@/features/offline/archivos'
import { agregarAviso, listarCola } from '@/features/offline/cola'
import { enLinea, esErrorDeRed, nuevoId } from '@/features/offline/red'
import { getErrorMessage } from '@/lib/api'
import { subirArchivo } from './queries'
import type { DestinoArchivo } from './types'

export interface ResultadoSubida {
  subidos: number
  /** Sin señal: quedaron guardados en el dispositivo y se suben solos al volver. */
  paraDespues: number
  /** El servidor los rechazó (tipo, tamaño, permisos): reintentar no sirve. */
  fallidos: { nombre: string; mensaje: string }[]
}

const subir = (est: string, destino: DestinoArchivo, contenido: Blob, nombre: string, id: string) =>
  subirArchivo(
    est,
    destino.tipo === 'EVENTO'
      ? { operacionId: destino.operacionId }
      : { animalId: destino.animalId },
    contenido,
    nombre,
    id,
  )

/**
 * Sube los archivos elegidos. Cada uno por separado: si uno falla, los demás igual se suben. Sin
 * conexión (o si se corta a mitad) el archivo se guarda en el dispositivo y se sube en la próxima
 * sincronización, con el mismo id, así nunca queda duplicado.
 *
 * Nunca lanza: lo que no se pudo subir viene en `fallidos`.
 */
export async function subirArchivos(
  est: string,
  destino: DestinoArchivo,
  archivos: File[],
  descripcion: string,
): Promise<ResultadoSubida> {
  const resultado: ResultadoSubida = { subidos: 0, paraDespues: 0, fallidos: [] }
  const guardarParaDespues = async (archivo: File, id: string) => {
    await guardarArchivoPendiente({
      id,
      establecimientoId: est,
      destino,
      nombre: archivo.name,
      contenido: archivo,
      descripcion,
      creadoEn: new Date().toISOString(),
    })
    resultado.paraDespues++
  }

  for (const archivo of archivos) {
    const id = nuevoId()
    try {
      if (!enLinea()) {
        await guardarParaDespues(archivo, id)
        continue
      }
      await subir(est, destino, archivo, archivo.name, id)
      resultado.subidos++
    } catch (error) {
      if (esErrorDeRed(error)) {
        try {
          await guardarParaDespues(archivo, id)
        } catch {
          resultado.fallidos.push({
            nombre: archivo.name,
            mensaje: 'No se pudo guardar en el dispositivo',
          })
        }
      } else {
        resultado.fallidos.push({ nombre: archivo.name, mensaje: getErrorMessage(error) })
      }
    }
  }
  return resultado
}

/** Fallas que se arreglan solas con el tiempo: no se descarta el archivo, se reintenta después. */
function esTransitorio(error: unknown) {
  if (esErrorDeRed(error)) return true
  if (!isAxiosError(error) || !error.response) return false
  const { status } = error.response
  return status === 401 || status === 408 || status === 429 || status >= 500
}

/**
 * Sube los archivos guardados de ese establecimiento (se llama al sincronizar, #30). Los de un
 * evento esperan a que el evento haya llegado al servidor: mientras siga en la cola de operaciones
 * no hay a qué adjuntarlos. Si el servidor rechaza uno para siempre (ej: el evento se descartó),
 * se descarta y se avisa. Devuelve true si subió alguno.
 */
export async function subirArchivosPendientes(est: string): Promise<boolean> {
  const pendientes = await listarArchivosPendientes(est)
  if (pendientes.length === 0) return false
  const operacionesEnCola = new Set((await listarCola(est)).map((o) => o.id))
  let subioAlguno = false

  for (const archivo of pendientes) {
    if (archivo.destino.tipo === 'EVENTO' && operacionesEnCola.has(archivo.destino.operacionId))
      continue
    try {
      await subir(est, archivo.destino, archivo.contenido, archivo.nombre, archivo.id)
      await quitarArchivoPendiente(archivo.id)
      subioAlguno = true
    } catch (error) {
      // Sin señal o servidor caído: no tiene sentido probar con los demás
      if (esTransitorio(error)) break
      await quitarArchivoPendiente(archivo.id)
      await agregarAviso({
        id: archivo.id,
        establecimientoId: est,
        descripcion: `${archivo.descripcion} · ${archivo.nombre}`,
        mensaje: `No se pudo subir el archivo: ${getErrorMessage(error)}`,
        en: new Date().toISOString(),
      })
    }
  }
  return subioAlguno
}
