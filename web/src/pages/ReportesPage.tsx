import { useState } from 'react'
import { useSearchParams } from 'react-router'
import {
  Baby,
  FileSpreadsheet,
  FileText,
  LoaderCircle,
  Scale,
  ShieldCheck,
  Table,
} from 'lucide-react'
import { PageHeader } from '@/components/PageHeader'
import { SelectField } from '@/components/SelectField'
import { TextField } from '@/components/TextField'
import { useAgrupaciones } from '@/features/agrupaciones/queries'
import { CATEGORIAS, LISTA_CATEGORIAS } from '@/features/animales/config'
import type { Categoria } from '@/features/animales/types'
import { useEstablecimientoActivo } from '@/features/establecimientos/useEstablecimientoActivo'
import { MiniGrafico } from '@/features/mercado/MiniGrafico'
import { reporteExistencias, reportePesos, reporteStock } from '@/features/reportes/armado'
import { exportarExcel, exportarPdf } from '@/features/reportes/exportar'
import { hoyISO, type Reporte } from '@/features/reportes/modelo'
import { useExistencias, useReportePesos } from '@/features/reportes/queries'
import type { FiltroPesos } from '@/features/reportes/types'
import { VistaReporte } from '@/features/reportes/VistaReporte'
import { useStock } from '@/features/stock/queries'
import { getErrorMessage } from '@/lib/api'

type TipoReporte = 'stock' | 'existencias' | 'pesos' | 'reproductivos'

const REPORTES: {
  tipo: TipoReporte
  titulo: string
  descripcion: string
  icono: typeof Table
  disponible: boolean
}[] = [
  {
    tipo: 'stock',
    titulo: 'Stock',
    descripcion: 'Por categoría, lote y rodeo',
    icono: Table,
    disponible: true,
  },
  {
    tipo: 'existencias',
    titulo: 'Existencias',
    descripcion: 'Para vacunación y SENASA',
    icono: ShieldCheck,
    disponible: true,
  },
  {
    tipo: 'pesos',
    titulo: 'Pesos',
    descripcion: 'Evolución y ganancia diaria',
    icono: Scale,
    disponible: true,
  },
  {
    tipo: 'reproductivos',
    titulo: 'Índices reproductivos',
    descripcion: '% de preñez y de destete',
    icono: Baby,
    disponible: false,
  },
]

