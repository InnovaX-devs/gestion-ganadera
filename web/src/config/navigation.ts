import {
  ArrowLeftRight,
  BarChart3,
  Baby,
  ClipboardList,
  Fence,
  HeartHandshake,
  House,
  Layers,
  type LucideIcon,
  MailPlus,
  MapPinned,
  PawPrint,
  Scale,
  ScanSearch,
  Syringe,
  Tag,
  Upload,
} from 'lucide-react'

export interface NavItem {
  label: string
  path: string
  icon: LucideIcon
}

export interface NavSection {
  title?: string
  items: NavItem[]
}

/** Menú lateral. Agregar acá cada página nueva. */
export const navigation: NavSection[] = [
  {
    items: [{ label: 'Inicio', path: '/', icon: House }],
  },
  {
    title: 'Establecimiento',
    items: [
      { label: 'Establecimientos', path: '/establecimientos', icon: MapPinned },
      { label: 'Lotes', path: '/lotes', icon: Fence },
      { label: 'Rodeos', path: '/rodeos', icon: Layers },
      { label: 'Invitaciones', path: '/invitaciones', icon: MailPlus },
    ],
  },
  {
    title: 'Hacienda',
    items: [
      { label: 'Animales', path: '/animales', icon: PawPrint },
      { label: 'Nacimientos', path: '/nacimientos', icon: Baby },
      { label: 'Servicios', path: '/servicios', icon: HeartHandshake },
      { label: 'Tactos', path: '/tactos', icon: ScanSearch },
      { label: 'Tratamientos', path: '/tratamientos', icon: Syringe },
      { label: 'Pesajes', path: '/pesajes', icon: Scale },
      { label: 'Movimientos', path: '/movimientos', icon: ArrowLeftRight },
      { label: 'Ventas y bajas', path: '/ventas', icon: Tag },
      { label: 'Observaciones', path: '/observaciones', icon: ClipboardList },
    ],
  },
  {
    title: 'Análisis',
    items: [
      { label: 'Reportes', path: '/reportes', icon: BarChart3 },
      { label: 'Importar', path: '/importar', icon: Upload },
    ],
  },
]

export const allNavItems = navigation.flatMap((section) => section.items)
