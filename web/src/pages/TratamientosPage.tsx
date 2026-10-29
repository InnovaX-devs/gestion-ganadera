import { useState } from 'react'
import { Link } from 'react-router'
import { LoaderCircle, MapPinned, PackageOpen, Plus, Syringe, Trash2, X } from 'lucide-react'
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
import { CatalogoDialog } from '@/features/sanidad/CatalogoDialog'
import { TratamientoDialog } from '@/features/sanidad/TratamientoDialog'
import { carenciaDeTratamiento } from '@/features/sanidad/carencia'
import { TIPOS_PRODUCTO, VIAS, formatearDiasCarencia } from '@/features/sanidad/config'
import type { TratamientoDatos } from '@/features/sanidad/types'
import { getErrorMessage } from '@/lib/api'

const POR_PAGINA = 30

export function TratamientosPage() {
  const { activo, cargando } = useEstablecimientoActivo()

  if (cargando) return <Cargando />
  if (!activo) {
    return (
      <>
        <PageHeader title="Tratamientos" />
        <EmptyState
          icono={MapPinned}
          titulo="Primero creá un establecimiento"
          texto="Los tratamientos se registran en un establecimiento."
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
      puedeRegistrar={puede(activo.rol, 'REGISTRAR_SANIDAD')}
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
  const [catalogo, setCatalogo] = useState(false)
  const [aAnular, setAAnular] = useState<Evento<TratamientoDatos> | null>(null)
  const { data, isPending, error } = useEventos<TratamientoDatos>(establecimientoId, {
    tipo: 'TRATAMIENTO',
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

  const tratamientos = data?.contenido ?? []
  const hayFiltros = !!desde || !!hasta

  return (
    <>
      <PageHeader
        title="Tratamientos"
        description={`Vacunas, antiparasitarios y antibióticos aplicados en ${establecimientoNombre}.`}
        actions={
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" onClick={() => setCatalogo(true)}>
              <PackageOpen className="size-4" />
              Productos
            </Button>
            {puedeRegistrar && (
              <Button onClick={() => setCreando(true)}>
                <Plus className="size-4" />
                Nuevo tratamiento
              </Button>
            )}
          </div>
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

      {data && tratamientos.length === 0 && (
        <EmptyState
          icono={Syringe}
          titulo={hayFiltros ? 'No hay tratamientos en esas fechas' : 'Todavía no hay tratamientos'}
          texto="Registrá una vacuna o un antiparasitario, de animales puntuales o de un lote o rodeo entero."
          accion={
            puedeRegistrar &&
            !hayFiltros && (
              <Button onClick={() => setCreando(true)}>
                <Plus className="size-4" />
                Nuevo tratamiento
              </Button>
            )
          }
        />
      )}

      {tratamientos.length > 0 && data && (
        <div className="overflow-hidden rounded-xl border border-stone-200 bg-white dark:border-stone-800 dark:bg-stone-900">
          <ul className="divide-y divide-stone-200 dark:divide-stone-800">
            {tratamientos.map((t) => (
              <Fila
                key={t.id}
                tratamiento={t}
                onAnular={puedeRegistrar ? () => setAAnular(t) : undefined}
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

      <TratamientoDialog
        establecimientoId={establecimientoId}
        open={creando}
        onClose={() => setCreando(false)}
      />
      <CatalogoDialog
        establecimientoId={establecimientoId}
        open={catalogo}
        puedeEditar={puedeRegistrar}
        onClose={() => setCatalogo(false)}
      />

      <ConfirmDialog
        open={aAnular !== null}
        title="Anular tratamiento"
        description={
          aAnular?.animal
            ? `Deja de aparecer en el historial de ${aAnular.animal.nombre} y se levanta la carencia que haya generado.`
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
  tratamiento: t,
  onAnular,
}: {
  tratamiento: Evento<TratamientoDatos>
  onAnular?: () => void
}) {
  const { datos } = t
  const tipo = datos.tipo ? TIPOS_PRODUCTO[datos.tipo] : null
  const carencia = carenciaDeTratamiento(t.fecha, datos.diasCarencia)
  return (
    <li className="flex gap-3 px-4 py-4">
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
          <span className="font-medium tabular-nums">{formatearFecha(t.fecha)}</span>
          {t.animal && (
            <Link
              to={`/animales/${t.animal.id}`}
              className="text-brand-700 dark:text-brand-300 font-semibold tabular-nums hover:underline"
            >
              {t.animal.nombre}
            </Link>
          )}
        </div>
        <p className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-stone-700 dark:text-stone-300">
          <span className="font-medium">{datos.producto ?? 'Producto'}</span>
          {tipo && (
            <span className="inline-flex items-center gap-1 rounded-full bg-stone-100 px-2 py-0.5 text-xs text-stone-600 dark:bg-stone-800 dark:text-stone-300">
              <tipo.icono className="size-3.5" />
              {tipo.etiqueta}
            </span>
          )}
          <span className="text-stone-500">
            {[datos.dosis, VIAS[datos.via]].filter(Boolean).join(' · ')}
          </span>
        </p>
        <p className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-stone-500">
          {datos.veterinario && <span>Vet. {datos.veterinario}</span>}
          <span>{formatearDiasCarencia(datos.diasCarencia ?? 0)}</span>
          {carencia.enCarencia && (
            <span className="rounded-full bg-amber-100 px-2 py-0.5 font-medium text-amber-800 dark:bg-amber-950/60 dark:text-amber-300">
              En carencia · libre el {formatearFecha(carencia.libreDesde)}
            </span>
          )}
          <span>{t.registradoPor.nombre}</span>
        </p>
      </div>
      {onAnular && (
        <button
          type="button"
          onClick={onAnular}
          aria-label="Anular tratamiento"
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
