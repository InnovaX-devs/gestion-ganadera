import { useCallback, useRef, useState } from 'react'
import {
  CircleAlert,
  CloudCheck,
  CloudOff,
  CloudUpload,
  Info,
  RefreshCw,
  RotateCcw,
  Trash2,
} from 'lucide-react'
import { useEstablecimientoActivo } from '@/features/establecimientos/useEstablecimientoActivo'
import { useDismiss } from '@/hooks/useDismiss'
import { pedirSincronizacion, quitarAviso, quitarDeLaCola, reintentar } from './cola'
import { useAvisosSync, useEnLinea, useEstadoSync, usePendientes } from './hooks'
import type { AvisoSync, OperacionPendiente } from './tipos'

/**
 * Indicador de sincronización en el encabezado (#30): si hay conexión, si se está sincronizando y
 * cuántas cargas faltan enviar. Al tocarlo se abre el detalle, con las cargas rechazadas para
 * reintentarlas o descartarlas.
 */
export function IndicadorSync() {
  const enLinea = useEnLinea()
  const { sincronizando, sinRed } = useEstadoSync()
  const pendientes = usePendientes()
  const avisos = useAvisosSync()
  const [abierto, setAbierto] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const cerrar = useCallback(() => setAbierto(false), [])
  useDismiss(ref, abierto, cerrar)

  const rechazadas = pendientes.filter((p) => p.estado === 'RECHAZADO')
  const porEnviar = pendientes.length - rechazadas.length

  const { Icono, etiqueta, color } =
    !enLinea || (sinRed && !sincronizando)
      ? { Icono: CloudOff, etiqueta: 'Sin conexión', color: 'text-amber-600 dark:text-amber-400' }
      : sincronizando
        ? { Icono: RefreshCw, etiqueta: 'Sincronizando', color: 'text-stone-500' }
        : porEnviar > 0
          ? {
              Icono: CloudUpload,
              etiqueta: 'Hay cargas por enviar',
              color: 'text-amber-600 dark:text-amber-400',
            }
          : { Icono: CloudCheck, etiqueta: 'Todo sincronizado', color: 'text-stone-500' }

  const marca = rechazadas.length > 0 ? rechazadas.length : porEnviar
  const resumen = [
    etiqueta,
    porEnviar > 0 && `${porEnviar} ${porEnviar === 1 ? 'carga pendiente' : 'cargas pendientes'}`,
    rechazadas.length > 0 &&
      `${rechazadas.length} ${rechazadas.length === 1 ? 'rechazada' : 'rechazadas'}`,
  ]
    .filter(Boolean)
    .join(', ')

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setAbierto((a) => !a)}
        aria-haspopup="dialog"
        aria-expanded={abierto}
        aria-label={`Sincronización: ${resumen}`}
        title={resumen}
        className="focus-visible:ring-brand-300 relative grid size-9 place-items-center rounded-lg outline-none hover:bg-stone-100 focus-visible:ring-2 dark:hover:bg-stone-800"
      >
        <Icono className={`size-5 ${color} ${sincronizando && enLinea ? 'animate-spin' : ''}`} />
        {(marca > 0 || avisos.length > 0) && (
          <span
            aria-hidden
            className={`absolute -top-0.5 -right-0.5 grid min-w-4 place-items-center rounded-full px-1 text-[10px] leading-4 font-semibold text-white ${rechazadas.length > 0 ? 'bg-red-600' : marca > 0 ? 'bg-amber-500' : 'bg-sky-500'}`}
          >
            {marca > 0 ? (marca > 99 ? '99+' : marca) : '!'}
          </span>
        )}
      </button>

      {abierto && (
        <Panel
          pendientes={pendientes}
          rechazadas={rechazadas}
          avisos={avisos}
          enLinea={enLinea}
          sincronizando={sincronizando}
        />
      )}
    </div>
  )
}

