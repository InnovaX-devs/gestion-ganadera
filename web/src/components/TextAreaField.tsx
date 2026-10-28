import { type TextareaHTMLAttributes, useId } from 'react'

interface TextAreaFieldProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: string
  error?: string
  hint?: string
}

export function TextAreaField({ label, error, hint, className, ...props }: TextAreaFieldProps) {
  const id = useId()
  const descripcionId = error || hint ? `${id}-desc` : undefined

  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium">
        {label}
      </label>
      <textarea
        id={id}
        rows={3}
        aria-invalid={!!error}
        aria-describedby={descripcionId}
        className={`block w-full resize-y rounded-lg border bg-white px-3 py-2 text-sm shadow-xs transition outline-none placeholder:text-stone-400 focus:ring-2 dark:bg-stone-900 ${
          error
            ? 'border-red-400 focus:ring-red-200 dark:border-red-500 dark:focus:ring-red-900'
            : 'focus:border-brand-500 focus:ring-brand-100 dark:focus:ring-brand-900 border-stone-300 dark:border-stone-700'
        }`}
        {...props}
      />
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
