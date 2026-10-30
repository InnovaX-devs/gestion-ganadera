import type { Cotizacion, Grupo, Producto } from './types'

export const PRODUCTOS: Record<Producto, string> = {
  TERNERO: 'Ternero',
  TERNERA: 'Ternera',
  NOVILLO: 'Novillo',
  NOVILLITO: 'Novillito',
  MAIZ: 'Maíz',
  SOJA: 'Soja',
  TRIGO: 'Trigo',
  DOLAR_OFICIAL: 'Oficial',
  DOLAR_BLUE: 'Blue',
}

export const GRUPOS: Record<Grupo, string> = {
  INVERNADA: 'Invernada',
  CONSUMO: 'Consumo',
  GRANOS: 'Granos',
  DOLAR: 'Dólar',
}

/** "/kg" o "/tn" después del precio. En el dólar no va nada. */
const SUFIJO: Record<string, string> = { '$/kg vivo': '/kg', '$/tn': '/tn' }

const conDecimales = new Intl.NumberFormat('es-AR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})
const entero = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 0 })

/** "$ 5.758,85" (por kilo y dólar) o "$ 311.000" (por tonelada, sin centavos). */
export function formatearPrecio(valor: number, unidad: string) {
  return `$ ${(unidad === '$/tn' ? entero : conDecimales).format(valor)}`
}

export const sufijoUnidad = (unidad: string) => SUFIJO[unidad] ?? ''

/** "1,35" */
export const formatearRelacion = (valor: number) => conDecimales.format(valor)

/** Variación porcentual respecto al día anterior. null si no hay con qué comparar. */
export function variacion(actual: number | undefined, anterior: number | undefined) {
  if (actual == null || anterior == null || anterior === 0) return null
  return ((actual - anterior) / anterior) * 100
}

/** Productos de cada tarjeta, en orden. */
export function delGrupo(cotizaciones: Cotizacion[], grupo: Grupo) {
  return cotizaciones.filter((c) => c.grupo === grupo)
}

const diaMes = new Intl.DateTimeFormat('es-AR', { day: 'numeric', month: 'numeric' })

/** "29/10" a partir de "2026-10-29". */
export const formatearDia = (iso: string) => diaMes.format(new Date(`${iso}T00:00:00`))

const diaSemana = new Intl.DateTimeFormat('es-AR', { weekday: 'long' })
const hora = new Intl.DateTimeFormat('es-AR', {
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

/** "el viernes 30/10 a las 19:00" a partir de un instante ISO, en la hora de la computadora. */
export function formatearActualizacion(iso: string) {
  const d = new Date(iso)
  return `el ${diaSemana.format(d)} ${diaMes.format(d)} a las ${hora.format(d)}`
}