/** "2026-08-02": 90 días antes de hoy (lo mismo que usa el backend por defecto). */
function hace90Dias() {
  const d = new Date()
  d.setDate(d.getDate() - 90)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** Reportes exportables a Excel y PDF (#32). */
export function ReportesPage() {
  const { activo, establecimientos } = useEstablecimientoActivo()
  const [params, setParams] = useSearchParams()
  const tipo = (REPORTES.find((r) => r.tipo === params.get('reporte'))?.tipo ??
    'stock') as TipoReporte
  // Se puede sacar el reporte de otro establecimiento sin cambiar el activo
  const [establecimientoId, setEstablecimientoId] = useState<string | undefined>()
  const estId = establecimientoId ?? activo?.id
  const establecimiento = establecimientos.find((e) => e.id === estId)

  return (
    <>
      <PageHeader
        title="Reportes"
        description="Elegí un reporte, revisalo y descargalo en Excel o PDF."
      />

      <div className="grid grid-cols-2 gap-2 lg:grid-cols-4" role="tablist" aria-label="Reportes">
        {REPORTES.map((r) => {
          const Icono = r.icono
          const elegido = r.tipo === tipo
          return (
            <button
              key={r.tipo}
              type="button"
              role="tab"
              aria-selected={elegido}
              disabled={!r.disponible}
              onClick={() => setParams({ reporte: r.tipo }, { replace: true })}
              className={`focus-visible:ring-brand-300 flex items-start gap-3 rounded-xl border p-3 text-left outline-none focus-visible:ring-2 disabled:cursor-not-allowed disabled:opacity-60 ${
                elegido
                  ? 'border-brand-400 bg-brand-50 dark:border-brand-700 dark:bg-brand-900/20'
                  : 'border-stone-200 bg-white hover:border-stone-300 dark:border-stone-800 dark:bg-stone-900'
              }`}
            >
              <Icono
                className={`mt-0.5 size-5 shrink-0 ${elegido ? 'text-brand-700 dark:text-brand-200' : 'text-stone-400'}`}
              />
              <span className="min-w-0">
                <span className="block text-sm font-medium">{r.titulo}</span>
                <span className="block text-xs text-stone-500">
                  {r.disponible ? r.descripcion : 'Próximamente'}
                </span>
              </span>
            </button>
          )
        })}
      </div>

      {!estId || !establecimiento ? (
        <p className="mt-6 text-sm text-stone-500">
          Elegí un establecimiento para ver los reportes.
        </p>
      ) : (
        <div className="mt-6">
          {establecimientos.length > 1 && (
            <div className="mb-4 max-w-xs">
              <SelectField
                label="Establecimiento"
                value={estId}
                onChange={(e) => setEstablecimientoId(e.target.value)}
              >
                {establecimientos.map((e) => (
                  <option key={e.id} value={e.id}>
                    {e.nombre}
                  </option>
                ))}
              </SelectField>
            </div>
          )}
          {tipo === 'stock' && (
            <ReporteStock establecimientoId={estId} nombre={establecimiento.nombre} />
          )}
          {tipo === 'existencias' && <ReporteExistencias establecimientoId={estId} />}
          {tipo === 'pesos' && (
            <ReportePesos key={estId} establecimientoId={estId} nombre={establecimiento.nombre} />
          )}
          {tipo === 'reproductivos' && <Reproductivos />}
        </div>
      )}
    </>
  )
}

// ---------- Cada reporte ----------

function ReporteStock({
  establecimientoId,
  nombre,
}: {
  establecimientoId: string
  nombre: string
}) {
  const { data, isPending, error } = useStock(establecimientoId)
  return (
    <Contenido
      isPending={isPending}
      error={error}
      reporte={data ? reporteStock(data, nombre) : undefined}
    />
  )
}

function ReporteExistencias({ establecimientoId }: { establecimientoId: string }) {
  const { data, isPending, error } = useExistencias(establecimientoId, true)
  return (
    <Contenido
      isPending={isPending}
      error={error}
      reporte={data ? reporteExistencias(data) : undefined}
    />
  )
}

function ReportePesos({
  establecimientoId,
  nombre,
}: {
  establecimientoId: string
  nombre: string
}) {
  const [filtro, setFiltro] = useState<FiltroPesos>({ desde: hace90Dias(), hasta: hoyISO() })
  const { data: lotes } = useAgrupaciones('lotes', establecimientoId)
  const fechasValidas = !!filtro.desde && !!filtro.hasta && filtro.desde <= filtro.hasta
  const { data, isPending, isFetching, error } = useReportePesos(
    establecimientoId,
    filtro,
    fechasValidas,
  )

  const cambiar = (parcial: Partial<FiltroPesos>) => setFiltro((f) => ({ ...f, ...parcial }))
  const nombreLote = lotes?.find((l) => l.id === filtro.loteId)?.nombre
  const filtros = [
    nombreLote && `Lote: ${nombreLote}`,
    filtro.categoria && `Categoría: ${CATEGORIAS[filtro.categoria].plural}`,
  ].filter(Boolean) as string[]
  const reporte = data ? reportePesos(data, nombre, filtros) : undefined

  return (
    <>
      <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <TextField
          label="Desde"
          type="date"
          value={filtro.desde}
          max={filtro.hasta}
          onChange={(e) => cambiar({ desde: e.target.value })}
        />
        <TextField
          label="Hasta"
          type="date"
          value={filtro.hasta}
          min={filtro.desde}
          max={hoyISO()}
          onChange={(e) => cambiar({ hasta: e.target.value })}
        />
        <SelectField
          label="Lote"
          value={filtro.loteId ?? ''}
          onChange={(e) => cambiar({ loteId: e.target.value || undefined })}
        >
          <option value="">Todos</option>
          {lotes?.map((l) => (
            <option key={l.id} value={l.id}>
              {l.nombre}
            </option>
          ))}
        </SelectField>
        <SelectField
          label="Categoría"
          value={filtro.categoria ?? ''}
          onChange={(e) =>
            cambiar({ categoria: (e.target.value || undefined) as Categoria | undefined })
          }
        >
          <option value="">Todas</option>
          {LISTA_CATEGORIAS.map((c) => (
            <option key={c} value={c}>
              {CATEGORIAS[c].plural}
            </option>
          ))}
        </SelectField>
      </div>
      {!fechasValidas ? (
        <p className="text-sm text-red-700 dark:text-red-400">
          Revisá las fechas: "desde" no puede ser posterior a "hasta".
        </p>
      ) : (
        <Contenido
          isPending={isPending}
          actualizando={isFetching && !isPending}
          error={error}
          reporte={reporte}
          extra={
            data && data.porFecha.length >= 2 ? (
              <div className="mt-5 max-w-xl">
                <p className="mb-1 text-sm font-semibold">Peso promedio en el período</p>
                <MiniGrafico
                  etiqueta="Peso promedio"
                  puntos={data.porFecha.map((f) => ({ fecha: f.fecha, valor: f.kgPromedio }))}
                  formatear={(v) => `${v.toLocaleString('es-AR', { maximumFractionDigits: 1 })} kg`}
                />
              </div>
            ) : undefined
          }
        />
      )}
    </>
  )
}

function Reproductivos() {
  return (
    <div className="rounded-xl border border-dashed border-stone-300 bg-white p-6 text-sm dark:border-stone-700 dark:bg-stone-900">
      <p className="font-medium">Índices reproductivos: próximamente</p>
      <p className="mt-1 text-stone-600 dark:text-stone-400">
        El % de preñez y el % de destete se calculan con los servicios, los tactos y los destetes.
        Cuando se puedan registrar esos eventos, este reporte va a estar disponible.
      </p>
    </div>
  )
}

// ---------- Vista previa y descarga ----------

function Contenido({
  isPending,
  actualizando = false,
  error,
  reporte,
  extra,
}: {
  isPending: boolean
  actualizando?: boolean
  error: Error | null
  reporte: Reporte | undefined
  extra?: React.ReactNode
}) {
  if (isPending) {
    return (
      <p className="flex items-center gap-2 text-sm text-stone-500">
        <LoaderCircle className="size-4 animate-spin" /> Armando el reporte…
      </p>
    )
  }
  if (error || !reporte) {
    return (
      <p className="text-sm text-red-700 dark:text-red-400">
        No se pudo armar el reporte: {getErrorMessage(error)}
      </p>
    )
  }
  return (
    <>
      <Descargas reporte={reporte} deshabilitado={actualizando} />
      <div className={actualizando ? 'opacity-60 transition-opacity' : undefined}>
        <VistaReporte reporte={reporte} extra={extra} />
      </div>
    </>
  )
}

function Descargas({ reporte, deshabilitado }: { reporte: Reporte; deshabilitado: boolean }) {
  const [exportando, setExportando] = useState<'excel' | 'pdf' | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function exportar(formato: 'excel' | 'pdf') {
    setExportando(formato)
    setError(null)
    try {
      await (formato === 'excel' ? exportarExcel(reporte) : exportarPdf(reporte))
    } catch {
      setError('No se pudo generar el archivo. Probá de nuevo.')
    } finally {
      setExportando(null)
    }
  }

  const clase =
    'inline-flex items-center gap-2 rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm font-medium hover:bg-stone-50 disabled:opacity-50 dark:border-stone-700 dark:bg-stone-900 dark:hover:bg-stone-800'
  return (
    <div className="mb-4 flex flex-wrap items-center gap-2">
      <button
        type="button"
        className={clase}
        disabled={!!exportando || deshabilitado}
        onClick={() => exportar('excel')}
      >
        {exportando === 'excel' ? (
          <LoaderCircle className="size-4 animate-spin" />
        ) : (
          <FileSpreadsheet className="size-4 text-emerald-700" />
        )}
        Descargar Excel
      </button>
      <button
        type="button"
        className={clase}
        disabled={!!exportando || deshabilitado}
        onClick={() => exportar('pdf')}
      >
        {exportando === 'pdf' ? (
          <LoaderCircle className="size-4 animate-spin" />
        ) : (
          <FileText className="size-4 text-red-700" />
        )}
        Descargar PDF
      </button>
      {error && <span className="text-sm text-red-700 dark:text-red-400">{error}</span>}
    </div>
  )
}
