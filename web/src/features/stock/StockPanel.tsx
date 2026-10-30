import { Link } from 'react-router'
import { PawPrint, Plus } from 'lucide-react'
import { CATEGORIAS, categoriasDe } from '@/features/animales/config'
import type { AnimalesLocationState, Categoria, Sexo } from '@/features/animales/types'
import { getErrorMessage } from '@/lib/api'
import { useStock } from './queries'
import type { Stock, StockGrupo } from './types'

const tarjeta =
  'rounded-xl border border-stone-200 bg-white p-5 dark:border-stone-800 dark:bg-stone-900'

// Mismos colores que las etiquetas de categoría del listado de animales
const COLOR_SEXO: Record<Sexo, string> = {
  HEMBRA: 'bg-rose-400 dark:bg-rose-500',
  MACHO: 'bg-sky-400 dark:bg-sky-500',
}

/**
 * Stock del establecimiento en el inicio (#25): total, hembras y machos, categorías, lotes y rodeos.
 * Cada fila abre el listado de animales con ese filtro.
 */
export function StockPanel({
  establecimientoId,
  establecimientoNombre,
  puedeCargarAnimales = true,
}: {
  establecimientoId: string
  establecimientoNombre: string
  /** Sin permiso para cargar animales, el estado vacío no ofrece el botón. */
  puedeCargarAnimales?: boolean
}) {
  const { data: stock, isPending, error } = useStock(establecimientoId)

  if (isPending) return <Esqueleto />
  if (error) {
    return (
      <section className={`${tarjeta} text-sm text-red-700 dark:text-red-400`}>
        No se pudo cargar el stock: {getErrorMessage(error)}
      </section>
    )
  }
  if (stock.total === 0)
    return (
      <SinAnimales
        stock={stock}
        establecimientoNombre={establecimientoNombre}
        puedeCargar={puedeCargarAnimales}
      />
    )

  return (
    <div className="grid grid-cols-1 gap-4">
      <section
        className={`${tarjeta} grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,17rem)_minmax(0,1fr)]`}
      >
        <Total stock={stock} />
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
          <Categorias stock={stock} sexo="HEMBRA" titulo="Hembras" />
          <Categorias stock={stock} sexo="MACHO" titulo="Machos" />
        </div>
      </section>
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <Grupos
          titulo="Lotes"
          grupos={stock.porLote}
          total={stock.total}
          sinAsignar="Sin lote"
          vacio="Todavía no hay lotes."
          crearEn="/lotes"
          filtro={(id) => ({ lote: id ?? 'SIN' })}
        />
        <Grupos
          titulo="Rodeos"
          grupos={stock.porRodeo}
          total={stock.total}
          sinAsignar="Sin rodeo"
          vacio="Todavía no hay rodeos."
          crearEn="/rodeos"
          filtro={(id) => ({ rodeo: id ?? 'SIN' })}
        />
      </div>
    </div>
  )
}

function Total({ stock }: { stock: Stock }) {
  const hembras = stock.porSexo.HEMBRA
  const machos = stock.porSexo.MACHO
  const porcentaje = (n: number) => Math.round((n / stock.total) * 100)

  return (
    <div className="flex flex-col">
      <h2 className="text-sm font-medium text-stone-500">Stock</h2>
      <p className="mt-1 text-4xl font-semibold tracking-tight tabular-nums">
        {stock.total.toLocaleString('es-AR')}
        <span className="ml-2 text-base font-normal tracking-normal text-stone-500">
          {stock.total === 1 ? 'cabeza' : 'cabezas'}
        </span>
      </p>

      <div
        role="img"
        aria-label={`${hembras} hembras y ${machos} machos`}
        className="mt-4 flex h-2.5 gap-0.5 overflow-hidden rounded-full bg-stone-100 dark:bg-stone-800"
      >
        {hembras > 0 && (
          <span
            className={COLOR_SEXO.HEMBRA}
            style={{ width: `${(hembras / stock.total) * 100}%` }}
          />
        )}
        {machos > 0 && <span className={`flex-1 ${COLOR_SEXO.MACHO}`} />}
      </div>
      <dl className="mt-3 grid grid-cols-2 gap-2 text-sm">
        <SexoLeyenda
          sexo="HEMBRA"
          etiqueta="Hembras"
          cantidad={hembras}
          porcentaje={porcentaje(hembras)}
        />
        <SexoLeyenda
          sexo="MACHO"
          etiqueta="Machos"
          cantidad={machos}
          porcentaje={porcentaje(machos)}
        />
      </dl>

      <Link
        to="/animales"
        className="text-brand-700 hover:text-brand-800 dark:text-brand-200 mt-auto pt-5 text-sm font-medium"
      >
        Ver todos los animales
      </Link>
    </div>
  )
}

