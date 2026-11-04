/** Fechas "2026-11-03" sin corrimientos de zona horaria. */

export const aISO = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

export const deISO = (iso: string) => {
  const [a, m, d] = iso.split('-').map(Number)
  return new Date(a, m - 1, d)
}

export function sumarDias(iso: string, dias: number) {
  const d = deISO(iso)
  d.setDate(d.getDate() + dias)
  return aISO(d)
}

export function diasEntre(desde: string, hasta: string) {
  return Math.round((deISO(hasta).getTime() - deISO(desde).getTime()) / 86_400_000)
}

/** "hoy", "mañana", "en 3 días", "hace 2 días", "ayer" */
export function cuando(fecha: string, hoy: string) {
  const d = diasEntre(hoy, fecha)
  if (d === 0) return 'hoy'
  if (d === 1) return 'mañana'
  if (d === -1) return 'ayer'
  return d > 0 ? `en ${d} días` : `hace ${-d} días`
}

const diaMes = new Intl.DateTimeFormat('es-AR', {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
})
/** "mar 3 nov" */
export const fechaCorta = (iso: string) => diaMes.format(deISO(iso)).replace(/[.,]/g, '')

/** Solo la primera letra en mayúscula ("Noviembre de 2026", no "Noviembre De 2026"). */
export const conMayuscula = (texto: string) => texto.charAt(0).toUpperCase() + texto.slice(1)

/** Días que se muestran: semanas completas de lunes a domingo que cubren el mes. */
export function rangoDelMes(mes: string) {
  const primero = deISO(`${mes}-01`)
  const inicio = new Date(primero)
  inicio.setDate(1 - ((primero.getDay() + 6) % 7))
  const ultimo = new Date(primero.getFullYear(), primero.getMonth() + 1, 0)
  const fin = new Date(ultimo)
  fin.setDate(ultimo.getDate() + (6 - ((ultimo.getDay() + 6) % 7)))
  return { desde: aISO(inicio), hasta: aISO(fin) }
}
