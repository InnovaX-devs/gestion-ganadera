import { Link } from 'react-router'
import { ChevronRight, History } from 'lucide-react'
import { TIPOS_EVENTO } from '@/features/eventos/config'
import { formatearFecha, hoyISO } from '@/features/eventos/fechas'
import { useEventos } from '@/features/eventos/queries'
import type { Evento, TipoEvento } from '@/features/eventos/types'
import { getErrorMessage } from '@/lib/api'

/** Cuántos eventos se piden: alcanza para juntar varias operaciones aunque alguna sea de muchos animales. */
const TAMANIO = 100
const MAXIMO = 6

/** Página de cada tipo (las mismas rutas del menú). */
const PAGINA: Record<TipoEvento, string> = {
  NACIMIENTO: '/nacimientos',
  SERVICIO: '/servicios',
  TACTO: '/tactos',
  TRATAMIENTO: '/tratamientos',
  PESAJE: '/pesajes',
  MOVIMIENTO: '/movimientos',
  VENTA: '/ventas',
  BAJA: '/ventas',
  OBSERVACION: '/observaciones',
}

/**
 * Una carga del usuario. Un evento sobre varios animales (ej: un tratamiento al rodeo) se guarda
 * como una fila por animal con el mismo `operacionId`: acá se muestra como una sola.
 */
interface Registro {
  evento: Evento
  animales: number
  /** La última operación de la página puede seguir en la siguiente: su cantidad es un mínimo. */
  incompleto: boolean
}

function agruparPorOperacion(eventos: Evento[], hayMas: boolean): Registro[] {
  const registros = new Map<string, Registro>()
  for (const evento of eventos) {
    const registro = registros.get(evento.operacionId)
    if (registro) registro.animales += evento.animal ? 1 : 0
    else
      registros.set(evento.operacionId, {
        evento,
        animales: evento.animal ? 1 : 0,
        incompleto: false,
      })
  }
  const lista = [...registros.values()]
  if (hayMas && lista.length > 0) lista[lista.length - 1].incompleto = true
  return lista
}

/** Últimos eventos del establecimiento, del más reciente al más viejo (#26). */
export function UltimosEventos({ establecimientoId }: { establecimientoId: string }) {
  const { data, isPending, error } = useEventos(establecimientoId, { tamanio: TAMANIO })

  const registros = data
    ? agruparPorOperacion(data.contenido, data.total > data.contenido.length).slice(0, MAXIMO)
    : []

  return (
    <section className="rounded-xl border border-stone-200 bg-white dark:border-stone-800 dark:bg-stone-900">
      <h2 className="px-5 pt-5 text-sm font-medium text-stone-500">Últimos eventos</h2>

      {isPending && (
        <ul className="grid animate-pulse gap-4 p-5" aria-busy aria-label="Cargando eventos">
          {Array.from({ length: 3 }, (_, i) => (
            <li key={i} className="flex gap-3">
              <span className="size-9 rounded-lg bg-stone-200 dark:bg-stone-800" />
              <span className="grid flex-1 gap-2 py-1">
                <span className="h-3.5 w-1/3 rounded bg-stone-200 dark:bg-stone-800" />
                <span className="h-3 w-2/3 rounded bg-stone-100 dark:bg-stone-800/60" />
              </span>
            </li>
          ))}
        </ul>
      )}

      {error && (
        <p className="p-5 text-sm text-red-700 dark:text-red-400">
          No se pudieron cargar los eventos: {getErrorMessage(error)}
        </p>
      )}

      {data && registros.length === 0 && (
        <div className="flex flex-col items-center px-5 py-10 text-center">
          <History className="size-6 text-stone-400" />
          <p className="mt-2 text-sm font-medium">Todavía no hay eventos registrados</p>
          <p className="mt-0.5 text-sm text-stone-500">
            Nacimientos, tratamientos, pesajes y observaciones van a aparecer acá.
          </p>
        </div>
      )}

      {registros.length > 0 && (
        <ul className="mt-2 divide-y divide-stone-100 pb-2 dark:divide-stone-800">
          {registros.map((r) => (
            <li key={r.evento.operacionId}>
              <FilaEvento registro={r} />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function FilaEvento({ registro }: { registro: Registro }) {
  const { evento, animales, incompleto } = registro
  const { etiqueta, icono: Icono, resumen } = TIPOS_EVENTO[evento.tipo]
  // Primera línea del resumen: en el inicio alcanza con lo principal
  const detalle = resumen?.(evento.datos)?.split('\n')[0]
  // Con un solo animal, la ficha muestra su historial completo
  const destino =
    animales === 1 && evento.animal ? `/animales/${evento.animal.id}` : PAGINA[evento.tipo]

  return (
    <Link
      to={destino}
      className="group focus-visible:ring-brand-300 flex items-center gap-3 px-5 py-3 outline-none hover:bg-stone-50 focus-visible:ring-2 focus-visible:ring-inset dark:hover:bg-stone-800/50"
    >
      <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-stone-100 text-stone-600 dark:bg-stone-800 dark:text-stone-300">
        <Icono className="size-[1.125rem]" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex items-baseline justify-between gap-3">
          <span className="min-w-0 truncate text-sm">
            <span className="font-medium">{etiqueta}</span>
            <span className="text-stone-500"> · {sobreQue(evento, animales, incompleto)}</span>
          </span>
          <span className="shrink-0 text-xs text-stone-500 tabular-nums">
            {fechaCorta(evento.fecha)}
          </span>
        </span>
        <span className="mt-0.5 block truncate text-xs text-stone-500">
          {[detalle, evento.registradoPor.nombre].filter(Boolean).join(' · ')}
        </span>
      </span>
      <ChevronRight className="size-4 shrink-0 text-stone-300 group-hover:text-stone-500 dark:text-stone-600" />
    </Link>
  )
}

/** "C-101", "12 animales", "Lote Recría" o "Establecimiento". */
function sobreQue(evento: Evento, animales: number, incompleto: boolean) {
  if (animales > 1) return `${animales}${incompleto ? '+' : ''} animales`
  if (evento.animal) return evento.animal.nombre
  if (evento.lote) return `Lote ${evento.lote.nombre}`
  return 'Establecimiento'
}

function fechaCorta(iso: string) {
  const hoy = hoyISO()
  if (iso === hoy) return 'Hoy'
  const ayer = new Date(`${hoy}T00:00:00`)
  ayer.setDate(ayer.getDate() - 1)
  const ayerISO = `${ayer.getFullYear()}-${String(ayer.getMonth() + 1).padStart(2, '0')}-${String(ayer.getDate()).padStart(2, '0')}`
  if (iso === ayerISO) return 'Ayer'
  // En el año en curso no hace falta el año
  const fecha = formatearFecha(iso)
  return iso.startsWith(hoy.slice(0, 4)) ? fecha.replace(/ \d{4}$/, '') : fecha
}
