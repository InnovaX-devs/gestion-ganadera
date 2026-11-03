import { Link } from 'react-router'
import { CalendarClock, CircleAlert, ShieldCheck } from 'lucide-react'
import { textoDestino } from './destino'
import { cuando } from './fechas'
import { useAlertas } from './queries'
import type { Plan } from './types'

/**
 * Alertas sanitarias (#34): tratamientos vencidos y próximos, y animales que terminan la carencia.
 * En el inicio va compacta y lleva al calendario; en el calendario, cada tratamiento se abre.
 */
export function AlertasSanitarias({
  establecimientoId,
  compacta = false,
  onPlan,
}: {
  establecimientoId: string
  compacta?: boolean
  onPlan?: (plan: Plan) => void
}) {
  const { data } = useAlertas(establecimientoId)
  if (!data) return null
  const { tratamientosVencidos: vencidos, tratamientosProximos: proximos, carencias } = data
  const total = vencidos.length + proximos.length + carencias.salenProximamente.length
  // En el inicio, sin nada que avisar, no ocupa lugar
  if (compacta && total === 0) return null

  const limite = compacta ? 3 : Infinity
  const fila = (p: Plan, vencido: boolean) => {
    const contenido = (
      <>
        <span
          className={`w-20 shrink-0 text-xs font-medium ${vencido ? 'text-red-700 dark:text-red-400' : 'text-amber-700 dark:text-amber-400'}`}
        >
          {cuando(p.fecha, data.hoy)}
        </span>
        <span className="min-w-0 flex-1 truncate">
          <span className="font-medium">{p.descripcion}</span>
          <span className="text-stone-500"> · {textoDestino(p)}</span>
        </span>
      </>
    )
    return (
      <li key={p.id}>
        {onPlan ? (
          <button
            type="button"
            onClick={() => onPlan(p)}
            className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm hover:bg-stone-50 dark:hover:bg-stone-800"
          >
            {contenido}
          </button>
        ) : (
          <div className="flex items-center gap-2 px-2 py-1.5 text-sm">{contenido}</div>
        )}
      </li>
    )
  }

  return (
    <section className="rounded-xl border border-stone-200 bg-white p-5 dark:border-stone-800 dark:bg-stone-900">
      <div className="flex items-baseline justify-between gap-3">
        <h2 className="text-sm font-medium text-stone-500">Alertas sanitarias</h2>
        {compacta && (
          <Link
            to="/calendario-sanitario"
            className="text-brand-700 hover:text-brand-800 dark:text-brand-200 text-sm font-medium"
          >
            Ver calendario
          </Link>
        )}
      </div>

      {total === 0 && (
        <p className="mt-3 flex items-center gap-2 text-sm text-stone-500">
          <ShieldCheck className="size-4 text-emerald-600" />
          No hay tratamientos vencidos ni próximos, ni animales por salir de carencia en los
          próximos {data.diasProximos} días.
        </p>
      )}

      {vencidos.length > 0 && (
        <div className="mt-3">
          <h3 className="flex items-center gap-1.5 text-xs font-semibold tracking-wide text-red-700 uppercase dark:text-red-400">
            <CircleAlert className="size-3.5" /> Vencidos ({vencidos.length})
          </h3>
          <ul className="-mx-2 mt-1">{vencidos.slice(0, limite).map((p) => fila(p, true))}</ul>
        </div>
      )}

      {proximos.length > 0 && (
        <div className="mt-3">
          <h3 className="flex items-center gap-1.5 text-xs font-semibold tracking-wide text-amber-700 uppercase dark:text-amber-400">
            <CalendarClock className="size-3.5" /> Próximos {data.diasProximos} días (
            {proximos.length})
          </h3>
          <ul className="-mx-2 mt-1">{proximos.slice(0, limite).map((p) => fila(p, false))}</ul>
        </div>
      )}

      {carencias.salenProximamente.length > 0 && (
        <div className="mt-3">
          <h3 className="text-xs font-semibold tracking-wide text-sky-700 uppercase dark:text-sky-400">
            Terminan la carencia ({carencias.salenProximamente.length})
          </h3>
          <ul className="mt-1 space-y-1 text-sm">
            {carencias.salenProximamente.slice(0, limite).map((c) => (
              <li key={c.animalId} className="flex items-center gap-2">
                <span className="w-20 shrink-0 text-xs font-medium text-sky-700 dark:text-sky-400">
                  {cuando(c.libreDesde, data.hoy)}
                </span>
                <Link to={`/animales/${c.animalId}`} className="font-medium hover:underline">
                  {c.caravana}
                </Link>
                <span className="truncate text-stone-500">· {c.productos}</span>
              </li>
            ))}
          </ul>
          <p className="mt-1 text-xs text-stone-500">
            Desde esa fecha se pueden vender o faenar. Hoy hay {carencias.animalesEnCarencia}{' '}
            {carencias.animalesEnCarencia === 1 ? 'animal' : 'animales'} en carencia.
          </p>
        </div>
      )}

      {compacta && total > 3 * 3 && (
        <p className="mt-3 text-xs text-stone-500">Hay más: miralas todas en el calendario.</p>
      )}
    </section>
  )
}
