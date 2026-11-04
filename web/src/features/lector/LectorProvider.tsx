import { type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { CircleAlert, CircleCheck, ScanLine, X } from 'lucide-react'
import type { AnimalesLocationState } from '@/features/animales/types'
import { useEstablecimientoActivo } from '@/features/establecimientos/useEstablecimientoActivo'
import { buscarPorEid } from './buscar'
import * as conexiones from './conexiones'
import {
  type Aviso,
  type LecturaRecibida,
  LectorContext,
  type LectorContexto,
  type Manejador,
  esEditable,
  useLector,
  useLectura,
} from './contexto'
import { formatearEid, interpretarLectura } from './lectura'
import { DetectorTeclado } from './teclado'

const DURACION_AVISO = 6000

/**
 * Lector de caravanas electrónicas (#33) para toda la app.
 *
 * - Escucha el teclado siempre: un bastón en modo teclado se reconoce por la velocidad.
 * - Puede conectarse por puerto serie o Bluetooth (botón del encabezado).
 * - Cada lectura va a la pantalla que la esté esperando (`useLectura`); si ninguna la usa, abre la
 *   ficha del animal o, si no está cargado, ofrece darlo de alta.
 */
export function LectorProvider({ children }: { children: ReactNode }) {
  const manejadores = useRef<Manejador[]>([])
  const [conexion, setConexion] = useState<conexiones.Conexion | null>(null)
  const [conectando, setConectando] = useState(false)
  const [ultima, setUltima] = useState<LectorContexto['ultima']>(null)
  const [aviso, setAviso] = useState<Aviso | null>(null)
  const temporizador = useRef<number | undefined>(undefined)

  const avisar = useCallback((nuevo: Aviso) => {
    window.clearTimeout(temporizador.current)
    setAviso(nuevo)
    temporizador.current = window.setTimeout(() => setAviso(null), DURACION_AVISO)
  }, [])

  const registrar = useCallback((manejador: Manejador) => {
    manejadores.current.push(manejador)
    return () => {
      manejadores.current = manejadores.current.filter((m) => m !== manejador)
    }
  }, [])

  /** De la pantalla más nueva a la más vieja; la primera que la usa se la queda. */
  const despachar = useCallback(
    (lectura: LecturaRecibida) => {
      setUltima({ ...lectura, hora: new Date() })
      for (let i = manejadores.current.length - 1; i >= 0; i--) {
        if (manejadores.current[i](lectura)) return true
      }
      // Escrita en un campo que no la esperaba: queda como texto, igual que si la hubieran tipeado
      if (!lectura.campo) {
        avisar({ texto: `Caravana ${formatearEid(lectura.eid)} leída`, tono: 'info' })
      }
      return false
    },
    [avisar],
  )

  // Modo teclado: siempre escuchando
  useEffect(() => {
    const detector = new DetectorTeclado()
    const alTeclear = (e: KeyboardEvent) => {
      if (e.ctrlKey || e.metaKey || e.altKey) return
      const leido = detector.tecla(e.key, e.timeStamp)
      if (!leido) {
        // El espacio que manda el lector ("982 000...") no tiene que mover la página
        if (e.key === ' ' && detector.enCurso && !esEditable(document.activeElement)) {
          e.preventDefault()
        }
        return
      }
      const campo = esEditable(document.activeElement) ? document.activeElement : null
      const usada = despachar({ ...leido.lectura, origen: 'teclado', campo, crudo: leido.crudo })
      // Si alguien la usó, el Enter no tiene que enviar formularios ni nada
      if (usada) {
        e.preventDefault()
        e.stopPropagation()
      }
    }
    window.addEventListener('keydown', alTeclear, true)
    return () => window.removeEventListener('keydown', alTeclear, true)
  }, [despachar])

  const recibirLinea = useCallback(
    (origen: 'serie' | 'bluetooth') => (linea: string) => {
      const lectura = interpretarLectura(linea)
      if (lectura) despachar({ ...lectura, origen, campo: null })
    },
    [despachar],
  )

  const cortar = useCallback(() => {
    setConexion(null)
    avisar({ texto: 'Se desconectó el lector', tono: 'error' })
  }, [avisar])

  const conectar = useCallback(
    async (abrir: () => Promise<conexiones.Conexion>) => {
      setConectando(true)
      try {
        await conexion?.cerrar()
        const nueva = await abrir()
        setConexion(nueva)
        avisar({ texto: `Conectado: ${nueva.nombre}. Leé una caravana para probar.`, tono: 'ok' })
      } catch (e) {
        // Cerrar la ventana de elegir dispositivo no es un error
        if (!(e instanceof DOMException && e.name === 'NotFoundError')) {
          avisar({
            texto: e instanceof Error && e.message ? e.message : 'No se pudo conectar el lector',
            tono: 'error',
          })
        }
      } finally {
        setConectando(false)
      }
    },
    [conexion, avisar],
  )

  const valor = useMemo<LectorContexto>(
    () => ({
      registrar,
      conexion,
      conectando,
      conectarSerie: (velocidad) =>
        conectar(() => conexiones.conectarSerie(velocidad, recibirLinea('serie'), cortar)),
      conectarBluetooth: () =>
        conectar(() => conexiones.conectarBluetooth(recibirLinea('bluetooth'), cortar)),
      desconectar: async () => {
        await conexion?.cerrar()
        setConexion(null)
      },
      ultima,
      simular: (texto) => {
        const lectura = interpretarLectura(texto)
        if (lectura) despachar({ ...lectura, origen: 'simulada', campo: null })
        else avisar({ texto: `"${texto}" no tiene una caravana electrónica`, tono: 'error' })
      },
      avisar,
    }),
    [registrar, conexion, conectando, conectar, recibirLinea, cortar, ultima, despachar, avisar],
  )

  return (
    <LectorContext.Provider value={valor}>
      <ManejadorGeneral />
      {children}
      {aviso && <AvisoLector aviso={aviso} onCerrar={() => setAviso(null)} />}
    </LectorContext.Provider>
  )
}

/**
 * Lo que pasa cuando ninguna pantalla espera la lectura: abre la ficha del animal, o si no está
 * cargado, ofrece darlo de alta con la caravana electrónica ya puesta.
 */
function ManejadorGeneral() {
  const navigate = useNavigate()
  const { activo } = useEstablecimientoActivo()
  const { avisar } = useLector()

  // Es lo primero que se registra: queda al fondo y recibe lo que ninguna pantalla usó
  useLectura((lectura) => {
    // Con una ventana abierta, o si se escribió en un campo, no se cambia de pantalla
    if (!activo || lectura.campo || document.querySelector('dialog[open]')) return false
    const eid = formatearEid(lectura.eid)
    void buscarPorEid(activo.id, lectura.eid)
      .then((animal) => {
        if (animal) {
          navigate(`/animales/${animal.id}`)
          avisar({ texto: `${animal.caravana} · ${eid}`, tono: 'ok' })
          return
        }
        const state: AnimalesLocationState = { nuevo: { caravanaElectronica: lectura.eid } }
        avisar({
          texto: `La caravana ${eid} no está cargada en ${activo.nombre}`,
          tono: 'error',
          accion: { texto: 'Darla de alta', hacer: () => navigate('/animales', { state }) },
        })
      })
      .catch(() => avisar({ texto: `No se pudo buscar la caravana ${eid}`, tono: 'error' }))
    return true
  })
  return null
}

function AvisoLector({ aviso, onCerrar }: { aviso: Aviso; onCerrar: () => void }) {
  const Icono = aviso.tono === 'ok' ? CircleCheck : aviso.tono === 'error' ? CircleAlert : ScanLine
  const color =
    aviso.tono === 'ok'
      ? 'text-emerald-600 dark:text-emerald-400'
      : aviso.tono === 'error'
        ? 'text-red-600 dark:text-red-400'
        : 'text-brand-600 dark:text-brand-300'
  return (
    <div
      role="status"
      className="fixed inset-x-4 bottom-4 z-50 mx-auto flex max-w-md items-center gap-3 rounded-xl border border-stone-200 bg-white px-4 py-3 text-sm shadow-lg dark:border-stone-700 dark:bg-stone-900"
    >
      <Icono className={`size-5 shrink-0 ${color}`} />
      <span className="min-w-0 flex-1 tabular-nums">{aviso.texto}</span>
      {aviso.accion && (
        <button
          type="button"
          onClick={() => {
            aviso.accion!.hacer()
            onCerrar()
          }}
          className="text-brand-700 dark:text-brand-300 shrink-0 font-medium hover:underline"
        >
          {aviso.accion.texto}
        </button>
      )}
      <button
        type="button"
        onClick={onCerrar}
        aria-label="Cerrar aviso"
        className="shrink-0 rounded-md p-1 text-stone-400 hover:bg-stone-100 dark:hover:bg-stone-800"
      >
        <X className="size-4" />
      </button>
    </div>
  )
}
