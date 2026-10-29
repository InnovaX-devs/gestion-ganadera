import { useState } from 'react'
import { Link } from 'react-router'
import { Baby, LoaderCircle, MapPinned, Plus, Trash2 } from 'lucide-react'
import { Button } from '@/components/Button'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { FormError } from '@/components/FormError'
import { PageHeader } from '@/components/PageHeader'
import { Paginacion } from '@/components/Paginacion'
import { useEstablecimientoActivo } from '@/features/establecimientos/useEstablecimientoActivo'
import { NacimientoDialog } from '@/features/eventos/NacimientoDialog'
import { TIPOS_EVENTO, puedeRegistrarTipo } from '@/features/eventos/config'
import { formatearFecha } from '@/features/eventos/fechas'
import { useAnularEvento, useEventos } from '@/features/eventos/queries'
import type { Evento, NacimientoDatos } from '@/features/eventos/types'
import { getErrorMessage } from '@/lib/api'

const POR_PAGINA = 30
const resumir = TIPOS_EVENTO.NACIMIENTO.resumen!

export function NacimientosPage() {
  const { activo, cargando } = useEstablecimientoActivo()

  if (cargando) return <Cargando />
  if (!activo) {
    return (
      <>
        <PageHeader title="Nacimientos" />
        <EmptyState
          icono={MapPinned}
          titulo="Primero creá un establecimiento"
          texto="Los nacimientos se registran en un establecimiento."
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
      puedeRegistrar={puedeRegistrarTipo(activo.rol, 'NACIMIENTO')}
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
  const [creando, setCreando] = useState(false)
  const [aAnular, setAAnular] = useState<Evento<NacimientoDatos> | null>(null)
  const { data, isPending, error } = useEventos<NacimientoDatos>(establecimientoId, {
    tipo: 'NACIMIENTO',
    pagina,
    tamanio: POR_PAGINA,
  })
  const anular = useAnularEvento(establecimientoId)

  const cerrarAnulado = () => {
    setAAnular(null)
    anular.reset()
  }

  const nacimientos = data?.contenido ?? []
  const nuevo = puedeRegistrar && (
    <Button onClick={() => setCreando(true)}>
      <Plus className="size-4" />
      Nuevo nacimiento
    </Button>
  )

  return (
    <>
      <PageHeader
        title="Nacimientos"
        description={`Partos registrados en ${establecimientoNombre}. Cada cría viva queda cargada como animal.`}
        actions={nuevo}
      />

      {error && <FormError message={getErrorMessage(error)} />}
      {isPending && <Cargando />}

      {data && nacimientos.length === 0 && (
        <EmptyState
          icono={Baby}
          titulo="Todavía no hay nacimientos"
          texto="Al registrar un parto se crean las crías vinculadas a su madre, y si la madre era vaquillona pasa a vaca."
          accion={nuevo}
        />
      )}

      {nacimientos.length > 0 && data && (
        <div className="overflow-hidden rounded-xl border border-stone-200 bg-white dark:border-stone-800 dark:bg-stone-900">
          <ul className="divide-y divide-stone-200 dark:divide-stone-800">
            {nacimientos.map((n) => (
              <li key={n.id} className="flex gap-3 px-4 py-4">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
                    <span className="font-medium tabular-nums">{formatearFecha(n.fecha)}</span>
                    {n.animal && (
                      <span className="text-stone-500">
                        Madre{' '}
                        <Link
                          to={`/animales/${n.animal.id}`}
                          className="text-brand-700 dark:text-brand-300 font-medium tabular-nums hover:underline"
                        >
                          {n.animal.nombre}
                        </Link>
                      </span>
                    )}
                  </div>
                  <p className="mt-1.5 text-sm whitespace-pre-line text-stone-700 dark:text-stone-300">
                    {resumir(n.datos as unknown as Record<string, unknown>)}
                  </p>
                  <p className="mt-1.5 text-xs text-stone-500">{n.registradoPor.nombre}</p>
                </div>
                {puedeRegistrar && (
                  <button
                    type="button"
                    onClick={() => setAAnular(n)}
                    aria-label="Anular nacimiento"
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

      <NacimientoDialog
        establecimientoId={establecimientoId}
        open={creando}
        onClose={() => setCreando(false)}
      />

      <ConfirmDialog
        open={aAnular !== null}
        title="Anular nacimiento"
        description="Se borran las crías que creó (si todavía no tienen eventos propios) y, si la madre no tiene otro parto, vuelve a su categoría anterior."
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
