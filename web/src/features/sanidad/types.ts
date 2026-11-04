export type TipoProducto = 'VACUNA' | 'ANTIPARASITARIO' | 'ANTIBIOTICO' | 'OTRO'

export type ViaAdministracion =
  'SUBCUTANEA' | 'INTRAMUSCULAR' | 'INTRAVENOSA' | 'ORAL' | 'TOPICA' | 'INTRAMAMARIA' | 'OTRA'

/** Un producto del catálogo del establecimiento. Dosis, vía y carencia son los valores habituales. */
export interface ProductoSanitario {
  id: string
  nombre: string
  tipo: TipoProducto
  diasCarencia: number
  dosisHabitual?: string | null
  viaHabitual?: ViaAdministracion | null
}

export interface ProductoInput {
  nombre: string
  tipo: TipoProducto
  diasCarencia: number
  dosisHabitual: string | null
  viaHabitual: ViaAdministracion | null
}

/** Carencia vigente de un animal. Un animal sin carencia no la trae. */
export interface Carencia {
  /** Desde este día ya se puede faenar o vender. */
  libreDesde: string
  tratamientos: {
    producto: string
    fecha: string
    diasCarencia: number
    libreDesde: string
  }[]
}

/** Datos del evento TRATAMIENTO. `producto` y `tipo` los completa el servidor. */
export interface TratamientoDatos {
  productoId: string
  producto?: string
  tipo?: TipoProducto
  dosis: string
  via: ViaAdministracion
  veterinario?: string | null
  diasCarencia?: number | null
}
