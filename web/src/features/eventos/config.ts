import {
  ArrowLeftRight,
  Baby,
  CircleSlash,
  ClipboardList,
  HeartHandshake,
  type LucideIcon,
  Scale,
  ScanSearch,
  Syringe,
  Tag,
} from 'lucide-react'
import { VIAS, formatearDiasCarencia } from '@/features/sanidad/config'
import type { TratamientoDatos } from '@/features/sanidad/types'
import type { TipoEvento } from './types'

interface ConfigTipo {
  etiqueta: string
  icono: LucideIcon
  /** Una línea con lo importante del evento para el historial. Si falta, solo se ve el tipo. */
  resumen?: (datos: Record<string, unknown>) => string | null
}

const texto = (v: unknown) => (typeof v === 'string' && v.trim() ? v : null)

/**
 * Cómo se muestra cada tipo en historiales. Al implementar un tipo (#18 a #23), completar su
 * `resumen` con los datos que guarda.
 */
export const TIPOS_EVENTO: Record<TipoEvento, ConfigTipo> = {
  NACIMIENTO: { etiqueta: 'Nacimiento', icono: Baby },
  SERVICIO: { etiqueta: 'Servicio', icono: HeartHandshake },
  TACTO: { etiqueta: 'Tacto', icono: ScanSearch },
  TRATAMIENTO: {
    etiqueta: 'Tratamiento',
    icono: Syringe,
    resumen: (d) => resumenTratamiento(d as unknown as TratamientoDatos),
  },
  PESAJE: {
    etiqueta: 'Pesaje',
    icono: Scale,
    resumen: (d) => (typeof d.kg === 'number' ? `${formatearKg(d.kg)} kg` : null),
  },
  MOVIMIENTO: { etiqueta: 'Movimiento', icono: ArrowLeftRight },
  VENTA: { etiqueta: 'Venta', icono: Tag },
  BAJA: { etiqueta: 'Baja', icono: CircleSlash },
  OBSERVACION: { etiqueta: 'Observación', icono: ClipboardList, resumen: (d) => texto(d.texto) },
}

/** "Ivomec · 5 ml · Subcutánea · Carencia 28 días" */
function resumenTratamiento(d: TratamientoDatos): string | null {
  if (!d.producto) return null
  return [
    d.producto,
    d.dosis,
    VIAS[d.via],
    d.diasCarencia ? `Carencia ${formatearDiasCarencia(d.diasCarencia)}` : null,
  ]
    .filter(Boolean)
    .join(' · ')
}

const kg = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 1 })
export const formatearKg = (valor: number) => kg.format(valor)
