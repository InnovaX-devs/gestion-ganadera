import { CircleAlert } from 'lucide-react'

/** Error general de un formulario (no asociado a un campo). */
export function FormError({ message }: { message: string | null }) {
  if (!message) return null
  return (
    <div
      role="alert"
      className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2.5 text-sm text-red-700 dark:border-red-900 dark:bg-red-950/50 dark:text-red-300"
    >
      <CircleAlert className="mt-0.5 size-4 shrink-0" />
      <span className="whitespace-pre-line">{message}</span>
    </div>
  )
}
