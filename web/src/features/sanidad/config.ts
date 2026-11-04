import { Bug, Pill, ShieldPlus, Syringe, type LucideIcon } from 'lucide-react'
import type { TipoProducto, ViaAdministracion } from './types'

export const TIPOS_PRODUCTO: Record<TipoProducto, { etiqueta: string; icono: LucideIcon }> = {
  VACUNA: { etiqueta: 'Vacuna', icono: ShieldPlus },
  ANTIPARASITARIO: { etiqueta: 'Antiparasitario', icono: Bug },
  ANTIBIOTICO: { etiqueta: 'Antibiótico', icono: Pill },
  OTRO: { etiqueta: 'Otro', icono: Syringe },
}

export const LISTA_TIPOS_PRODUCTO = Object.keys(TIPOS_PRODUCTO) as TipoProducto[]

export const VIAS: Record<ViaAdministracion, string> = {
  SUBCUTANEA: 'Subcutánea',
  INTRAMUSCULAR: 'Intramuscular',
  INTRAVENOSA: 'Intravenosa',
  ORAL: 'Oral',
  TOPICA: 'Tópica (pour-on)',
  INTRAMAMARIA: 'Intramamaria',
  OTRA: 'Otra',
}

export const LISTA_VIAS = Object.keys(VIAS) as ViaAdministracion[]

/** Tope de días de carencia que acepta el backend. */
export const MAX_DIAS_CARENCIA = 365

/** "Sin carencia", "1 día", "28 días". */
export function formatearDiasCarencia(dias: number): string {
  if (dias <= 0) return 'Sin carencia'
  return `${dias} ${dias === 1 ? 'día' : 'días'}`
}
