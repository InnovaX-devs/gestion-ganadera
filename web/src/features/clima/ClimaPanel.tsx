import { Link } from 'react-router'
import { Droplets, MapPin, RotateCw, Wind } from 'lucide-react'
import { hoyISO } from '@/features/eventos/fechas'
import { getErrorMessage } from '@/lib/api'
import { esSinUbicacion, useClima } from './queries'
import { estadoTiempo } from './tiempo'
import type { Clima, DiaClima } from './types'

const tarjeta =
  'rounded-xl border border-stone-200 bg-white dark:border-stone-800 dark:bg-stone-900'

const numero = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 1 })
const grados = (v: number | undefined) => (v == null ? '–' : `${Math.round(v)}°`)
const diaCorto = new Intl.DateTimeFormat('es-AR', { weekday: 'short' })

/** Clima del establecimiento (#29): ahora, los próximos días y la lluvia esperada. */
export function ClimaPanel({
  establecimientoId,
  puedeEditarUbicacion,
}: {
  establecimientoId: string
  /** Sin ubicación cargada, ofrece ir a cargarla */
  puedeEditarUbicacion: boolean
}) {
  const { data, isPending, error, refetch, isFetching } = useClima(establecimientoId)

  return (
    <section className={`${tarjeta} flex flex-col`} aria-labelledby="titulo-clima">
      <div className="flex items-baseline justify-between gap-3 px-5 pt-5">
        <h2 id="titulo-clima" className="text-sm font-medium text-stone-500">
          Clima
        </h2>
        {data && <Ubicacion clima={data} />}
      </div>

      {isPending && <Esqueleto />}

      {error && esSinUbicacion(error) && (
        <div className="px-5 py-6 text-sm">
          <p className="flex items-start gap-2 text-stone-600 dark:text-stone-400">
            <MapPin className="mt-0.5 size-4 shrink-0 text-stone-400" />
            {getErrorMessage(error)}
          </p>
          {puedeEditarUbicacion && (
            <Link
              to="/establecimientos"
              className="text-brand-700 hover:text-brand-800 dark:text-brand-200 mt-3 inline-block font-medium"
            >
              Cargar ubicación
            </Link>
          )}
        </div>
      )}

      {error && !esSinUbicacion(error) && (
        <div className="px-5 py-6 text-sm">
          <p className="text-stone-600 dark:text-stone-400">{getErrorMessage(error)}</p>
          <button
            type="button"
            onClick={() => refetch()}
            disabled={isFetching}
            className="text-brand-700 hover:text-brand-800 dark:text-brand-200 mt-3 inline-flex items-center gap-1.5 font-medium disabled:opacity-50"
          >
            <RotateCw className={`size-3.5 ${isFetching ? 'animate-spin' : ''}`} />
            Reintentar
          </button>
        </div>
      )}

      {data && <Contenido clima={data} />}
    </section>
  )
}

function Ubicacion({ clima }: { clima: Clima }) {
  const { ubicacion } = clima
  const texto =
    ubicacion.origen === 'LOCALIDAD' && ubicacion.nombre ? ubicacion.nombre : 'Ubicación del campo'
  return (
    <span
      className="inline-flex min-w-0 items-center gap-1 text-xs text-stone-500"
      title={`${numero.format(ubicacion.latitud)}, ${numero.format(ubicacion.longitud)}`}
    >
      <MapPin className="size-3 shrink-0" />
      <span className="truncate">{texto}</span>
    </span>
  )
}

