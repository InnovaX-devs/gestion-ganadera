import { ShieldAlert } from 'lucide-react'
import { formatearFecha } from '@/features/eventos/fechas'
import type { Carencia } from './types'

/**
 * Aviso de que el animal está en período de carencia (no faenar ni vender). Sin carencia no
 * muestra nada, así se puede poner en cualquier fila sin preguntar antes.
 */
export function CarenciaBadge({ carencia }: { carencia?: Carencia | null }) {
  if (!carencia) return null
  return (
    <span
      title={`No faenar ni vender hasta el ${formatearFecha(carencia.libreDesde)} (libre desde ese día)`}
      className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium whitespace-nowrap text-amber-800 dark:bg-amber-950/60 dark:text-amber-300"
    >
      <ShieldAlert className="size-3.5" />
      En carencia · libre el {formatearFecha(carencia.libreDesde)}
    </span>
  )
}