function Panel({
  pendientes,
  rechazadas,
  avisos,
  enLinea,
  sincronizando,
}: {
  pendientes: OperacionPendiente[]
  rechazadas: OperacionPendiente[]
  avisos: AvisoSync[]
  enLinea: boolean
  sincronizando: boolean
}) {
  const { activo, establecimientos } = useEstablecimientoActivo()
  const { ultima, error, sinRed } = useEstadoSync()
  const porEnviar = pendientes.filter((p) => p.estado !== 'RECHAZADO')
  // Si hay cargas de otro campo, se dice de cuál es cada una
  const nombreDe = (id: string) =>
    id === activo?.id
      ? null
      : (establecimientos.find((e) => e.id === id)?.nombre ?? 'otro establecimiento')
  const ultimaDelActivo = activo ? ultima[activo.id] : undefined

  const estado =
    !enLinea || sinRed
      ? 'Sin conexión. Lo pendiente se envía solo cuando vuelva la señal.'
      : sincronizando
        ? 'Sincronizando…'
        : error
          ? `No se pudo sincronizar: ${error}`
          : ultimaDelActivo
            ? `Al día. Última sincronización ${haceCuanto(ultimaDelActivo)}.`
            : 'Todavía no se sincronizó en esta sesión.'

  return (
    <div
      role="dialog"
      aria-label="Sincronización"
      // En celular ocupa el ancho de la pantalla: el ícono no está en el borde (a la derecha va el menú)
      className="fixed inset-x-4 top-16 z-30 overflow-hidden rounded-xl border border-stone-200 bg-white shadow-lg sm:absolute sm:inset-x-auto sm:top-auto sm:right-0 sm:mt-2 sm:w-96 dark:border-stone-800 dark:bg-stone-900"
    >
      <div className="flex items-start justify-between gap-3 border-b border-stone-200 px-4 py-3 dark:border-stone-800">
        <div className="min-w-0">
          <p className="text-sm font-semibold">Sincronización</p>
          <p
            className={`mt-0.5 text-xs ${error && enLinea ? 'text-red-700 dark:text-red-400' : 'text-stone-500'}`}
          >
            {estado}
          </p>
        </div>
        <button
          type="button"
          onClick={pedirSincronizacion}
          disabled={!enLinea || sincronizando}
          className="text-brand-700 hover:bg-brand-50 dark:text-brand-200 dark:hover:bg-brand-900/30 inline-flex shrink-0 items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-medium disabled:opacity-40"
        >
          <RefreshCw className={`size-3.5 ${sincronizando ? 'animate-spin' : ''}`} />
          Sincronizar ahora
        </button>
      </div>

      <div className="max-h-[min(28rem,70vh)] overflow-y-auto">
        {pendientes.length === 0 && avisos.length === 0 && (
          <p className="flex items-center gap-2 px-4 py-6 text-sm text-stone-500">
            <CloudCheck className="size-4 shrink-0" />
            No hay nada pendiente: todo lo cargado ya está en el servidor.
          </p>
        )}

        {rechazadas.length > 0 && (
          <Seccion titulo="Rechazadas" ayuda="El servidor no las aceptó. Reintentá o descartalas.">
            {rechazadas.map((op) => (
              <Rechazada key={op.id} op={op} establecimiento={nombreDe(op.establecimientoId)} />
            ))}
          </Seccion>
        )}

        {porEnviar.length > 0 && (
          <Seccion titulo="Por enviar" ayuda="Guardadas en este dispositivo.">
            {porEnviar.map((op) => (
              <li key={op.id} className="px-4 py-2.5">
                <p className="truncate text-sm">{op.descripcion}</p>
                <p className="text-xs text-stone-500">
                  {[nombreDe(op.establecimientoId), `cargada ${haceCuanto(op.creadaEn)}`]
                    .filter(Boolean)
                    .join(' · ')}
                  {op.estado === 'ERROR' && (
                    <span className="text-amber-700 dark:text-amber-400">
                      {' '}
                      · falló, se reintenta
                    </span>
                  )}
                </p>
              </li>
            ))}
          </Seccion>
        )}

        {avisos.length > 0 && (
          <Seccion titulo="Avisos">
            {avisos.map((a) => (
              <li key={a.id} className="flex gap-2 px-4 py-2.5">
                <Info className="mt-0.5 size-4 shrink-0 text-sky-600" />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm">{a.descripcion}</p>
                  <p className="text-xs text-stone-600 dark:text-stone-400">{a.mensaje}</p>
                </div>
                <button
                  type="button"
                  onClick={() => quitarAviso(a.id)}
                  className="text-brand-700 dark:text-brand-200 shrink-0 self-start text-xs font-medium"
                >
                  Entendido
                </button>
              </li>
            ))}
          </Seccion>
        )}
      </div>
    </div>
  )
}

