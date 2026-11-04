import { type ReactNode, useState } from 'react'
import { Link } from 'react-router'
import { LoaderCircle, MapPinned, Plus, Trash2, X } from 'lucide-react'
import { Button } from '@/components/Button'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { FormError } from '@/components/FormError'
import { PageHeader } from '@/components/PageHeader'
import { Paginacion } from '@/components/Paginacion'
import { TextField } from '@/components/TextField'
import { useEstablecimientoActivo } from '@/features/establecimientos/useEstablecimientoActivo'
import { TIPOS_EVENTO, puedeRegistrarTipo } from '@/features/eventos/config'
import { formatearFecha } from '@/features/eventos/fechas'
import { useAnularEvento, useEventos } from '@/features/eventos/queries'
import type { Evento } from '@/features/eventos/types'
import { getErrorMessage } from '@/lib/api'

const POR_PAGINA = 30

interface Props<D> {
  tipo: 'SERVICIO' | 'TACTO'
  titulo: string
  descripcion: (establecimiento: string) => string
  vacio: { titulo: string; texto: string }
  botonNuevo: string
  /** Qué se dice al anular, además de sacarlo del historial. */
  alAnular: string
  /** Lo propio de cada fila, debajo de la fecha y la caravana. */
  detalle: (evento: Evento<D>) => ReactNode
  dialogo: (props: { establecimientoId: string; open: boolean; onClose: () => void }) => ReactNode
}

/** Página de servicios o de tactos (#19): filtro por fechas, listado, alta y anulación. */
export function ListadoReproductivo<D>(props: Props<D>) {
  const { activo, cargando } = useEstablecimientoActivo()

  if (cargando) return <Cargando />
  if (!activo) {
    return (
      <>
        <PageHeader title={props.titulo} />
        <EmptyState
          icono={MapPinned}
          titulo="Primero creá un establecimiento"
          texto={`Los ${props.titulo.toLowerCase()} se registran en un establecimiento.`}
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
      {...props}
      establecimientoId={activo.id}
      establecimientoNombre={activo.nombre}
      puedeRegistrar={puedeRegistrarTipo(activo.rol, props.tipo)}
    />
  )
}

function Listado<D>({
  tipo,
  titulo,
  descripcion,
  vacio,
  botonNuevo,
  alAnular,
  detalle,
  dialogo,
  establecimientoId,
  establecimientoNombre,
  puedeRegistrar,
}: Props<D> & {
  establecimientoId: string
  establecimientoNombre: string
  puedeRegistrar: boolean
}) {
  const [pagina, setPagina] = useState(0)
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [creando, setCreando] = useState(false)
  const [aAnular, setAAnular] = useState<Evento<D> | null>(null)
  const { data, isPending, error } = useEventos<D>(establecimientoId, {
    tipo,
    desde: desde || undefined,
    hasta: hasta || undefined,
    pagina,
    tamanio: POR_PAGINA,
  })
  const anular = useAnularEvento(establecimientoId)
  const cerrarAnulado = () => {
    setAAnular(null)
    anular.reset()
  }

  const eventos = data?.contenido ?? []
  const hayFiltros = !!desde || !!hasta
  const etiqueta = TIPOS_EVENTO[tipo].etiqueta.toLowerCase()
  const Icono = TIPOS_EVENTO[tipo].icono
  const nuevo = (
    <Button onClick={() => setCreando(true)}>
      <Plus className="size-4" />
      {botonNuevo}
    </Button>
  )

  return (
    <>
      <PageHeader
        title={titulo}
        description={descripcion(establecimientoNombre)}
        actions={puedeRegistrar && nuevo}
      />

      <div className="mb-4 flex flex-wrap items-end gap-2">
        <TextField
          label="Desde"
          type="date"
          value={desde}
          max={hasta || undefined}
          onChange={(e) => {
            setDesde(e.target.value)
            setPagina(0)
          }}
        />
        <TextField
          label="Hasta"
          type="date"
          value={hasta}
          min={desde || undefined}
          onChange={(e) => {
            setHasta(e.target.value)
            setPagina(0)
          }}
        />
        {hayFiltros && (
          <Button
            variant="secondary"
            onClick={() => {
              setDesde('')
              setHasta('')
              setPagina(0)
            }}
          >
            <X className="size-4" />
            Limpiar
          </Button>
        )}
      </div>

      {error && <FormError message={getErrorMessage(error)} />}
      {isPending && <Cargando />}

      {data && eventos.length === 0 && (
        <EmptyState
          icono={Icono}
          titulo={hayFiltros ? `No hay ${titulo.toLowerCase()} en esas fechas` : vacio.titulo}
          texto={vacio.texto}
          accion={puedeRegistrar && !hayFiltros && nuevo}
        />
      )}

      {data && eventos.length > 0 && (
        <div className="overflow-hidden rounded-xl border border-stone-200 bg-white dark:border-stone-800 dark:bg-stone-900">
          <ul className="divide-y divide-stone-200 dark:divide-stone-800">
            {eventos.map((e) => (
              <li key={e.id} className="flex gap-3 px-4 py-4">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
                    <span className="font-medium tabular-nums">{formatearFecha(e.fecha)}</span>
                    {e.animal && (
                      <Link
                        to={`/animales/${e.animal.id}`}
                        className="text-brand-700 dark:text-brand-300 font-semibold tabular-nums hover:underline"
                      >
                        {e.animal.nombre}
                      </Link>
                    )}
                  </div>
                  <div className="mt-1.5 text-sm text-stone-700 dark:text-stone-300">
                    {detalle(e)}
                  </div>
                  <p className="mt-1.5 text-xs text-stone-500">{e.registradoPor.nombre}</p>
                </div>
                {puedeRegistrar && (
                  <button
                    type="button"
                    onClick={() => setAAnular(e)}
                    aria-label={`Anular ${etiqueta}`}
                    title="Anular"
                    className="self-start rounded-md p-2 text-stone-500 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-950/50"
                  >
                    <Trash2 className="size-4" />
                  </button>
                )}
              </li>
            ))}
          </ul>
          {data.totalPaginas > 1 && (
            <Paginacion
              pagina={data.pagina}
              totalPaginas={data.totalPaginas}
              total={data.total}
              tamanio={data.tamanio}
              onPagina={setPagina}
            />
          )}
        </div>
      )}

      {dialogo({ establecimientoId, open: creando, onClose: () => setCreando(false) })}

      <ConfirmDialog
        open={aAnular !== null}
        title={`Anular ${etiqueta}`}
        description={
          aAnular?.animal
            ? `Deja de aparecer en el historial de ${aAnular.animal.nombre} ${alAnular}`
            : 'Deja de aparecer en el historial.'
        }
        confirmLabel="Anular"
        loading={anular.isPending}
        error={anular.error ? getErrorMessage(anular.error) : null}
        onClose={cerrarAnulado}
        onConfirm={() => aAnular && anular.mutate(aAnular.id, { onSuccess: cerrarAnulado })}
      />
    </>
  )
}

function Cargando() {
  return (
    <div className="grid place-items-center py-16 text-stone-400">
      <LoaderCircle className="size-6 animate-spin" />
    </div>
  )
}
