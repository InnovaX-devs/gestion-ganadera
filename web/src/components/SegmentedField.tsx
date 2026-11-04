import { useId } from 'react'

interface Props<T extends string> {
  label: string
  value: T
  opciones: { valor: T; etiqueta: string }[]
  onChange: (valor: T) => void
  hint?: string
}

/**
 * Elegir una entre pocas opciones (2 o 3) con un toque, sin abrir un select. Son radios nativos:
 * se recorren con las flechas del teclado.
 */
export function SegmentedField<T extends string>({
  label,
  value,
  opciones,
  onChange,
  hint,
}: Props<T>) {
  const nombre = useId()
  return (
    <fieldset>
      <legend className="mb-1.5 block text-sm font-medium">{label}</legend>
      <div className="grid auto-cols-fr grid-flow-col gap-1 rounded-lg border border-stone-300 bg-stone-50 p-1 dark:border-stone-700 dark:bg-stone-950">
        {opciones.map((o) => (
          <label
            key={o.valor}
            className="has-checked:text-brand-800 dark:has-checked:text-brand-200 has-focus-visible:ring-brand-500 cursor-pointer rounded-md px-3 py-1.5 text-center text-sm font-medium text-stone-600 transition-colors has-checked:bg-white has-checked:shadow-xs has-focus-visible:ring-2 dark:text-stone-400 dark:has-checked:bg-stone-800"
          >
            <input
              type="radio"
              name={nombre}
              value={o.valor}
              checked={value === o.valor}
              onChange={() => onChange(o.valor)}
              className="sr-only"
            />
            {o.etiqueta}
          </label>
        ))}
      </div>
      {hint && <p className="mt-1.5 text-xs text-stone-500">{hint}</p>}
    </fieldset>
  )
}
