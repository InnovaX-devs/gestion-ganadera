import { useState } from 'react'
import { LoaderCircle, MailPlus, RotateCw, Ban } from 'lucide-react'
import { Button } from '@/components/Button'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { FormError } from '@/components/FormError'
import { PageHeader } from '@/components/PageHeader'
import { ETIQUETA_ROL, puede } from '@/features/establecimientos/permisos'
import { useEstablecimientoActivo } from '@/features/establecimientos/useEstablecimientoActivo'
import { InvitacionFormDialog } from '@/features/invitaciones/InvitacionFormDialog'
import {
  useInvitaciones,
  useReenviarInvitacion,
  useRevocarInvitacion,
} from '@/features/invitaciones/queries'
import {
  ETIQUETA_ESTADO,
  type EstadoInvitacion,
  type Invitacion,
} from '@/features/invitaciones/types'
import { getErrorMessage } from '@/lib/api'

const fecha = new Intl.DateTimeFormat('es-AR', { dateStyle: 'medium', timeStyle: 'short' })

const COLOR_ESTADO: Record<EstadoInvitacion, string> = {
  PENDIENTE: 'bg-amber-50 text-amber-700 dark:bg-amber-900/30 dark:text-amber-200',
  ACEPTADA: 'bg-brand-50 text-brand-700 dark:bg-brand-900/40 dark:text-brand-200',
  VENCIDA: 'bg-stone-100 text-stone-600 dark:bg-stone-800 dark:text-stone-300',
  REVOCADA: 'bg-red-50 text-red-700 dark:bg-red-950/50 dark:text-red-300',
}

export function InvitacionesPage() {
  const { activo, establecimientos, seleccionar, cargando } = useEstablecimientoActivo()
  const puedeInvitar = !!activo && puede(activo.rol, 'GESTIONAR_MIEMBROS')
  const { data: invitaciones, isPending, error } = useInvitaciones(puedeInvitar ? activo.id : null)
  const reenviar = useReenviarInvitacion(activo?.id ?? '')
  const revocar = useRevocarInvitacion(activo?.id ?? '')

  const [formularioAbierto, setFormularioAbierto] = useState(false)
  const [aRevocar, setARevocar] = useState<Invitacion | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const [errorReenvio, setErrorReenvio] = useState<string | null>(null)

  const donde = establecimientos.filter((e) => puede(e.rol, 'GESTIONAR_MIEMBROS'))

  const cerrarRevocar = () => {
    setARevocar(null)
    revocar.reset()
  }

  function handleReenviar(i: Invitacion) {
    setAviso(null)
    setErrorReenvio(null)
    reenviar.mutate(i.id, {
      onSuccess: () => setAviso(`Reenviamos la invitación a ${i.email} con un link nuevo.`),
      onError: (err) => setErrorReenvio(getErrorMessage(err)),
    })
  }

  if (cargando) {
    return (
      <div className="grid place-items-center py-16">
        <LoaderCircle className="size-6 animate-spin text-stone-400" />
      </div>
    )
  }

  return (
    <>
      <PageHeader
        title="Invitaciones"
        description={
          activo
            ? `Personas invitadas a ${activo.nombre}. Podés reenviar el link o cancelar la invitación.`
            : 'Invitá a tu equipo por email.'
        }
        actions={
          donde.length > 0 && (
            <Button onClick={() => setFormularioAbierto(true)}>
              <MailPlus className="size-4" />
              Invitar por email
            </Button>
          )
        }
      />

      {!activo && (
        <p className="text-sm text-stone-600 dark:text-stone-400">
          Primero creá un establecimiento para poder invitar gente.
        </p>
      )}

      {activo && !puedeInvitar && (
        <p className="rounded-lg border border-stone-200 bg-white p-4 text-sm text-stone-600 dark:border-stone-800 dark:bg-stone-900 dark:text-stone-400">
          Solo el dueño de {activo.nombre} puede ver y enviar invitaciones.
        </p>
      )}

      {aviso && (
        <p
          role="status"
          className="bg-brand-50 text-brand-700 dark:bg-brand-900/40 dark:text-brand-200 mb-4 rounded-lg px-3 py-2 text-sm"
        >
          {aviso}
        </p>
      )}
      <FormError message={errorReenvio} />

      {puedeInvitar && isPending && (
        <div className="grid place-items-center py-16">
          <LoaderCircle className="size-6 animate-spin text-stone-400" />
        </div>
      )}
      {error && <FormError message={getErrorMessage(error)} />}

      {invitaciones?.length === 0 && (
        <div className="grid place-items-center rounded-xl border border-dashed border-stone-300 bg-white px-6 py-16 text-center dark:border-stone-700 dark:bg-stone-900">
          <p className="font-medium">Todavía no invitaste a nadie</p>
          <p className="mt-1 max-w-sm text-sm text-stone-500">
            Mandá una invitación por email y la persona se suma con el rol que elijas.
          </p>
        </div>
      )}

      {invitaciones && invitaciones.length > 0 && (
        <ul className="divide-y divide-stone-200 rounded-xl border border-stone-200 bg-white dark:divide-stone-800 dark:border-stone-800 dark:bg-stone-900">
          {invitaciones.map((i) => {
            const reenviable = i.estado === 'PENDIENTE' || i.estado === 'VENCIDA'
            return (
              <li key={i.id} className="flex flex-wrap items-center gap-3 p-4">
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium">{i.email}</p>
                  <p className="mt-0.5 text-xs text-stone-500">
                    {ETIQUETA_ROL[i.rol]} · invitó {i.invitadoPorNombre} ·{' '}
                    {i.estado === 'PENDIENTE' ? 'vence' : 'venció'}{' '}
                    {fecha.format(new Date(i.venceAt))}
                  </p>
                </div>
                <span
                  className={`rounded-full px-2 py-0.5 text-xs font-medium ${COLOR_ESTADO[i.estado]}`}
                >
                  {ETIQUETA_ESTADO[i.estado]}
                </span>
                {reenviable && (
                  <div className="flex gap-2">
                    <Button
                      variant="secondary"
                      className="px-3 py-1.5"
                      loading={reenviar.isPending && reenviar.variables === i.id}
                      onClick={() => handleReenviar(i)}
                    >
                      <RotateCw className="size-4" />
                      Reenviar
                    </Button>
                    <Button
                      variant="secondary"
                      className="px-3 py-1.5"
                      onClick={() => setARevocar(i)}
                    >
                      <Ban className="size-4" />
                      Revocar
                    </Button>
                  </div>
                )}
              </li>
            )
          })}
        </ul>
      )}

      <InvitacionFormDialog
        open={formularioAbierto}
        establecimientos={donde}
        inicial={activo?.id ?? null}
        onClose={() => setFormularioAbierto(false)}
        onInvitado={(establecimientoId, email) => {
          // La lista muestra el establecimiento activo: si se invitó a otro, se pasa a ese
          if (establecimientoId !== activo?.id) seleccionar(establecimientoId)
          setErrorReenvio(null)
          setAviso(`Enviamos la invitación a ${email}.`)
        }}
      />

      <ConfirmDialog
        open={aRevocar !== null}
        title="Revocar invitación"
        description={`El link enviado a ${aRevocar?.email} dejará de funcionar.`}
        confirmLabel="Revocar"
        loading={revocar.isPending}
        error={revocar.error ? getErrorMessage(revocar.error) : null}
        onClose={cerrarRevocar}
        onConfirm={() => aRevocar && revocar.mutate(aRevocar.id, { onSuccess: cerrarRevocar })}
      />
    </>
  )
}
