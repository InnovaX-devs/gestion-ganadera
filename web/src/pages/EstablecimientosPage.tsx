import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { Hash, LoaderCircle, MapPin, MapPinned, Pencil, Plus, Ruler, Trash2 } from 'lucide-react'
import { Button } from '@/components/Button'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { FormError } from '@/components/FormError'
import { PageHeader } from '@/components/PageHeader'
import { EstablecimientoFormDialog } from '@/features/establecimientos/EstablecimientoFormDialog'
import {
  useEliminarEstablecimiento,
  useEstablecimientos,
} from '@/features/establecimientos/queries'
import type { Establecimiento } from '@/features/establecimientos/types'
import { useEstablecimientoActivo } from '@/features/establecimientos/useEstablecimientoActivo'
import { getErrorMessage } from '@/lib/api'

const hectareas = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 2 })

type Formulario = { abierto: false } | { abierto: true; establecimiento: Establecimiento | null }

export function EstablecimientosPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const { data: establecimientos, isPending, error } = useEstablecimientos()
  const { activo, seleccionar } = useEstablecimientoActivo()
  const eliminar = useEliminarEstablecimiento()

  // Si se llega desde "Crear establecimiento" (header o inicio), el formulario arranca abierto
  const [formulario, setFormulario] = useState<Formulario>(() =>
    (location.state as { crear?: boolean } | null)?.crear
      ? { abierto: true, establecimiento: null }
      : { abierto: false },
  )
  const [aBorrar, setABorrar] = useState<Establecimiento | null>(null)

  const cerrarFormulario = () => {
    setFormulario({ abierto: false })
    if (location.state) navigate(location.pathname, { replace: true, state: null })
  }

  const cerrarBorrado = () => {
    setABorrar(null)
    eliminar.reset()
  }

  return (
    <>
      <PageHeader
        title="Establecimientos"
        description="Los campos de tu organización. Elegí en cuál trabajar desde el selector de arriba."
        actions={
          establecimientos &&
          establecimientos.length > 0 && (
            <Button onClick={() => setFormulario({ abierto: true, establecimiento: null })}>
              <Plus className="size-4" />
              Nuevo establecimiento
            </Button>
          )
        }
      />

      {isPending && (
        <div className="grid place-items-center py-16">
          <LoaderCircle className="size-6 animate-spin text-stone-400" />
        </div>
      )}

      {error && <FormError message={getErrorMessage(error)} />}

      {establecimientos?.length === 0 && (
        <div className="grid place-items-center rounded-xl border border-dashed border-stone-300 bg-white px-6 py-16 text-center dark:border-stone-700 dark:bg-stone-900">
          <span className="bg-brand-50 text-brand-600 dark:bg-brand-900/40 dark:text-brand-200 grid size-12 place-items-center rounded-full">
            <MapPinned className="size-6" />
          </span>
          <p className="mt-4 font-medium">Todavía no cargaste ningún establecimiento</p>
          <p className="mt-1 max-w-sm text-sm text-stone-500">
            Creá tu primer campo para empezar a organizar lotes, rodeos y animales.
          </p>
          <Button
            className="mt-6"
            onClick={() => setFormulario({ abierto: true, establecimiento: null })}
          >
            <Plus className="size-4" />
            Crear establecimiento
          </Button>
        </div>
      )}

      {establecimientos && establecimientos.length > 0 && (
        <ul className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {establecimientos.map((e) => {
            const esActivo = e.id === activo?.id
            const ubicacion = [e.localidad, e.provincia].filter(Boolean).join(', ')
            return (
              <li
                key={e.id}
                className={`flex flex-col rounded-xl border bg-white p-5 dark:bg-stone-900 ${
                  esActivo
                    ? 'border-brand-300 ring-brand-100 dark:border-brand-700 dark:ring-brand-900/50 ring-2'
                    : 'border-stone-200 dark:border-stone-800'
                }`}
              >
                <div className="flex items-start justify-between gap-3">
                  <h2 className="min-w-0 truncate font-semibold">{e.nombre}</h2>
                  {esActivo && (
                    <span className="bg-brand-50 text-brand-700 dark:bg-brand-900/40 dark:text-brand-200 shrink-0 rounded-full px-2 py-0.5 text-xs font-medium">
                      Activo
                    </span>
                  )}
                </div>

                <dl className="mt-3 flex-1 space-y-1.5 text-sm text-stone-600 dark:text-stone-400">
                  <Dato
                    icono={MapPin}
                    etiqueta="Ubicación"
                    valor={ubicacion || null}
                    vacio="Sin ubicación"
                  />
                  <Dato
                    icono={Ruler}
                    etiqueta="Superficie"
                    vacio="Sin superficie"
                    valor={
                      e.superficieHa !== null ? `${hectareas.format(e.superficieHa)} ha` : null
                    }
                  />
                  <Dato icono={Hash} etiqueta="RENSPA" valor={e.renspa} vacio="Sin RENSPA" />
                </dl>

                <div className="mt-4 flex items-center gap-2 border-t border-stone-100 pt-4 dark:border-stone-800">
                  {!esActivo && (
                    <Button
                      variant="secondary"
                      className="mr-auto px-3 py-1.5"
                      onClick={() => seleccionar(e.id)}
                    >
                      Trabajar en este
                    </Button>
                  )}
                  <div className="ml-auto flex gap-1">
                    <IconButton
                      label={`Editar ${e.nombre}`}
                      onClick={() => setFormulario({ abierto: true, establecimiento: e })}
                    >
                      <Pencil className="size-4" />
                    </IconButton>
                    <IconButton
                      label={`Eliminar ${e.nombre}`}
                      peligro
                      onClick={() => setABorrar(e)}
                    >
                      <Trash2 className="size-4" />
                    </IconButton>
                  </div>
                </div>
              </li>
            )
          })}
        </ul>
      )}

      <EstablecimientoFormDialog
        open={formulario.abierto}
        establecimiento={formulario.abierto ? formulario.establecimiento : null}
        onClose={cerrarFormulario}
        onGuardado={(e) => {
          // Al crear uno nuevo, queda elegido para empezar a trabajar en él
          if (!formulario.abierto || !formulario.establecimiento) seleccionar(e.id)
        }}
      />

      <ConfirmDialog
        open={aBorrar !== null}
        title="Eliminar establecimiento"
        description={`"${aBorrar?.nombre}" dejará de aparecer en el sistema. Esta acción no se puede deshacer desde la app.`}
        confirmLabel="Eliminar"
        loading={eliminar.isPending}
        error={eliminar.error ? getErrorMessage(eliminar.error) : null}
        onClose={cerrarBorrado}
        onConfirm={() => aBorrar && eliminar.mutate(aBorrar.id, { onSuccess: cerrarBorrado })}
      />
    </>
  )
}

function Dato({
  icono: Icono,
  etiqueta,
  valor,
  vacio,
}: {
  icono: typeof MapPin
  etiqueta: string
  valor: string | null
  vacio: string
}) {
  return (
    <div className="flex items-center gap-2">
      <Icono className="size-4 shrink-0 text-stone-400" aria-hidden />
      <dt className="sr-only">{etiqueta}</dt>
      <dd className={`truncate ${valor ? '' : 'text-stone-400 italic dark:text-stone-600'}`}>
        {valor ?? vacio}
      </dd>
    </div>
  )
}

function IconButton({
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
