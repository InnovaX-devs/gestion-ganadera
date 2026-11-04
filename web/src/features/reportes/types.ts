import type { Categoria, Sexo } from '@/features/animales/types'
import type { Stock } from '@/features/stock/types'

/** GET /establecimientos/{id}/reportes/pesos */
export interface ReportePesos {
  desde: string
  hasta: string
  resumen: {
    animales: number
    pesajes: number
    animalesConGanancia: number
    pesoInicialPromedio?: number
    pesoFinalPromedio?: number
    gananciaDiariaPromedio?: number
  }
  porFecha: { fecha: string; pesajes: number; kgPromedio: number }[]
  porCategoria: {
    categoria: Categoria
    animales: number
    pesoFinalPromedio?: number
    gananciaDiariaPromedio?: number
  }[]
  porAnimal: {
    animalId: string
    caravana: string
    categoria: Categoria
    lote?: string
    pesajes: number
    fechaInicial: string
    pesoInicial: number
    fechaFinal: string
    pesoFinal: number
    dias?: number
    variacionKg?: number
    gananciaDiaria?: number
  }[]
}

export interface FiltroPesos {
  desde: string
  hasta: string
  loteId?: string
  categoria?: Categoria
}

/** GET /establecimientos/{id}/reportes/existencias */
export interface Existencias {
  establecimiento: {
    id: string
    nombre: string
    renspa?: string
    localidad?: string
    provincia?: string
  }
  generadoEn: string
  stock: Stock
  animales: {
    id: string
    caravana: string
    caravanaElectronica?: string
    sexo: Sexo
    categoria: Categoria
    lote?: string
    rodeo?: string
  }[]
}
