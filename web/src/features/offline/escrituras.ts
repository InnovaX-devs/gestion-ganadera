import type { EventoInput, Operacion } from '@/features/eventos/types'
import { TIPOS_EVENTO } from '@/features/eventos/config'
import type { GrillaInput } from '@/features/pesajes/types'
import { ErrorConMensaje } from '@/lib/api'
import { encolar } from './cola'
import { db } from './db'
import { animalPorCaravana, anularEventoLocal } from './local'
import { esErrorDeRed, enLinea, nuevoId } from './red'

/**
 * Escrituras que funcionan sin conexión (#30): si no hay red (o se corta en el medio), se guardan en
 * la cola y se mandan en la próxima sincronización. Con red, van directo al servidor como siempre.
 */
export async function online<T>(remoto: () => Promise<T>, offline: () => Promise<T>): Promise<T> {
  if (!enLinea()) return offline()
  try {
    return await remoto()
  } catch (error) {
    // Si el pedido llegó y el servidor contestó (ej: datos inválidos), es un error de verdad
    if (esErrorDeRed(error)) return offline()
    throw error
  }
}

const pendiente = (operacionId: string): Operacion => ({
  operacionId,
  cantidad: 0,
  eventos: [],
  pendiente: true,
})

/** "Observación · V-214", "Tratamiento · 12 animales", "Pesaje · Lote Recría" */
async function describir(tipo: EventoInput['tipo'], animalIds: string[], loteId?: string | null) {
  const base = await db()
  const etiqueta = TIPOS_EVENTO[tipo].etiqueta
  if (animalIds.length === 1) {
    const animal = await base.get('animales', animalIds[0])
    return `${etiqueta} · ${animal?.caravana ?? '1 animal'}`
  }
  if (animalIds.length > 1) return `${etiqueta} · ${animalIds.length} animales`
  if (loteId) {
    const lote = (await base.get('lotes', loteId)) ?? (await base.get('rodeos', loteId))
    return `${etiqueta} · ${lote ? lote.nombre : 'un lote'}`
  }
  return `${etiqueta} · Establecimiento`
}

/** Mismo id para el reintento online y para la cola: el servidor nunca lo duplica. */
export async function registrarEventoSinConexion<D>(
  est: string,
  input: EventoInput<D> & { operacionId: string },
) {
  const { operacionId, ...evento } = input
  await encolar(
    est,
    {
      id: operacionId,
      tipo: 'REGISTRAR_EVENTO',
      evento: evento as Omit<EventoInput, 'operacionId'>,
    },
    await describir(input.tipo, input.animalIds ?? [], input.loteId ?? input.rodeoId),
  )
  return pendiente(operacionId)
}

/** La grilla trae caravanas: sin conexión se buscan entre los animales guardados en el dispositivo. */
export async function registrarGrillaSinConexion(
  est: string,
  input: GrillaInput & { operacionId: string },
) {
  const items = []
  const faltantes: string[] = []
  for (const p of input.pesajes) {
    const animal = await animalPorCaravana(est, p.caravana)
    if (animal) items.push({ animalId: animal.id, datos: { kg: p.kg } })
    else faltantes.push(p.caravana)
  }
  if (faltantes.length > 0) {
    throw new ErrorConMensaje(
      `Sin conexión no se encontró ${faltantes.length === 1 ? 'la caravana' : 'las caravanas'} ` +
        `${faltantes.join(', ')} entre los animales guardados en este dispositivo`,
    )
  }
  await encolar(
    est,
    {
      id: input.operacionId,
      tipo: 'REGISTRAR_EVENTOS_POR_ANIMAL',
      eventoTipo: 'PESAJE',
      fecha: input.fecha,
      items,
    },
    `Pesaje · ${items.length} ${items.length === 1 ? 'animal' : 'animales'}`,
  )
  return pendiente(input.operacionId)
}

export async function anularEventoSinConexion(est: string, eventoId: string) {
  const evento = await (await db()).get('eventos', eventoId)
  await encolar(
    est,
    { id: nuevoId(), tipo: 'ANULAR_EVENTO', eventoId },
    `Anular ${evento ? TIPOS_EVENTO[evento.tipo].etiqueta.toLowerCase() : 'evento'}`,
  )
  await anularEventoLocal(eventoId)
}
