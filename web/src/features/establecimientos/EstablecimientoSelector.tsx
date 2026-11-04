import { useCallback, useRef, useState } from 'react'
import { Link } from 'react-router'
import { Check, ChevronDown, MapPinned, Plus, Settings2 } from 'lucide-react'
import { useDismiss } from '@/hooks/useDismiss'
import { useEstablecimientoActivo } from './useEstablecimientoActivo'

/** Selector del header: elige sobre qué establecimiento se trabaja. */
export function EstablecimientoSelector() {
  const { activo, establecimientos, cargando, seleccionar } = useEstablecimientoActivo()
  const [abierto, setAbierto] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const cerrar = useCallback(() => setAbierto(false), [])
  useDismiss(ref, abierto, cerrar)

  if (cargando) {
    return <div className="h-9 w-44 animate-pulse rounded-lg bg-stone-200 dark:bg-stone-800" />
  }

  if (!activo) {
    return (
      <Link
        to="/establecimientos"
        state={{ crear: true }}
        className="border-brand-300 text-brand-700 hover:bg-brand-50 dark:border-brand-700 dark:text-brand-200 dark:hover:bg-brand-900/30 flex items-center gap-2 rounded-lg border border-dashed px-3 py-1.5 text-sm font-medium"
      >
        <Plus className="size-4" />
        Crear establecimiento
      </Link>
    )
  }

  return (
    <div ref={ref} className="relative min-w-0">
      <button
        type="button"
        onClick={() => setAbierto((a) => !a)}
        aria-haspopup="listbox"
        aria-expanded={abierto}
        className="flex max-w-[60vw] items-center gap-2 rounded-lg border border-stone-200 px-3 py-1.5 text-sm font-medium hover:bg-stone-50 sm:max-w-xs dark:border-stone-700 dark:hover:bg-stone-800"
      >
        <MapPinned className="size-4 shrink-0 text-stone-500" />
        <span className="truncate">{activo.nombre}</span>
        <ChevronDown className="size-4 shrink-0 text-stone-500" />
      </button>

      {abierto && (
        <div className="absolute left-0 z-30 mt-2 w-72 overflow-hidden rounded-xl border border-stone-200 bg-white shadow-lg dark:border-stone-800 dark:bg-stone-900">
          <p className="px-4 pt-3 pb-1 text-xs font-medium tracking-wide text-stone-500 uppercase">
            Establecimientos
          </p>
          <ul role="listbox" className="max-h-72 overflow-y-auto py-1">
            {establecimientos.map((e) => (
              <li key={e.id}>
                <button
                  type="button"
                  role="option"
                  aria-selected={e.id === activo.id}
                  onClick={() => {
                    seleccionar(e.id)
                    cerrar()
                  }}
                  className="flex w-full items-center gap-3 px-4 py-2 text-left text-sm hover:bg-stone-100 dark:hover:bg-stone-800"
                >
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-medium">{e.nombre}</span>
                    {e.localidad && (
                      <span className="block truncate text-xs text-stone-500">{e.localidad}</span>
                    )}
                  </span>
                  {e.id === activo.id && <Check className="text-brand-600 size-4 shrink-0" />}
                </button>
              </li>
            ))}
          </ul>
          <Link
            to="/establecimientos"
            onClick={cerrar}
            className="flex items-center gap-2 border-t border-stone-200 px-4 py-2.5 text-sm text-stone-700 hover:bg-stone-100 dark:border-stone-800 dark:text-stone-300 dark:hover:bg-stone-800"
          >
            <Settings2 className="size-4" />
            Administrar establecimientos
          </Link>
        </div>
      )}
    </div>
  )
}