function SexoLeyenda({
  sexo,
  etiqueta,
  cantidad,
  porcentaje,
}: {
  sexo: Sexo
  etiqueta: string
  cantidad: number
  porcentaje: number
}) {
  return (
    <div>
      <dt className="flex items-center gap-1.5 text-stone-500">
        <span className={`size-2 rounded-full ${COLOR_SEXO[sexo]}`} aria-hidden />
        {etiqueta}
      </dt>
      <dd className="font-semibold tabular-nums">
        {cantidad.toLocaleString('es-AR')}{' '}
        <span className="font-normal text-stone-500">{porcentaje}%</span>
      </dd>
    </div>
  )
}

/** Las categorías de un sexo, con una barra proporcional a la categoría más numerosa del stock. */
function Categorias({ stock, sexo, titulo }: { stock: Stock; sexo: Sexo; titulo: string }) {
  const maximo = Math.max(...Object.values(stock.porCategoria))
  return (
    <div>
      <h3 className="text-sm font-medium text-stone-500">{titulo}</h3>
      <ul className="mt-2 grid grid-cols-1 gap-0.5">
        {categoriasDe(sexo).map((c) => (
          <li key={c}>
            <FilaCategoria
              categoria={c}
              cantidad={stock.porCategoria[c]}
              maximo={maximo}
              color={COLOR_SEXO[sexo]}
            />
          </li>
        ))}
      </ul>
    </div>
  )
}

function FilaCategoria({
  categoria,
  cantidad,
  maximo,
  color,
}: {
  categoria: Categoria
  cantidad: number
  maximo: number
  color: string
}) {
  const contenido = (
    <>
      <span className="w-24 shrink-0 truncate">{CATEGORIAS[categoria].plural}</span>
      <span className="h-1.5 flex-1 overflow-hidden rounded-full bg-stone-100 dark:bg-stone-800">
        <span
          className={`block h-full rounded-full ${color}`}
          style={{ width: `${(cantidad / maximo) * 100}%` }}
        />
      </span>
      <span className="w-10 shrink-0 text-right font-semibold tabular-nums">{cantidad}</span>
    </>
  )
  const clases = 'flex items-center gap-3 rounded-md px-2 py-1.5 text-sm'

  // Las categorías en 0 se muestran igual (para ver la composición completa) pero no son un link
  if (cantidad === 0) {
    return (
      <div className={`${clases} text-stone-400 dark:text-stone-600`} aria-hidden>
        {contenido}
      </div>
    )
  }
  return (
    <Link
      to="/animales"
      state={{ filtros: { categoria } } satisfies AnimalesLocationState}
      className={`${clases} focus-visible:ring-brand-300 outline-none hover:bg-stone-50 focus-visible:ring-2 dark:hover:bg-stone-800`}
      aria-label={`${cantidad} ${CATEGORIAS[categoria].plural.toLowerCase()}: ver en el listado`}
    >
      {contenido}
    </Link>
  )
}

function Grupos({
  titulo,
  grupos,
  total,
  sinAsignar,
  vacio,
  crearEn,
  filtro,
}: {
  titulo: string
  grupos: StockGrupo[]
  total: number
  sinAsignar: string
  vacio: string
  crearEn: string
  filtro: (id: string | undefined) => NonNullable<AnimalesLocationState['filtros']>
}) {
  const cantidad = contarAgrupaciones(grupos)
  return (
    <section className={tarjeta}>
      <div className="flex items-baseline justify-between gap-3">
        <h2 className="text-sm font-medium text-stone-500">
          {titulo}
          {cantidad > 0 && (
            <span className="ml-2 rounded-full bg-stone-100 px-2 py-0.5 text-xs font-semibold text-stone-700 tabular-nums dark:bg-stone-800 dark:text-stone-300">
              {cantidad}
            </span>
          )}
        </h2>
        {cantidad > 0 && (
          <Link
            to={crearEn}
            className="text-brand-700 hover:text-brand-800 dark:text-brand-200 text-sm font-medium"
          >
            Gestionar
          </Link>
        )}
      </div>
      <ul className="-mx-2 mt-2 grid grid-cols-1 gap-0.5">
        {grupos.map((g) => (
          <li key={g.id ?? 'sin-asignar'} className="min-w-0">
            <Link
              to="/animales"
              state={{ filtros: filtro(g.id) } satisfies AnimalesLocationState}
              className="focus-visible:ring-brand-300 block rounded-md px-2 py-2 outline-none hover:bg-stone-50 focus-visible:ring-2 dark:hover:bg-stone-800"
            >
              <span className="flex items-baseline justify-between gap-3 text-sm">
                <span
                  className={`min-w-0 truncate ${g.id ? 'font-medium' : 'text-stone-500 italic'}`}
                >
                  {g.nombre ?? sinAsignar}
                </span>
                <span className="shrink-0 font-semibold tabular-nums">
                  {g.total.toLocaleString('es-AR')}
                </span>
              </span>
              <span
                className="mt-0.5 block truncate text-xs text-stone-500"
                title={composicion(g) || undefined}
              >
                {composicion(g) || 'Sin animales'}
              </span>
              <span
                className="mt-1.5 block h-1 overflow-hidden rounded-full bg-stone-100 dark:bg-stone-800"
                aria-hidden
              >
                <span
                  className="bg-brand-500 block h-full rounded-full"
                  style={{ width: `${(g.total / total) * 100}%` }}
                />
              </span>
            </Link>
          </li>
        ))}
      </ul>
      {cantidad === 0 && (
        <p className="mt-3 text-sm text-stone-500">
          {vacio}{' '}
          <Link
            to={crearEn}
            className="text-brand-700 hover:text-brand-800 dark:text-brand-200 font-medium"
          >
            Crear uno
          </Link>
        </p>
      )}
    </section>
  )
}

