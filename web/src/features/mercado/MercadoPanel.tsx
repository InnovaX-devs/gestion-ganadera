import { useState } from 'react'
import { ChevronDown, Minus, TrendingDown, TrendingUp } from 'lucide-react'
import { getErrorMessage } from '@/lib/api'
import {
  GRUPOS,
  PRODUCTOS,
  delGrupo,
  formatearActualizacion,
  formatearDia,
  formatearPrecio,
  formatearRelacion,
  sufijoUnidad,
  variacion,
} from './config'
import { MiniGrafico } from './MiniGrafico'
import { useHistoricoMercado, useMercadoHoy } from './queries'
import type { Cotizacion, Grupo, Historico, MercadoHoy, Producto } from './types'

const tarjeta =
  'rounded-xl border border-stone-200 bg-white dark:border-stone-800 dark:bg-stone-900'

const ORDEN: Grupo[] = ['INVERNADA', 'CONSUMO', 'GRANOS', 'DOLAR']

/**
 * Referencias de mercado en el inicio (#28): una tarjeta por grupo más la relación de reposición.
 * Al tocar una tarjeta se abre el gráfico de los últimos 30 días.
 */
export function MercadoPanel() {
  const { data, isPending, error } = useMercadoHoy()

  return (
    <section aria-labelledby="titulo-mercado">
      <div className="mb-3 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <h2 id="titulo-mercado" className="text-sm font-medium text-stone-500">
          Mercado
        </h2>
        {data?.actualizadoEn && (
          <p className="text-xs text-stone-500">
            Actualizado {formatearActualizacion(data.actualizadoEn)}
          </p>
        )}
      </div>

      {isPending && <Esqueleto />}
      {error && (
        <p className={`${tarjeta} p-5 text-sm text-red-700 dark:text-red-400`}>
          No se pudieron cargar las cotizaciones: {getErrorMessage(error)}
        </p>
      )}
      {data && <Tarjetas hoy={data} />}
    </section>
  )
}

function Tarjetas({ hoy }: { hoy: MercadoHoy }) {
  return (
    // items-start: al abrir una tarjeta, las de al lado no se estiran
    <div className="grid grid-cols-1 items-start gap-3 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-5">
      {ORDEN.slice(0, 2).map((g) => (
        <TarjetaGrupo key={g} grupo={g} cotizaciones={delGrupo(hoy.cotizaciones, g)} />
      ))}
      <TarjetaRelacion hoy={hoy} />
      {ORDEN.slice(2).map((g) => (
        <TarjetaGrupo key={g} grupo={g} cotizaciones={delGrupo(hoy.cotizaciones, g)} />
      ))}
    </div>
  )
}

/** Tarjeta que se abre al tocarla para mostrar el gráfico. */
function Desplegable({
  titulo,
  pie,
  children,
  grafico,
}: {
  titulo: string
  pie?: string
  children: React.ReactNode
  grafico: (abierto: boolean) => React.ReactNode
}) {
  const [abierto, setAbierto] = useState(false)
  return (
    <article className={tarjeta}>
      <button
        type="button"
        onClick={() => setAbierto((a) => !a)}
        aria-expanded={abierto}
        className="group focus-visible:ring-brand-300 block w-full rounded-xl p-4 text-left outline-none hover:bg-stone-50 focus-visible:ring-2 dark:hover:bg-stone-800/50"
      >
        <span className="flex items-center justify-between gap-2">
          <span className="text-xs font-semibold tracking-wide text-stone-500 uppercase">
            {titulo}
          </span>
          <ChevronDown
            aria-hidden
            className={`size-4 text-stone-400 transition-transform group-hover:text-stone-600 ${abierto ? 'rotate-180' : ''}`}
          />
        </span>
        <span className="mt-2 block">{children}</span>
        {pie && <span className="mt-2 block truncate text-[11px] text-stone-500">{pie}</span>}
      </button>
      {abierto && (
        <div className="border-t border-stone-100 px-4 pt-3 pb-4 dark:border-stone-800">
          {grafico(abierto)}
        </div>
      )}
    </article>
  )
}

