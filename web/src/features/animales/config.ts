import type { Categoria, Sexo } from './types'

export const CATEGORIAS: Record<Categoria, { etiqueta: string; sexo: Sexo; plural: string }> = {
  VACA: { etiqueta: 'Vaca', plural: 'Vacas', sexo: 'HEMBRA' },
  VAQUILLONA: { etiqueta: 'Vaquillona', plural: 'Vaquillonas', sexo: 'HEMBRA' },
  TERNERA: { etiqueta: 'Ternera', plural: 'Terneras', sexo: 'HEMBRA' },
  TERNERO: { etiqueta: 'Ternero', plural: 'Terneros', sexo: 'MACHO' },
  NOVILLITO: { etiqueta: 'Novillito', plural: 'Novillitos', sexo: 'MACHO' },
  NOVILLO: { etiqueta: 'Novillo', plural: 'Novillos', sexo: 'MACHO' },
  TORITO: { etiqueta: 'Torito', plural: 'Toritos', sexo: 'MACHO' },
  TORO: { etiqueta: 'Toro', plural: 'Toros', sexo: 'MACHO' },
}

export const LISTA_CATEGORIAS = Object.keys(CATEGORIAS) as Categoria[]

export const categoriasDe = (sexo: Sexo) =>
  LISTA_CATEGORIAS.filter((c) => CATEGORIAS[c].sexo === sexo)

export const SEXOS: Record<Sexo, string> = { HEMBRA: 'Hembra', MACHO: 'Macho' }

/** Sugerencias para el campo raza (se puede escribir cualquier otra). */
export const RAZAS_SUGERIDAS = [
  'Aberdeen Angus',
  'Hereford',
  'Polled Hereford',
  'Braford',
  'Brangus',
  'Shorthorn',
  'Limangus',
  'Holando Argentino',
  'Jersey',
  'Cruza',
]

/** "2 años 3 meses", "8 meses", "12 días". Con "~" adelante si la fecha es estimada. */
export function formatearEdad(fechaNacimiento?: string | null, estimada = false): string | null {
  if (!fechaNacimiento) return null
  const nacimiento = new Date(`${fechaNacimiento}T00:00:00`)
  const hoy = new Date()
  let meses =
    (hoy.getFullYear() - nacimiento.getFullYear()) * 12 + (hoy.getMonth() - nacimiento.getMonth())
  if (hoy.getDate() < nacimiento.getDate()) meses--

  let texto: string
  if (meses < 1) {
    const dias = Math.max(0, Math.floor((hoy.getTime() - nacimiento.getTime()) / 86_400_000))
    texto = `${dias} ${dias === 1 ? 'día' : 'días'}`
  } else if (meses < 24) {
    texto = `${meses} ${meses === 1 ? 'mes' : 'meses'}`
  } else {
    const anios = Math.floor(meses / 12)
    const resto = meses % 12
    texto = `${anios} años${resto ? ` ${resto} ${resto === 1 ? 'mes' : 'meses'}` : ''}`
  }
  return estimada ? `~${texto}` : texto
}
