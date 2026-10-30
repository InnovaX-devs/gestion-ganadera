/** GET /establecimientos/{id}/clima. °C, km/h, mm y %. Cualquier valor puede faltar. */
export interface Clima {
  ubicacion: {
    latitud: number
    longitud: number
    /** COORDENADAS: las del establecimiento. LOCALIDAD: buscada por nombre. */
    origen: 'COORDENADAS' | 'LOCALIDAD'
    /** "Río Cuarto, Córdoba" cuando se buscó por localidad */
    nombre?: string
  }
  actual: {
    /** Hora local del lugar, "2026-10-30T14:15" */
    hora?: string
    temperatura?: number
    sensacionTermica?: number
    humedad?: number
    viento?: number
    precipitacion?: number
    /** Código WMO del estado del tiempo */
    codigo?: number
  }
  dias: DiaClima[]
  lluviaEsperadaMm?: number
  fuente: string
  consultadoEn: string
}

export interface DiaClima {
  fecha: string
  codigo?: number
  maxima?: number
  minima?: number
  lluviaMm?: number
  probabilidadLluvia?: number
}
