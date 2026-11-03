import { useState } from 'react'
import { CalendarCheck, Pencil, RotateCcw, Syringe, Trash2, X } from 'lucide-react'
import { Button } from '@/components/Button'
import { Dialog } from '@/components/Dialog'
import { FormError } from '@/components/FormError'
import { useProductos } from '@/features/sanidad/queries'
import { TratamientoDialog } from '@/features/sanidad/TratamientoDialog'
import { getErrorMessage } from '@/lib/api'
import { textoDestino } from './destino'
import { conMayuscula, cuando, fechaCorta } from './fechas'
import { type AccionPlan, useAccionPlan } from './queries'
import type { Plan } from './types'

const secundaria =
  'inline-flex items-center gap-1 font-medium text-stone-600 hover:text-stone-900 disabled:opacity-50 dark:text-stone-400 dark:hover:text-stone-200'

const ESTADOS = {
  PENDIENTE: 'Pendiente',
  REALIZADO: 'Hecho',
  CANCELADO: 'Cancelado',
}

/**
 * Un tratamiento planificado y lo que se puede hacer con él. "Registrar tratamiento" abre el
 * formulario de siempre ya completo (lote o rodeo, producto, dosis) y, al guardarlo, lo marca como hecho.
 */
export function PlanDetalleDialog({
  establecimientoId,
  plan,
  hoy,
  puedeEditar,
  onEditar,
  onClose,
}: {
  establecimientoId: string
  plan: Plan | null
  hoy: string
  puedeEditar: boolean
  onEditar: (plan: Plan) => void
  onClose: () => void
}) {
  const accion = useAccionPlan(establecimientoId)
  const { data: productos } = useProductos(establecimientoId)
  const [registrando, setRegistrando] = useState(false)
  const [confirmarBorrar, setConfirmarBorrar] = useState(false)
  const [aviso, setAviso] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  if (!plan) return null
  const producto = productos?.find((p) => p.id === plan.producto?.id)

  async function hacer(nombre: AccionPlan, operacionId?: string) {
    setError(null)
    try {
      await accion.mutateAsync({ id: plan!.id, accion: nombre, operacionId })
      if (nombre === 'eliminar') onClose()
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  function cerrar() {
    setConfirmarBorrar(false)
    setAviso(null)
    setError(null)
    onClose()
  }

  return (
    <>
      <Dialog open onClose={cerrar} title={plan.descripcion} size="sm">
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
          <dt className="text-stone-500">Fecha</dt>
          <dd>
            {conMayuscula(fechaCorta(plan.fecha))}{' '}
            <span className="text-stone-500">({cuando(plan.fecha, hoy)})</span>
          </dd>
          <dt className="text-stone-500">Se aplica a</dt>
          <dd>{textoDestino(plan)}</dd>
          {plan.producto && (
            <>
              <dt className="text-stone-500">Producto</dt>
              <dd>{plan.producto.nombre}</dd>
            </>
          )}
          {plan.dosis && (
            <>
              <dt className="text-stone-500">Dosis</dt>
              <dd>{plan.dosis}</dd>
            </>
          )}
          <dt className="text-stone-500">Estado</dt>
          <dd>
            {ESTADOS[plan.estado]}
            {plan.vencido && (
              <span className="ml-1 font-medium text-red-700 dark:text-red-400">· vencido</span>
            )}
          </dd>
          <dt className="text-stone-500">Aviso</dt>
          <dd>
            {plan.avisarDiasAntes === 0
              ? 'El mismo día'
              : `${plan.avisarDiasAntes} ${plan.avisarDiasAntes === 1 ? 'día' : 'días'} antes`}
          </dd>
          {plan.notas && (
            <>
              <dt className="text-stone-500">Notas</dt>
              <dd className="whitespace-pre-line">{plan.notas}</dd>
            </>
          )}
          {plan.creadoPor && (
            <>
              <dt className="text-stone-500">Planificó</dt>
              <dd>{plan.creadoPor}</dd>
            </>
          )}
        </dl>

        {aviso && (
          <p className="mt-4 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:bg-amber-950/40 dark:text-amber-200">
            {aviso}
          </p>
        )}
        <FormError message={error} />

        {puedeEditar && (
          <div className="mt-5 flex flex-wrap justify-end gap-2">
            {confirmarBorrar ? (
              <>
                <span className="self-center text-sm text-stone-600 dark:text-stone-400">
                  ¿Eliminarlo del calendario?
                </span>
                <Button variant="secondary" onClick={() => setConfirmarBorrar(false)}>
                  No
                </Button>
                <Button
                  variant="danger"
                  loading={accion.isPending}
                  onClick={() => hacer('eliminar')}
                >
                  Sí, eliminar
                </Button>
              </>
            ) : plan.estado === 'PENDIENTE' ? (
              <div className="flex w-full flex-col gap-3">
                <div className="flex flex-wrap justify-end gap-2">
                  <Button
                    variant="secondary"
                    onClick={() => hacer('realizado')}
                    disabled={accion.isPending}
                  >
                    <CalendarCheck className="size-4" />
                    Marcar como hecho
                  </Button>
                  <Button onClick={() => setRegistrando(true)}>
                    <Syringe className="size-4" />
                    Registrar tratamiento
                  </Button>
                </div>
                <div className="flex flex-wrap justify-end gap-x-4 gap-y-1 text-sm">
                  <button type="button" onClick={() => onEditar(plan)} className={secundaria}>
                    <Pencil className="size-3.5" /> Editar
                  </button>
                  <button
                    type="button"
                    onClick={() => hacer('cancelar')}
                    disabled={accion.isPending}
                    className={secundaria}
                  >
                    <X className="size-3.5" /> No se hace
                  </button>
                  <button
                    type="button"
                    onClick={() => setConfirmarBorrar(true)}
                    className={`${secundaria} hover:text-red-700`}
                  >
                    <Trash2 className="size-3.5" /> Eliminar
                  </button>
                </div>
              </div>
            ) : (
              <>
                <Button variant="secondary" onClick={() => setConfirmarBorrar(true)}>
                  <Trash2 className="size-4" />
                  Eliminar
                </Button>
                <Button
                  variant="secondary"
                  onClick={() => hacer('reabrir')}
                  disabled={accion.isPending}
                >
                  <RotateCcw className="size-4" />
                  Volver a pendiente
                </Button>
              </>
            )}
          </div>
        )}
      </Dialog>

      <TratamientoDialog
        establecimientoId={establecimientoId}
        open={registrando}
        inicial={{
          loteId: plan.destino.tipo === 'LOTE' ? plan.destino.id : undefined,
          rodeoId: plan.destino.tipo === 'RODEO' ? plan.destino.id : undefined,
          productoId: plan.producto?.id,
          dosis: plan.dosis ?? producto?.dosisHabitual ?? undefined,
          via: producto?.viaHabitual ?? undefined,
          diasCarencia: producto?.diasCarencia,
        }}
        onClose={() => setRegistrando(false)}
        onHecho={(operacion) => {
          if (operacion.pendiente) {
            // Sin conexión el tratamiento quedó en el dispositivo: el plan se marca cuando haya red
            setAviso(
              'El tratamiento se guardó sin conexión. Cuando se sincronice, marcá este plan como hecho.',
            )
          } else {
            hacer('realizado', operacion.operacionId)
          }
        }}
      />
    </>
  )
}
