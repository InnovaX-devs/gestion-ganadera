import { useId } from 'react'
import { formatearDia } from './config'

interface Punto {
  fecha: string
  valor: number
}

const ANCHO = 100
const ALTO = 40

/**
 * Línea de la evolución de un valor, sin ejes: alcanza para ver la tendencia. Abajo, el rango de
 * fechas y el mínimo y máximo del período.
 */
export function MiniGrafico({
  puntos,
  etiqueta,
  formatear,
}: {
  puntos: Punto[]
  /** Para lectores de pantalla: "Ternero" */
  etiqueta: string
  formatear: (valor: number) => string
}) {
  const idGradiente = useId()

  if (puntos.length < 2) {
    return <p className="text-xs text-stone-500">Todavía no hay historia suficiente.</p>
  }

  const valores = puntos.map((p) => p.valor)
  const min = Math.min(...valores)
  const max = Math.max(...valores)
  // Si el valor no cambió en todo el período, la línea va al medio
  const rango = max - min || 1
  const coordenadas = puntos.map((p, i) => {
    const x = (i / (puntos.length - 1)) * ANCHO
    const y = max === min ? ALTO / 2 : ALTO - 2 - ((p.valor - min) / rango) * (ALTO - 4)
    return `${x.toFixed(2)},${y.toFixed(2)}`
  })
  const primero = puntos[0]
  const ultimo = puntos[puntos.length - 1]
  const sube = ultimo.valor >= primero.valor

  return (
    <figure className="min-w-0">
      <svg
        viewBox={`0 0 ${ANCHO} ${ALTO}`}
        preserveAspectRatio="none"
        className={`h-12 w-full ${sube ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'}`}
        role="img"
        aria-label={`${etiqueta}: de ${formatear(primero.valor)} el ${formatearDia(primero.fecha)} a ${formatear(ultimo.valor)} el ${formatearDia(ultimo.fecha)}`}
      >
        <defs>
          <linearGradient id={idGradiente} x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="currentColor" stopOpacity="0.18" />
            <stop offset="100%" stopColor="currentColor" stopOpacity="0" />
          </linearGradient>
        </defs>
        <polygon
          points={`0,${ALTO} ${coordenadas.join(' ')} ${ANCHO},${ALTO}`}
          fill={`url(#${idGradiente})`}
        />
        <polyline
          points={coordenadas.join(' ')}
          fill="none"
          stroke="currentColor"
          strokeWidth="1.75"
          strokeLinejoin="round"
          strokeLinecap="round"
          vectorEffect="non-scaling-stroke"
        />
      </svg>
      <figcaption className="mt-1 flex justify-between gap-2 text-[11px] text-stone-500 tabular-nums">
        <span>{formatearDia(primero.fecha)}</span>
        <span className="truncate">
          mín {formatear(min)} · máx {formatear(max)}
        </span>
        <span>{formatearDia(ultimo.fecha)}</span>
      </figcaption>
    </figure>
  )
}
