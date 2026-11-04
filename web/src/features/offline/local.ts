import type { Agrupacion, TipoAgrupacion } from '@/features/agrupaciones/types'
import { LISTA_CATEGORIAS } from '@/features/animales/config'
import type { Animal, Categoria, FiltroAnimales, Pagina, Sexo } from '@/features/animales/types'
import type { Establecimiento } from '@/features/establecimientos/types'
import type { Evento, FiltroEventos } from '@/features/eventos/types'
import type { ProductoSanitario } from '@/features/sanidad/types'
import type { Stock, StockGrupo } from '@/features/stock/types'
import { db, metaClave } from './db'
import type { AgrupacionSync, AnimalSync, EventoSync } from './tipos'

/**
 * Lecturas desde la base local (#30): lo que devuelve la API, con la misma forma y los mismos
 * filtros, pero con los datos de la última sincronización. Se usan cuando no hay conexión.
 * Devuelven `undefined` si el establecimiento nunca se sincronizó en este dispositivo.
 */

async function sincronizado(est: string) {
  return (await (await db()).get('meta', metaClave.cursor(est))) !== undefined
}

async function filas<T extends 'animales' | 'lotes' | 'rodeos' | 'productos' | 'eventos'>(
  tabla: T,
  est: string,
) {
  // Todas las tablas tienen el índice "est": el genérico de idb no lo puede deducir solo
  return (await db()).getAllFromIndex(tabla, 'est' as never, est as never)
}

const porNombre = <T extends { nombre: string }>(a: T, b: T) =>
  a.nombre.localeCompare(b.nombre, 'es')
const paginar = <T>(lista: T[], pagina = 0, tamanio = 20): Pagina<T> => ({
  contenido: lista.slice(pagina * tamanio, (pagina + 1) * tamanio),
  total: lista.length,
  pagina,
  tamanio,
  totalPaginas: Math.ceil(lista.length / tamanio),
})

// ---------- Establecimientos ----------

export async function guardarEstablecimientos(lista: Establecimiento[]) {
  await (await db()).put('meta', lista, metaClave.establecimientos)
}

export async function establecimientosLocales() {
  return (await (await db()).get('meta', metaClave.establecimientos)) as
    Establecimiento[] | undefined
}

// ---------- Animales ----------

async function contexto(est: string) {
  const [lotes, rodeos, animales] = await Promise.all([
    filas('lotes', est),
    filas('rodeos', est),
    filas('animales', est),
  ])
  return {
    lotes: new Map(lotes.map((l) => [l.id, l])),
    rodeos: new Map(rodeos.map((r) => [r.id, r])),
    animales: new Map(animales.map((a) => [a.id, a])),
  }
}
type Contexto = Awaited<ReturnType<typeof contexto>>

const referencia = (id: string | undefined, nombres: Map<string, { nombre: string }>) => {
  if (!id) return null
  return { id, nombre: nombres.get(id)?.nombre ?? '—' }
}

function aAnimal(a: AnimalSync, est: string, ctx: Contexto): Animal {
  const caravana = (id?: string) =>
    id ? { id, nombre: ctx.animales.get(id)?.caravana ?? '—' } : null
  return {
    id: a.id,
    establecimientoId: est,
    caravana: a.caravana,
    caravanaElectronica: a.caravanaElectronica ?? null,
    sexo: a.sexo,
    categoria: a.categoria,
    raza: a.raza ?? null,
    fechaNacimiento: a.fechaNacimiento ?? null,
    fechaNacimientoEstimada: a.fechaNacimientoEstimada,
    castrado: a.castrado,
    estado: a.estado,
    lote: referencia(a.loteId, ctx.lotes),
    rodeo: referencia(a.rodeoId, ctx.rodeos),
    madre: caravana(a.madreId),
    padre: caravana(a.padreId),
    observaciones: a.observaciones ?? null,
    createdAt: a.updatedAt,
  }
}

/** Como GET /animales: solo activos, por caravana; `q` busca en las dos caravanas. */
export async function animalesLocales(est: string, filtro: FiltroAnimales) {
  if (!(await sincronizado(est))) return undefined
  const ctx = await contexto(est)
  const q = filtro.q?.trim().toUpperCase()
  const lista = [...ctx.animales.values()]
    .filter((a) => a.estado === 'ACTIVO')
    .filter((a) => !q || a.caravana.includes(q) || (a.caravanaElectronica ?? '').includes(q))
    .filter((a) => !filtro.categoria || a.categoria === filtro.categoria)
    .filter((a) => !filtro.sexo || a.sexo === filtro.sexo)
    .filter((a) => !filtro.lote || (filtro.lote === 'SIN' ? !a.loteId : a.loteId === filtro.lote))
    .filter(
      (a) => !filtro.rodeo || (filtro.rodeo === 'SIN' ? !a.rodeoId : a.rodeoId === filtro.rodeo),
    )
    .sort((a, b) => a.caravana.localeCompare(b.caravana, 'es'))
  return paginar(
    lista.map((a) => aAnimal(a, est, ctx)),
    filtro.pagina,
    filtro.tamanio,
  )
}

