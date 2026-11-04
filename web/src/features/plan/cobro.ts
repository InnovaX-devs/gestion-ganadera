import type { Plan } from './types'

// Funciones puras del cobro (#35), sin dependencias: se testean en tests/cobro.test.ts

export const pesos = new Intl.NumberFormat('es-AR', {
  style: 'currency',
  currency: 'ARS',
  maximumFractionDigits: 0,
})

/** Se puede contratar online: el pago está configurado y el plan tiene precio. */
export function contratable(plan: Plan, precio?: number | null) {
  return !!plan.cobro?.habilitado && typeof precio === 'number' && precio > 0
}

/**
 * Por qué el primer cobro de Mercado Pago es más adelante: arranca el día después de lo último que
 * ya está cubierto, el pago por transferencia o la prueba gratis.
 */
export function motivoPrimerCobro(primerCobro: string, pagoManualHasta?: string | null) {
  return pagoManualHasta && diaSiguiente(pagoManualHasta) === primerCobro
    ? 'cuando termina lo pagado por transferencia'
    : 'cuando termina la prueba gratis'
}

/** 'YYYY-MM-DD' + 1 día */
export function diaSiguiente(fecha: string) {
  const [a, m, d] = fecha.split('-').map(Number)
  const siguiente = new Date(Date.UTC(a, m - 1, d + 1))
  return siguiente.toISOString().slice(0, 10)
}

/**
 * Pago por transferencia (#35): se paga del 1 al día de vencimiento de cada mes, por ese mes.
 * Hasta cuándo queda pagada al registrar un pago: un mes más si todavía estaba al día; si no, hasta
 * el vencimiento del mes que viene. Igual que PagoManual.siguienteVencimiento en el backend.
 */
export function siguienteVencimiento(actual: string | null | undefined, hoy: string, dia: number) {
  if (actual && actual >= hoy) return sumarMesesISO(actual, 1)
  const [a, m] = hoy.split('-').map(Number)
  const ultimo = new Date(Date.UTC(a, m + 1, 0)).getUTCDate()
  const siguiente = new Date(Date.UTC(a, m, Math.min(dia, ultimo)))
  return siguiente.toISOString().slice(0, 10)
}

/** 'YYYY-MM-DD' + n meses; si el día no existe en ese mes, el último del mes (31/1 + 1 = 28/2). */
export function sumarMesesISO(fecha: string, meses: number) {
  const [a, m, d] = fecha.split('-').map(Number)
  const ultimo = new Date(Date.UTC(a, m - 1 + meses + 1, 0)).getUTCDate()
  return new Date(Date.UTC(a, m - 1 + meses, Math.min(d, ultimo))).toISOString().slice(0, 10)
}