function Seccion({
  titulo,
  ayuda,
  children,
}: {
  titulo: string
  ayuda?: string
  children: React.ReactNode
}) {
  return (
    <section className="border-b border-stone-100 last:border-b-0 dark:border-stone-800">
      <h3 className="px-4 pt-3 text-xs font-semibold tracking-wide text-stone-500 uppercase">
        {titulo}
      </h3>
      {ayuda && <p className="px-4 text-xs text-stone-400">{ayuda}</p>}
      <ul className="py-1">{children}</ul>
    </section>
  )
}

function Rechazada({
  op,
  establecimiento,
}: {
  op: OperacionPendiente
  establecimiento: string | null
}) {
  const [confirmando, setConfirmando] = useState(false)
  return (
    <li className="px-4 py-2.5">
      <div className="flex gap-2">
        <CircleAlert className="mt-0.5 size-4 shrink-0 text-red-600" />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium">{op.descripcion}</p>
          <p className="text-xs text-stone-500">
            {[establecimiento, `cargada ${haceCuanto(op.creadaEn)}`].filter(Boolean).join(' · ')}
          </p>
          <p className="mt-1 text-xs text-red-700 dark:text-red-400">
            {op.mensaje ?? 'El servidor la rechazó'}
          </p>
          {op.errores && op.errores.length > 0 && (
            <ul className="mt-0.5 list-disc pl-4 text-xs text-red-700 dark:text-red-400">
              {op.errores.map((e) => (
                <li key={e.field}>{e.message}</li>
              ))}
            </ul>
          )}
        </div>
      </div>
      <div className="mt-2 flex justify-end gap-2 text-xs">
        {confirmando ? (
          <>
            <span className="self-center text-stone-600 dark:text-stone-400">
              ¿Descartarla? No se puede deshacer.
            </span>
            <button
              type="button"
              onClick={() => setConfirmando(false)}
              className="rounded-md px-2 py-1 font-medium hover:bg-stone-100 dark:hover:bg-stone-800"
            >
              No
            </button>
            <button
              type="button"
              onClick={() => quitarDeLaCola(op.id)}
              className="rounded-md bg-red-600 px-2 py-1 font-medium text-white hover:bg-red-700"
            >
              Sí, descartar
            </button>
          </>
        ) : (
          <>
            <button
              type="button"
              onClick={() => setConfirmando(true)}
              className="inline-flex items-center gap-1 rounded-md px-2 py-1 font-medium text-stone-600 hover:bg-stone-100 dark:text-stone-300 dark:hover:bg-stone-800"
            >
              <Trash2 className="size-3.5" />
              Descartar
            </button>
            <button
              type="button"
              onClick={() => reintentar(op.id)}
              className="text-brand-700 hover:bg-brand-50 dark:text-brand-200 dark:hover:bg-brand-900/30 inline-flex items-center gap-1 rounded-md px-2 py-1 font-medium"
            >
              <RotateCcw className="size-3.5" />
              Reintentar
            </button>
          </>
        )}
      </div>
    </li>
  )
}

/** "hace un momento", "hace 5 min", "hace 2 h", "el 28/10" */
function haceCuanto(iso: string) {
  const minutos = Math.round((Date.now() - new Date(iso).getTime()) / 60_000)
  if (minutos < 1) return 'hace un momento'
  if (minutos < 60) return `hace ${minutos} min`
  if (minutos < 24 * 60) return `hace ${Math.round(minutos / 60)} h`
  return `el ${new Date(iso).toLocaleDateString('es-AR', { day: 'numeric', month: 'numeric' })}`
}