export async function animalLocal(est: string, id: string) {
  if (!(await sincronizado(est))) return undefined
  const ctx = await contexto(est)
  const animal = ctx.animales.get(id)
  return animal ? aAnimal(animal, est, ctx) : undefined
}

/** Animal activo por caravana (la grilla de pesajes carga caravanas). */
export async function animalPorCaravana(est: string, caravana: string) {
  const buscada = caravana.trim().toUpperCase()
  return (await filas('animales', est)).find((a) => a.estado === 'ACTIVO' && a.caravana === buscada)
}

// ---------- Lotes, rodeos y productos ----------

export async function agrupacionesLocales(tipo: TipoAgrupacion, est: string) {
  if (!(await sincronizado(est))) return undefined
  const [grupos, animales] = await Promise.all([filas(tipo, est), filas('animales', est)])
  const campo = tipo === 'lotes' ? 'loteId' : 'rodeoId'
  return grupos
    .filter((g: AgrupacionSync) => g.activo)
    .map((g): Agrupacion => ({
      id: g.id,
      establecimientoId: est,
      nombre: g.nombre,
      descripcion: g.descripcion ?? null,
      cantidadAnimales: animales.filter((a) => a.estado === 'ACTIVO' && a[campo] === g.id).length,
      createdAt: g.updatedAt,
    }))
    .sort(porNombre)
}

export async function productosLocales(est: string) {
  if (!(await sincronizado(est))) return undefined
  return (await filas('productos', est))
    .filter((p) => p.activo)
    .map((p): ProductoSanitario => ({
      id: p.id,
      nombre: p.nombre,
      tipo: p.tipo,
      diasCarencia: p.diasCarencia,
      dosisHabitual: p.dosisHabitual ?? null,
      viaHabitual: p.viaHabitual ?? null,
    }))
    .sort(porNombre)
}

// ---------- Eventos ----------

/** Como GET /eventos: sin anulados, más recientes primero. */
export async function eventosLocales(est: string, filtro: FiltroEventos) {
  if (!(await sincronizado(est))) return undefined
  const [eventos, ctx] = await Promise.all([filas('eventos', est), contexto(est)])
  const lista = eventos
    .filter((e: EventoSync) => !e.anulado)
    .filter((e) => !filtro.tipo || e.tipo === filtro.tipo)
    .filter((e) => !filtro.animalId || e.animalId === filtro.animalId)
    .filter((e) => !filtro.loteId || e.loteId === filtro.loteId)
    .filter((e) => !filtro.desde || e.fecha >= filtro.desde)
    .filter((e) => !filtro.hasta || e.fecha <= filtro.hasta)
    .sort((a, b) => b.fecha.localeCompare(a.fecha) || b.createdAt.localeCompare(a.createdAt))
    .map((e): Evento => ({
      id: e.id,
      operacionId: e.operacionId,
      tipo: e.tipo,
      fecha: e.fecha,
      animal: e.animalId
        ? { id: e.animalId, nombre: ctx.animales.get(e.animalId)?.caravana ?? '—' }
        : null,
      lote: referencia(e.loteId, ctx.lotes),
      datos: e.datos,
      registradoPor: { id: '', nombre: e.registradoPor },
      createdAt: e.createdAt,
    }))
  return paginar(lista, filtro.pagina, filtro.tamanio)
}

/** Lo marca anulado acá, para que desaparezca del historial antes de sincronizar. */
export async function anularEventoLocal(id: string) {
  const base = await db()
  const evento = await base.get('eventos', id)
  if (evento) await base.put('eventos', { ...evento, anulado: true })
}

// ---------- Stock ----------

/** Como GET /stock: animales activos por sexo, categoría, lote y rodeo. */
export async function stockLocal(est: string): Promise<Stock | undefined> {
  if (!(await sincronizado(est))) return undefined
  const [animales, lotes, rodeos] = await Promise.all([
    filas('animales', est),
    filas('lotes', est),
    filas('rodeos', est),
  ])
  const activos = animales.filter((a) => a.estado === 'ACTIVO')

  const contar = (lista: AnimalSync[]) => {
    const porSexo: Record<Sexo, number> = { HEMBRA: 0, MACHO: 0 }
    const porCategoria = Object.fromEntries(LISTA_CATEGORIAS.map((c) => [c, 0])) as Record<
      Categoria,
      number
    >
    for (const a of lista) {
      porSexo[a.sexo]++
      porCategoria[a.categoria]++
    }
    return { total: lista.length, porSexo, porCategoria }
  }
  const grupos = (todos: AgrupacionSync[], campo: 'loteId' | 'rodeoId'): StockGrupo[] => {
    const lista: StockGrupo[] = todos
      .filter((g) => g.activo)
      .sort(porNombre)
      .map((g) => ({
        id: g.id,
        nombre: g.nombre,
        ...contar(activos.filter((a) => a[campo] === g.id)),
      }))
    const sinAsignar = activos.filter((a) => !a[campo])
    if (sinAsignar.length > 0) lista.push(contar(sinAsignar))
    return lista
  }

  return {
    establecimientoId: est,
    ...contar(activos),
    porLote: grupos(lotes, 'loteId'),
    porRodeo: grupos(rodeos, 'rodeoId'),
  }
}
