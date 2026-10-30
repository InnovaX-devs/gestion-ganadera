import { useState } from 'react'
import { Link } from 'react-router'
import { ArrowLeftRight, LoaderCircle, MapPinned, Plus, Trash2, X } from 'lucide-react'
import { Button } from '@/components/Button'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { FormError } from '@/components/FormError'
import { PageHeader } from '@/components/PageHeader'
import { Paginacion } from '@/components/Paginacion'
import { TextField } from '@/components/TextField'
import { puede } from '@/features/establecimientos/permisos'
import { useEstablecimientoActivo } from '@/features/establecimientos/useEstablecimientoActivo'
import { formatearFecha } from '@/features/eventos/fechas'
import { useAnularEvento, useEventos } from '@/features/eventos/queries'
import type { Evento } from '@/features/eventos/types'
import { MovimientoDialog } from '@/features/movimientos/MovimientoDialog'
import { cambiosDeMovimiento } from '@/features/movimientos/config'
import type { MovimientoDatos } from '@/features/movimientos/types'
import { getErrorMessage } from '@/lib/api'

const POR_PAGINA = 30

export function MovimientosPage() {
  const { activo, cargando } = useEstablecimientoActivo()

  if (cargando) return <Cargando />
  if (!activo) {
    return (
      <>
        <PageHeader title="Movimientos" />
        <EmptyState
          icono={MapPinned}
          titulo="Primero creá un establecimiento"
          texto="Los movimientos se registran en un establecimiento."
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
      puedeRegistrar={puede(activo.rol, 'GESTIONAR_ANIMALES')}
    />
  )
}

function Listado({
  establecimientoId,
  establecimientoNombre,
  puedeRegistrar,
}: {
  establecimientoId: string
  establecimientoNombre: string
  puedeRegistrar: boolean
}) {
  const [pagina, setPagina] = useState(0)
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [creando, setCreando] = useState(false)
  const [aAnular, setAAnular] = useState<Evento<MovimientoDatos> | null>(null)
  const { data, isPending, error } = useEventos<MovimientoDatos>(establecimientoId, {
    tipo: 'MOVIMIENTO',
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

  const movimientos = data?.contenido ?? []
  const hayFiltros = !!desde || !!hasta

  return (
    <>
      <PageHeader
        title="Movimientos"
        description={`Cambios de lote, rodeo o establecimiento de los animales de ${establecimientoNombre}. Los que llegan desde otro campo se ven en el historial de cada animal.`}
        actions={
          puedeRegistrar && (
            <Button onClick={() => setCreando(true)}>
              <Plus className="size-4" />
              Nuevo movimiento
            </Button>
          )
        }
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

      {data && movimientos.length === 0 && (
        <EmptyState
          icono={ArrowLeftRight}
          titulo={hayFiltros ? 'No hay movimientos en esas fechas' : 'Todavía no hay movimientos'}
          texto="Cambiá animales de lote, de rodeo o de establecimiento: de a uno, varios o un lote entero."
          accion={
            puedeRegistrar &&
            !hayFiltros && (
              <Button onClick={() => setCreando(true)}>
                <Plus className="size-4" />
                Nuevo movimiento
              </Button>
            )
          }
        />
      )}

      {movimientos.length > 0 && data && (
        <div className="overflow-hidden rounded-xl border border-stone-200 bg-white dark:border-stone-800 dark:bg-stone-900">
          <ul className="divide-y divide-stone-200 dark:divide-stone-800">
            {movimientos.map((m) => (
              <Fila
                key={m.id}
                movimiento={m}
                establecimientoId={establecimientoId}
                onAnular={puedeRegistrar ? () => setAAnular(m) : undefined}
              />
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

      <MovimientoDialog
        establecimientoId={establecimientoId}
        open={creando}
        onClose={() => setCreando(false)}
      />

      <ConfirmDialog
        open={aAnular !== null}
        title="Anular movimiento"
        description={
          aAnular?.animal
            ? `${aAnular.animal.nombre} vuelve a donde estaba y el movimiento deja de aparecer en su historial.`
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

function Fila({
  movimiento: m,
  establecimientoId,
  onAnular,
}: {
  movimiento: Evento<MovimientoDatos>
  establecimientoId: string
  onAnular?: () => void
}) {
  const cambios = cambiosDeMovimiento(m.datos)
  // Si se fue a otro campo, su ficha ya no se abre desde este establecimiento
  const sigueAca = m.datos.hacia?.establecimiento.id === establecimientoId
  return (
    <li className="flex gap-3 px-4 py-4">
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
          <span className="font-medium tabular-nums">{formatearFecha(m.fecha)}</span>
          {m.animal &&
            (sigueAca ? (
              <Link
                to={`/animales/${m.animal.id}`}
                className="text-brand-700 dark:text-brand-300 font-semibold tabular-nums hover:underline"
              >
                {m.animal.nombre}
              </Link>
            ) : (
              <span className="font-semibold tabular-nums">{m.animal.nombre}</span>
            ))}
        </div>
        <ul className="mt-1.5 space-y-0.5 text-sm text-stone-700 dark:text-stone-300">
          {cambios.map((c) => (
            <li key={c.campo}>
              <span className="text-stone-500">{c.campo}: </span>
              {c.desde} → <span className="font-medium">{c.hacia}</span>
            </li>
          ))}
        </ul>
        <p className="mt-1.5 text-xs text-stone-500">{m.registradoPor.nombre}</p>
      </div>
      {onAnular && (
        <button
          type="button"
          onClick={onAnular}
          aria-label="Anular movimiento"
          title="Anular"
          className="self-start rounded-md p-2 text-stone-500 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-950/50"
        >
          <Trash2 className="size-4" />
        </button>
      )}
    </li>
  )
}

function Cargando() {
  return (
    <div className="grid place-items-center py-16 text-stone-400">
      <LoaderCircle className="size-6 animate-spin" />
    </div>
  )
}
