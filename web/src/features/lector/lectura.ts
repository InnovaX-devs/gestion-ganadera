/**
 * Interpretar lo que manda un lector de caravanas electrónicas (#33).
 *
 * Cada marca lo manda distinto: "982000123456789", "982 000123456789", con prefijos ("LA ",
 * "EID:"), con caracteres de control, con un dígito de bandera adelante (16 dígitos) o con el peso
 * de la balanza al lado ("982 000123456789,452.5"). Acá se saca la caravana (los 15 dígitos de la
 * norma ISO 11784: 3 de país o fabricante + 12 de número) y, si vino, el peso.
 *
 * Sin dependencias: lo usan el navegador y los tests (tests/lectura.test.ts).
 */

export interface Lectura {
  /** Caravana electrónica normalizada: 15 dígitos, sin espacios */
  eid: string
  /** Peso en kg, si el lector está conectado a una balanza */
  kg?: number
}

const PESO_MINIMO = 10
const PESO_MAXIMO = 1500

export function interpretarLectura(texto: string): Lectura | null {
  // Sin caracteres de control (STX, ETX, \r, \n...) y con un solo tipo de espacio
  // eslint-disable-next-line no-control-regex
  const limpio = texto.replace(/[\u0000-\u001f\u007f]/g, ' ').trim()
  if (!limpio) return null

  const encontrada = buscarEid(limpio)
  if (!encontrada) return null

  const resto = limpio.slice(0, encontrada.desde) + ' ' + limpio.slice(encontrada.hasta)
  const kg = buscarPeso(resto)
  return kg === undefined ? { eid: encontrada.eid } : { eid: encontrada.eid, kg }
}

/** Solo los dígitos de una caravana electrónica, para comparar "982 000..." con "982000...". */
export function soloDigitos(valor: string | null | undefined): string {
  return (valor ?? '').replace(/\D/g, '')
}

/** "982 000123456789", para mostrar. */
export function formatearEid(eid: string): string {
  return eid.length === 15 ? `${eid.slice(0, 3)} ${eid.slice(3)}` : eid
}

function buscarEid(texto: string): { eid: string; desde: number; hasta: number } | null {
  // 3 dígitos + (espacio o guion opcional) + 12 dígitos, sin más dígitos pegados a los costados
  const separada = /(?<!\d)(\d{3})[ -]?(\d{12})(?!\d)/.exec(texto)
  if (separada) {
    return {
      eid: separada[1] + separada[2],
      desde: separada.index,
      hasta: separada.index + separada[0].length,
    }
  }
  // 16 dígitos seguidos: algunos lectores agregan adelante la bandera de "animal" (0 o 1)
  const conBandera = /(?<!\d)[01](\d{15})(?!\d)/.exec(texto)
  if (conBandera) {
    return {
      eid: conBandera[1],
      desde: conBandera.index,
      hasta: conBandera.index + conBandera[0].length,
    }
  }
  return null
}

function buscarPeso(texto: string): number | undefined {
  for (const m of texto.matchAll(/(?<!\d[.,]?)(\d{1,4})(?:[.,](\d{1,2}))?(?![\d.,]*\d)/g)) {
    const kg = Number(`${m[1]}.${m[2] ?? '0'}`)
    if (kg >= PESO_MINIMO && kg <= PESO_MAXIMO) return kg
  }
  return undefined
}
