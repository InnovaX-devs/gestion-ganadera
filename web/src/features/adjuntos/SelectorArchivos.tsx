import { type ChangeEvent, useRef, useState } from 'react'
import { Camera, FileText, LoaderCircle, Paperclip, X } from 'lucide-react'
import { prepararArchivo } from './comprimir'
import { useUrlDeObjeto } from './useUrlDeObjeto'
import {
  ACEPTA,
  MAXIMO_ARCHIVOS,
  TAMANIO_MAXIMO_MB,
  esImagen,
  formatearTamanio,
  validarTamanio,
  validarTipo,
} from './validacion'

interface Props {
  archivos: File[]
  onChange: (archivos: File[]) => void
  /** Cuántos puede haber en total. Por defecto, el máximo por animal o evento. */
  maximo?: number
  disabled?: boolean
}

/**
 * Elegir fotos y archivos para adjuntar. "Sacar foto" abre la cámara del celular; "Elegir" abre la
 * galería o los archivos. Las fotos se achican acá mismo (comprimir.ts) antes de pasar a la lista,
 * así lo que se ve es lo que se va a subir.
 */
export function SelectorArchivos({
  archivos,
  onChange,
  maximo = MAXIMO_ARCHIVOS,
  disabled,
}: Props) {
  const camara = useRef<HTMLInputElement>(null)
  const galeria = useRef<HTMLInputElement>(null)
  const [preparando, setPreparando] = useState(false)
  const [avisos, setAvisos] = useState<string[]>([])
  const completo = archivos.length >= maximo

  async function elegidos(e: ChangeEvent<HTMLInputElement>) {
    const elegidos = Array.from(e.target.files ?? [])
    // Para poder elegir el mismo archivo otra vez después de quitarlo
    e.target.value = ''
    if (elegidos.length === 0) return

    setPreparando(true)
    const nuevos: File[] = []
    const problemas: string[] = []
    try {
      for (const archivo of elegidos) {
        if (archivos.length + nuevos.length >= maximo) {
          problemas.push(`Se pueden adjuntar hasta ${maximo} archivos`)
          break
        }
        const errorTipo = validarTipo(archivo)
        if (errorTipo) {
          problemas.push(errorTipo)
          continue
        }
        const listo = await prepararArchivo(archivo)
        const errorTamanio = validarTamanio(listo)
        if (errorTamanio) problemas.push(errorTamanio)
        else nuevos.push(listo)
      }
    } finally {
      setPreparando(false)
    }
    setAvisos([...new Set(problemas)])
    if (nuevos.length > 0) onChange([...archivos, ...nuevos])
  }

  const quitar = (i: number) => {
    setAvisos([])
    onChange(archivos.filter((_, indice) => indice !== i))
  }

  return (
    <div>
      <p className="mb-1.5 text-sm font-medium">
        Fotos y archivos <span className="font-normal text-stone-500">(opcional)</span>
      </p>

      <div className="flex flex-wrap gap-2">
        <BotonElegir
          icono={Camera}
          onClick={() => camara.current?.click()}
          disabled={disabled || preparando || completo}
        >
          Sacar foto
        </BotonElegir>
        <BotonElegir
          icono={Paperclip}
          onClick={() => galeria.current?.click()}
          disabled={disabled || preparando || completo}
        >
          Elegir archivos
        </BotonElegir>
        {preparando && (
          <span className="inline-flex items-center gap-1.5 text-xs text-stone-500" role="status">
            <LoaderCircle className="size-3.5 animate-spin" />
            Preparando…
          </span>
        )}
      </div>

      {/* capture abre la cámara trasera en el celular; en la compu abre el selector de archivos */}
      <input
        ref={camara}
        type="file"
        accept="image/*"
        capture="environment"
        className="hidden"
        onChange={elegidos}
        tabIndex={-1}
      />
      <input
        ref={galeria}
        type="file"
        accept={ACEPTA}
        multiple
        className="hidden"
        onChange={elegidos}
        tabIndex={-1}
      />

      {archivos.length > 0 && (
        <ul className="mt-3 grid gap-2">
          {archivos.map((archivo, i) => (
            <li
              key={`${archivo.name}-${archivo.size}-${i}`}
              className="flex items-center gap-3 rounded-lg border border-stone-200 p-2 dark:border-stone-700"
            >
              <Vista archivo={archivo} />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm">{archivo.name}</p>
                <p className="text-xs text-stone-500">{formatearTamanio(archivo.size)}</p>
              </div>
              <button
                type="button"
                onClick={() => quitar(i)}
                disabled={disabled}
                aria-label={`Quitar ${archivo.name}`}
                title="Quitar"
                className="rounded-md p-2 text-stone-500 hover:bg-red-50 hover:text-red-600 disabled:opacity-50 dark:hover:bg-red-950/50"
              >
                <X className="size-4" />
              </button>
            </li>
          ))}
        </ul>
      )}

      {avisos.length > 0 && (
        <ul role="alert" className="mt-2 space-y-0.5 text-xs text-red-600 dark:text-red-400">
          {avisos.map((aviso) => (
            <li key={aviso}>{aviso}</li>
          ))}
        </ul>
      )}
      {archivos.length === 0 && avisos.length === 0 && (
        <p className="mt-1.5 text-xs text-stone-500">
          Hasta {maximo} archivos de {TAMANIO_MAXIMO_MB} MB. Las fotos se achican solas para subir
          más rápido.
        </p>
      )}
    </div>
  )
}

function BotonElegir({
  icono: Icono,
  children,
  ...props
}: {
  icono: typeof Camera
  children: string
  onClick: () => void
  disabled: boolean
}) {
  return (
    <button
      type="button"
      className="inline-flex items-center gap-2 rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm font-medium text-stone-800 transition outline-none hover:bg-stone-50 focus-visible:ring-2 focus-visible:ring-stone-300 disabled:cursor-not-allowed disabled:opacity-60 dark:border-stone-700 dark:bg-stone-900 dark:text-stone-200 dark:hover:bg-stone-800"
      {...props}
    >
      <Icono className="size-4" />
      {children}
    </button>
  )
}

/** Miniatura de la foto elegida, o un ícono si es un PDF o el navegador no puede mostrarla (HEIC). */
function Vista({ archivo }: { archivo: File }) {
  const url = useUrlDeObjeto(esImagen(archivo.type) ? archivo : null)
  const [fallo, setFallo] = useState(false)

  return url && !fallo ? (
    <img
      src={url}
      alt=""
      onError={() => setFallo(true)}
      className="size-12 shrink-0 rounded-md bg-stone-100 object-cover dark:bg-stone-800"
    />
  ) : (
    <span className="grid size-12 shrink-0 place-items-center rounded-md bg-stone-100 text-stone-500 dark:bg-stone-800">
      <FileText className="size-5" />
    </span>
  )
}
