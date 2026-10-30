import { formatearKg } from '@/features/eventos/config'
import type { BajaDatos, ModalidadPrecio, MotivoBaja, VentaDatos } from './types'

export const MODALIDADES: Record<ModalidadPrecio, string> = {
  POR_KG: 'Por kg',
  POR_CABEZA: 'Por cabeza',
}

export const MOTIVOS_BAJA: Record<MotivoBaja, string> = {
  MUERTE: 'Muerte',
  PERDIDA: 'Pérdida',
}

const pesos = new Intl.NumberFormat('es-AR', {
  style: 'currency',
  currency: 'ARS',
  maximumFractionDigits: 2,
})

/** "$ 1.001.250,00" */
export const formatearPesos = (valor: number) => pesos.format(valor)

/** "Frigorífico Sur · $ 2.500,00 por kg · 400,5 kg · Importe $ 1.001.250,00 · DT-e 123" */
export function resumenVenta(d: VentaDatos): string | null {
  if (!d.comprador) return null
  return [
    d.comprador,
    `${formatearPesos(d.precio)} ${d.modalidad === 'POR_KG' ? 'por kg' : 'por cabeza'}`,
    typeof d.kg === 'number'
      ? `${formatearKg(d.kg)} kg${d.pesoTotal ? ` (promedio de ${d.cantidad ?? '?'})` : ''}`
      : null,
    typeof d.importe === 'number' ? `Importe ${formatearPesos(d.importe)}` : null,
    d.documento ? `DT-e / guía ${d.documento}` : null,
    d.confirmarCarencia ? 'Vendido en carencia' : null,
  ]
    .filter(Boolean)
    .join(' · ')
}

/** "Muerte · Meteorismo" */
export function resumenBaja(d: BajaDatos): string | null {
  if (!d.motivo) return null
  return [MOTIVOS_BAJA[d.motivo], d.causa].filter(Boolean).join(' · ')
}
