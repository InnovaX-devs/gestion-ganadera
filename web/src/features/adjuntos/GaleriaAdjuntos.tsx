import { type RefObject, useEffect, useRef, useState } from 'react'
import { Download, FileText, ImageOff, LoaderCircle, X } from 'lucide-react'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { getErrorMessage } from '@/lib/api'
import { descargarArchivo, useContenido, useEliminarAdjunto } from './queries'
import type { Adjunto } from './types'
import { useUrlDeObjeto } from './useUrlDeObjeto'
import { esImagen, formatearTamanio } from './validacion'

interface Props {
  establecimientoId: string
  adjuntos: Adjunto[]
  /** Muestra la cruz para eliminar cada archivo. El backend igual controla el permiso. */
  puedeEliminar?: boolean
}

/**
 * Las fotos como miniaturas (al tocarlas se ven grandes) y los PDF como una fila que se baja.
 * No muestra nada si no hay archivos, así se puede poner en cualquier lista sin chequear antes.
 */
export function GaleriaAdjuntos({ establecimientoId, adjuntos, puedeEliminar }: Props) {
  const [viendo, setViendo] = useState<Adjunto | null>(null)
  const [aEliminar, setAEliminar] = useState<Adjunto | null>(null)
  const eliminar = useEliminarAdjunto(establecimientoId)
  if (adjuntos.length === 0) return null

  const fotos = adjuntos.filter((a) => esImagen(a.tipoContenido))
  const archivos = adjuntos.filter((a) => !esImagen(a.tipoContenido))
  const cerrarEliminar = () => {
    setAEliminar(null)
    eliminar.reset()
  }

  return (
    <div className="mt-2 space-y-2">
      {fotos.length > 0 && (
        <ul className="flex flex-wrap gap-2">
          {fotos.map((foto) => (
            <li key={foto.id} className="relative">
              <Miniatura
                establecimientoId={establecimientoId}
                adjunto={foto}
                onAbrir={() => setViendo(foto)}
              />
              {puedeEliminar && <BotonEliminar adjunto={foto} onClick={() => setAEliminar(foto)} />}
            </li>
          ))}
        </ul>
      )}

      {archivos.length > 0 && (
        <ul className="space-y-1.5">
          {archivos.map((archivo) => (
            <li key={archivo.id} className="flex items-center gap-1">
              <FilaArchivo establecimientoId={establecimientoId} adjunto={archivo} />
              {puedeEliminar && (
                <BotonEliminar adjunto={archivo} onClick={() => setAEliminar(archivo)} enLinea />
              )}
            </li>
          ))}
        </ul>
      )}

      <Visor
        establecimientoId={establecimientoId}
        adjunto={viendo}
        onClose={() => setViendo(null)}
      />

      <ConfirmDialog
        open={aEliminar !== null}
        title="Eliminar archivo"
        description={`${aEliminar?.nombre ?? ''} se borra para siempre. Esto no se puede deshacer.`}
        confirmLabel="Eliminar"
        loading={eliminar.isPending}
        error={eliminar.error ? getErrorMessage(eliminar.error) : null}
        onClose={cerrarEliminar}
        onConfirm={() => aEliminar && eliminar.mutate(aEliminar.id, { onSuccess: cerrarEliminar })}
      />
    </div>
  )
}

/**
 * true cuando el elemento está (o está por estar) en pantalla. Una lista de 30 eventos con fotos
 * no tiene por qué bajar todas juntas: en el campo, cada megabyte cuenta.
 */
function useCercaDePantalla(ref: RefObject<HTMLElement | null>) {
  // Un navegador sin IntersectionObserver baja todo de una: es lo más simple y casi no existe
  const [cerca, setCerca] = useState(() => !('IntersectionObserver' in window))
  useEffect(() => {
    const elemento = ref.current
    if (cerca || !elemento) return
    const observador = new IntersectionObserver(
      ([entrada]) => {
        if (entrada?.isIntersecting) {
          setCerca(true)
          observador.disconnect()
        }
      },
      { rootMargin: '300px' },
    )
    observador.observe(elemento)
    return () => observador.disconnect()
  }, [cerca, ref])
  return cerca
}

function Miniatura({
  establecimientoId,
  adjunto,
  onAbrir,
}: {
  establecimientoId: string
  adjunto: Adjunto
  onAbrir: () => void
}) {
  const ref = useRef<HTMLButtonElement>(null)
  const cerca = useCercaDePantalla(ref)
  const { data, isPending, isError } = useContenido(establecimientoId, adjunto.id, cerca)
  const url = useUrlDeObjeto(data)
  const [noSePuedeMostrar, setNoSePuedeMostrar] = useState(false)

  return (
    <button
      ref={ref}
      type="button"
      onClick={onAbrir}
      aria-label={`Ver ${adjunto.nombre}`}
      title={adjunto.nombre}
      className="focus-visible:ring-brand-300 grid size-20 place-items-center overflow-hidden rounded-lg border border-stone-200 bg-stone-100 text-stone-400 outline-none focus-visible:ring-2 dark:border-stone-700 dark:bg-stone-800"
    >
      {url && !noSePuedeMostrar ? (
        <img
          src={url}
          alt={adjunto.nombre}
          onError={() => setNoSePuedeMostrar(true)}
          className="size-full object-cover"
        />
      ) : isError || noSePuedeMostrar ? (
        <ImageOff className="size-5" />
      ) : (
        <LoaderCircle className={`size-5 ${cerca && isPending ? 'animate-spin' : ''}`} />
      )}
    </button>
  )
}

