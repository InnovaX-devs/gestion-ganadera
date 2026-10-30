import type { FilaImportacion } from './types'

export type Campo = Exclude<keyof FilaImportacion, 'fila'>

interface Columna {
  campo: Campo
  /** Título en la plantilla */
  titulo: string
  /** Otros títulos que se aceptan (sin acentos ni mayúsculas) */
  alias: string[]
  obligatoria?: boolean
  ayuda: string
  ancho: number
}

/** Columnas de la plantilla, en orden. El backend recibe cada una por su `campo`. */
export const COLUMNAS: Columna[] = [
  {
    campo: 'caravana',
    titulo: 'Caravana',
    alias: ['caravana visual', 'nro caravana', 'numero de caravana', 'caravana *'],
    obligatoria: true,
    ayuda: 'Obligatoria. No se puede repetir en el establecimiento.',
    ancho: 14,
  },
  {
    campo: 'categoria',
    titulo: 'Categoría',
    alias: ['categoria', 'cat'],
    obligatoria: true,
    ayuda: 'Obligatoria. Vaca, Vaquillona, Ternera, Ternero, Novillito, Novillo, Torito o Toro.',
    ancho: 14,
  },
  {
    campo: 'sexo',
    titulo: 'Sexo',
    alias: [],
    ayuda: 'Hembra o Macho (también H o M). Si la dejás vacía, sale de la categoría.',
    ancho: 10,
  },
  {
    campo: 'caravanaElectronica',
    titulo: 'Caravana electrónica',
    alias: ['electronica', 'rfid', 'eid', 'caravana rfid', 'chip'],
    ayuda: 'Opcional. Los 15 dígitos del chip (ej: 032000123456789).',
    ancho: 22,
  },
  {
    campo: 'raza',
    titulo: 'Raza',
    alias: [],
    ayuda: 'Opcional. Ej: Aberdeen Angus, Hereford, Braford.',
    ancho: 18,
  },
  {
    campo: 'fechaNacimiento',
    titulo: 'Fecha de nacimiento',
    alias: ['nacimiento', 'fecha nacimiento', 'fecha nac', 'f nacimiento'],
    ayuda: 'Opcional. Formato 15/09/2024.',
    ancho: 20,
  },
  {
    campo: 'fechaNacimientoEstimada',
    titulo: 'Fecha estimada',
    alias: ['estimada', 'fecha aproximada', 'aproximada'],
    ayuda: 'Sí si la fecha es aproximada (animal comprado o sin registro).',
    ancho: 15,
  },
  {
    campo: 'castrado',
    titulo: 'Castrado',
    alias: [],
    ayuda: 'Solo para terneros: Sí o No. En el resto lo define la categoría.',
    ancho: 11,
  },
  {
    campo: 'lote',
    titulo: 'Lote',
    alias: [],
    ayuda: 'Opcional. Si el lote no existe, se crea.',
    ancho: 16,
  },
  {
    campo: 'rodeo',
    titulo: 'Rodeo',
    alias: [],
    ayuda: 'Opcional. Si el rodeo no existe, se crea.',
    ancho: 16,
  },
  {
    campo: 'madre',
    titulo: 'Madre',
    alias: ['caravana madre', 'madre caravana'],
    ayuda: 'Opcional. Caravana de la madre: puede estar en esta misma planilla.',
    ancho: 12,
  },
  {
    campo: 'padre',
    titulo: 'Padre',
    alias: ['caravana padre', 'padre caravana'],
    ayuda: 'Opcional. Caravana del padre: puede estar en esta misma planilla.',
    ancho: 12,
  },
  {
    campo: 'observaciones',
    titulo: 'Observaciones',
    alias: ['obs', 'notas', 'comentarios'],
    ayuda: 'Opcional. Hasta 500 caracteres.',
    ancho: 30,
  },
]

export const TITULO_DE: Record<Campo, string> = Object.fromEntries(
  COLUMNAS.map((c) => [c.campo, c.titulo]),
) as Record<Campo, string>

/** Minúsculas, sin acentos, sin "*" ni lo que esté entre paréntesis. */
export function normalizarTitulo(valor: string): string {
  return valor
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .replace(/\(.*?\)/g, '')
    .replace(/[*:]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase()
}

const POR_TITULO = new Map<string, Campo>()
for (const c of COLUMNAS) {
  POR_TITULO.set(normalizarTitulo(c.titulo), c.campo)
  POR_TITULO.set(c.campo.toLowerCase(), c.campo)
  for (const alias of c.alias) POR_TITULO.set(normalizarTitulo(alias), c.campo)
}

export function campoDeTitulo(titulo: string): Campo | undefined {
  return POR_TITULO.get(normalizarTitulo(titulo))
}