function TarjetaGrupo({ grupo, cotizaciones }: { grupo: Grupo; cotizaciones: Cotizacion[] }) {
  const productos = cotizaciones.map((c) => c.producto)
  return (
    <Desplegable
      titulo={GRUPOS[grupo]}
      pie={pieDeFuente(cotizaciones)}
      grafico={(abierto) => (
        <GraficosGrupo cotizaciones={cotizaciones} productos={productos} abierto={abierto} />
      )}
    >
      <span className="grid gap-1.5">
        {cotizaciones.map((c) => (
          <FilaCotizacion key={c.producto} cotizacion={c} />
        ))}
      </span>
    </Desplegable>
  )
}

function FilaCotizacion({ cotizacion: c }: { cotizacion: Cotizacion }) {
  return (
    <span className="flex items-baseline justify-between gap-2">
      <span className="min-w-0 truncate text-sm">
        {PRODUCTOS[c.producto]}
        {c.estado === 'ANTERIOR' && <Desactualizado />}
      </span>
      {c.valor == null ? (
        <span className="text-sm text-stone-400">Sin datos</span>
      ) : (
        <span className="flex shrink-0 items-baseline gap-2">
          <span className="text-sm font-semibold tabular-nums" title={c.nota}>
            {formatearPrecio(c.valor, c.unidad)}
            <span className="text-xs font-normal text-stone-500">{sufijoUnidad(c.unidad)}</span>
            {c.nota && <span className="text-amber-600">*</span>}
          </span>
          <Variacion
            porcentaje={variacion(c.valor, c.valorAnterior)}
            respectoDe={c.fechaAnterior}
          />
        </span>
      )}
    </span>
  )
}

function TarjetaRelacion({ hoy }: { hoy: MercadoHoy }) {
  const r = hoy.relacionReposicion
  return (
    <Desplegable
      titulo="Relación de reposición"
      pie={
        r
          ? `Ternero ${formatearPrecio(r.ternero, '$/kg vivo')} / novillo ${formatearPrecio(r.novillo, '$/kg vivo')}`
          : undefined
      }
      grafico={(abierto) => <GraficoRelacion abierto={abierto} />}
    >
      {r ? (
        <span className="block">
          <span className="flex items-baseline gap-2">
            <span className="text-2xl font-semibold tracking-tight tabular-nums">
              {formatearRelacion(r.valor)}
            </span>
            <Variacion porcentaje={variacion(r.valor, r.valorAnterior)} />
            {!r.actualizada && <Desactualizado />}
          </span>
          <span className="mt-0.5 block text-xs text-stone-500">
            Kilos de novillo para comprar un kilo de ternero
          </span>
        </span>
      ) : (
        <span className="block text-sm text-stone-500">
          Falta el precio del ternero o del novillo.
        </span>
      )}
    </Desplegable>
  )
}

function GraficosGrupo({
  cotizaciones,
  productos,
  abierto,
}: {
  cotizaciones: Cotizacion[]
  productos: Producto[]
  abierto: boolean
}) {
  const { data, isPending, error } = useHistoricoMercado(productos, abierto)
  return (
    <EstadoHistorico isPending={isPending} error={error} data={data}>
      {(historico) => (
        <div className="grid gap-4">
          {cotizaciones.map((c) => (
            <div key={c.producto}>
              <p className="mb-1 text-xs font-medium">{PRODUCTOS[c.producto]}</p>
              <MiniGrafico
                etiqueta={PRODUCTOS[c.producto]}
                puntos={conValor(historico.series[c.producto] ?? [])}
                formatear={(v) => formatearPrecio(v, c.unidad)}
              />
            </div>
          ))}
        </div>
      )}
    </EstadoHistorico>
  )
}

