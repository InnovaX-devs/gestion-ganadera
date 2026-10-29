import { type SelectHTMLAttributes, useId } from 'react'
import { ChevronDown } from 'lucide-react'

interface SelectFieldProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string
  error?: string
  hint?: string
}

/** Select nativo con el mismo estilo que TextField. Sin `label` sirve como filtro compacto. */
export function SelectField({
  label,
  error,
  hint,
  className,
  children,
  ...props
}: SelectFieldProps) {
  const id = useId()
  const descripcionId = error || hint ? `${id}-desc` : undefined

  return (
    <div className={className}>
      {label && (
        <label htmlFor={id} className="mb-1.5 block text-sm font-medium">
          {label}
        </label>
      )}
      <div className="relative">
        <select
          id={id}
          aria-invalid={!!error}
          aria-describedby={descripcionId}
          className={`block w-full appearance-none rounded-lg border bg-white py-2 pr-9 pl-3 text-sm shadow-xs transition outline-none focus:ring-2 disabled:opacity-60 dark:bg-stone-900 ${
            error
              ? 'border-red-400 focus:ring-red-200 dark:border-red-500 dark:focus:ring-red-900'
              : 'focus:border-brand-500 focus:ring-brand-100 dark:focus:ring-brand-900 border-stone-300 dark:border-stone-700'
          }`}
          {...props}
        >
          {children}
        </select>
        <ChevronDown className="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 text-stone-400" />
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
