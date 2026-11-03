import { type FormEvent, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { LoaderCircle } from 'lucide-react'
import { Button } from '@/components/Button'
import { FormError } from '@/components/FormError'
import { TextField } from '@/components/TextField'
import { activarCuenta, useActivacion } from '@/features/admin/queries'
import { useAuth } from '@/features/auth/useAuth'
import { axiosIsNotFound } from '@/features/invitaciones/errores'
import { AuthLayout } from '@/layouts/AuthLayout'
import { getErrorMessage, getFieldErrors } from '@/lib/api'

const LINK = 'text-brand-700 dark:text-brand-200 font-medium hover:underline'

/**
 * Link del email de una cuenta creada por el equipo de Innovax (#35): el dueño elige su contraseña
 * y queda logueado.
 */
export function ActivarCuentaPage() {
  const { token = '' } = useParams()
  const { data, isPending, error } = useActivacion(token)

  if (isPending) {
    return (
      <div className="grid min-h-dvh place-items-center">
        <LoaderCircle className="size-6 animate-spin text-stone-400" aria-label="Cargando" />
      </div>
    )
  }

  if (error || !data) {
    return (
      <AuthLayout
        title="No se puede usar este enlace"
        subtitle={
          axiosIsNotFound(error)
            ? 'El enlace no es válido o ya fue reemplazado por uno nuevo.'
            : getErrorMessage(error)
        }
        footer={
          <Link to="/login" className={LINK}>
            Ir a ingresar
          </Link>
        }
      >
        <p className="text-sm text-stone-600 dark:text-stone-400">
          Si todavía no elegiste tu contraseña, pedile a Innovax que te mande un enlace nuevo.
        </p>
      </AuthLayout>
    )
  }

  return (
    <Formulario
      token={token}
      nombre={data.nombre}
      email={data.email}
      cuenta={data.organizacionNombre}
    />
  )
}

function Formulario({
  token,
  nombre,
  email,
  cuenta,
}: {
  token: string
  nombre: string
  email: string
  cuenta: string
}) {
  const { iniciarSesion } = useAuth()
  const navigate = useNavigate()
  const [password, setPassword] = useState('')
  const [repetida, setRepetida] = useState('')
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  async function activar(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (password.length < 8)
      return setErrores({ password: 'Tiene que tener al menos 8 caracteres' })
    if (password !== repetida) return setErrores({ repetida: 'Las contraseñas no coinciden' })
    setErrores({})
    setEnviando(true)
    try {
      iniciarSesion(await activarCuenta(token, password))
      navigate('/', { replace: true })
    } catch (err) {
      setErrores(getFieldErrors(err))
      setError(getErrorMessage(err))
    } finally {
      setEnviando(false)
    }
  }

  return (
    <AuthLayout
      title={`Hola, ${nombre}`}
      subtitle={`Tu cuenta "${cuenta}" está lista. Elegí una contraseña para empezar.`}
      footer={
        <span className="text-stone-500">
          ¿Ya la elegiste?{' '}
          <Link to="/login" className={LINK}>
            Ingresá
          </Link>
        </span>
      }
    >
      <form onSubmit={activar} noValidate className="space-y-4">
        <FormError message={error} />
        <TextField label="Email" type="email" value={email} disabled readOnly />
        <TextField
          label="Contraseña"
          type="password"
          autoComplete="new-password"
          autoFocus
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={errores.password}
          hint="Al menos 8 caracteres."
        />
        <TextField
          label="Repetí la contraseña"
          type="password"
          autoComplete="new-password"
          value={repetida}
          onChange={(e) => setRepetida(e.target.value)}
          error={errores.repetida}
        />
        <Button type="submit" className="w-full" loading={enviando}>
          Empezar a usar el sistema
        </Button>
      </form>
    </AuthLayout>
  )
}
