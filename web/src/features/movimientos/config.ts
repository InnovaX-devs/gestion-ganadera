import type { MovimientoDatos } from './types'

export interface CambioMovimiento {
  campo: 'Establecimiento' | 'Lote' | 'Rodeo'
  desde: string
  hacia: string
}

/** Qué cambió en un movimiento, campo por campo. Un campo que quedó igual no aparece. */
export function cambiosDeMovimiento(datos: MovimientoDatos): CambioMovimiento[] {
  const { desde, hacia } = datos
  if (!desde || !hacia) return []
  const cambios: CambioMovimiento[] = []
  if (desde.establecimiento.id !== hacia.establecimiento.id) {
    cambios.push({
      campo: 'Establecimiento',
      desde: desde.establecimiento.nombre,
      hacia: hacia.establecimiento.nombre,
    })
  }
  if (desde.lote?.id !== hacia.lote?.id) {
    cambios.push({
      campo: 'Lote',
      desde: desde.lote?.nombre ?? 'Sin lote',
      hacia: hacia.lote?.nombre ?? 'Sin lote',
    })
  }
  if (desde.rodeo?.id !== hacia.rodeo?.id) {
    cambios.push({
      campo: 'Rodeo',
      desde: desde.rodeo?.nombre ?? 'Sin rodeo',
      hacia: hacia.rodeo?.nombre ?? 'Sin rodeo',
    })
  }
  return cambios
}

/** "Establecimiento: La Esperanza → El Ombú" y una línea más por cada otro cambio. */
export function resumenMovimiento(datos: MovimientoDatos): string | null {
  const cambios = cambiosDeMovimiento(datos)
  if (cambios.length === 0) return null
  return cambios.map((c) => `${c.campo}: ${c.desde} → ${c.hacia}`).join('\n')
}
