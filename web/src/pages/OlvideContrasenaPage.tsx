import { type FormEvent, useState } from 'react'
import { Link } from 'react-router'
import { MailCheck } from 'lucide-react'
import { Button } from '@/components/Button'
import { FormError } from '@/components/FormError'
import { TextField } from '@/components/TextField'
import { pedirRestablecimiento } from '@/features/auth/contrasena'
import { AuthLayout } from '@/layouts/AuthLayout'
import { getErrorMessage, getFieldErrors } from '@/lib/api'

const LINK = 'text-brand-700 dark:text-brand-200 font-medium hover:underline'

/** Pide el email y manda el link para elegir una contraseña nueva. */
export function OlvideContrasenaPage() {
  const [email, setEmail] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [enviado, setEnviado] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [errores, setErrores] = useState<Record<string, string>>({})

  async function enviar(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setErrores({})
    if (!email.trim()) return setErrores({ email: 'Ingresá tu email' })
    setEnviando(true)
    try {
      await pedirRestablecimiento(email)
      setEnviado(true)
    } catch (err) {
      setErrores(getFieldErrors(err))
      setError(getErrorMessage(err))
    } finally {
      setEnviando(false)
    }
  }

  const volver = (
    <Link to="/login" className={LINK}>
      Volver a ingresar
    </Link>
  )

  if (enviado) {
    return (
      <AuthLayout
        title="Revisá tu email"
        subtitle={`Si ${email.trim()} tiene una cuenta, le mandamos un enlace para elegir una contraseña nueva.`}
        footer={volver}
      >
        <div className="flex gap-3 text-sm text-stone-600 dark:text-stone-400">
          <MailCheck className="text-brand-600 mt-0.5 size-5 shrink-0" />
          <p>
            El enlace vence en 1 hora y sirve una sola vez. Si no lo ves en unos minutos, revisá la
            carpeta de spam.
          </p>
        </div>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout
      title="¿Olvidaste tu contraseña?"
      subtitle="Ingresá tu email y te mandamos un enlace para elegir una nueva."
      footer={volver}
    >
      <form onSubmit={enviar} noValidate className="space-y-4">
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
        <Button type="submit" loading={enviando} className="w-full">
          Mandarme el enlace
        </Button>
      </form>
    </AuthLayout>
  )
}
