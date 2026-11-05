import { type DBSchema, type IDBPDatabase, openDB } from 'idb'
import type { ArchivoPendiente } from '@/features/adjuntos/types'
import type {
  AgrupacionSync,
  AnimalSync,
  EventoSync,
  OperacionPendiente,
  ProductoSync,
} from './tipos'

/** Toda fila guardada lleva el establecimiento: se puede tener más de uno en el celular. */
type DeEstablecimiento<T> = T & { establecimientoId: string }

interface Esquema extends DBSchema {
  animales: { key: string; value: DeEstablecimiento<AnimalSync>; indexes: { est: string } }
  lotes: { key: string; value: DeEstablecimiento<AgrupacionSync>; indexes: { est: string } }
  rodeos: { key: string; value: DeEstablecimiento<AgrupacionSync>; indexes: { est: string } }
  productos: { key: string; value: DeEstablecimiento<ProductoSync>; indexes: { est: string } }
  eventos: {
    key: string
    value: DeEstablecimiento<EventoSync>
    indexes: { est: string; animal: string }
  }
  /** Lo hecho sin conexión, en el orden en que se hizo. */
  cola: { key: string; value: OperacionPendiente; indexes: { est: string; creadaEn: string } }
  /** Fotos y archivos elegidos sin conexión, esperando para subirse (#43). */
  archivos: { key: string; value: ArchivoPendiente; indexes: { est: string } }
  /** Cursor y fecha de la última sincronización por establecimiento, y otros datos sueltos. */
  meta: { key: string; value: unknown }
}

export type Tabla = 'animales' | 'lotes' | 'rodeos' | 'productos' | 'eventos'
export const TABLAS: Tabla[] = ['animales', 'lotes', 'rodeos', 'productos', 'eventos']

const NOMBRE = 'gestion-ganadera'
const VERSION = 2

let conexion: Promise<IDBPDatabase<Esquema>> | null = null

/** Base local del navegador (IndexedDB) para trabajar sin conexión (#30). */
export function db() {
  conexion ??= openDB<Esquema>(NOMBRE, VERSION, {
    upgrade(base, versionAnterior) {
      // Cada versión agrega lo suyo: quien viene de la 1 no vuelve a crear lo de la 1
      if (versionAnterior < 1) {
        for (const tabla of ['animales', 'lotes', 'rodeos', 'productos'] as const) {
          base.createObjectStore(tabla, { keyPath: 'id' }).createIndex('est', 'establecimientoId')
        }
        const eventos = base.createObjectStore('eventos', { keyPath: 'id' })
        eventos.createIndex('est', 'establecimientoId')
        eventos.createIndex('animal', 'animalId')
        const cola = base.createObjectStore('cola', { keyPath: 'id' })
        cola.createIndex('est', 'establecimientoId')
        cola.createIndex('creadaEn', 'creadaEn')
        base.createObjectStore('meta')
      }
      if (versionAnterior < 2) {
        base
          .createObjectStore('archivos', { keyPath: 'id' })
          .createIndex('est', 'establecimientoId')
      }
    },
  })
  return conexion
}

export const metaClave = {
  cursor: (est: string) => `cursor:${est}`,
  ultimaSync: (est: string) => `ultimaSync:${est}`,
  establecimientos: 'establecimientos',
  usuarioId: 'usuarioId',
}

/** Borra todo lo guardado de un establecimiento (no la cola). */
export async function borrarEstablecimiento(est: string) {
  const base = await db()
  const tx = base.transaction([...TABLAS, 'meta'], 'readwrite')
  for (const tabla of TABLAS) {
    let cursor = await tx.objectStore(tabla).index('est').openCursor(est)
    while (cursor) {
      await cursor.delete()
      cursor = await cursor.continue()
    }
  }
  await tx.objectStore('meta').delete(metaClave.cursor(est))
  await tx.objectStore('meta').delete(metaClave.ultimaSync(est))
  await tx.done
}

/** Borra absolutamente todo (al cerrar sesión o si entra otro usuario). */
export async function borrarTodo() {
  const base = await db()
  const tx = base.transaction([...TABLAS, 'cola', 'archivos', 'meta'], 'readwrite')
  await Promise.all(
    [...TABLAS, 'cola' as const, 'archivos' as const, 'meta' as const].map((t) =>
      tx.objectStore(t).clear(),
    ),
  )
  await tx.done
}
