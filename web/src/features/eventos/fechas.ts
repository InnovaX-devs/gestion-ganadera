/** Hoy en formato YYYY-MM-DD según la hora local (toISOString usa UTC y de noche daría mañana). */
export function hoyISO(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const formato = new Intl.DateTimeFormat('es-AR', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
})

/** "29 oct 2026" a partir de "2026-10-29". */
export function formatearFecha(iso: string): string {
  return formato
    .format(new Date(`${iso}T00:00:00`))
    .replace(/ de /g, ' ')
    .replace('.', '')
}
