export interface Establecimiento {
  id: string
  nombre: string
  localidad: string | null
  provincia: string | null
  superficieHa: number | null
  renspa: string | null
  latitud: number | null
  longitud: number | null
  createdAt: string
}

export interface EstablecimientoInput {
  nombre: string
  localidad: string | null
  provincia: string | null
  superficieHa: number | null
  renspa: string | null
  latitud: number | null
  longitud: number | null
}
