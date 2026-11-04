import { type FormEvent, useState } from 'react'
import { Button } from '@/components/Button'
import { Dialog } from '@/components/Dialog'
import { FormError } from '@/components/FormError'
import { TextField } from '@/components/TextField'
import { ETIQUETA_ROL, type Rol } from '@/features/establecimientos/permisos'
import type { Establecimiento } from '@/features/establecimientos/types'
import { getErrorMessage, getFieldErrors } from '@/lib/api'
import { useInvitar } from './queries'

const ROLES: Rol[] = ['ENCARGADO', 'VETERINARIO', 'SOCIO', 'DUENO']

const CLASE_SELECT =
  'focus:border-brand-500 focus:ring-brand-100 dark:focus:ring-brand-900 block w-full rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm shadow-xs outline-none focus:ring-2 dark:border-stone-700 dark:bg-stone-900'

interface Props {
  open: boolean
  /** Establecimientos donde el usuario puede invitar (es dueño). */
  establecimientos: Establecimiento[]
  /** Establecimiento preseleccionado. */
  inicial: string | null
  onClose: () => void
  onInvitado: (establecimientoId: string, email: string) => void
}

export function InvitacionFormDialog(props: Props) {
  return (
    <Dialog
      open={props.open}
      onClose={props.onClose}
      title="Invitar por email"
      description="Le llega un link para sumarse al establecimiento con el rol que elijas. El link vence a los 7 días."
    >
      {props.open && <Formulario {...props} />}
    </Dialog>
  )
}

function Formulario({ establecimientos, inicial, onClose, onInvitado }: Props) {
  const [email, setEmail] = useState('')
  const [rol, setRol] = useState<Rol>('ENCARGADO')
  const [establecimientoId, setEstablecimientoId] = useState(
    inicial ?? establecimientos[0]?.id ?? '',
  )
  const [error, setError] = useState<string | null>(null)
  const [errores, setErrores] = useState<Record<string, string>>({})
  const invitar = useInvitar()

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setErrores({})
    if (!email.trim()) {
      setErrores({ email: 'Ingresá el email de la persona' })
      return
    }
    try {
      await invitar.mutateAsync({
        establecimientoId,
        data: { email: email.trim(), rol },
      })
      onInvitado(establecimientoId, email.trim().toLowerCase())
      onClose()
    } catch (err) {
      const porCampo = getFieldErrors(err)
      setErrores(porCampo)
      setError(
        Object.keys(porCampo).length > 0 ? 'Revisá los datos marcados.' : getErrorMessage(err),
      )
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-4">
      <FormError message={error} />
      <TextField
        label="Email"
        type="email"
        inputMode="email"
        placeholder="persona@ejemplo.com"
        autoFocus
        required
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        error={errores.email}
      />
      <div>
        <label htmlFor="inv-establecimiento" className="mb-1.5 block text-sm font-medium">
          Establecimiento
        </label>
        <select
          id="inv-establecimiento"
          className={CLASE_SELECT}
          value={establecimientoId}
          onChange={(e) => setEstablecimientoId(e.target.value)}
        >
          {establecimientos.map((e) => (
            <option key={e.id} value={e.id}>
              {e.nombre}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label htmlFor="inv-rol" className="mb-1.5 block text-sm font-medium">
          Rol
        </label>
        <select
          id="inv-rol"
          className={CLASE_SELECT}
          value={rol}
          onChange={(e) => setRol(e.target.value as Rol)}
        >
          {ROLES.map((r) => (
            <option key={r} value={r}>
              {ETIQUETA_ROL[r]}
            </option>
          ))}
        </select>
        {errores.rol && <p className="mt-1.5 text-xs text-red-600">{errores.rol}</p>}
      </div>
      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onClose} disabled={invitar.isPending}>
          Cancelar
        </Button>
        <Button type="submit" loading={invitar.isPending}>
          Enviar invitación
        </Button>
      </div>
    </form>
  )
}
