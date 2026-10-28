import { useState } from 'react'
import { Link } from 'react-router'
import { LoaderCircle, MapPinned, Pencil, Plus, Trash2 } from 'lucide-react'
import { Button } from '@/components/Button'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { FormError } from '@/components/FormError'
import { PageHeader } from '@/components/PageHeader'
import { AgrupacionFormDialog } from '@/features/agrupaciones/AgrupacionFormDialog'
import { AGRUPACIONES } from '@/features/agrupaciones/config'
import { useAgrupaciones, useEliminarAgrupacion } from '@/features/agrupaciones/queries'
import type { Agrupacion, TipoAgrupacion } from '@/features/agrupaciones/types'
import { puede } from '@/features/establecimientos/permisos'
import { useEstablecimientoActivo } from '@/features/establecimientos/useEstablecimientoActivo'
import { getErrorMessage } from '@/lib/api'

/** Página de lotes o de rodeos (misma pantalla, distinta configuración). */
export function AgrupacionesPage({ tipo }: { tipo: TipoAgrupacion }) {
  const config = AGRUPACIONES[tipo]
  const { activo, cargando } = useEstablecimientoActivo()

  if (cargando) return <Cargando />

  if (!activo) {
    return (
      <>
        <PageHeader title={config.titulo} description={config.descripcion} />
        <Vacio
          icono={MapPinned}
          titulo="Primero creá un establecimiento"
          texto={`Los ${config.plural} pertenecen a un establecimiento.`}
          accion={
            <Link
              to="/establecimientos"
              state={{ crear: true }}
              className="bg-brand-600 hover:bg-brand-700 mt-6 inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium text-white"
            >
              <Plus className="size-4" />
              Crear establecimiento
            </Link>
          }
        />
      </>
    )
  }

  // key: al cambiar de establecimiento, el listado arranca limpio (sin diálogos abiertos)
  return (
    <Listado
      key={activo.id}
      tipo={tipo}
      establecimientoId={activo.id}
      establecimientoNombre={activo.nombre}
      puedeGestionar={puede(activo.rol, 'GESTIONAR_AGRUPACIONES')}
    />
  )
}

function Listado({
  tipo,
  establecimientoId,
  establecimientoNombre,
  puedeGestionar,
}: {
  tipo: TipoAgrupacion
  establecimientoId: string
  establecimientoNombre: string
  /** Crear, editar y eliminar. Los demás roles solo ven el listado. */
  puedeGestionar: boolean
}) {
  const config = AGRUPACIONES[tipo]
  const { data, isPending, error } = useAgrupaciones(tipo, establecimientoId)
  const eliminar = useEliminarAgrupacion(tipo, establecimientoId)
  const [formulario, setFormulario] = useState<{ agrupacion: Agrupacion | null } | null>(null)
  const [aBorrar, setABorrar] = useState<Agrupacion | null>(null)

  const nuevo = () => setFormulario({ agrupacion: null })
  const cerrarBorrado = () => {
    setABorrar(null)
    eliminar.reset()
  }

  return (
    <>
      <PageHeader
        title={config.titulo}
        description={`${config.descripcion} · ${establecimientoNombre}`}
        actions={
          puedeGestionar &&
          data &&
          data.length > 0 && (
            <Button onClick={nuevo}>
              <Plus className="size-4" />
              Nuevo {config.singular}
            </Button>
          )
        }
      />

      {isPending && <Cargando />}
      {error && <FormError message={getErrorMessage(error)} />}

      {data?.length === 0 && (
        <Vacio
          icono={config.icono}
          titulo={`Todavía no hay ${config.plural} en ${establecimientoNombre}`}
          texto={config.descripcion}
          accion={
            puedeGestionar && (
              <Button className="mt-6" onClick={nuevo}>
                <Plus className="size-4" />
                Crear {config.singular}
              </Button>
            )
          }
        />
      )}

      {data && data.length > 0 && (
        <ul className="divide-y divide-stone-200 overflow-hidden rounded-xl border border-stone-200 bg-white dark:divide-stone-800 dark:border-stone-800 dark:bg-stone-900">
          {data.map((a) => (
            <li key={a.id} className="flex items-center gap-4 px-4 py-3.5 sm:px-5">
              <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-stone-100 text-stone-500 dark:bg-stone-800">
                <config.icono className="size-4.5" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium">{a.nombre}</p>
                {a.descripcion && (
                  <p className="truncate text-sm text-stone-500">{a.descripcion}</p>
                )}
              </div>
              <span className="hidden shrink-0 rounded-full bg-stone-100 px-2.5 py-0.5 text-xs font-medium text-stone-600 tabular-nums sm:inline dark:bg-stone-800 dark:text-stone-300">
                {a.cantidadAnimales} {a.cantidadAnimales === 1 ? 'animal' : 'animales'}
              </span>
              {puedeGestionar && (
                <div className="flex shrink-0 gap-1">
                  <BotonIcono
                    label={`Editar ${a.nombre}`}
                    onClick={() => setFormulario({ agrupacion: a })}
                  >
                    <Pencil className="size-4" />
                  </BotonIcono>
                  <BotonIcono label={`Eliminar ${a.nombre}`} peligro onClick={() => setABorrar(a)}>
                    <Trash2 className="size-4" />
                  </BotonIcono>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      <AgrupacionFormDialog
        tipo={tipo}
        establecimientoId={establecimientoId}
        open={formulario !== null}
        agrupacion={formulario?.agrupacion ?? null}
        onClose={() => setFormulario(null)}
      />

      <ConfirmDialog
        open={aBorrar !== null}
        title={`Eliminar ${config.singular}`}
        description={`"${aBorrar?.nombre}" dejará de aparecer en el sistema.`}
        confirmLabel="Eliminar"
        loading={eliminar.isPending}
        error={eliminar.error ? getErrorMessage(eliminar.error) : null}
        onClose={cerrarBorrado}
        onConfirm={() => aBorrar && eliminar.mutate(aBorrar.id, { onSuccess: cerrarBorrado })}
      />
    </>
  )
}

function Cargando() {
  return (
    <div className="grid place-items-center py-16">
      <LoaderCircle className="size-6 animate-spin text-stone-400" />
    </div>
  )
}

function Vacio({
  icono: Icono,
  titulo,
  texto,
  accion,
}: {
  icono: typeof MapPinned
  titulo: string
  texto: string
  accion: React.ReactNode
}) {
  return (
    <div className="grid place-items-center rounded-xl border border-dashed border-stone-300 bg-white px-6 py-16 text-center dark:border-stone-700 dark:bg-stone-900">
      <span className="bg-brand-50 text-brand-600 dark:bg-brand-900/40 dark:text-brand-200 grid size-12 place-items-center rounded-full">
        <Icono className="size-6" />
      </span>
      <p className="mt-4 font-medium">{titulo}</p>
      <p className="mt-1 max-w-sm text-sm text-stone-500">{texto}</p>
      {accion}
    </div>
  )
}

function BotonIcono({
  label,
  peligro,
  onClick,
  children,
}: {
  label: string
  peligro?: boolean
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={label}
      className={`rounded-md p-2 text-stone-500 transition ${
        peligro
          ? 'hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-950/50'
          : 'hover:bg-stone-100 hover:text-stone-700 dark:hover:bg-stone-800 dark:hover:text-stone-200'
      }`}
    >
      {children}
    </button>
  )
}
