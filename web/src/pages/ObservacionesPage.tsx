import { useState } from 'react'
import { Link } from 'react-router'
import {
  ClipboardList,
  Fence,
  LoaderCircle,
  MapPinned,
  PawPrint,
  Plus,
  Trash2,
  Warehouse,
} from 'lucide-react'
import { Button } from '@/components/Button'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { FormError } from '@/components/FormError'
import { PageHeader } from '@/components/PageHeader'
import { Paginacion } from '@/components/Paginacion'
import { SelectField } from '@/components/SelectField'
import { useAgrupaciones } from '@/features/agrupaciones/queries'
import { puede } from '@/features/establecimientos/permisos'
import { useEstablecimientoActivo } from '@/features/establecimientos/useEstablecimientoActivo'
import { ObservacionDialog } from '@/features/eventos/ObservacionDialog'
import { formatearFecha } from '@/features/eventos/fechas'
import { useAnularEvento, useEventos } from '@/features/eventos/queries'
import type { Evento, ObservacionDatos } from '@/features/eventos/types'
import { getErrorMessage } from '@/lib/api'

const POR_PAGINA = 30

export function ObservacionesPage() {
  const { activo, cargando } = useEstablecimientoActivo()

  if (cargando) return <Cargando />
  if (!activo) {
    return (
      <>
        <PageHeader title="Observaciones" />
        <EmptyState
          icono={MapPinned}
          titulo="Primero creá un establecimiento"
          texto="Las observaciones se registran en un establecimiento."
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
      puedeRegistrar={
        puede(activo.rol, 'GESTIONAR_ANIMALES') || puede(activo.rol, 'REGISTRAR_SANIDAD')
      }
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
  const [loteId, setLoteId] = useState('')
  const [creando, setCreando] = useState(false)
  const [aAnular, setAAnular] = useState<Evento<ObservacionDatos> | null>(null)
  const { data: lotes } = useAgrupaciones('lotes', establecimientoId)
  const { data, isPending, error } = useEventos<ObservacionDatos>(establecimientoId, {
    tipo: 'OBSERVACION',
    loteId: loteId || undefined,
    pagina,
    tamanio: POR_PAGINA,
  })
  const anular = useAnularEvento(establecimientoId)

  const cerrarAnulado = () => {
    setAAnular(null)
    anular.reset()
  }

  const observaciones = data?.contenido ?? []

  return (
    <>
      <PageHeader
        title="Observaciones"
        description={`Notas del día a día en ${establecimientoNombre}: del campo, de un lote o de animales puntuales.`}
        actions={
          puedeRegistrar && (
            <Button onClick={() => setCreando(true)}>
              <Plus className="size-4" />
              Nueva observación
            </Button>
          )
        }
      />

      {(lotes?.length ?? 0) > 0 && (
        <div className="mb-4 max-w-xs">
          <SelectField
            label="Lote"
            value={loteId}
            onChange={(e) => {
              setLoteId(e.target.value)
              setPagina(0)
            }}
          >
            <option value="">Todas</option>
            {lotes?.map((l) => (
              <option key={l.id} value={l.id}>
                {l.nombre}
              </option>
            ))}
          </SelectField>
        </div>
      )}

      {error && <FormError message={getErrorMessage(error)} />}
      {isPending && <Cargando />}

      {data && observaciones.length === 0 && (
        <EmptyState
          icono={ClipboardList}
          titulo={loteId ? 'Este lote no tiene observaciones' : 'Todavía no hay observaciones'}
          texto="Anotá lo que ves en la recorrida: una aguada rota, un animal rengo, el estado de una pastura."
          accion={
            puedeRegistrar &&
            !loteId && (
              <Button onClick={() => setCreando(true)}>
                <Plus className="size-4" />
                Nueva observación
              </Button>
            )
          }
        />
      )}

      {observaciones.length > 0 && data && (
        <div className="overflow-hidden rounded-xl border border-stone-200 bg-white dark:border-stone-800 dark:bg-stone-900">
          <ul className="divide-y divide-stone-200 dark:divide-stone-800">
            {observaciones.map((o) => (
              <li key={o.id} className="flex gap-3 px-4 py-4">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
                    <span className="font-medium tabular-nums">{formatearFecha(o.fecha)}</span>
                    <Destino evento={o} />
                  </div>
                  <p className="mt-1.5 text-sm whitespace-pre-line text-stone-700 dark:text-stone-300">
                    {o.datos.texto}
                  </p>
                  <p className="mt-1.5 text-xs text-stone-500">{o.registradoPor.nombre}</p>
                </div>
                {puedeRegistrar && (
                  <button
                    type="button"
                    onClick={() => setAAnular(o)}
                    aria-label="Anular observación"
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

      <ObservacionDialog
        establecimientoId={establecimientoId}
        open={creando}
        onClose={() => setCreando(false)}
      />

      <ConfirmDialog
        open={aAnular !== null}
        title="Anular observación"
        description="Deja de aparecer en el historial."
        confirmLabel="Anular"
        loading={anular.isPending}
        error={anular.error ? getErrorMessage(anular.error) : null}
        onClose={cerrarAnulado}
        onConfirm={() => aAnular && anular.mutate(aAnular.id, { onSuccess: cerrarAnulado })}
      />
    </>
  )
}

/** De qué es la observación: un animal, un lote o el establecimiento entero. */
function Destino({ evento }: { evento: Pick<Evento, 'animal' | 'lote'> }) {
  const [Icono, texto] = evento.animal
    ? [PawPrint, evento.animal.nombre]
    : evento.lote
      ? [Fence, `Lote ${evento.lote.nombre}`]
      : [Warehouse, 'Establecimiento']
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-stone-100 px-2 py-0.5 text-xs text-stone-600 dark:bg-stone-800 dark:text-stone-300">
      <Icono className="size-3.5" />
      {texto}
    </span>
  )
}

function Cargando() {
  return (
    <div className="grid place-items-center py-16 text-stone-400">
      <LoaderCircle className="size-6 animate-spin" />
    </div>
  )
}
