import { type FormEvent, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { Button } from '@/components/Button'
import { FormError } from '@/components/FormError'
import { TextField } from '@/components/TextField'
import { ingresarAlPanel } from '@/features/admin/admin-api'
import { sesionAdmin } from '@/features/admin/sesion'
import { AuthLayout } from '@/layouts/AuthLayout'
import { getErrorMessage, getFieldErrors } from '@/lib/api'

/** Ingreso al panel de Innovax: email del equipo (ADMIN_EMAILS) y la contraseña del panel. */
export function AdminLoginPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [errores, setErrores] = useState<Record<string, string>>({})

  if (sesionAdmin.leer()) return <Navigate to="/admin" replace />

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setEnviando(true)
    setError(null)
    setErrores({})
    try {
      await ingresarAlPanel(email, password)
      navigate('/admin', { replace: true })
    } catch (err) {
      setErrores(getFieldErrors(err))
      setError(getErrorMessage(err))
      setEnviando(false)
    }
  }

  return (
    <AuthLayout
      title="Panel de Innovax"
      subtitle="Administración de cuentas y planes. Solo para el equipo."
      footer={
        <Link
          to="/login"
          className="text-brand-700 dark:text-brand-200 font-medium hover:underline"
        >
          Ir a la app
        </Link>
      }
    >
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormError message={error} />
        <TextField
          label="Email"
          type="email"
          autoComplete="username"
          inputMode="email"
          required
          autoFocus
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={errores.email}
        />
        <TextField
          label="Contraseña del panel"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={errores.password}
        />
        <Button type="submit" loading={enviando} className="w-full">
          Entrar al panel
        </Button>
      </form>
    </AuthLayout>
  )
}
