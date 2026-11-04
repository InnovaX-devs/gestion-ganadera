import { isAxiosError } from 'axios'
import { queryClient } from '@/lib/query-client'
import { api } from '@/lib/api'
import { borrarEstablecimiento, db, metaClave, TABLAS } from './db'
import { actualizarEnLaCola, agregarAviso, paraEnviar, quitarDeLaCola } from './cola'
import { enLinea } from './red'
import type { OperacionPendiente, SyncResponse } from './tipos'

/**
 * Motor de sincronización (#30, docs/sync.md). En cada vuelta manda lo pendiente (push) y guarda lo
 * que cambió en el servidor (pull), hasta que no queda nada. Nunca hay dos corriendo a la vez.
 */

export interface EstadoSync {
  sincronizando: boolean
  /** El último intento falló porque no llegó al servidor (señal mala aunque el celular diga que hay red) */
  sinRed: boolean
  /** Última sincronización completa, por establecimiento (ISO) */
  ultima: Record<string, string>
  error: string | null
}

let estado: EstadoSync = { sincronizando: false, sinRed: false, ultima: {}, error: null }
const oyentes = new Set<() => void>()

export const leerEstadoSync = () => estado
export function alCambiarEstadoSync(oyente: () => void) {
  oyentes.add(oyente)
  return () => {
    oyentes.delete(oyente)
  }
}
function cambiar(parcial: Partial<EstadoSync>) {
  estado = { ...estado, ...parcial }
  oyentes.forEach((o) => o())
}

/** Campos de la ficha que se editan sin conexión (como los llama el backend). */
const CAMPOS: Record<string, string> = {
  caravana: 'caravana',
  caravanaElectronica: 'caravana electrónica',
  raza: 'raza',
  fechaNacimiento: 'fecha de nacimiento',
  fechaNacimientoEstimada: 'fecha estimada',
  observaciones: 'observaciones',
}

const MAX_OPERACIONES = 100
const MAX_VUELTAS = 50
let enCurso: Promise<void> | null = null

/** Sincroniza esos establecimientos. Si ya hay una sincronización en curso, espera esa. */
export function sincronizar(establecimientos: string[]): Promise<void> {
  // Sin red o sin nada que sincronizar no se marca "en curso": si no, quedaría trabado
  if (!enLinea() || establecimientos.length === 0) return Promise.resolve()
  if (enCurso) return enCurso
  enCurso = (async () => {
    cambiar({ sincronizando: true, error: null, sinRed: false })
    try {
      for (const est of new Set(establecimientos)) await sincronizarUno(est)
    } catch (e) {
      cambiar(isAxiosError(e) && !e.response ? { sinRed: true } : { error: mensajeDe(e) })
    } finally {
      cambiar({ sincronizando: false })
      enCurso = null
    }
  })()
  return enCurso
}

async function sincronizarUno(est: string) {
  const base = await db()
  let cursor = (await base.get('meta', metaClave.cursor(est))) as string | undefined
  const enviadas = new Set<string>()
  let huboCambios = false
  let reinicios = 0

  for (let vuelta = 0; vuelta < MAX_VUELTAS; vuelta++) {
    // Una operación con error se manda una sola vez por sincronización
    const lote = (await paraEnviar(est))
      .filter((o) => !enviadas.has(o.id))
      .slice(0, MAX_OPERACIONES)
    lote.forEach((o) => enviadas.add(o.id))

    let respuesta: SyncResponse
    try {
      respuesta = (
        await api.post<SyncResponse>('/sync', {
          establecimientoId: est,
          cursor,
          operaciones: lote.map((o) => o.operacion),
        })
      ).data
    } catch (e) {
      const status = isAxiosError(e) ? e.response?.status : undefined
      // Cursor inválido: se borra lo guardado y se empieza de cero (las operaciones no se aplicaron)
      if (status === 422 && reinicios === 0) {
        reinicios++
        lote.forEach((o) => enviadas.delete(o.id))
        await borrarEstablecimiento(est)
        cursor = undefined
        continue
      }
      // Sin acceso al establecimiento: lo guardado ya no sirve
      if (status === 404) await borrarEstablecimiento(est)
      throw e
    }

    await aplicarResultados(lote, respuesta)
    await guardarCambios(est, respuesta)
    cursor = respuesta.cursor
    huboCambios ||= lote.length > 0 || hayCambios(respuesta)

    const quedan = (await paraEnviar(est)).some((o) => !enviadas.has(o.id))
    if (!respuesta.hayMas && !quedan) break
  }

  cambiar({ ultima: { ...estado.ultima, [est]: new Date().toISOString() } })
  // Lo que se ve en pantalla puede haber cambiado
  if (huboCambios) {
    await Promise.all(
      ['animales', 'eventos', 'agrupaciones', 'stock', 'productos-sanitarios', 'pesajes'].map((k) =>
        queryClient.invalidateQueries({ queryKey: [k] }),
      ),
    )
  }
}

