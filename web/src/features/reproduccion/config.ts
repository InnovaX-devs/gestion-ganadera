import type { Animal } from '@/features/animales/types'
import { formatearFecha } from '@/features/eventos/fechas'
import type {
  EstadoReproductivoCodigo,
  ResultadoTacto,
  ServicioDatos,
  TactoDatos,
  TipoServicio,
} from './types'

export const TIPOS_SERVICIO: Record<TipoServicio, string> = {
  NATURAL: 'Natural',
  INSEMINACION: 'Inseminación',
}

export const RESULTADOS_TACTO: Record<ResultadoTacto, string> = {
  PRENADA: 'Preñada',
  VACIA: 'Vacía',
}

/** Igual que ServicioDatos.MAX_TOROS. */
export const MAX_TOROS = 10

/** Servicio y tacto van solo en vacas y vaquillonas (igual que Reproduccion.esHembraDeCria). */
export const esHembraDeCria = (animal: Pick<Animal, 'categoria'>) =>
  animal.categoria === 'VACA' || animal.categoria === 'VAQUILLONA'

/** Los toros de un servicio: machos enteros. */
export const esToro = (animal: Pick<Animal, 'categoria'>) =>
  animal.categoria === 'TORO' || animal.categoria === 'TORITO'

export const ESTADOS_REPRODUCTIVOS: Record<
  EstadoReproductivoCodigo,
  { etiqueta: string; clases: string }
> = {
  SIN_DATOS: {
    etiqueta: 'Sin datos reproductivos',
    clases: 'bg-stone-100 text-stone-600 dark:bg-stone-800 dark:text-stone-300',
  },
  EN_SERVICIO: {
    etiqueta: 'En servicio',
    clases: 'bg-violet-100 text-violet-800 dark:bg-violet-950/60 dark:text-violet-300',
  },
  SERVIDA: {
    etiqueta: 'Servida',
    clases: 'bg-violet-100 text-violet-800 dark:bg-violet-950/60 dark:text-violet-300',
  },
  PRENADA: {
    etiqueta: 'Preñada',
    clases: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950/60 dark:text-emerald-300',
  },
  VACIA: {
    etiqueta: 'Vacía',
    clases: 'bg-orange-100 text-orange-800 dark:bg-orange-950/60 dark:text-orange-300',
  },
  PARIDA: {
    etiqueta: 'Parida',
    clases: 'bg-sky-100 text-sky-800 dark:bg-sky-950/60 dark:text-sky-300',
  },
}

export const textoMeses = (meses: number) => `${meses} ${meses === 1 ? 'mes' : 'meses'}`

/** "Natural · toros T-1, T-2 · hasta el 31 ene 2027" / "Inseminación · Angus Rito 707" */
export function resumenServicio(d: ServicioDatos): string | null {
  if (!d.tipo) return null
  const toros = d.toros?.map((t) => t.caravana) ?? []
  const lineas = [
    [
      TIPOS_SERVICIO[d.tipo],
      d.pajuela,
      toros.length > 0 ? `${toros.length === 1 ? 'toro' : 'toros'} ${toros.join(', ')}` : null,
      d.fechaFin ? `hasta el ${formatearFecha(d.fechaFin)}` : null,
    ]
      .filter(Boolean)
      .join(' · '),
    d.observaciones,
  ]
  return lineas.filter(Boolean).join('\n')
}

/** "Preñada de 3 meses · parto estimado 9 sep 2026 · Vet. Dra. Gómez" / "Vacía" */
export function resumenTacto(d: TactoDatos): string | null {
  if (!d.resultado) return null
  const lineas = [
    [
      d.resultado === 'PRENADA' && d.mesesGestacion
        ? `Preñada de ${textoMeses(d.mesesGestacion)}`
        : RESULTADOS_TACTO[d.resultado],
      d.fechaEstimadaParto ? `parto estimado ${formatearFecha(d.fechaEstimadaParto)}` : null,
      d.veterinario ? `Vet. ${d.veterinario}` : null,
    ]
      .filter(Boolean)
      .join(' · '),
    d.observaciones,
  ]
  return lineas.filter(Boolean).join('\n')
}