function GraficoRelacion({ abierto }: { abierto: boolean }) {
  // Se pide un producto cualquiera: la relación viene siempre en el histórico
  const { data, isPending, error } = useHistoricoMercado(['TERNERO', 'NOVILLO'], abierto)
  return (
    <EstadoHistorico isPending={isPending} error={error} data={data}>
      {(historico) => (
        <MiniGrafico
          etiqueta="Relación de reposición"
          puntos={historico.relacionReposicion}
          formatear={formatearRelacion}
        />
      )}
    </EstadoHistorico>
  )
}

function EstadoHistorico({
  isPending,
  error,
  data,
  children,
}: {
  isPending: boolean
  error: Error | null
  data: Historico | undefined
  children: (historico: Historico) => React.ReactNode
}) {
  if (isPending)
    return <div className="h-14 animate-pulse rounded bg-stone-100 dark:bg-stone-800" />
  if (error || !data) {
    return (
      <p className="text-xs text-red-700 dark:text-red-400">
        No se pudo cargar el historial: {getErrorMessage(error)}
      </p>
    )
  }
  return <>{children(data)}</>
}

/** "▲ 1,2 %" verde, "▼ 0,4 %" rojo o "=" si no cambió. Nada si no hay con qué comparar. */
function Variacion({ porcentaje, respectoDe }: { porcentaje: number | null; respectoDe?: string }) {
  if (porcentaje == null) return null
  const titulo = respectoDe ? `Respecto al ${formatearDia(respectoDe)}` : 'Respecto al día anterior'
  if (Math.abs(porcentaje) < 0.05) {
    return (
      <span className="inline-flex items-center text-xs text-stone-400" title={titulo}>
        <Minus className="size-3.5" aria-label="Sin cambios" />
      </span>
    )
  }
  const sube = porcentaje > 0
  const Icono = sube ? TrendingUp : TrendingDown
  return (
    <span
      className={`inline-flex items-center gap-0.5 text-xs font-medium tabular-nums ${sube ? 'text-emerald-700 dark:text-emerald-400' : 'text-rose-700 dark:text-rose-400'}`}
      title={titulo}
    >
      <Icono className="size-3.5" aria-hidden />
      <span className="sr-only">{sube ? 'Subió' : 'Bajó'}</span>
      {porcentaje
        .toLocaleString('es-AR', { maximumFractionDigits: 1, minimumFractionDigits: 1 })
        .replace('-', '')}
      %
    </span>
  )
}

function Desactualizado() {
  return (
    <span
      className="ml-1.5 inline-block size-1.5 rounded-full bg-amber-500 align-middle"
      title="No se pudo actualizar: es el último valor conocido"
      aria-label="Sin actualizar"
      role="img"
    />
  )
}

/** "Dato del 29/10 · BCR Pizarra Rosario" con lo que comparten los productos de la tarjeta. */
function pieDeFuente(cotizaciones: Cotizacion[]) {
  const conValor = cotizaciones.filter((c) => c.valor != null)
  if (conValor.length === 0) return 'Sin datos todavía'
  const fechas = [...new Set(conValor.map((c) => c.fechaDato).filter(Boolean))] as string[]
  const fuentes = [...new Set(conValor.map((c) => c.fuente).filter(Boolean))]
  const fecha = fechas.length === 1 ? `Dato del ${formatearDia(fechas[0])}` : null
  const estimativo = conValor.some((c) => c.nota) ? '* estimativo' : null
  return [fecha, fuentes.join(', '), estimativo].filter(Boolean).join(' · ')
}

const conValor = (puntos: { fecha: string; valor?: number }[]) =>
  puntos.filter((p): p is { fecha: string; valor: number } => p.valor != null)

function Esqueleto() {
  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-5" aria-busy>
      {Array.from({ length: 5 }, (_, i) => (
        <div key={i} className={`${tarjeta} grid animate-pulse gap-3 p-4`}>
          <span className="h-3 w-20 rounded bg-stone-200 dark:bg-stone-800" />
          <span className="h-4 rounded bg-stone-100 dark:bg-stone-800/60" />
          <span className="h-4 rounded bg-stone-100 dark:bg-stone-800/60" />
        </div>
      ))}
    </div>
  )
}
