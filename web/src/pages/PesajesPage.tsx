import { useState } from 'react'
import { Link } from 'react-router'
import { LoaderCircle, MapPinned, Plus, Scale, Trash2, Users, X } from 'lucide-react'
import { Button } from '@/components/Button'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { FormError } from '@/components/FormError'
import { PageHeader } from '@/components/PageHeader'
import { Paginacion } from '@/components/Paginacion'
import { TextField } from '@/components/TextField'
import { puede } from '@/features/establecimientos/permisos'
import { useEstablecimientoActivo } from '@/features/establecimientos/useEstablecimientoActivo'
import { formatearKg } from '@/features/eventos/config'
import { formatearFecha } from '@/features/eventos/fechas'
import { useAnularEvento } from '@/features/eventos/queries'
import { Ganancia } from '@/features/pesajes/Ganancia'
import { GrillaPesajes } from '@/features/pesajes/GrillaPesajes'
import { PesajeGrupalDialog } from '@/features/pesajes/PesajeGrupalDialog'
import { usePesajes } from '@/features/pesajes/queries'
import type { Pesaje } from '@/features/pesajes/types'
import { getErrorMessage } from '@/lib/api'

const POR_PAGINA = 30

export function PesajesPage() {
  const { activo, cargando } = useEstablecimientoActivo()

  if (cargando) return <Cargando />
  if (!activo) {
    return (
      <>
        <PageHeader title="Pesajes" />
        <EmptyState
          icono={MapPinned}
          titulo="Primero creá un establecimiento"
          texto="Los pesajes se registran en un establecimiento."
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
    <Contenido
      key={activo.id}
      establecimientoId={activo.id}
      establecimientoNombre={activo.nombre}
      puedeRegistrar={puede(activo.rol, 'GESTIONAR_ANIMALES')}
    />
  )
}

function Contenido({
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
  const [grupal, setGrupal] = useState(false)
  const [aAnular, setAAnular] = useState<Pesaje | null>(null)
  const { data, isPending, error } = usePesajes(establecimientoId, {
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

  const pesajes = data?.contenido ?? []
  const hayFiltros = !!desde || !!hasta

  return (
    <>
      <PageHeader
        title="Pesajes"
        description={`Pesajes de ${establecimientoNombre}, con la ganancia diaria respecto al pesaje anterior.`}
        actions={
          puedeRegistrar && (
            <Button variant="secondary" onClick={() => setGrupal(true)}>
              <Users className="size-4" />
              Pesaje grupal
            </Button>
          )
        }
      />

      {puedeRegistrar && (
        <div className="mb-6">
          <GrillaPesajes establecimientoId={establecimientoId} />
        </div>
      )}

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

      {data && pesajes.length === 0 && (
        <EmptyState
          icono={Scale}
          titulo={hayFiltros ? 'No hay pesajes en esas fechas' : 'Todavía no hay pesajes'}
          texto="Cargá caravana y peso en la grilla de arriba, o hacé un pesaje grupal."
        />
      )}

      {pesajes.length > 0 && data && (
        <div className="overflow-hidden rounded-xl border border-stone-200 bg-white dark:border-stone-800 dark:bg-stone-900">
          <ul className="divide-y divide-stone-200 dark:divide-stone-800">
            {pesajes.map((p) => (
              <Fila
                key={p.id}
                pesaje={p}
                onAnular={puedeRegistrar ? () => setAAnular(p) : undefined}
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

      <PesajeGrupalDialog
        establecimientoId={establecimientoId}
        open={grupal}
        onClose={() => setGrupal(false)}
      />

      <ConfirmDialog
        open={aAnular !== null}
        title="Anular pesaje"
        description="Deja de aparecer en el historial y se recalcula la ganancia diaria del pesaje siguiente."
        confirmLabel="Anular"
        loading={anular.isPending}
        error={anular.error ? getErrorMessage(anular.error) : null}
        onClose={cerrarAnulado}
        onConfirm={() => aAnular && anular.mutate(aAnular.id, { onSuccess: cerrarAnulado })}
      />
    </>
  )
}

function Fila({ pesaje: p, onAnular }: { pesaje: Pesaje; onAnular?: () => void }) {
  return (
    <li className="flex gap-3 px-4 py-4">
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
          <span className="font-medium tabular-nums">{formatearFecha(p.fecha)}</span>
          {p.animal && (
            <Link
              to={`/animales/${p.animal.id}`}
              className="text-brand-700 dark:text-brand-300 font-semibold tabular-nums hover:underline"
            >
              {p.animal.nombre}
            </Link>
          )}
          <span className="font-semibold tabular-nums">{formatearKg(p.kg)} kg</span>
          {p.grupal && (
            <span className="rounded-full bg-stone-100 px-2 py-0.5 text-xs text-stone-600 dark:bg-stone-800 dark:text-stone-300">
              Promedio de {p.cantidad} · total{' '}
              {p.pesoTotal != null ? formatearKg(p.pesoTotal) : '?'} kg
            </span>
          )}
        </div>
        <p className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1">
          <Ganancia pesaje={p} />
          <span className="text-xs text-stone-500">{p.registradoPor.nombre}</span>
        </p>
      </div>
      {onAnular && (
        <button
          type="button"
          onClick={onAnular}
          aria-label="Anular pesaje"
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
