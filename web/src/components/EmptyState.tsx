import type { ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'

interface EmptyStateProps {
  icono: LucideIcon
  titulo: string
  texto?: string
  accion?: ReactNode
}

/** Estado vacío de un listado: ícono, título, explicación y una acción opcional. */
export function EmptyState({ icono: Icono, titulo, texto, accion }: EmptyStateProps) {
  return (
    <div className="grid place-items-center rounded-xl border border-dashed border-stone-300 bg-white px-6 py-14 text-center dark:border-stone-700 dark:bg-stone-900">
      <span className="bg-brand-50 text-brand-600 dark:bg-brand-900/40 dark:text-brand-200 grid size-12 place-items-center rounded-full">
        <Icono className="size-6" />
      </span>
      <p className="mt-4 font-medium">{titulo}</p>
      {texto && <p className="mt-1 max-w-sm text-sm text-stone-500">{texto}</p>}
      {accion && <div className="mt-6">{accion}</div>}
    </div>
  )
}
