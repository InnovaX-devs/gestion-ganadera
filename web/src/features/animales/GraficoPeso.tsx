import { useEffect, useRef, useState } from 'react'
import { formatearKg } from '@/features/eventos/config'
import { formatearFecha } from '@/features/eventos/fechas'

export interface PuntoPeso {
  fecha: string
  kg: number
}

const ALTO = 200
const M = { arriba: 12, derecha: 16, abajo: 26, izquierda: 44 }

const dia = (iso: string) => new Date(`${iso}T00:00:00`).getTime()

/** Evolución de peso: una línea con un punto por pesaje y tooltip al pasar el mouse. */
export function GraficoPeso({ puntos }: { puntos: PuntoPeso[] }) {
  const [activo, setActivo] = useState<number | null>(null)
  // Se dibuja al ancho real del contenedor: escalar un viewBox fijo achica el texto en celular
  const contenedor = useRef<HTMLDivElement>(null)
  const [ANCHO, setAncho] = useState(600)
  useEffect(() => {
    const el = contenedor.current
    if (!el) return
    const obs = new ResizeObserver(([e]) => setAncho(Math.max(240, e.contentRect.width)))
    obs.observe(el)
    return () => obs.disconnect()
  }, [])
  const datos = [...puntos].sort((a, b) => a.fecha.localeCompare(b.fecha))

  const xs = datos.map((p) => dia(p.fecha))
  const minX = Math.min(...xs)
  const maxX = Math.max(...xs)
  const kgs = datos.map((p) => p.kg)
  const { min: minY, max: maxY, marcas } = escala(Math.min(...kgs), Math.max(...kgs))

  const w = ANCHO - M.izquierda - M.derecha
  const h = ALTO - M.arriba - M.abajo
  const x = (t: number) => M.izquierda + (maxX === minX ? w / 2 : ((t - minX) / (maxX - minX)) * w)
  const y = (v: number) => M.arriba + h - ((v - minY) / (maxY - minY)) * h

  const linea = datos.map((p, i) => `${i ? 'L' : 'M'}${x(xs[i])},${y(p.kg)}`).join(' ')
  const p = activo === null ? null : datos[activo]

  return (
    <div ref={contenedor} className="relative">
      <svg
        viewBox={`0 0 ${ANCHO} ${ALTO}`}
        className="block h-auto w-full touch-none"
        role="img"
        aria-label={`Evolución de peso: ${datos.map((d) => `${formatearKg(d.kg)} kg el ${formatearFecha(d.fecha)}`).join(', ')}`}
        onMouseLeave={() => setActivo(null)}
        onPointerMove={(e) => {
          // El punto más cercano en horizontal al puntero
          const r = e.currentTarget.getBoundingClientRect()
          const px = ((e.clientX - r.left) / r.width) * ANCHO
          let mejor = 0
          xs.forEach((t, i) => {
            if (Math.abs(x(t) - px) < Math.abs(x(xs[mejor]) - px)) mejor = i
          })
          setActivo(mejor)
        }}
      >
        {marcas.map((m) => (
          <g key={m}>
            <line
              x1={M.izquierda}
              x2={ANCHO - M.derecha}
              y1={y(m)}
              y2={y(m)}
              className="stroke-stone-200 dark:stroke-stone-800"
              strokeWidth={1}
            />
            <text
              x={M.izquierda - 8}
              y={y(m)}
              dy="0.32em"
              textAnchor="end"
              className="fill-stone-500 text-[11px] tabular-nums"
            >
              {formatearKg(m)}
            </text>
          </g>
        ))}
        <text x={M.izquierda} y={ALTO - 6} className="fill-stone-500 text-[11px]">
          {formatearFecha(datos[0].fecha)}
        </text>
        {datos.length > 1 && (
          <text
            x={ANCHO - M.derecha}
            y={ALTO - 6}
            textAnchor="end"
            className="fill-stone-500 text-[11px]"
          >
            {formatearFecha(datos[datos.length - 1].fecha)}
          </text>
        )}

        {p && (
          <line
            x1={x(xs[activo!])}
            x2={x(xs[activo!])}
            y1={M.arriba}
            y2={M.arriba + h}
            className="stroke-stone-300 dark:stroke-stone-600"
            strokeWidth={1}
          />
        )}
        <path
          d={linea}
          fill="none"
          className="stroke-brand-600 dark:stroke-brand-400"
          strokeWidth={2}
          strokeLinejoin="round"
          strokeLinecap="round"
        />
        {datos.map((d, i) => (
          <circle
            key={d.fecha + i}
            cx={x(xs[i])}
            cy={y(d.kg)}
            r={activo === i ? 5.5 : 4}
            className="fill-brand-600 dark:fill-brand-400 stroke-white dark:stroke-stone-900"
            strokeWidth={2}
          />
        ))}
      </svg>

      {p && (
        <div
          className="pointer-events-none absolute top-0 -translate-x-1/2 rounded-lg border border-stone-200 bg-white px-2.5 py-1.5 text-xs shadow-sm dark:border-stone-700 dark:bg-stone-900"
          style={{
            left: `${Math.min(85, Math.max(15, (x(xs[activo!]) / ANCHO) * 100))}%`,
          }}
        >
          <p className="font-semibold tabular-nums">{formatearKg(p.kg)} kg</p>
          <p className="text-stone-500">{formatearFecha(p.fecha)}</p>
        </div>
      )}
    </div>
  )
}

/** Rango del eje con números redondos (de 50, 100, 200 kg...) y 3 a 5 marcas. */
function escala(min: number, max: number) {
  if (min === max) {
    min -= 10
    max += 10
  }
  const crudo = (max - min) / 4
  const pot = 10 ** Math.floor(Math.log10(crudo))
  const paso = [1, 2, 5, 10].map((f) => f * pot).find((s) => s >= crudo) ?? crudo
  const ini = Math.floor(min / paso) * paso
  const fin = Math.ceil(max / paso) * paso
  const marcas: number[] = []
  for (let v = ini; v <= fin + paso / 2; v += paso) marcas.push(Math.round(v * 10) / 10)
  return { min: ini, max: fin, marcas }
}
