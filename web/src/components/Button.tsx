import type { ButtonHTMLAttributes } from 'react'
import { LoaderCircle } from 'lucide-react'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  loading?: boolean
}

/** Botón principal. Con `loading` se deshabilita y muestra un spinner. */
export function Button({ loading, disabled, children, className = '', ...props }: ButtonProps) {
  return (
    <button
      disabled={disabled || loading}
      className={`bg-brand-600 hover:bg-brand-700 focus-visible:ring-brand-300 inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-medium text-white transition outline-none focus-visible:ring-2 disabled:cursor-not-allowed disabled:opacity-60 ${className}`}
      {...props}
    >
      {loading && <LoaderCircle className="size-4 animate-spin" />}
      {children}
    </button>
  )
}
