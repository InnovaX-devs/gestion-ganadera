import { useState } from 'react'
import { Link } from 'react-router'
import {
  ChevronLeft,
  ChevronRight,
  Layers,
  LoaderCircle,
  MapPinned,
  PawPrint,
  Pencil,
  Plus,
  Search,
  SearchX,
  Fence,
  Trash2,
  X,
} from 'lucide-react'
import { Button } from '@/components/Button'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { FormError } from '@/components/FormError'
import { PageHeader } from '@/components/PageHeader'
import { SelectField } from '@/components/SelectField'
import { useAgrupaciones } from '@/features/agrupaciones/queries'
import { AnimalFormDialog } from '@/features/animales/AnimalFormDialog'
import { AsignarDialog, type CampoAsignacion } from '@/features/animales/AsignarDialog'
import { CATEGORIAS, LISTA_CATEGORIAS, SEXOS, formatearEdad } from '@/features/animales/config'
import { useAnimales, useEliminarAnimal, useResumenAnimales } from '@/features/animales/queries'
import type { Animal, Categoria, FiltroAnimales, Sexo } from '@/features/animales/types'
import { puede } from '@/features/establecimientos/permisos'
import { useEstablecimientoActivo } from '@/features/establecimientos/useEstablecimientoActivo'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { getErrorMessage } from '@/lib/api'

const POR_PAGINA = 50

export function AnimalesPage() {
  const { activo, cargando } = useEstablecimientoActivo()

  if (cargando) return <Cargando />
  if (!activo) {
    return (
      <>
        <PageHeader title="Animales" />
        <EmptyState
          icono={MapPinned}
          titulo="Primero creá un establecimiento"
          texto="Los animales pertenecen a un establecimiento."
          accion={
            <Link
              to="/establecimientos"
              state={{ crear: true }}
              className="bg-brand-600 hover:bg-brand-700 inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium text-white"
            >
              <Plus className="size-4" />
              Crear establecimiento
            </Link>
          }
        />
      </>
    )
  }

  return (
    <Listado
      key={activo.id}
      establecimientoId={activo.id}
      establecimientoNombre={activo.nombre}
      puedeGestionar={puede(activo.rol, 'GESTIONAR_ANIMALES')}
    />
  )
}

type Filtros = Omit<FiltroAnimales, 'pagina' | 'tamanio'>

