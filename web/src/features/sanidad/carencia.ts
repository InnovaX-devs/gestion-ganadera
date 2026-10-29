import { hoyISO } from '@/features/eventos/fechas'

/** Suma días a una fecha YYYY-MM-DD (sin pasar por UTC, que correría el día). */
export function sumarDias(iso: string, dias: number): string {
  const [anio, mes, dia] = iso.split('-').map(Number)
  const fecha = new Date(anio, mes - 1, dia + dias)
  return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`
}

/** Debe coincidir con `app.sanidad.carencia-dias-extra` del backend (SANIDAD_CARENCIA_DIAS_EXTRA). */
export const DIAS_EXTRA_CARENCIA = Math.max(
  0,
  Number(import.meta.env.VITE_CARENCIA_DIAS_EXTRA) || 0,
)

/** Fecha desde la cual queda libre un tratamiento aplicado en `fecha` con `dias` de carencia. */
export const libreDesdeDe = (fecha: string, dias: number) =>
  sumarDias(fecha, dias + DIAS_EXTRA_CARENCIA)

/**
 * Carencia de un tratamiento aplicado en `fecha` con `dias` de carencia. Misma regla que el
 * backend: queda libre desde fecha + días (ese día ya se puede faenar o vender).
 */
export function carenciaDeTratamiento(fecha: string, dias: number | null | undefined) {
  const total = dias ?? 0
  const libreDesde = libreDesdeDe(fecha, total)
  return { libreDesde, enCarencia: total > 0 && libreDesde > hoyISO() }
}
