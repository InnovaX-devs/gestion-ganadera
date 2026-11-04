import { type FormEvent, useState } from 'react'
import { Link, useLocation } from 'react-router'
import { Button } from '@/components/Button'
import { FormError } from '@/components/FormError'
import { TextField } from '@/components/TextField'
import { useAuth } from '@/features/auth/useAuth'
import { AuthLayout } from '@/layouts/AuthLayout'
import { getErrorMessage, getFieldErrors } from '@/lib/api'

export function LoginPage() {
  const { login } = useAuth()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [errores, setErrores] = useState<Record<string, string>>({})

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setEnviando(true)
    setError(null)
    setErrores({})
    try {
      await login({ email, password })
      // La redirección la hace PublicOnly al cambiar el estado de la sesión
    } catch (err) {
      setErrores(getFieldErrors(err))
      setError(getErrorMessage(err))
      setEnviando(false)
    }
  }

  return (
    <AuthLayout
      title="Ingresar"
      subtitle="Accedé a la gestión de tu hacienda."
      footer={
        <>
          ¿No tenés cuenta?{' '}
          <Link
            to="/registro"
            state={location.state}
            className="text-brand-700 dark:text-brand-200 font-medium hover:underline"
          >
            Creá una gratis
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormError message={error} />
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          inputMode="email"
          required
          autoFocus
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={errores.email}
        />
        <TextField
          label="Contraseña"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={errores.password}
        />
        <Button type="submit" loading={enviando} className="w-full">
          Ingresar
        </Button>
      </form>
    </AuthLayout>
  )
}
