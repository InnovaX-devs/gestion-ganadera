import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Plan, PlanInfo, Recurso } from './types'

export const planKey = ['plan'] as const

export function usePlan() {
  return useQuery({
    queryKey: planKey,
    queryFn: () => api.get<Plan>('/plan').then((r) => r.data),
    staleTime: 60_000,
  })
}

const LIMITE: Record<Recurso, keyof PlanInfo> = {
  animales: 'maxAnimales',
  establecimientos: 'maxEstablecimientos',
  usuarios: 'maxUsuarios',
}

/** Límite de un recurso en un plan; null = sin límite. */
export function limite(plan: PlanInfo, recurso: Recurso): number | null {
  const valor = plan[LIMITE[recurso]]
  return typeof valor === 'number' ? valor : null
}

/** Uso de un recurso; los usuarios cuentan también las invitaciones pendientes. */
export function usado(datos: Plan, recurso: Recurso): number {
  return recurso === 'usuarios'
    ? datos.uso.usuarios + datos.uso.invitacionesPendientes
    : datos.uso[recurso]
}

/** Recursos que ya llegaron al límite del plan que rige hoy. */
export function llenos(datos: Plan): Recurso[] {
  return (['animales', 'establecimientos', 'usuarios'] as const).filter((r) => {
    const max = limite(datos.plan, r)
    return max !== null && usado(datos, r) >= max
  })
}

/** Si al terminar la prueba alguno queda por encima del plan contratado. */
export function excedidosAlTerminarPrueba(datos: Plan): Recurso[] {
  if (!datos.enPrueba) return []
  return (['animales', 'establecimientos', 'usuarios'] as const).filter((r) => {
    const max = limite(datos.contratado, r)
    return max !== null && usado(datos, r) > max
  })
}

export const NOMBRE: Record<Recurso, { uno: string; varios: string }> = {
  animales: { uno: 'animal', varios: 'animales' },
  establecimientos: { uno: 'establecimiento', varios: 'establecimientos' },
  usuarios: { uno: 'usuario', varios: 'usuarios' },
}
