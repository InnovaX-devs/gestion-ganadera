import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from './Button'

interface PaginacionProps {
  pagina: number
  totalPaginas: number
  total: number
  tamanio: number
  onPagina: (p: number) => void
}

/** Pie de un listado paginado: "51–100 de 230" y anterior/siguiente. */
export function Paginacion({ pagina, totalPaginas, total, tamanio, onPagina }: PaginacionProps) {
  const desde = pagina * tamanio + 1
  const hasta = Math.min(total, (pagina + 1) * tamanio)
  return (
    <div className="flex items-center justify-between gap-4 border-t border-stone-200 px-4 py-3 text-sm dark:border-stone-800">
      <span className="text-stone-500 tabular-nums">
        {desde}–{hasta} de {total}
      </span>
      <div className="flex gap-1">
        <Button
          variant="secondary"
          className="px-2.5 py-1.5"
          disabled={pagina === 0}
          onClick={() => onPagina(pagina - 1)}
          aria-label="Página anterior"
        >
          <ChevronLeft className="size-4" />
        </Button>
        <Button
          variant="secondary"
          className="px-2.5 py-1.5"
          disabled={pagina >= totalPaginas - 1}
          onClick={() => onPagina(pagina + 1)}
          aria-label="Página siguiente"
        >
          <ChevronRight className="size-4" />
        </Button>
      </div>
    </div>
  )
}
