import { useState } from 'react'
import { Link } from 'react-router'
import { CircleSlash, LoaderCircle, MapPinned, Plus, Tag, Trash2, X } from 'lucide-react'
import { Button } from '@/components/Button'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { FormError } from '@/components/FormError'
import { PageHeader } from '@/components/PageHeader'
import { Paginacion } from '@/components/Paginacion'
import { TextField } from '@/components/TextField'
import { puede } from '@/features/establecimientos/permisos'
import { useEstablecimientoActivo } from '@/features/establecimientos/useEstablecimientoActivo'
import { TIPOS_EVENTO } from '@/features/eventos/config'
import { formatearFecha } from '@/features/eventos/fechas'
import { useAnularEvento, useEventos } from '@/features/eventos/queries'
import type { Evento } from '@/features/eventos/types'
import { BajaDialog } from '@/features/ventas/BajaDialog'
import { VentaDialog } from '@/features/ventas/VentaDialog'
import { resumenBaja, resumenVenta } from '@/features/ventas/config'
import type { BajaDatos, VentaDatos } from '@/features/ventas/types'
import { getErrorMessage } from '@/lib/api'

const POR_PAGINA = 30

type Solapa = 'VENTA' | 'BAJA'

export function VentasPage() {
  const { activo, cargando } = useEstablecimientoActivo()

  if (cargando) return <Cargando />
  if (!activo) {
    return (
      <>
        <PageHeader title="Ventas y bajas" />
        <EmptyState
          icono={MapPinned}
          titulo="Primero creá un establecimiento"
          texto="Las ventas y bajas se registran en un establecimiento."
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
  const [solapa, setSolapa] = useState<Solapa>('VENTA')
  const [pagina, setPagina] = useState(0)
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [creando, setCreando] = useState(false)
  const [aAnular, setAAnular] = useState<Evento<VentaDatos & BajaDatos> | null>(null)
  const { data, isPending, error } = useEventos<VentaDatos & BajaDatos>(establecimientoId, {
    tipo: solapa,
    desde: desde || undefined,
    hasta: hasta || undefined,
    pagina,
    tamanio: POR_PAGINA,
  })
  const anular = useAnularEvento(establecimientoId)

  const esVenta = solapa === 'VENTA'
  const etiqueta = esVenta ? 'venta' : 'baja'
  const eventos = data?.contenido ?? []
  const hayFiltros = !!desde || !!hasta

  const cerrarAnulado = () => {
    setAAnular(null)
    anular.reset()
  }

  return (
    <>
      <PageHeader
        title="Ventas y bajas"
        description={`Animales de ${establecimientoNombre} que salen del stock: vendidos, muertos o perdidos. Si algo se cargó por error, anulalo y el animal vuelve al stock.`}
        actions={
          puedeRegistrar && (
            <Button onClick={() => setCreando(true)}>
              <Plus className="size-4" />
              {esVenta ? 'Nueva venta' : 'Nueva baja'}
            </Button>
          )
        }
      />

      <div
        role="tablist"
        className="mb-4 flex gap-1 border-b border-stone-200 dark:border-stone-800"
      >
        {(['VENTA', 'BAJA'] as const).map((s) => (
          <button
            key={s}
            type="button"
            role="tab"
            aria-selected={solapa === s}
            onClick={() => {
              setSolapa(s)
              setPagina(0)
            }}
            className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium ${
              solapa === s
                ? 'border-brand-600 text-brand-700 dark:text-brand-300'
                : 'border-transparent text-stone-500 hover:text-stone-800 dark:hover:text-stone-200'
            }`}
          >
            {s === 'VENTA' ? 'Ventas' : 'Bajas'}
          </button>
        ))}
      </div>

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
          icono={esVenta ? Tag : CircleSlash}
          titulo={
            hayFiltros
              ? `No hay ${esVenta ? 'ventas' : 'bajas'} en esas fechas`
              : `Todavía no hay ${esVenta ? 'ventas' : 'bajas'}`
          }
          texto={
            esVenta
              ? 'Vendé animales sueltos, varios a la vez o un lote entero: comprador, precio, peso y DT-e.'
              : 'Registrá la muerte o pérdida de animales, de a uno o varios.'
          }
          accion={
            puedeRegistrar &&
            !hayFiltros && (
              <Button onClick={() => setCreando(true)}>
                <Plus className="size-4" />
                {esVenta ? 'Nueva venta' : 'Nueva baja'}
              </Button>
            )
          }
        />
      )}

      {eventos.length > 0 && data && (
        <div className="overflow-hidden rounded-xl border border-stone-200 bg-white dark:border-stone-800 dark:bg-stone-900">
          <ul className="divide-y divide-stone-200 dark:divide-stone-800">
            {eventos.map((ev) => (
              <Fila
                key={ev.id}
                evento={ev}
                resumen={esVenta ? resumenVenta(ev.datos) : resumenBaja(ev.datos)}
                etiqueta={etiqueta}
                onAnular={puedeRegistrar ? () => setAAnular(ev) : undefined}
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

      <VentaDialog
        establecimientoId={establecimientoId}
        open={creando && esVenta}
        onClose={() => setCreando(false)}
      />
      <BajaDialog
        establecimientoId={establecimientoId}
        open={creando && !esVenta}
        onClose={() => setCreando(false)}
      />

      <ConfirmDialog
        open={aAnular !== null}
        title={`Anular ${etiqueta}`}
        description={
          aAnular?.animal
            ? `${aAnular.animal.nombre} vuelve al stock y la ${etiqueta} deja de aparecer en su historial.`
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
  evento: ev,
  resumen,
  etiqueta,
  onAnular,
}: {
  evento: Evento<object>
  resumen: string | null
  etiqueta: string
  onAnular?: () => void
}) {
  const Icono = TIPOS_EVENTO[ev.tipo].icono
  return (
    <li className="flex gap-3 px-4 py-4">
      <Icono className="mt-0.5 size-4 shrink-0 text-stone-400" />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
          <span className="font-medium tabular-nums">{formatearFecha(ev.fecha)}</span>
          {ev.animal && (
            <Link
              to={`/animales/${ev.animal.id}`}
              className="text-brand-700 dark:text-brand-300 font-semibold tabular-nums hover:underline"
            >
              {ev.animal.nombre}
            </Link>
          )}
        </div>
        {resumen && <p className="mt-1.5 text-sm text-stone-700 dark:text-stone-300">{resumen}</p>}
        <p className="mt-1.5 text-xs text-stone-500">{ev.registradoPor.nombre}</p>
      </div>
      {onAnular && (
        <button
          type="button"
          onClick={onAnular}
          aria-label={`Anular ${etiqueta}`}
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
