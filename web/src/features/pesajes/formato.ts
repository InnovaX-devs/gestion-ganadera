const dosDecimales = new Intl.NumberFormat('es-AR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})
const unDecimal = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 1 })

/** "+0,85 kg/día", "−0,45 kg/día". */
export function formatearGanancia(kgPorDia: number): string {
  const signo = kgPorDia > 0 ? '+' : kgPorDia < 0 ? '−' : ''
  return `${signo}${dosDecimales.format(Math.abs(kgPorDia))} kg/día`
}

/** "+30 kg", "−4,5 kg". */
export function formatearVariacion(kg: number): string {
  const signo = kg > 0 ? '+' : kg < 0 ? '−' : ''
  return `${signo}${unDecimal.format(Math.abs(kg))} kg`
}

/** Acepta "412,5" y "412.5". Devuelve null si no es un número. */
export function leerKg(texto: string): number | null {
  const limpio = texto.trim().replace(',', '.')
  if (!limpio || !/^\d+(\.\d{1,2})?$/.test(limpio)) return null
  return Number(limpio)
}
