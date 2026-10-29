import { formatearGanancia, formatearVariacion } from './formato'
import type { Pesaje } from './types'

/** Ganancia diaria con el contexto que la explica (cuántos kg, en cuántos días). */
export function Ganancia({ pesaje: p }: { pesaje: Pesaje }) {
  if (p.gananciaDiaria == null) {
    return <span className="text-xs text-stone-400">Primer pesaje</span>
  }
  const color =
    p.gananciaDiaria < 0 ? 'text-red-600 dark:text-red-400' : 'text-green-700 dark:text-green-400'
  return (
    <span className="text-xs text-stone-500">
      <strong className={`text-sm tabular-nums ${color}`}>
        {formatearGanancia(p.gananciaDiaria)}
      </strong>
      {p.variacionKg != null && p.diasDesdeAnterior != null && (
        <>
          {' '}
          · {formatearVariacion(p.variacionKg)} en {p.diasDesdeAnterior}{' '}
          {p.diasDesdeAnterior === 1 ? 'día' : 'días'}
        </>
      )}
    </span>
  )
}