function FilaArchivo({
  establecimientoId,
  adjunto,
}: {
  establecimientoId: string
  adjunto: Adjunto
}) {
  const [bajando, setBajando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function bajar() {
    setBajando(true)
    setError(null)
    try {
      await descargarArchivo(establecimientoId, adjunto)
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setBajando(false)
    }
  }

  return (
    <div className="min-w-0 flex-1">
      <button
        type="button"
        onClick={bajar}
        disabled={bajando}
        className="hover:border-brand-300 flex w-full items-center gap-2 rounded-lg border border-stone-200 px-3 py-2 text-left text-sm outline-none focus-visible:ring-2 focus-visible:ring-stone-300 disabled:opacity-60 dark:border-stone-700"
      >
        {bajando ? (
          <LoaderCircle className="size-4 shrink-0 animate-spin text-stone-500" />
        ) : (
          <FileText className="size-4 shrink-0 text-stone-500" />
        )}
        <span className="min-w-0 flex-1 truncate">{adjunto.nombre}</span>
        <span className="shrink-0 text-xs text-stone-500">{formatearTamanio(adjunto.tamanio)}</span>
      </button>
      {error && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{error}</p>}
    </div>
  )
}

function BotonEliminar({
  adjunto,
  onClick,
  enLinea,
}: {
  adjunto: Adjunto
  onClick: () => void
  /** En una fila, al costado; en una miniatura, encima de la esquina. */
  enLinea?: boolean
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={`Eliminar ${adjunto.nombre}`}
      title="Eliminar"
      className={
        enLinea
          ? 'rounded-md p-2 text-stone-400 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-950/50'
          : 'absolute -top-1.5 -right-1.5 grid size-6 place-items-center rounded-full border border-stone-200 bg-white text-stone-500 shadow-xs hover:text-red-600 dark:border-stone-700 dark:bg-stone-900'
      }
    >
      <X className="size-3.5" />
    </button>
  )
}

/** La foto en grande, sobre un fondo oscuro. Se cierra con Escape, con la cruz o tocando afuera. */
function Visor({
  establecimientoId,
  adjunto,
  onClose,
}: {
  establecimientoId: string
  adjunto: Adjunto | null
  onClose: () => void
}) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const dialogo = ref.current
    if (!dialogo) return
    if (adjunto && !dialogo.open) dialogo.showModal()
    if (!adjunto && dialogo.open) dialogo.close()
  }, [adjunto])

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      onCancel={(e) => {
        e.preventDefault()
        onClose()
      }}
      onClick={(e) => e.target === ref.current && onClose()}
      aria-label={adjunto?.nombre ?? 'Foto'}
      className="m-auto max-h-dvh w-full max-w-5xl overflow-hidden bg-transparent p-0 text-white backdrop:bg-stone-950/85"
    >
      {adjunto && (
        <FotoGrande establecimientoId={establecimientoId} adjunto={adjunto} onClose={onClose} />
      )}
    </dialog>
  )
}

function FotoGrande({
  establecimientoId,
  adjunto,
  onClose,
}: {
  establecimientoId: string
  adjunto: Adjunto
  onClose: () => void
}) {
  const { data, isPending, error } = useContenido(establecimientoId, adjunto.id)
  const url = useUrlDeObjeto(data)
  const [fallo, setFallo] = useState(false)
  const [errorDescarga, setErrorDescarga] = useState<string | null>(null)

  return (
    <div className="flex max-h-dvh flex-col">
      <div className="flex items-center gap-2 px-3 py-2">
        <p className="min-w-0 flex-1 truncate text-sm">
          {adjunto.nombre}
          <span className="text-stone-400"> · {formatearTamanio(adjunto.tamanio)}</span>
        </p>
        <button
          type="button"
          onClick={() =>
            descargarArchivo(establecimientoId, adjunto).catch((e) =>
              setErrorDescarga(getErrorMessage(e)),
            )
          }
          aria-label="Descargar"
          title="Descargar"
          className="rounded-md p-2 hover:bg-white/15"
        >
          <Download className="size-5" />
        </button>
        <button
          type="button"
          onClick={onClose}
          aria-label="Cerrar"
          title="Cerrar"
          className="rounded-md p-2 hover:bg-white/15"
        >
          <X className="size-5" />
        </button>
      </div>
      <div className="grid min-h-48 flex-1 place-items-center overflow-auto px-3 pb-3">
        {url && !fallo ? (
          <img
            src={url}
            alt={adjunto.nombre}
            onError={() => setFallo(true)}
            className="max-h-[calc(100dvh-5rem)] max-w-full rounded-md object-contain"
          />
        ) : error || fallo ? (
          <p className="flex items-center gap-2 text-sm text-stone-300">
            <ImageOff className="size-5" />
            {fallo
              ? 'Este navegador no puede mostrar la foto. Descargala para verla.'
              : getErrorMessage(error)}
          </p>
        ) : (
          isPending && <LoaderCircle className="size-6 animate-spin text-stone-300" />
        )}
        {errorDescarga && <p className="mt-2 text-sm text-red-300">{errorDescarga}</p>}
      </div>
    </div>
  )
}