async function aplicarResultados(lote: OperacionPendiente[], respuesta: SyncResponse) {
  const porId = new Map(respuesta.resultados.map((r) => [r.id, r]))
  for (const op of lote) {
    const r = porId.get(op.id)
    if (!r) continue
    if (r.estado === 'APLICADO') {
      await quitarDeLaCola(op.id)
      // Una edición de la ficha puede aplicarse a medias: se avisa qué campos ganó el servidor
      if (r.descartados && r.descartados.length > 0) {
        await agregarAviso({
          id: op.id,
          establecimientoId: op.establecimientoId,
          descripcion: op.descripcion,
          mensaje:
            `No se aplicó: ${r.descartados.map((c) => CAMPOS[c] ?? c).join(', ')}. ` +
            'Alguien lo cambió después en el servidor y quedó ese valor.',
          en: new Date().toISOString(),
        })
      }
    } else {
      await actualizarEnLaCola({
        ...op,
        estado: r.estado,
        mensaje: r.mensaje,
        errores: r.errores,
        intentos: op.intentos + 1,
      })
    }
  }
}

/** Guarda filas y bajas, y recién al final el cursor: si se corta a mitad, se repite sin perder nada. */
async function guardarCambios(est: string, r: SyncResponse) {
  const base = await db()
  const tx = base.transaction([...TABLAS, 'meta'], 'readwrite')
  const guardar = async (tabla: (typeof TABLAS)[number], filas: { id: string }[]) => {
    const store = tx.objectStore(tabla)
    for (const fila of filas) await store.put({ ...fila, establecimientoId: est } as never)
  }
  await guardar('lotes', r.cambios.lotes)
  await guardar('rodeos', r.cambios.rodeos)
  await guardar('productos', r.cambios.productos)
  await guardar('animales', r.cambios.animales)
  await guardar('eventos', r.cambios.eventos)

  for (const baja of r.bajas) {
    if (baja.entidad !== 'ANIMAL') continue
    await tx.objectStore('animales').delete(baja.id)
    let eventos = await tx.objectStore('eventos').index('animal').openCursor(baja.id)
    while (eventos) {
      if (eventos.value.establecimientoId === est) await eventos.delete()
      eventos = await eventos.continue()
    }
  }
  await tx.objectStore('meta').put(r.cursor, metaClave.cursor(est))
  await tx.objectStore('meta').put(r.servidorEn, metaClave.ultimaSync(est))
  await tx.done
}

const hayCambios = (r: SyncResponse) =>
  Object.values(r.cambios).some((filas) => filas.length > 0) || r.bajas.length > 0

function mensajeDe(e: unknown) {
  if (isAxiosError(e) && !e.response) return 'Sin conexión con el servidor'
  if (isAxiosError(e))
    return (e.response?.data as { message?: string })?.message ?? 'Error del servidor'
  return 'No se pudo sincronizar'
}

/** ¿Este dispositivo ya tiene datos de ese establecimiento? */
export async function tieneDatosLocales(est: string) {
  return (await (await db()).get('meta', metaClave.cursor(est))) !== undefined
}
