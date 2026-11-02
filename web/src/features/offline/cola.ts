import { db } from './db'
import type { AvisoSync, OperacionPendiente, OperacionSync } from './tipos'

/**
 * Cola de lo hecho sin conexión (#30). Se guarda en IndexedDB, así sobrevive a cerrar la app.
 * Avisa cada cambio para que la pantalla actualice el contador de pendientes.
 */

const oyentes = new Set<() => void>()

export function alCambiarLaCola(oyente: () => void) {
  oyentes.add(oyente)
  return () => {
    oyentes.delete(oyente)
  }
}

export function avisarCambioDeCola() {
  oyentes.forEach((o) => o())
}

export async function encolar(
  establecimientoId: string,
  operacion: OperacionSync,
  descripcion: string,
) {
  const pendiente: OperacionPendiente = {
    id: operacion.id,
    establecimientoId,
    operacion,
    descripcion,
    creadaEn: new Date().toISOString(),
    estado: 'PENDIENTE',
    intentos: 0,
  }
  await (await db()).put('cola', pendiente)
  avisarCambioDeCola()
  return pendiente
}

/** Todas las operaciones, de la más vieja a la más nueva. */
export async function listarCola(establecimientoId?: string) {
  const todas = await (await db()).getAllFromIndex('cola', 'creadaEn')
  return establecimientoId ? todas.filter((o) => o.establecimientoId === establecimientoId) : todas
}

/** Las que hay que mandar: pendientes y con error (las rechazadas esperan al usuario). */
export async function paraEnviar(establecimientoId: string) {
  return (await listarCola(establecimientoId)).filter((o) => o.estado !== 'RECHAZADO')
}

export async function quitarDeLaCola(id: string) {
  await (await db()).delete('cola', id)
  avisarCambioDeCola()
}

export async function actualizarEnLaCola(op: OperacionPendiente) {
  await (await db()).put('cola', op)
  avisarCambioDeCola()
}

/** Establecimientos con algo para enviar (puede haber cargas de otro campo que no es el activo). */
export async function establecimientosConPendientes() {
  const todas = await listarCola()
  return [...new Set(todas.filter((o) => o.estado !== 'RECHAZADO').map((o) => o.establecimientoId))]
}

/** Vuelve a intentar una rechazada (ej: después de que le dieron el permiso que faltaba). */
export async function reintentar(id: string) {
  const op = await (await db()).get('cola', id)
  if (!op) return
  await actualizarEnLaCola({ ...op, estado: 'PENDIENTE', mensaje: undefined, errores: undefined })
}

// ---------- Avisos ----------

/**
 * Cosas que el usuario tiene que saber aunque se hayan aplicado: hoy, campos de una edición que no
 * se aplicaron porque en el servidor había un cambio más nuevo (last-write-wins por campo).
 */
const AVISOS = 'avisos'

export async function listarAvisos(): Promise<AvisoSync[]> {
  return ((await (await db()).get('meta', AVISOS)) as AvisoSync[] | undefined) ?? []
}

export async function agregarAviso(aviso: AvisoSync) {
  const base = await db()
  await base.put('meta', [...(await listarAvisos()), aviso], AVISOS)
  avisarCambioDeCola()
}

export async function quitarAviso(id: string) {
  const base = await db()
  await base.put(
    'meta',
    (await listarAvisos()).filter((a) => a.id !== id),
    AVISOS,
  )
  avisarCambioDeCola()
}

// ---------- Sincronizar a mano ----------

const pedidos = new Set<() => void>()

/** Lo escucha SincronizacionAutomatica, que sabe qué establecimientos sincronizar. */
export function alPedirSincronizacion(oyente: () => void) {
  pedidos.add(oyente)
  return () => {
    pedidos.delete(oyente)
  }
}

export function pedirSincronizacion() {
  pedidos.forEach((o) => o())
}