function Listado({
  establecimientoId,
  establecimientoNombre,
  puedeGestionar,
}: {
  establecimientoId: string
  establecimientoNombre: string
  puedeGestionar: boolean
}) {
  const [filtros, setFiltros] = useState<Filtros>({})
  const [busqueda, setBusqueda] = useState('')
  const [pagina, setPagina] = useState(0)
  const [seleccion, setSeleccion] = useState<Set<string>>(new Set())
  const [formulario, setFormulario] = useState<{ animal: Animal | null } | null>(null)
  const [aBorrar, setABorrar] = useState<Animal | null>(null)
  const [asignando, setAsignando] = useState<CampoAsignacion | null>(null)

  const q = useDebouncedValue(busqueda.trim())
  const { data, isPending, isFetching, error } = useAnimales(establecimientoId, {
    ...filtros,
    q,
    pagina,
    tamanio: POR_PAGINA,
  })
  const { data: resumen } = useResumenAnimales(establecimientoId)
  const { data: lotes } = useAgrupaciones('lotes', establecimientoId)
  const { data: rodeos } = useAgrupaciones('rodeos', establecimientoId)
  const eliminar = useEliminarAnimal(establecimientoId)

  // Al cambiar un filtro se vuelve a la primera página y se limpia la selección
  const filtrar = (cambios: Partial<Filtros>) => {
    setFiltros((f) => ({ ...f, ...cambios }))
    setPagina(0)
    setSeleccion(new Set())
  }
  const buscar = (texto: string) => {
    setBusqueda(texto)
    setPagina(0)
    setSeleccion(new Set())
  }
  const irAPagina = (p: number) => {
    setPagina(p)
    setSeleccion(new Set())
  }
  const limpiarFiltros = () => {
    setFiltros({})
    buscar('')
  }

  const hayFiltros = !!busqueda || Object.values(filtros).some(Boolean)
  const animales = data?.contenido ?? []
  const todosSeleccionados = animales.length > 0 && animales.every((a) => seleccion.has(a.id))

  const alternar = (id: string) =>
    setSeleccion((s) => {
      const nueva = new Set(s)
      if (nueva.has(id)) nueva.delete(id)
      else nueva.add(id)
      return nueva
    })
  const alternarTodos = () =>
    setSeleccion(todosSeleccionados ? new Set() : new Set(animales.map((a) => a.id)))

  const cerrarBorrado = () => {
    setABorrar(null)
    eliminar.reset()
  }

  const sinAnimales = !isPending && resumen?.total === 0 && !hayFiltros

  return (
    <>
      <PageHeader
        title="Animales"
        description={establecimientoNombre}
        actions={
          puedeGestionar &&
          !sinAnimales && (
            <Button onClick={() => setFormulario({ animal: null })}>
              <Plus className="size-4" />
              Nuevo animal
            </Button>
          )
        }
      />

      {sinAnimales ? (
        <EmptyState
          icono={PawPrint}
          titulo={`Todavía no hay animales en ${establecimientoNombre}`}
          texto="Cargá el primero para empezar a llevar el stock y el historial de cada animal."
          accion={
            puedeGestionar && (
              <Button onClick={() => setFormulario({ animal: null })}>
                <Plus className="size-4" />
                Cargar animal
              </Button>
            )
          }
        />
      ) : (
        <>
          {resumen && (
            <Resumen
              resumen={resumen}
              categoriaActiva={filtros.categoria}
              onCategoria={(categoria) =>
                filtrar({ categoria: filtros.categoria === categoria ? undefined : categoria })
              }
            />
          )}

          {/* Filtros */}
          <div className="mb-4 grid grid-cols-2 gap-2 lg:grid-cols-[1fr_repeat(4,minmax(0,10rem))_auto]">
            <div className="relative col-span-2 lg:col-span-1">
              <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-stone-400" />
              <input
                type="search"
                value={busqueda}
                onChange={(e) => buscar(e.target.value)}
                placeholder="Buscar por caravana…"
                aria-label="Buscar por caravana"
                className="focus:border-brand-500 focus:ring-brand-100 dark:focus:ring-brand-900 block w-full rounded-lg border border-stone-300 bg-white py-2 pr-3 pl-9 text-sm shadow-xs outline-none focus:ring-2 dark:border-stone-700 dark:bg-stone-900"
              />
            </div>
            <SelectField
              aria-label="Categoría"
              value={filtros.categoria ?? ''}
              onChange={(e) => filtrar({ categoria: (e.target.value || undefined) as Categoria })}
            >
              <option value="">Categoría</option>
              {LISTA_CATEGORIAS.map((c) => (
                <option key={c} value={c}>
                  {CATEGORIAS[c].etiqueta}
                </option>
              ))}
            </SelectField>
            <SelectField
              aria-label="Sexo"
              value={filtros.sexo ?? ''}
              onChange={(e) => filtrar({ sexo: (e.target.value || undefined) as Sexo })}
            >
              <option value="">Sexo</option>
              {(Object.keys(SEXOS) as Sexo[]).map((s) => (
                <option key={s} value={s}>
                  {SEXOS[s]}
                </option>
              ))}
            </SelectField>
            <SelectField
              aria-label="Lote"
              value={filtros.lote ?? ''}
              onChange={(e) => filtrar({ lote: e.target.value || undefined })}
            >
              <option value="">Lote</option>
              <option value="SIN">Sin lote</option>
              {lotes?.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.nombre}
                </option>
              ))}
            </SelectField>
            <SelectField
              aria-label="Rodeo"
              value={filtros.rodeo ?? ''}
              onChange={(e) => filtrar({ rodeo: e.target.value || undefined })}
            >
              <option value="">Rodeo</option>
              <option value="SIN">Sin rodeo</option>
              {rodeos?.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.nombre}
                </option>
              ))}
            </SelectField>
            {hayFiltros && (
              <Button variant="secondary" onClick={limpiarFiltros} className="whitespace-nowrap">
                <X className="size-4" />
                Limpiar
              </Button>
            )}
          </div>

          {/* Acciones sobre la selección */}
          {puedeGestionar && seleccion.size > 0 && (
            <div className="border-brand-200 bg-brand-50 dark:border-brand-900 dark:bg-brand-900/30 sticky top-16 z-10 mb-3 flex flex-wrap items-center gap-2 rounded-xl border px-4 py-2.5">
              <span className="mr-auto text-sm font-medium">
                {seleccion.size} {seleccion.size === 1 ? 'seleccionado' : 'seleccionados'}
              </span>
              <Button
                variant="secondary"
                className="px-3 py-1.5"
                onClick={() => setAsignando('LOTE')}
              >
                <Fence className="size-4" />
                Asignar lote
              </Button>
              <Button
                variant="secondary"
                className="px-3 py-1.5"
                onClick={() => setAsignando('RODEO')}
              >
                <Layers className="size-4" />
                Asignar rodeo
              </Button>
              <button
                type="button"
                onClick={() => setSeleccion(new Set())}
                className="rounded-md p-1.5 text-stone-500 hover:bg-white/60 dark:hover:bg-stone-800"
                aria-label="Quitar selección"
              >
                <X className="size-4" />
              </button>
            </div>
          )}

          {error && <FormError message={getErrorMessage(error)} />}
          {isPending && <Cargando />}

          {data && animales.length === 0 && (
            <EmptyState
              icono={SearchX}
              titulo="Ningún animal coincide con la búsqueda"
              accion={
                <Button variant="secondary" onClick={limpiarFiltros}>
                  Limpiar filtros
                </Button>
              }
            />
          )}

          {animales.length > 0 && (
            <div
              className={`overflow-hidden rounded-xl border border-stone-200 bg-white transition-opacity dark:border-stone-800 dark:bg-stone-900 ${
                isFetching ? 'opacity-70' : ''
              }`}
            >
              {/* Tabla en pantallas medianas y grandes */}
              <table className="hidden w-full text-sm md:table">
                <thead className="border-b border-stone-200 bg-stone-50 text-left text-xs font-medium tracking-wide text-stone-500 uppercase dark:border-stone-800 dark:bg-stone-900">
                  <tr>
                    {puedeGestionar && (
                      <th className="w-10 px-4 py-3">
                        <input
                          type="checkbox"
                          aria-label="Seleccionar todos"
                          checked={todosSeleccionados}
                          onChange={alternarTodos}
                          className="accent-brand-600 size-4"
                        />
                      </th>
                    )}
                    <th className="px-4 py-3">Caravana</th>
                    <th className="px-4 py-3">Categoría</th>
                    <th className="px-4 py-3">Edad</th>
                    <th className="px-4 py-3">Raza</th>
                    <th className="px-4 py-3">Lote</th>
                    <th className="px-4 py-3">Rodeo</th>
                    {puedeGestionar && <th className="w-24 px-4 py-3" />}
                  </tr>
                </thead>
                <tbody className="divide-y divide-stone-100 dark:divide-stone-800">
                  {animales.map((a) => (
                    <tr
                      key={a.id}
                      className={seleccion.has(a.id) ? 'bg-brand-50/60 dark:bg-brand-900/20' : ''}
                    >
                      {puedeGestionar && (
                        <td className="px-4 py-3">
                          <input
                            type="checkbox"
                            aria-label={`Seleccionar ${a.caravana}`}
                            checked={seleccion.has(a.id)}
                            onChange={() => alternar(a.id)}
                            className="accent-brand-600 size-4"
                          />
                        </td>
                      )}
                      <td className="px-4 py-3">
                        <span className="font-semibold tabular-nums">{a.caravana}</span>
                        {a.caravanaElectronica && (
                          <span className="block text-xs text-stone-500 tabular-nums">
                            {a.caravanaElectronica}
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3">
                        <CategoriaBadge animal={a} />
                      </td>
                      <td className="px-4 py-3 text-stone-600 dark:text-stone-400">
                        {formatearEdad(a.fechaNacimiento, a.fechaNacimientoEstimada) ?? <Vacio />}
                      </td>
                      <td className="px-4 py-3 text-stone-600 dark:text-stone-400">
                        {a.raza ?? <Vacio />}
                      </td>
                      <td className="px-4 py-3">{a.lote?.nombre ?? <Vacio />}</td>
                      <td className="px-4 py-3">{a.rodeo?.nombre ?? <Vacio />}</td>
                      {puedeGestionar && (
                        <td className="px-4 py-2 text-right">
                          <Acciones
                            animal={a}
                            onEditar={() => setFormulario({ animal: a })}
                            onEliminar={() => setABorrar(a)}
                          />
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>

              {/* Tarjetas en celular */}
              <ul className="divide-y divide-stone-100 md:hidden dark:divide-stone-800">
                {animales.map((a) => (
                  <li key={a.id} className="flex items-start gap-3 px-4 py-3">
                    {puedeGestionar && (
                      <input
                        type="checkbox"
                        aria-label={`Seleccionar ${a.caravana}`}
                        checked={seleccion.has(a.id)}
                        onChange={() => alternar(a.id)}
                        className="accent-brand-600 mt-1 size-4"
                      />
                    )}
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <span className="font-semibold tabular-nums">{a.caravana}</span>
                        <CategoriaBadge animal={a} />
                      </div>
                      <p className="mt-1 truncate text-xs text-stone-500">
                        {[
                          formatearEdad(a.fechaNacimiento, a.fechaNacimientoEstimada),
                          a.raza,
                          a.lote && `Lote ${a.lote.nombre}`,
                          a.rodeo && `Rodeo ${a.rodeo.nombre}`,
                        ]
                          .filter(Boolean)
                          .join(' · ') || 'Sin más datos'}
                      </p>
                    </div>
                    {puedeGestionar && (
                      <Acciones
                        animal={a}
                        onEditar={() => setFormulario({ animal: a })}
                        onEliminar={() => setABorrar(a)}
                      />
                    )}
                  </li>
                ))}
              </ul>

              {data && data.totalPaginas > 1 && (
                <Paginacion
                  pagina={data.pagina}
                  totalPaginas={data.totalPaginas}
                  total={data.total}
                  tamanio={data.tamanio}
                  onPagina={irAPagina}
                />
              )}
            </div>
          )}
        </>
      )}

      <AnimalFormDialog
        establecimientoId={establecimientoId}
        open={formulario !== null}
        animal={formulario?.animal ?? null}
        onClose={() => setFormulario(null)}
      />

      <AsignarDialog
        establecimientoId={establecimientoId}
        campo={asignando}
        animalIds={[...seleccion]}
        onClose={() => setAsignando(null)}
        onHecho={() => {
          setAsignando(null)
          setSeleccion(new Set())
        }}
      />

      <ConfirmDialog
        open={aBorrar !== null}
        title={`Eliminar ${aBorrar?.caravana ?? ''}`}
        description="Usalo solo si el animal se cargó por error. Las ventas y muertes se registran como eventos."
        confirmLabel="Eliminar"
        loading={eliminar.isPending}
        error={eliminar.error ? getErrorMessage(eliminar.error) : null}
        onClose={cerrarBorrado}
        onConfirm={() => aBorrar && eliminar.mutate(aBorrar.id, { onSuccess: cerrarBorrado })}
      />
    </>
  )
}

function Resumen({
  resumen,
  categoriaActiva,
  onCategoria,
}: {
  resumen: {
    total: number
    hembras: number
    machos: number
    porCategoria: Record<Categoria, number>
  }
  categoriaActiva?: Categoria
  onCategoria: (c: Categoria) => void
}) {
  const conAnimales = LISTA_CATEGORIAS.filter((c) => resumen.porCategoria[c] > 0)
  return (
    <section className="mb-5 flex flex-wrap items-stretch gap-3">
      <div className="rounded-xl border border-stone-200 bg-white px-4 py-3 dark:border-stone-800 dark:bg-stone-900">
        <p className="text-xs font-medium tracking-wide text-stone-500 uppercase">Stock</p>
        <p className="mt-0.5 text-2xl font-semibold tabular-nums">
          {resumen.total} <span className="text-sm font-normal text-stone-500">cabezas</span>
        </p>
        <p className="text-xs text-stone-500 tabular-nums">
          {resumen.hembras} {resumen.hembras === 1 ? 'hembra' : 'hembras'} · {resumen.machos}{' '}
          {resumen.machos === 1 ? 'macho' : 'machos'}
        </p>
      </div>
      {conAnimales.length > 0 && (
        <div className="flex flex-1 flex-wrap content-center gap-2">
          {conAnimales.map((c) => (
            <button
              key={c}
              type="button"
              onClick={() => onCategoria(c)}
              aria-pressed={categoriaActiva === c}
              className={`rounded-full border px-3 py-1 text-sm transition ${
                categoriaActiva === c
                  ? 'border-brand-500 bg-brand-50 text-brand-700 dark:bg-brand-900/40 dark:text-brand-200'
                  : 'border-stone-200 bg-white text-stone-700 hover:bg-stone-50 dark:border-stone-700 dark:bg-stone-900 dark:text-stone-300 dark:hover:bg-stone-800'
              }`}
            >
              {CATEGORIAS[c].plural}{' '}
              <span className="font-semibold tabular-nums">{resumen.porCategoria[c]}</span>
            </button>
          ))}
        </div>
      )}
    </section>
  )
}

function CategoriaBadge({ animal }: { animal: Animal }) {
  const hembra = animal.sexo === 'HEMBRA'
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${
        hembra
          ? 'bg-rose-50 text-rose-700 dark:bg-rose-950/50 dark:text-rose-300'
          : 'bg-sky-50 text-sky-700 dark:bg-sky-950/50 dark:text-sky-300'
      }`}
    >
      {CATEGORIAS[animal.categoria].etiqueta}
      {animal.categoria === 'TERNERO' && animal.castrado && ' (castrado)'}
    </span>
  )
}

function Acciones({
  animal,
  onEditar,
  onEliminar,
}: {
  animal: Animal
  onEditar: () => void
  onEliminar: () => void
}) {
  return (
    <div className="flex shrink-0 justify-end gap-1">
      <button
        type="button"
        onClick={onEditar}
        aria-label={`Editar ${animal.caravana}`}
        title="Editar"
        className="rounded-md p-2 text-stone-500 hover:bg-stone-100 hover:text-stone-700 dark:hover:bg-stone-800 dark:hover:text-stone-200"
      >
        <Pencil className="size-4" />
      </button>
      <button
        type="button"
        onClick={onEliminar}
        aria-label={`Eliminar ${animal.caravana}`}
        title="Eliminar"
        className="rounded-md p-2 text-stone-500 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-950/50"
      >
        <Trash2 className="size-4" />
      </button>
    </div>
  )
}

function Paginacion({
  pagina,
  totalPaginas,
  total,
  tamanio,
  onPagina,
}: {
  pagina: number
  totalPaginas: number
  total: number
  tamanio: number
  onPagina: (p: number) => void
}) {
  const desde = pagina * tamanio + 1
  const hasta = Math.min(total, (pagina + 1) * tamanio)
  return (
    <div className="flex items-center justify-between gap-4 border-t border-stone-200 px-4 py-3 text-sm dark:border-stone-800">
      <span className="text-stone-500 tabular-nums">
        {desde}–{hasta} de {total}
      </span>
      <div className="flex gap-1">
        <Button
          variant="secondary"
          className="px-2.5 py-1.5"
          disabled={pagina === 0}
          onClick={() => onPagina(pagina - 1)}
          aria-label="Página anterior"
        >
          <ChevronLeft className="size-4" />
        </Button>
        <Button
          variant="secondary"
          className="px-2.5 py-1.5"
          disabled={pagina >= totalPaginas - 1}
          onClick={() => onPagina(pagina + 1)}
          aria-label="Página siguiente"
        >
          <ChevronRight className="size-4" />
        </Button>
      </div>
    </div>
  )
}

function Vacio() {
  return <span className="text-stone-300 dark:text-stone-600">—</span>
}

function Cargando() {
  return (
    <div className="grid place-items-center py-16">
      <LoaderCircle className="size-6 animate-spin text-stone-400" />
    </div>
  )
}
