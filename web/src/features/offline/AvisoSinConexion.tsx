import { WifiOff } from 'lucide-react'
import { useEnLinea, usePendientes } from './hooks'

/** Franja arriba de la página mientras no hay conexión (#30). */
export function AvisoSinConexion() {
  const enLinea = useEnLinea()
  const pendientes = usePendientes().filter((p) => p.estado !== 'RECHAZADO').length
  if (enLinea) return null
  return (
    <div
      role="status"
      className="flex items-center gap-2 border-b border-amber-200 bg-amber-50 px-4 py-2 text-sm text-amber-900 sm:px-6 lg:px-8 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-200"
    >
      <WifiOff className="size-4 shrink-0" aria-hidden />
      <p className="min-w-0">
        <strong className="font-semibold">Sin conexión.</strong> Lo que registres se guarda en este
        dispositivo y se envía cuando vuelva la señal.
        {pendientes > 0 && (
          <span className="font-medium">
            {' '}
            {pendientes === 1 ? 'Hay 1 carga pendiente.' : `Hay ${pendientes} cargas pendientes.`}
          </span>
        )}
      </p>
    </div>
  )
}
