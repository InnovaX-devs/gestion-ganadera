import { useState } from 'react'
import { Link } from 'react-router'
import type { Rol } from '@/features/establecimientos/permisos'
import { NacimientoDialog } from '@/features/eventos/NacimientoDialog'
import { ObservacionDialog } from '@/features/eventos/ObservacionDialog'
import { TIPOS_EVENTO, puedeRegistrarTipo } from '@/features/eventos/config'
import type { TipoEvento } from '@/features/eventos/types'
import { TratamientoDialog } from '@/features/sanidad/TratamientoDialog'

type Dialogo = 'NACIMIENTO' | 'TRATAMIENTO' | 'OBSERVACION'

interface Acceso {
  tipo: TipoEvento
  descripcion: string
  /** Se registra en un diálogo acá mismo... */
  dialogo?: Dialogo
  /** ...o en su página (el pesaje se carga en la planilla de /pesajes). */
  ruta?: string
}

/**
 * Tipos de evento que ya se pueden registrar (#26). Al implementar uno nuevo (#19 a #23), agregarlo
 * acá: con `dialogo` si tiene uno que funcione sin animal elegido, o con `ruta` a su página.
 */
const ACCESOS: Acceso[] = [
  { tipo: 'NACIMIENTO', descripcion: 'Parto y crías', dialogo: 'NACIMIENTO' },
  { tipo: 'TRATAMIENTO', descripcion: 'Vacuna o remedio', dialogo: 'TRATAMIENTO' },
  { tipo: 'PESAJE', descripcion: 'Planilla de pesadas', ruta: '/pesajes' },
  { tipo: 'OBSERVACION', descripcion: 'Nota de la recorrida', dialogo: 'OBSERVACION' },
]

const claseAcceso =
  'group flex h-full flex-col items-start gap-2 rounded-lg border border-stone-200 px-3 py-3 text-left outline-none sm:flex-row sm:items-center sm:gap-3 hover:border-brand-300 hover:bg-brand-50 focus-visible:ring-2 focus-visible:ring-brand-300 dark:border-stone-800 dark:hover:border-brand-800 dark:hover:bg-brand-900/20'

/** Botones para registrar eventos desde el inicio. Solo muestra los que el rol puede registrar. */
export function AccesosRapidos({
  establecimientoId,
  rol,
}: {
  establecimientoId: string
  rol: Rol
}) {
  const [abierto, setAbierto] = useState<Dialogo | null>(null)
  const accesos = ACCESOS.filter((a) => puedeRegistrarTipo(rol, a.tipo))
  if (accesos.length === 0) return null

  const cerrar = () => setAbierto(null)

  return (
    <section className="rounded-xl border border-stone-200 bg-white p-5 dark:border-stone-800 dark:bg-stone-900">
      <h2 className="text-sm font-medium text-stone-500">Registrar</h2>
      <ul className="mt-3 grid grid-cols-2 gap-2 lg:grid-cols-4">
        {accesos.map((a) => (
          <li key={a.tipo} className="min-w-0">
            {a.ruta ? (
              <Link to={a.ruta} className={claseAcceso}>
                <Contenido acceso={a} />
              </Link>
            ) : (
              <button
                type="button"
                onClick={() => setAbierto(a.dialogo!)}
                className={`${claseAcceso} w-full`}
              >
                <Contenido acceso={a} />
              </button>
            )}
          </li>
        ))}
      </ul>

      <NacimientoDialog
        establecimientoId={establecimientoId}
        open={abierto === 'NACIMIENTO'}
        onClose={cerrar}
      />
      <TratamientoDialog
        establecimientoId={establecimientoId}
        open={abierto === 'TRATAMIENTO'}
        onClose={cerrar}
      />
      <ObservacionDialog
        establecimientoId={establecimientoId}
        open={abierto === 'OBSERVACION'}
        onClose={cerrar}
      />
    </section>
  )
}

function Contenido({ acceso }: { acceso: Acceso }) {
  const { etiqueta, icono: Icono } = TIPOS_EVENTO[acceso.tipo]
  return (
    <>
      <span className="bg-brand-50 text-brand-700 group-hover:bg-brand-100 dark:bg-brand-900/40 dark:text-brand-200 grid size-9 shrink-0 place-items-center rounded-lg">
        <Icono className="size-[1.125rem]" />
      </span>
      <span className="w-full min-w-0">
        <span className="block truncate text-sm font-medium">{etiqueta}</span>
        <span className="block truncate text-xs text-stone-500">{acceso.descripcion}</span>
      </span>
    </>
  )
}
