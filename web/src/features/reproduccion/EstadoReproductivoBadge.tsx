import { ESTADOS_REPRODUCTIVOS } from './config'
import type { EstadoReproductivo } from './types'

/**
 * Estado reproductivo de una vaca o vaquillona. En el resto de los animales (y sin datos) no
 * muestra nada, así se puede poner en cualquier fila sin preguntar antes.
 */
export function EstadoReproductivoBadge({ estado }: { estado?: EstadoReproductivo | null }) {
  if (!estado || estado.estado === 'SIN_DATOS') return null
  const { etiqueta, clases } = ESTADOS_REPRODUCTIVOS[estado.estado]
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium whitespace-nowrap ${clases}`}
    >
      {etiqueta}
    </span>
  )
}
