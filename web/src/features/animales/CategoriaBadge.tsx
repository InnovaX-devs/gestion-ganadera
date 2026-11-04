import { CATEGORIAS } from './config'
import type { Animal } from './types'

/** Categoría con color según sexo (rosa hembras, celeste machos). */
export function CategoriaBadge({
  animal,
}: {
  animal: Pick<Animal, 'sexo' | 'categoria' | 'castrado'>
}) {
  const hembra = animal.sexo === 'HEMBRA'
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${
        hembra
          ? 'bg-rose-50 text-rose-700 dark:bg-rose-950/50 dark:text-rose-300'
          : 'bg-sky-50 text-sky-700 dark:bg-sky-950/50 dark:text-sky-300'
      }`}
    >
      {CATEGORIAS[animal.categoria].etiqueta}
      {animal.categoria === 'TERNERO' && animal.castrado && ' (castrado)'}
    </span>
  )
}
