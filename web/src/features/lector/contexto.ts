import { createContext, useContext, useEffect, useRef } from 'react'
import type { Conexion } from './conexiones'
import type { Lectura } from './lectura'

export type Origen = 'teclado' | 'serie' | 'bluetooth' | 'simulada'

export interface LecturaRecibida extends Lectura {
  origen: Origen
  /**
   * Si la lectura llegó por teclado con el cursor en un campo de texto, ese campo (lo que mandó el
   * lector ya quedó escrito ahí, en `crudo`). Null si no había ningún campo con foco.
   */
  campo: HTMLElement | null
  crudo?: string
}

/**
 * Recibe cada lectura. Devuelve true si la usó (las demás pantallas ya no la reciben). Lo que haga
 * después (buscar el animal, etc.) puede ser asincrónico, pero la respuesta tiene que ser inmediata.
 */
export type Manejador = (lectura: LecturaRecibida) => boolean

export interface Aviso {
  texto: string
  tono: 'ok' | 'error' | 'info'
  accion?: { texto: string; hacer: () => void }
}

export interface LectorContexto {
  registrar: (manejador: Manejador) => () => void
  conexion: Conexion | null
  conectando: boolean
  conectarSerie: (velocidad: number) => Promise<void>
  conectarBluetooth: () => Promise<void>
  desconectar: () => Promise<void>
  ultima: (LecturaRecibida & { hora: Date }) | null
  /** Para probar sin lector (solo en desarrollo) */
  simular: (texto: string) => void
  avisar: (aviso: Aviso) => void
}

export const LectorContext = createContext<LectorContexto | null>(null)

export function useLector() {
  const contexto = useContext(LectorContext)
  if (!contexto) throw new Error('useLector necesita un LectorProvider')
  return contexto
}

/**
 * Atiende las lecturas mientras la pantalla está abierta. La última pantalla (o ventana) en
 * registrarse es la primera en recibirlas: una ventana abierta tiene prioridad sobre la página.
 */
export function useLectura(manejador: Manejador, activo = true) {
  const { registrar } = useLector()
  const actual = useRef(manejador)
  useEffect(() => {
    actual.current = manejador
  })
  useEffect(() => {
    if (!activo) return
    return registrar((lectura) => actual.current(lectura))
  }, [registrar, activo])
}

/** Inputs, textareas y selects donde el usuario escribe: ahí el lector "tecleó" la caravana. */
export function esEditable(elemento: Element | null): elemento is HTMLElement {
  if (!(elemento instanceof HTMLElement)) return false
  if (elemento.isContentEditable) return true
  if (elemento instanceof HTMLTextAreaElement) return true
  if (elemento instanceof HTMLInputElement) {
    return !['checkbox', 'radio', 'button', 'submit', 'reset', 'file', 'range', 'color'].includes(
      elemento.type,
    )
  }
  return false
}
