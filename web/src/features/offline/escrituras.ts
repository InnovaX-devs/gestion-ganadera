import type { Animal, AnimalInput } from '@/features/animales/types'
import type { EventoInput, Operacion } from '@/features/eventos/types'
import { TIPOS_EVENTO } from '@/features/eventos/config'
import type { GrillaInput } from '@/features/pesajes/types'
import { ErrorConMensaje } from '@/lib/api'
import { encolar } from './cola'
import { db } from './db'
import { animalLocal, animalPorCaravana, anularEventoLocal } from './local'
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

// ---------- Ficha del animal ----------

/**
 * Campos que se pueden editar sin conexión (docs/sync.md: last-write-wins por campo). El resto
 * (lote, rodeo, categoría, sexo, madre, padre) cambia con eventos o necesita validaciones del
 * servidor: sin conexión no se permite.
 */
const CAMPOS_OFFLINE = {
  caravana: 'caravana',
  caravanaElectronica: 'caravana electrónica',
  raza: 'raza',
  fechaNacimiento: 'fecha de nacimiento',
  fechaNacimientoEstimada: 'fecha estimada',
  observaciones: 'observaciones',
} as const

const CAMPOS_CON_CONEXION: Partial<Record<keyof AnimalInput, string>> = {
  sexo: 'el sexo',
  categoria: 'la categoría',
  castrado: 'si está castrado',
  loteId: 'el lote',
  rodeoId: 'el rodeo',
  madreId: 'la madre',
  padreId: 'el padre',
}

/** Igual que el backend: la caravana sin espacios y en mayúsculas; los textos vacíos son null. */
function normalizar(campo: string, valor: unknown) {
  if (valor === undefined || valor === '') return null
  if (typeof valor !== 'string') return valor
  const limpio = valor.trim()
  if (campo === 'caravana' || campo === 'caravanaElectronica') return limpio.toUpperCase() || null
  return limpio || null
}

/**
 * Guarda la edición en la cola como ACTUALIZAR_ANIMAL, solo con los campos que cambiaron, y la
 * aplica en la copia local para que se vea enseguida. Devuelve el animal como quedó acá.
 */
export async function editarAnimalSinConexion(
  est: string,
  id: string,
  data: AnimalInput,
): Promise<Animal> {
  const base = await db()
  const actual = await base.get('animales', id)
  if (!actual) {
    throw new ErrorConMensaje(
      'Este animal no está guardado en el dispositivo: para editarlo hace falta conexión',
    )
  }

  const anterior: Record<string, unknown> = { ...actual }
  const sinConexion = Object.entries(CAMPOS_CON_CONEXION)
    .filter(
      ([campo]) =>
        normalizar(campo, data[campo as keyof AnimalInput]) !== normalizar(campo, anterior[campo]),
    )
    .map(([, nombre]) => nombre)
  if (sinConexion.length > 0) {
    throw new ErrorConMensaje(
      `Sin conexión no se puede cambiar ${sinConexion.join(', ')}. ` +
        'Guardá solo los otros cambios, o esperá a tener señal.',
    )
  }

  const cambios: Record<string, unknown> = {}
  for (const campo of Object.keys(CAMPOS_OFFLINE)) {
    const nuevo = normalizar(campo, data[campo as keyof AnimalInput])
    if (nuevo !== normalizar(campo, anterior[campo])) cambios[campo] = nuevo
  }
  if (Object.keys(cambios).length === 0) {
    const sinCambios = await animalLocal(est, id)
    if (sinCambios) return sinCambios
  }
  if (cambios.caravana === null) throw new ErrorConMensaje('Ingresá la caravana')

  if (Object.keys(cambios).length > 0) {
    const nombres = Object.keys(cambios).map(
      (c) => CAMPOS_OFFLINE[c as keyof typeof CAMPOS_OFFLINE],
    )
    await encolar(
      est,
      {
        id: nuevoId(),
        tipo: 'ACTUALIZAR_ANIMAL',
        animalId: id,
        cambios,
        modificadoEn: new Date().toISOString(),
      },
      `Edición de ${actual.caravana} · ${nombres.join(', ')}`,
    )
    // Copia local: hasta sincronizar se ve lo editado (los null se guardan como "sin dato")
    const editado = { ...actual } as Record<string, unknown>
    for (const [campo, valor] of Object.entries(cambios)) editado[campo] = valor ?? undefined
    if (!editado.fechaNacimiento) editado.fechaNacimientoEstimada = false
    await base.put('animales', editado as unknown as typeof actual)
  }
  return (await animalLocal(est, id))!
}
