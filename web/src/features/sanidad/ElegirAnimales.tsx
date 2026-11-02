import { useState } from 'react'
import { Search, X } from 'lucide-react'
import { CategoriaBadge } from '@/features/animales/CategoriaBadge'
import { useAnimales } from '@/features/animales/queries'
import type { Animal, FiltroAnimales, Referencia } from '@/features/animales/types'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'

interface Props {
  establecimientoId: string
  elegidos: Referencia[]
  onChange: (elegidos: Referencia[]) => void
  error?: string
  /** Por defecto "Animales". */
  etiqueta?: string
  /** Filtro que se manda al buscar (ej: solo machos). */
  filtro?: Pick<FiltroAnimales, 'sexo' | 'categoria'>
  /** Para afinar lo que el filtro del servidor no alcanza (ej: vacas y vaquillonas). */
  aplica?: (animal: Animal) => boolean
  /** Tope de elegidos: al llegar, se deja de ofrecer resultados. */
  maximo?: number
  hint?: string
}

/** Buscar animales por caravana y armar la lista a la que se aplica un evento. */
export function ElegirAnimales({
  establecimientoId,
  elegidos,
  onChange,
  error,
  etiqueta = 'Animales',
  filtro,
  aplica,
  maximo,
  hint,
}: Props) {
  const [busqueda, setBusqueda] = useState('')
  const q = useDebouncedValue(busqueda.trim())
  // Con un filtro fino se piden más, para que queden resultados después de filtrar
  const { data } = useAnimales(establecimientoId, { q, ...filtro, tamanio: aplica ? 12 : 6 })
  const lleno = maximo !== undefined && elegidos.length >= maximo
  const resultados =
    q && !lleno
      ? (data?.contenido ?? [])
          .filter((a) => !elegidos.some((e) => e.id === a.id))
          .filter((a) => !aplica || aplica(a))
          .slice(0, 6)
      : []

  return (
    <div>
      <label className="mb-1.5 block text-sm font-medium">{etiqueta}</label>
      <div className="relative">
        <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-stone-400" />
        <input
          type="search"
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
          placeholder={lleno ? 'Ya elegiste el máximo' : 'Buscar por caravana…'}
          disabled={lleno}
          aria-label={`Buscar ${etiqueta.toLowerCase()} por caravana`}
          className="focus:border-brand-500 focus:ring-brand-100 dark:focus:ring-brand-900 block w-full rounded-lg border border-stone-300 bg-white py-2 pr-3 pl-9 text-sm shadow-xs outline-none focus:ring-2 disabled:opacity-60 dark:border-stone-700 dark:bg-stone-900"
        />
      </div>
      {resultados.length > 0 && (
        <ul className="mt-1 divide-y divide-stone-100 rounded-lg border border-stone-200 dark:divide-stone-800 dark:border-stone-800">
          {resultados.map((a) => (
            <li key={a.id}>
              <button
                type="button"
                onClick={() => {
                  onChange([...elegidos, { id: a.id, nombre: a.caravana }])
                  setBusqueda('')
                }}
                className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-stone-50 dark:hover:bg-stone-800"
              >
                <span className="font-semibold tabular-nums">{a.caravana}</span>
                <CategoriaBadge animal={a} />
              </button>
            </li>
          ))}
        </ul>
      )}
      {elegidos.length > 0 && (
        <ul className="mt-2 flex flex-wrap gap-1.5">
          {elegidos.map((a) => (
            <li
              key={a.id}
              className="bg-brand-50 text-brand-700 dark:bg-brand-900/40 dark:text-brand-200 inline-flex items-center gap-1 rounded-full py-0.5 pr-1 pl-2.5 text-xs font-medium tabular-nums"
            >
              {a.nombre}
              <button
                type="button"
                onClick={() => onChange(elegidos.filter((e) => e.id !== a.id))}
                aria-label={`Quitar ${a.nombre}`}
                className="rounded-full p-0.5 hover:bg-white/60 dark:hover:bg-stone-800"
              >
                <X className="size-3" />
              </button>
            </li>
          ))}
        </ul>
      )}
      {(error || hint) && (
        <p
          className={`mt-1.5 text-xs ${error ? 'text-red-600 dark:text-red-400' : 'text-stone-500'}`}
        >
          {error ?? hint}
        </p>
      )}
    </div>
  )
}
