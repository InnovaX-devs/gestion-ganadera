import {
  Cloud,
  CloudDrizzle,
  CloudFog,
  CloudHail,
  CloudLightning,
  CloudRain,
  CloudRainWind,
  CloudSnow,
  CloudSun,
  type LucideIcon,
  Sun,
} from 'lucide-react'

interface EstadoTiempo {
  texto: string
  icono: LucideIcon
}

/** Códigos WMO que usa Open-Meteo (https://open-meteo.com/en/docs, "WMO Weather interpretation codes"). */
const ESTADOS: Record<number, EstadoTiempo> = {
  0: { texto: 'Despejado', icono: Sun },
  1: { texto: 'Mayormente despejado', icono: CloudSun },
  2: { texto: 'Parcialmente nublado', icono: CloudSun },
  3: { texto: 'Nublado', icono: Cloud },
  45: { texto: 'Niebla', icono: CloudFog },
  48: { texto: 'Niebla con escarcha', icono: CloudFog },
  51: { texto: 'Llovizna débil', icono: CloudDrizzle },
  53: { texto: 'Llovizna', icono: CloudDrizzle },
  55: { texto: 'Llovizna intensa', icono: CloudDrizzle },
  56: { texto: 'Llovizna helada', icono: CloudDrizzle },
  57: { texto: 'Llovizna helada intensa', icono: CloudDrizzle },
  61: { texto: 'Lluvia débil', icono: CloudRain },
  63: { texto: 'Lluvia', icono: CloudRain },
  65: { texto: 'Lluvia fuerte', icono: CloudRain },
  66: { texto: 'Lluvia helada', icono: CloudRain },
  67: { texto: 'Lluvia helada fuerte', icono: CloudRain },
  71: { texto: 'Nevada débil', icono: CloudSnow },
  73: { texto: 'Nevada', icono: CloudSnow },
  75: { texto: 'Nevada fuerte', icono: CloudSnow },
  77: { texto: 'Cinarra', icono: CloudSnow },
  80: { texto: 'Chaparrones débiles', icono: CloudRainWind },
  81: { texto: 'Chaparrones', icono: CloudRainWind },
  82: { texto: 'Chaparrones fuertes', icono: CloudRainWind },
  85: { texto: 'Chaparrones de nieve', icono: CloudSnow },
  86: { texto: 'Chaparrones de nieve fuertes', icono: CloudSnow },
  95: { texto: 'Tormenta', icono: CloudLightning },
  96: { texto: 'Tormenta con granizo', icono: CloudHail },
  99: { texto: 'Tormenta con granizo fuerte', icono: CloudHail },
}

const DESCONOCIDO: EstadoTiempo = { texto: 'Sin datos', icono: Cloud }

export const estadoTiempo = (codigo: number | undefined) =>
  (codigo != null && ESTADOS[codigo]) || DESCONOCIDO
