import { ChevronLeft, ChevronRight } from 'lucide-react'
import { aISO, conMayuscula, deISO, fechaCorta, rangoDelMes } from './fechas'
import type { Plan } from './types'

const DIAS = ['lun', 'mar', 'mié', 'jue', 'vie', 'sáb', 'dom']
const nombreMes = new Intl.DateTimeFormat('es-AR', { month: 'long', year: 'numeric' })

/** Color de cada tratamiento según su estado. */
function claseDePlan(p: Plan) {
  if (p.estado === 'REALIZADO')
    return 'bg-emerald-50 text-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300'
  if (p.estado === 'CANCELADO') return 'bg-stone-100 text-stone-400 line-through dark:bg-stone-800'
  if (p.vencido) return 'bg-red-50 text-red-800 dark:bg-red-950/40 dark:text-red-300'
  return 'bg-brand-50 text-brand-900 dark:bg-brand-900/30 dark:text-brand-100'
}

/**
 * Calendario de un mes (#34). En pantallas chicas se ve como agenda (lista por día): una grilla de 7
 * columnas no entra en un celular.
 */
export function CalendarioMes({
  mes,
  hoy,
  planes,
  onCambiarMes,
  onDia,
  onPlan,
}: {
  /** "2026-11" */
  mes: string
  hoy: string
  planes: Plan[]
  onCambiarMes: (mes: string) => void
  /** Tocar un día vacío (para planificar ahí). Sin esto, los días no se pueden tocar. */
  onDia?: (fecha: string) => void
  onPlan: (plan: Plan) => void
}) {
  const { desde, hasta } = rangoDelMes(mes)
  const dias: string[] = []
  for (let d = deISO(desde); aISO(d) <= hasta; d.setDate(d.getDate() + 1)) dias.push(aISO(d))
  const porDia = new Map<string, Plan[]>()
  planes.forEach((p) => porDia.set(p.fecha, [...(porDia.get(p.fecha) ?? []), p]))
  const delMes = planes.filter((p) => p.fecha.startsWith(mes))

  const mover = (delta: number) => {
    const d = deISO(`${mes}-01`)
    d.setMonth(d.getMonth() + delta)
    onCambiarMes(aISO(d).slice(0, 7))
  }

  return (
    <section className="rounded-xl border border-stone-200 bg-white dark:border-stone-800 dark:bg-stone-900">
      <div className="flex items-center justify-between gap-2 border-b border-stone-200 px-4 py-3 dark:border-stone-800">
        <h2 className="font-semibold">{conMayuscula(nombreMes.format(deISO(`${mes}-01`)))}</h2>
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => onCambiarMes(hoy.slice(0, 7))}
            className="rounded-md px-2 py-1 text-sm font-medium text-stone-600 hover:bg-stone-100 dark:text-stone-300 dark:hover:bg-stone-800"
          >
            Hoy
          </button>
          <button
            type="button"
            onClick={() => mover(-1)}
            aria-label="Mes anterior"
            className="rounded-md p-1.5 hover:bg-stone-100 dark:hover:bg-stone-800"
          >
            <ChevronLeft className="size-4" />
          </button>
          <button
            type="button"
            onClick={() => mover(1)}
            aria-label="Mes siguiente"
            className="rounded-md p-1.5 hover:bg-stone-100 dark:hover:bg-stone-800"
          >
            <ChevronRight className="size-4" />
          </button>
        </div>
      </div>

      {/* Grilla (desde tablet) */}
      <div className="hidden sm:block">
        <div className="grid grid-cols-7 border-b border-stone-200 text-center text-xs font-medium text-stone-500 dark:border-stone-800">
          {DIAS.map((d) => (
            <div key={d} className="py-2">
              {d}
            </div>
          ))}
        </div>
        <div className="grid grid-cols-7">
          {dias.map((dia) => {
            const enMes = dia.startsWith(mes)
            const delDia = porDia.get(dia) ?? []
            return (
              <div
                key={dia}
                className={`min-h-24 border-r border-b border-stone-100 p-1.5 last:border-r-0 dark:border-stone-800 ${enMes ? '' : 'bg-stone-50/60 dark:bg-stone-950/40'}`}
              >
                <button
                  type="button"
                  disabled={!onDia}
                  onClick={() => onDia?.(dia)}
                  aria-label={`Planificar el ${fechaCorta(dia)}`}
                  className={`grid size-6 place-items-center rounded-full text-xs tabular-nums enabled:hover:bg-stone-100 dark:enabled:hover:bg-stone-800 ${
                    dia === hoy
                      ? 'bg-brand-600 enabled:hover:bg-brand-700 font-semibold text-white'
                      : enMes
                        ? ''
                        : 'text-stone-400'
                  }`}
                >
                  {Number(dia.slice(8))}
                </button>
                <ul className="mt-1 space-y-1">
                  {delDia.map((p) => (
                    <li key={p.id}>
                      <button
                        type="button"
                        onClick={() => onPlan(p)}
                        title={p.descripcion}
                        className={`block w-full truncate rounded px-1.5 py-0.5 text-left text-xs font-medium ${claseDePlan(p)}`}
                      >
                        {p.descripcion}
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            )
          })}
        </div>
      </div>

      {/* Agenda (celular) */}
      <ul className="divide-y divide-stone-100 sm:hidden dark:divide-stone-800">
        {delMes.length === 0 && (
          <li className="px-4 py-6 text-center text-sm text-stone-500">
            No hay tratamientos este mes.
          </li>
        )}
        {delMes.map((p) => (
          <li key={p.id}>
            <button
              type="button"
              onClick={() => onPlan(p)}
              className="flex w-full items-center gap-3 px-4 py-3 text-left"
            >
              <span
                className={`w-20 shrink-0 text-xs font-medium ${p.fecha === hoy ? 'text-brand-700 dark:text-brand-200' : 'text-stone-500'}`}
              >
                {conMayuscula(fechaCorta(p.fecha))}
              </span>
              <span
                className={`min-w-0 flex-1 truncate rounded px-2 py-1 text-sm font-medium ${claseDePlan(p)}`}
              >
                {p.descripcion}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  )
}
