import { type FormEvent, useState } from 'react'
import { Link } from 'react-router'
import { Button } from '@/components/Button'
import { FormError } from '@/components/FormError'
import { TextField } from '@/components/TextField'
import type { RegisterData } from '@/features/auth/types'
import { useAuth } from '@/features/auth/useAuth'
import { AuthLayout } from '@/layouts/AuthLayout'
import { getErrorMessage, getFieldErrors } from '@/lib/api'

const VACIO: Required<RegisterData> = {
  nombreOrganizacion: '',
  nombre: '',
  apellido: '',
  email: '',
  password: '',
}

export function RegisterPage() {
  const { register } = useAuth()
  const [form, setForm] = useState(VACIO)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [errores, setErrores] = useState<Record<string, string>>({})

  const campo = (nombre: keyof RegisterData) => ({
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
      await register(form)
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
      title="Crear cuenta"
      subtitle="Empezá a registrar tu hacienda en minutos."
      footer={
        <>
          ¿Ya tenés cuenta?{' '}
          <Link
            to="/login"
            className="text-brand-700 dark:text-brand-200 font-medium hover:underline"
          >
            Ingresá
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormError message={error} />
        <TextField
          label="Nombre del campo o empresa"
          placeholder="Ej: Estancia La Esperanza"
          autoComplete="organization"
          required
          autoFocus
          {...campo('nombreOrganizacion')}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Nombre" autoComplete="given-name" required {...campo('nombre')} />
          <TextField label="Apellido" autoComplete="family-name" {...campo('apellido')} />
        </div>
        <TextField
          label="Email"
          type="email"
          inputMode="email"
          autoComplete="email"
          required
          {...campo('email')}
        />
        <TextField
          label="Contraseña"
          type="password"
          autoComplete="new-password"
          required
          hint="Mínimo 8 caracteres."
          {...campo('password')}
        />
        <Button type="submit" loading={enviando} className="w-full">
          Crear cuenta
        </Button>
      </form>
    </AuthLayout>
  )
}