/** "12 vacas, 1 toro, 5 terneras": las categorías que tiene el grupo, en el orden de siempre. */
function composicion(grupo: StockGrupo) {
  return (Object.keys(CATEGORIAS) as Categoria[])
    .filter((c) => grupo.porCategoria[c] > 0)
    .map((c) => {
      const n = grupo.porCategoria[c]
      const { etiqueta, plural } = CATEGORIAS[c]
      return `${n} ${(n === 1 ? etiqueta : plural).toLowerCase()}`
    })
    .join(', ')
}

/** Lotes o rodeos activos (sin contar el grupo de los que no tienen asignado). */
const contarAgrupaciones = (grupos: StockGrupo[]) => grupos.filter((g) => g.id).length

function plural(n: number, singular: string, varios: string) {
  return `${n} ${n === 1 ? singular : varios}`
}

function SinAnimales({
  stock,
  establecimientoNombre,
  puedeCargar,
}: {
  stock: Stock
  establecimientoNombre: string
  puedeCargar: boolean
}) {
  const lotes = contarAgrupaciones(stock.porLote)
  const rodeos = contarAgrupaciones(stock.porRodeo)
  return (
    <section className={`${tarjeta} flex flex-col gap-4 sm:flex-row sm:items-center`}>
      <span className="bg-brand-50 text-brand-600 dark:bg-brand-900/40 dark:text-brand-200 grid size-10 shrink-0 place-items-center rounded-full">
        <PawPrint className="size-5" />
      </span>
      <div className="flex-1">
        <h2 className="font-semibold">Todavía no hay animales en {establecimientoNombre}</h2>
        <p className="mt-0.5 text-sm text-stone-600 dark:text-stone-400">
          El stock se arma solo a partir de los animales que cargues.{' '}
          {lotes + rodeos > 0
            ? `Ya tenés ${plural(lotes, 'lote', 'lotes')} y ${plural(rodeos, 'rodeo', 'rodeos')} para ubicarlos.`
            : 'Podés crear lotes y rodeos para ubicarlos.'}
        </p>
      </div>
      {puedeCargar && (
        <Link
          to="/animales"
          className="bg-brand-600 hover:bg-brand-700 inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-medium text-white"
        >
          <Plus className="size-4" />
          Cargar animales
        </Link>
      )}
    </section>
  )
}

function Esqueleto() {
  return (
    <section
      className={`${tarjeta} grid animate-pulse grid-cols-1 gap-6 lg:grid-cols-[minmax(0,17rem)_minmax(0,1fr)]`}
      aria-busy
      aria-label="Cargando el stock"
    >
      <div className="grid gap-3">
        <div className="h-4 w-16 rounded bg-stone-200 dark:bg-stone-800" />
        <div className="h-10 w-40 rounded bg-stone-200 dark:bg-stone-800" />
        <div className="h-2.5 rounded-full bg-stone-200 dark:bg-stone-800" />
      </div>
      <div className="grid gap-2">
        {Array.from({ length: 4 }, (_, i) => (
          <div key={i} className="h-5 rounded bg-stone-100 dark:bg-stone-800/60" />
        ))}
      </div>
    </section>
  )
}
