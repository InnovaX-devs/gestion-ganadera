import { type InputHTMLAttributes, useId, useState } from 'react'
import { Eye, EyeOff } from 'lucide-react'

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  error?: string
  hint?: string
}

/** Campo de texto con etiqueta y error. Si es de contraseña, trae el ojito para verla. */
export function TextField({ label, error, hint, className, type, ...props }: TextFieldProps) {
  const id = useId()
  const descripcionId = error || hint ? `${id}-desc` : undefined
  const esPassword = type === 'password'
  const [visible, setVisible] = useState(false)

  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium">
        {label}
      </label>
      <div className="relative">
        <input
          id={id}
          type={esPassword && visible ? 'text' : type}
          aria-invalid={!!error}
          aria-describedby={descripcionId}
          className={`block w-full rounded-lg border bg-white px-3 py-2 text-sm shadow-xs transition outline-none placeholder:text-stone-400 focus:ring-2 dark:bg-stone-900 ${
            esPassword ? 'pr-10' : ''
          } ${
            error
              ? 'border-red-400 focus:ring-red-200 dark:border-red-500 dark:focus:ring-red-900'
              : 'focus:border-brand-500 focus:ring-brand-100 dark:focus:ring-brand-900 border-stone-300 dark:border-stone-700'
          }`}
          {...props}
        />
        {esPassword && (
          <button
            type="button"
            onClick={() => setVisible((v) => !v)}
            aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}
            aria-pressed={visible}
            className="absolute inset-y-0 right-0 flex items-center rounded-r-lg px-3 text-stone-400 outline-none hover:text-stone-600 focus-visible:text-stone-700 dark:hover:text-stone-200"
          >
            {visible ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
          </button>
        )}
      </div>
      {(error || hint) && (
        <p
          id={descripcionId}
          className={`mt-1.5 text-xs ${error ? 'text-red-600 dark:text-red-400' : 'text-stone-500'}`}
        >
          {error ?? hint}
        </p>
      )}
    </div>
  )
}