function Contenido({ clima }: { clima: Clima }) {
  const { actual } = clima
  const estado = estadoTiempo(actual.codigo)
  const Icono = estado.icono

  return (
    <>
      {/* Ahora */}
      <div className="flex items-center gap-4 px-5 pt-3">
        <Icono className="text-brand-600 dark:text-brand-200 size-12 shrink-0" strokeWidth={1.5} />
        <div className="min-w-0">
          <p className="flex items-baseline gap-2">
            <span className="text-4xl font-semibold tracking-tight tabular-nums">
              {grados(actual.temperatura)}
            </span>
            <span className="truncate text-sm text-stone-600 dark:text-stone-300">
              {estado.texto}
            </span>
          </p>
          <p className="mt-0.5 flex flex-wrap gap-x-3 gap-y-0.5 text-xs text-stone-500">
            {actual.sensacionTermica != null && (
              <span>Sensación {grados(actual.sensacionTermica)}</span>
            )}
            {actual.humedad != null && (
              <span className="inline-flex items-center gap-1">
                <Droplets className="size-3" aria-hidden />
                {actual.humedad}% humedad
              </span>
            )}
            {actual.viento != null && (
              <span className="inline-flex items-center gap-1">
                <Wind className="size-3" aria-hidden />
                {Math.round(actual.viento)} km/h
              </span>
            )}
          </p>
        </div>
      </div>

      {/* Lluvia esperada */}
      <p className="mx-5 mt-4 flex items-center gap-2 rounded-lg bg-sky-50 px-3 py-2 text-sm text-sky-900 dark:bg-sky-950/40 dark:text-sky-200">
        <Droplets className="size-4 shrink-0" aria-hidden />
        <span>
          Lluvia esperada:{' '}
          <strong className="tabular-nums">{numero.format(clima.lluviaEsperadaMm ?? 0)} mm</strong>{' '}
          en {clima.dias.length} {clima.dias.length === 1 ? 'día' : 'días'}
        </span>
      </p>

      {/* Próximos días */}
      <ul className="grid grid-cols-5 gap-1 px-3 pt-3 pb-1">
        {clima.dias.slice(0, 5).map((d) => (
          <li key={d.fecha} className="min-w-0">
            <Dia dia={d} />
          </li>
        ))}
      </ul>

      {/* Open-Meteo pide citar la fuente con un link (CC BY 4.0) */}
      <p className="mt-auto px-5 pt-2 pb-4 text-[11px] text-stone-400">
        Datos:{' '}
        <a
          href="https://open-meteo.com/"
          target="_blank"
          rel="noreferrer"
          className="underline hover:text-stone-600"
        >
          Weather data by Open-Meteo.com
        </a>
      </p>
    </>
  )
}

function Dia({ dia }: { dia: DiaClima }) {
  const estado = estadoTiempo(dia.codigo)
  const Icono = estado.icono
  const lluvia = dia.lluviaMm ?? 0
  return (
    <div
      className="flex flex-col items-center rounded-lg px-1 py-2 text-center"
      title={`${estado.texto}${dia.probabilidadLluvia != null ? ` · ${dia.probabilidadLluvia}% de probabilidad de lluvia` : ''}`}
    >
      <span className="text-xs font-medium capitalize">{nombreDia(dia.fecha)}</span>
      <Icono className="my-1.5 size-6 text-stone-500" strokeWidth={1.5} aria-label={estado.texto} />
      <span className="text-sm font-semibold tabular-nums">{grados(dia.maxima)}</span>
      <span className="text-xs text-stone-500 tabular-nums">{grados(dia.minima)}</span>
      <span
        className={`mt-1 text-[11px] tabular-nums ${lluvia >= 1 ? 'font-medium text-sky-700 dark:text-sky-300' : 'text-stone-400'}`}
      >
        {numero.format(lluvia)} mm
      </span>
    </div>
  )
}

/** "Hoy" o "sáb" */
function nombreDia(iso: string) {
  if (iso === hoyISO()) return 'Hoy'
  return diaCorto.format(new Date(`${iso}T00:00:00`)).replace('.', '')
}

function Esqueleto() {
  return (
    <div className="animate-pulse px-5 pt-3 pb-5" aria-busy aria-label="Cargando el clima">
      <div className="flex items-center gap-4">
        <span className="size-12 rounded-full bg-stone-200 dark:bg-stone-800" />
        <span className="h-9 w-24 rounded bg-stone-200 dark:bg-stone-800" />
      </div>
      <div className="mt-4 h-9 rounded-lg bg-stone-100 dark:bg-stone-800/60" />
      <div className="mt-3 grid grid-cols-5 gap-2">
        {Array.from({ length: 5 }, (_, i) => (
          <span key={i} className="h-24 rounded-lg bg-stone-100 dark:bg-stone-800/60" />
        ))}
      </div>
    </div>
  )
}
