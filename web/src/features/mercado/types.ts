export type Producto =
  | 'TERNERO'
  | 'TERNERA'
  | 'NOVILLO'
  | 'NOVILLITO'
  | 'MAIZ'
  | 'SOJA'
  | 'TRIGO'
  | 'DOLAR_OFICIAL'
  | 'DOLAR_BLUE'

export type Grupo = 'INVERNADA' | 'CONSUMO' | 'GRANOS' | 'DOLAR'

/** ACTUAL: leído hoy. ANTERIOR: la fuente falló, es el último valor conocido. SIN_DATO: nunca se leyó. */
export type EstadoCotizacion = 'ACTUAL' | 'ANTERIOR' | 'SIN_DATO'

export interface Cotizacion {
  producto: Producto
  grupo: Grupo
  /** "$/kg vivo", "$/tn", "$/US$" */
  unidad: string
  valor?: number
  /** Día de la foto */
  fecha?: string
  /** Fecha que informa la fuente (ej: la pizarra es del día hábil anterior) */
  fechaDato?: string
  fuente?: string
  estado: EstadoCotizacion
  nota?: string
  /** Foto anterior con valor, para la variación */
  valorAnterior?: number
  fechaAnterior?: string
}

export interface RelacionReposicion {
  /** Kilo de ternero / kilo de novillo */
  valor: number
  ternero: number
  novillo: number
  fechaTernero?: string
  fechaNovillo?: string
  /** false si alguno de los precios es un valor anterior */
  actualizada: boolean
  valorAnterior?: number
}

/** GET /mercado/hoy */
export interface MercadoHoy {
  fecha?: string
  /** Instante ISO de la última vez que se guardó una cotización */
  actualizadoEn?: string
  /** Siempre los 9 productos */
  cotizaciones: Cotizacion[]
  relacionReposicion?: RelacionReposicion
}

export interface PuntoHistorico {
  fecha: string
  valor?: number
  fechaDato?: string
  estado: EstadoCotizacion
}

/** GET /mercado/historico */
export interface Historico {
  desde: string
  hasta: string
  series: Partial<Record<Producto, PuntoHistorico[]>>
  relacionReposicion: { fecha: string; valor: number }[]
}
