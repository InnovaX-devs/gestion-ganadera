import { type FormEvent, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { LoaderCircle } from 'lucide-react'
import { Button } from '@/components/Button'
import { FormError } from '@/components/FormError'
import { TextField } from '@/components/TextField'
import { ETIQUETA_ROL } from '@/features/establecimientos/permisos'
import { useAuth } from '@/features/auth/useAuth'
import {
  invitacionesApi,
  useAceptarInvitacion,
  useInvitacionPublica,
} from '@/features/invitaciones/queries'
import { MENSAJE_ESTADO, type InvitacionPublica } from '@/features/invitaciones/types'
import { AuthLayout } from '@/layouts/AuthLayout'
import { getErrorMessage, getFieldErrors } from '@/lib/api'
import { axiosIsNotFound } from '@/features/invitaciones/errores'

const LINK = 'text-brand-700 dark:text-brand-200 font-medium hover:underline'

/** Pantalla a la que llega el invitado desde el link del email. Funciona con o sin sesión. */
export function AceptarInvitacionPage() {
  const { token = '' } = useParams()
  const { data: invitacion, isPending, error } = useInvitacionPublica(token)

  if (isPending) {
    return (
      <div className="grid min-h-dvh place-items-center">
        <LoaderCircle className="size-6 animate-spin text-stone-400" aria-label="Cargando" />
      </div>
    )
  }

  if (error || !invitacion) {
    return (
      <AuthLayout
        title="Invitación no encontrada"
        subtitle={
          axiosIsNotFound(error)
            ? 'El link no es válido o ya fue reemplazado por uno nuevo.'
            : getErrorMessage(error)
        }
        footer={
          <Link to="/" className={LINK}>
            Ir al inicio
          </Link>
        }
      >
        <p className="text-sm text-stone-600 dark:text-stone-400">
          Pedile a quien te invitó que te mande otra.
        </p>
      </AuthLayout>
    )
  }

  return <Contenido token={token} invitacion={invitacion} />
}

function Contenido({ token, invitacion }: { token: string; invitacion: InvitacionPublica }) {
  const { status, usuario, logout } = useAuth()
  const location = useLocation()

  const subtitle = `${invitacion.organizacionNombre} te invitó a ${invitacion.establecimientoNombre} como ${ETIQUETA_ROL[invitacion.rol]}.`

  if (invitacion.estado !== 'PENDIENTE') {
    return (
      <AuthLayout
        title="Invitación"
        subtitle={subtitle}
        footer={
          <Link to="/" className={LINK}>
            Ir al inicio
          </Link>
        }
      >
        <p className="text-sm">{MENSAJE_ESTADO[invitacion.estado]}</p>
      </AuthLayout>
    )
  }

  if (status === 'loading') {
    return (
      <div className="grid min-h-dvh place-items-center">
        <LoaderCircle className="size-6 animate-spin text-stone-400" aria-label="Cargando" />
      </div>
    )
  }

  if (status === 'authenticated' && usuario) {
    if (usuario.email.toLowerCase() !== invitacion.email.toLowerCase()) {
      return (
        <AuthLayout
          title="Invitación"
          subtitle={subtitle}
          footer={
            <Link to="/" className={LINK}>
              Ir al inicio
            </Link>
          }
        >
          <div className="space-y-4">
            <p className="text-sm">
              Esta invitación es para <strong>{invitacion.email}</strong> y ahora estás ingresado
              como <strong>{usuario.email}</strong>.
            </p>
            <Button variant="secondary" className="w-full" onClick={logout}>
              Cerrar sesión
            </Button>
          </div>
        </AuthLayout>
      )
    }
    return <Aceptar token={token} subtitle={subtitle} />
  }

  // Sin sesión
  if (invitacion.cuentaExistente) {
    return (
      <AuthLayout
        title="Invitación"
        subtitle={subtitle}
        footer={<span>Ingresá con {invitacion.email} para aceptarla.</span>}
      >
        <Link to="/login" state={{ from: location }} className="block">
          <Button className="w-full">Ingresar para aceptar</Button>
        </Link>
      </AuthLayout>
    )
  }

  return <Registro token={token} invitacion={invitacion} subtitle={subtitle} />
}

function Aceptar({ token, subtitle }: { token: string; subtitle: string }) {
  const navigate = useNavigate()
  const aceptar = useAceptarInvitacion(token)

  return (
    <AuthLayout
      title="Invitación"
      subtitle={subtitle}
      footer={
        <Link to="/" className={LINK}>
          Ahora no
        </Link>
      }
    >
      <div className="space-y-4">
        <FormError message={aceptar.error ? getErrorMessage(aceptar.error) : null} />
        <Button
          className="w-full"
          loading={aceptar.isPending}
          onClick={() =>
            aceptar.mutate(undefined, { onSuccess: () => navigate('/establecimientos') })
          }
        >
          Aceptar invitación
        </Button>
      </div>
    </AuthLayout>
  )
}

function Registro({
  token,
  invitacion,
  subtitle,
}: {
  token: string
  invitacion: InvitacionPublica
  subtitle: string
}) {
  const { iniciarSesion } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ nombre: '', apellido: '', password: '' })
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [errores, setErrores] = useState<Record<string, string>>({})

  const campo = (nombre: keyof typeof form) => ({
    value: form[nombre],
    onChange: (e: React.ChangeEvent<HTMLInputElement>) =>
      setForm((f) => ({ ...f, [nombre]: e.target.value })),
    error: errores[nombre],
  })

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setEnviando(true)
    setError(null)
    setErrores({})
    try {
      const data = await invitacionesApi.registrar(token, {
        nombre: form.nombre.trim(),
        apellido: form.apellido.trim() || undefined,
        password: form.password,
      })
      iniciarSesion(data)
      navigate('/establecimientos', { replace: true })
    } catch (err) {
      const porCampo = getFieldErrors(err)
      setErrores(porCampo)
      setError(
        Object.keys(porCampo).length > 0 ? 'Revisá los datos marcados.' : getErrorMessage(err),
      )
      setEnviando(false)
    }
  }

  return (
    <AuthLayout
      title="Creá tu cuenta"
      subtitle={subtitle}
      footer={
        <>
          ¿Ya tenés cuenta?{' '}
          <Link to="/login" className={LINK}>
            Ingresá
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormError message={error} />
        <TextField label="Email" value={invitacion.email} readOnly disabled />
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label="Nombre"
            autoComplete="given-name"
            required
            autoFocus
            {...campo('nombre')}
          />
          <TextField label="Apellido" autoComplete="family-name" {...campo('apellido')} />
        </div>
        <TextField
          label="Contraseña"
          type="password"
          autoComplete="new-password"
          required
          hint="Mínimo 8 caracteres."
          {...campo('password')}
        />
        <Button type="submit" loading={enviando} className="w-full">
          Crear cuenta y unirme
        </Button>
      </form>
    </AuthLayout>
  )
}
