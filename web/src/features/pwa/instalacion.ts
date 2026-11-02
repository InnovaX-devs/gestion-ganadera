/**
 * Instalar la app desde el navegador (#30).
 *
 * Chrome, Edge y Android avisan con `beforeinstallprompt` cuando la app se puede instalar: se guarda
 * ese evento y se usa al tocar "Instalar". Safari en iPhone no tiene ese aviso (no se puede instalar
 * con un botón): ahí se muestran los pasos. Este módulo se importa en main.tsx, lo antes posible,
 * porque el aviso puede llegar antes de que se dibuje la pantalla.
 */

interface AvisoDeInstalacion extends Event {
  prompt(): Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}

/** boton: se puede instalar con un botón. ios: hay que hacerlo a mano. no: este navegador no deja. */
export type FormaDeInstalar = 'boton' | 'ios' | 'no'

export interface EstadoInstalacion {
  instalada: boolean
  forma: FormaDeInstalar
}

let aviso: AvisoDeInstalacion | null = null
let estado: EstadoInstalacion = calcular()
const oyentes = new Set<() => void>()

function esIOS() {
  const ua = navigator.userAgent
  // Los iPad nuevos se presentan como Mac: se distinguen por la pantalla táctil
  return /iphone|ipad|ipod/i.test(ua) || (/macintosh/i.test(ua) && navigator.maxTouchPoints > 1)
}

function abiertaComoApp() {
  return (
    window.matchMedia?.('(display-mode: standalone)').matches ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true
  )
}

function calcular(): EstadoInstalacion {
  if (typeof window === 'undefined') return { instalada: false, forma: 'no' }
  const instalada = abiertaComoApp()
  const forma: FormaDeInstalar = instalada ? 'no' : aviso ? 'boton' : esIOS() ? 'ios' : 'no'
  return { instalada, forma }
}

function actualizar() {
  estado = calcular()
  oyentes.forEach((o) => o())
}

if (typeof window !== 'undefined') {
  window.addEventListener('beforeinstallprompt', (e) => {
    // Sin esto, Chrome muestra su propia barra; preferimos nuestro botón
    e.preventDefault()
    aviso = e as AvisoDeInstalacion
    actualizar()
  })
  window.addEventListener('appinstalled', () => {
    aviso = null
    actualizar()
  })
}

export const leerEstadoInstalacion = () => estado

export function alCambiarInstalacion(oyente: () => void) {
  oyentes.add(oyente)
  return () => {
    oyentes.delete(oyente)
  }
}

/** Muestra el cuadro del navegador. true si el usuario aceptó. */
export async function instalar(): Promise<boolean> {
  if (!aviso) return false
  const actual = aviso
  await actual.prompt()
  const { outcome } = await actual.userChoice
  // El aviso se puede usar una sola vez
  aviso = null
  actualizar()
  return outcome === 'accepted'
}
