/**
 * Un reporte armado (#32): título, líneas de encabezado y tablas. La misma estructura se usa para
 * mostrarlo en pantalla, para el Excel y para el PDF, así los tres dicen exactamente lo mismo.
 */

export type TipoColumna = 'texto' | 'numero' | 'fecha'

export interface Columna {
  titulo: string
  tipo?: TipoColumna
  /** Decimales de una columna numérica (por defecto 0) */
  decimales?: number
  /** Ancho en caracteres para el Excel */
  ancho?: number
}

/** Fechas como "2026-10-30"; null es una celda vacía. */
export type Celda = string | number | null

export interface Tabla {
  titulo: string
  columnas: Columna[]
  filas: Celda[][]
  /** Fila final en negrita (ej: totales) */
  total?: Celda[]
  /** Texto si no hay filas */
  vacia?: string
}

export interface Reporte {
  titulo: string
  /** Sin extensión: "stock-la-esperanza-2026-10-30" */
  archivo: string
  /** "Establecimiento: La Esperanza", "Período: ..." */
  encabezado: string[]
  tablas: Tabla[]
  /** Aclaración al pie */
  nota?: string
}

const fechaCorta = new Intl.DateTimeFormat('es-AR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
})

/** "30/10/2026" a partir de "2026-10-30" (sin corrimientos por zona horaria). */
export function formatearFecha(iso: string) {
  const [a, m, d] = iso.slice(0, 10).split('-').map(Number)
  return fechaCorta.format(new Date(a, m - 1, d))
}

/** Cómo se ve una celda en pantalla y en el PDF. */
export function textoDeCelda(valor: Celda, columna: Columna) {
  if (valor === null || valor === '') return ''
  if (columna.tipo === 'fecha' && typeof valor === 'string') return formatearFecha(valor)
  if (typeof valor === 'number') {
    const d = columna.decimales ?? 0
    return valor.toLocaleString('es-AR', { minimumFractionDigits: d, maximumFractionDigits: d })
  }
  return String(valor)
}

/** "la-esperanza" para nombres de archivo. */
export function slug(texto: string) {
  return texto
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
}

export const hoyISO = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
