import { Fence, Layers, type LucideIcon } from 'lucide-react'
import type { TipoAgrupacion } from './types'

interface ConfigAgrupacion {
  titulo: string
  singular: string
  plural: string
  descripcion: string
  ejemploNombre: string
  ejemploDescripcion: string
  icono: LucideIcon
}

/** Textos de cada tipo. Lotes y rodeos usan la misma pantalla con esta configuración. */
export const AGRUPACIONES: Record<TipoAgrupacion, ConfigAgrupacion> = {
  lotes: {
    titulo: 'Lotes',
    singular: 'lote',
    plural: 'lotes',
    descripcion: 'Grupos de manejo: animales que se manejan juntos (por potrero, edad o destino).',
    ejemploNombre: 'Ej: Recría 2026',
    ejemploDescripcion: 'Ej: Terneros destetados en marzo, potrero 4',
    icono: Fence,
  },
  rodeos: {
    titulo: 'Rodeos',
    singular: 'rodeo',
    plural: 'rodeos',
    descripcion: 'Grupos productivos del establecimiento (cría, reposición, invernada…).',
    ejemploNombre: 'Ej: Rodeo de cría',
    ejemploDescripcion: 'Ej: Vacas Angus con servicio en primavera',
    icono: Layers,
  },
}
