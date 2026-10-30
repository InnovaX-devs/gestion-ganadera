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
import { type Permiso, type Rol, puede } from '@/features/establecimientos/permisos'
import { VIAS, formatearDiasCarencia } from '@/features/sanidad/config'
import { resumenMovimiento } from '@/features/movimientos/config'
import type { MovimientoDatos } from '@/features/movimientos/types'
import type { TratamientoDatos } from '@/features/sanidad/types'
import { resumenBaja, resumenVenta } from '@/features/ventas/config'
import type { BajaDatos, VentaDatos } from '@/features/ventas/types'
import type { CriaNacimiento, TipoEvento, TipoParto } from './types'

interface ConfigTipo {
  etiqueta: string
  icono: LucideIcon
  /** Copia de TipoEvento.java: alcanza con tener alguno para registrarlo o anularlo. */
  permisos: Permiso[]
  /** Una línea con lo importante del evento para el historial. Si falta, solo se ve el tipo. */
  resumen?: (datos: Record<string, unknown>) => string | null
}

const texto = (v: unknown) => (typeof v === 'string' && v.trim() ? v : null)

/**
 * Cómo se muestra cada tipo en historiales. Al implementar un tipo (#18 a #23), completar su
 * `resumen` con los datos que guarda.
 */
export const TIPOS_EVENTO: Record<TipoEvento, ConfigTipo> = {
  NACIMIENTO: {
    etiqueta: 'Nacimiento',
    icono: Baby,
    permisos: ['GESTIONAR_ANIMALES'],
    resumen: resumirNacimiento,
  },
  SERVICIO: { etiqueta: 'Servicio', icono: HeartHandshake, permisos: ['GESTIONAR_ANIMALES'] },
  TACTO: { etiqueta: 'Tacto', icono: ScanSearch, permisos: ['REGISTRAR_SANIDAD'] },
  TRATAMIENTO: {
    etiqueta: 'Tratamiento',
    icono: Syringe,
    permisos: ['REGISTRAR_SANIDAD'],
    resumen: (d) => resumenTratamiento(d as unknown as TratamientoDatos),
  },
  PESAJE: {
    etiqueta: 'Pesaje',
    icono: Scale,
    permisos: ['GESTIONAR_ANIMALES'],
    resumen: (d) =>
      typeof d.kg === 'number'
        ? `${formatearKg(d.kg)} kg${d.grupal ? ` (promedio de ${d.cantidad ?? '?'})` : ''}`
        : null,
  },
  MOVIMIENTO: {
    etiqueta: 'Movimiento',
    icono: ArrowLeftRight,
    permisos: ['GESTIONAR_ANIMALES'],
    resumen: (d) => resumenMovimiento(d as unknown as MovimientoDatos),
  },
  VENTA: {
    etiqueta: 'Venta',
    icono: Tag,
    permisos: ['GESTIONAR_ANIMALES'],
    resumen: (d) => resumenVenta(d as unknown as VentaDatos),
  },
  BAJA: {
    etiqueta: 'Baja',
    icono: CircleSlash,
    permisos: ['GESTIONAR_ANIMALES'],
    resumen: (d) => resumenBaja(d as unknown as BajaDatos),
  },
  OBSERVACION: {
    etiqueta: 'Observación',
    icono: ClipboardList,
    permisos: ['GESTIONAR_ANIMALES', 'REGISTRAR_SANIDAD'],
    resumen: (d) => texto(d.texto),
  },
}

/** Si el rol puede registrar (y anular) eventos de ese tipo. El backend igual lo controla. */
export const puedeRegistrarTipo = (rol: Rol, tipo: TipoEvento) =>
  TIPOS_EVENTO[tipo].permisos.some((p) => puede(rol, p))

export const TIPOS_PARTO: Record<TipoParto, string> = {
  NORMAL: 'Normal',
  ASISTIDO: 'Asistido',
  CESAREA: 'Cesárea',
}

/** El parto normal no se menciona: es lo esperable. */
const PARTO_EN_RESUMEN: Record<TipoParto, string | null> = {
  NORMAL: null,
  ASISTIDO: 'Parto asistido',
  CESAREA: 'Cesárea',
}

const CANTIDAD_CRIAS: Record<number, string> = { 2: 'Mellizos', 3: 'Trillizos' }

/** "Mellizos: Macho C-101, 34,5 kg; Hembra nacida muerta · Parto asistido" + observaciones. */
function resumirNacimiento(d: Record<string, unknown>): string | null {
  const crias = Array.isArray(d.crias) ? (d.crias as CriaNacimiento[]) : []
  if (crias.length === 0) return null
  const detalle = crias.map(describirCria).join('; ')
  const cantidad = CANTIDAD_CRIAS[crias.length]
  const tipo = d.tipoParto as TipoParto | undefined
  const lineas = [
    [cantidad ? `${cantidad}: ${detalle}` : detalle, tipo && PARTO_EN_RESUMEN[tipo]]
      .filter(Boolean)
      .join(' · '),
    texto(d.observaciones),
  ]
  return lineas.filter(Boolean).join('\n')
}

function describirCria(c: CriaNacimiento): string {
  const hembra = c.sexo === 'HEMBRA'
  if (c.nacidoMuerto) return `${hembra ? 'Hembra nacida muerta' : 'Macho nacido muerto'}`
  const peso = typeof c.pesoKg === 'number' ? `, ${formatearKg(c.pesoKg)} kg` : ''
  return `${hembra ? 'Hembra' : 'Macho'} ${c.caravana ?? ''}`.trim() + peso
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
