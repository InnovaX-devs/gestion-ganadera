import { db } from './db'
import type { OperacionPendiente, OperacionSync } from './tipos'

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
