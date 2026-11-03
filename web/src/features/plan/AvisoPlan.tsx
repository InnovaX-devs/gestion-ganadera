import { useState } from 'react'
import { Link, useLocation } from 'react-router'
import { CircleAlert, Clock, X } from 'lucide-react'
import { NOMBRE, excedidosAlTerminarPrueba, limite, llenos, usePlan } from './queries'
import { diaSiguiente } from './cobro'
import { formatearFecha } from '@/features/eventos/fechas'

const DIAS_AVISO_PRUEBA = 7

/**
 * Franja arriba de la página cuando no se pudo cobrar la suscripción, se llegó al límite de
 * animales o la prueba gratis está por terminar (#35). Se puede cerrar: vuelve a aparecer al recargar si sigue pasando.
 *
 * Establecimientos y usuarios no avisan acá: en el plan Inicial (1 establecimiento) estar "en el
 * límite" es lo normal. Recién al intentar agregar uno de más, esa pantalla muestra el error.
 */
export function AvisoPlan() {
  const { data } = usePlan()
  const { pathname } = useLocation()
  const [cerrado, setCerrado] = useState<string | null>(null)
  if (!data || pathname === '/plan') return null

  const lleno = llenos(data).filter((r) => r === 'animales')
  const suscripcion = data.cobro?.suscripcion
  let aviso: { clave: string; texto: string; urgente: boolean; fijo?: boolean } | null = null
  if (data.cobro?.habilitada === false) {
    // Ningún plan es gratis (#35): sin pago, solo lectura. No se puede cerrar.
    aviso = {
      clave: 'solo-lectura',
      texto: `Tu cuenta está en solo lectura: no tiene un plan pago vigente. Podés ver todo, pero para cargar o modificar datos ${
        data.cobro.habilitado ? 'suscribite a un plan.' : 'hablá con nosotros para elegir un plan.'
      }`,
      urgente: true,
      fijo: true,
    }
  } else if (suscripcion?.enGracia && suscripcion.hasta) {
    aviso = {
      clave: `impago-${suscripcion.hasta}`,
      texto: `No pudimos cobrar tu plan ${suscripcion.planNombre}. Revisá tu medio de pago en Mercado Pago: si no se cobra, desde el ${formatearFecha(diaSiguiente(suscripcion.hasta))} la cuenta queda en solo lectura hasta que pagues.`,
      urgente: true,
    }
  } else if (lleno.length > 0) {
    const r = lleno[0]
    const max = limite(data.plan, r)!
    aviso = {
      clave: `lleno-${lleno.join()}`,
      texto: `Llegaste al límite de ${max} ${max === 1 ? NOMBRE[r].uno : NOMBRE[r].varios} del plan ${data.plan.nombre}. Para cargar más, pasá a un plan mayor.`,
      urgente: true,
    }
  } else if (data.enPrueba && data.diasDePrueba <= DIAS_AVISO_PRUEBA) {
    const dias =
      data.diasDePrueba === 1 ? 'Hoy es el último día' : `Te quedan ${data.diasDePrueba} días`
    const excedidos = excedidosAlTerminarPrueba(data)
    const s = data.cobro?.suscripcion
    const paga =
      !data.cobro ||
      !!data.cobro.pagoManualHasta ||
      (!!s && (s.estado === 'ACTIVA' || s.estado === 'PAUSADA'))
    const despues = !paga
      ? data.cobro?.habilitado
        ? ' Suscribite antes para no quedar en solo lectura: el primer cobro es recién cuando termina la prueba.'
        : ' Para seguir cargando datos después, hablá con nosotros para elegir un plan.'
      : excedidos.length > 0
        ? ` Después rige el plan ${data.contratado.nombre} y vas a tener más ${NOMBRE[excedidos[0]].varios} de los que permite: no vas a poder cargar más.`
        : ` Después rige el plan ${data.contratado.nombre}.`
    aviso = {
      clave: `prueba-${data.diasDePrueba}`,
      texto: `${dias} de la prueba gratis.${despues}`,
      urgente: !paga || excedidos.length > 0,
    }
  }
  if (!aviso || (!aviso.fijo && cerrado === aviso.clave)) return null

  const Icono = aviso.urgente ? CircleAlert : Clock
  return (
    <div
      role="status"
      className={`flex items-center gap-2 border-b px-4 py-2 text-sm sm:px-6 lg:px-8 ${
        aviso.urgente
          ? 'border-red-200 bg-red-50 text-red-900 dark:border-red-900/60 dark:bg-red-950/40 dark:text-red-200'
          : 'border-sky-200 bg-sky-50 text-sky-900 dark:border-sky-900/60 dark:bg-sky-950/40 dark:text-sky-200'
      }`}
    >
      <Icono className="size-4 shrink-0" aria-hidden />
      <p className="min-w-0 flex-1">
        {aviso.texto}{' '}
        <Link to="/plan" className="font-semibold underline underline-offset-2">
          {aviso.fijo ? 'Elegir un plan' : 'Ver planes'}
        </Link>
      </p>
      {!aviso.fijo && (
        <button
          type="button"
          onClick={() => setCerrado(aviso.clave)}
          aria-label="Cerrar aviso"
          className="shrink-0 rounded-md p-1 opacity-70 hover:opacity-100"
        >
          <X className="size-4" />
        </button>
      )}
    </div>
  )
}
