import { sumarDias } from '@/features/sanidad/carencia'

/**
 * Copia de reproduccion/Gestacion.java, para la vista previa del tacto. El valor que queda es el que
 * calcula el servidor: si cambia algo allá, cambiarlo acá.
 */
export const DIAS_GESTACION = 283
const DIAS_POR_MES = 30.44

/** Java redondea .5 hacia arriba y Math.round de JS también (para positivos): coinciden. */
export const concepcionEstimada = (fechaTacto: string, meses: number) =>
  sumarDias(fechaTacto, -Math.round(meses * DIAS_POR_MES))

export const fechaEstimadaParto = (fechaTacto: string, meses: number) =>
  sumarDias(concepcionEstimada(fechaTacto, meses), DIAS_GESTACION)

/** Días que faltan hasta `fecha` (negativo si ya pasó). */
export function diasHasta(fecha: string, hoy: string): number {
  const a = new Date(`${hoy}T00:00:00`)
  const b = new Date(`${fecha}T00:00:00`)
  return Math.round((b.getTime() - a.getTime()) / 86_400_000)
}
