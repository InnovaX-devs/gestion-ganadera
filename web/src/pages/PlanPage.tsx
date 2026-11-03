import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import {
  Check,
  CircleAlert,
  Clock,
  CreditCard,
  ExternalLink,
  LoaderCircle,
  MapPinned,
  PawPrint,
  RefreshCw,
  Users,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { Button } from '@/components/Button'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { FormError } from '@/components/FormError'
import { PageHeader } from '@/components/PageHeader'
import { formatearFecha, hoyISO } from '@/features/eventos/fechas'
import { NOMBRE, excedidosAlTerminarPrueba, limite, usado, usePlan } from '@/features/plan/queries'
import {
  contratable,
  diaSiguiente,
  pesos,
  useCancelarSuscripcion,
  useSincronizarSuscripcion,
  useSuscribir,
} from '@/features/plan/suscripcion'
import type { Cobro, Plan, PlanInfo, Recurso } from '@/features/plan/types'
import { getErrorMessage } from '@/lib/api'

const RECURSOS: { recurso: Recurso; icono: LucideIcon }[] = [
  { recurso: 'animales', icono: PawPrint },
  { recurso: 'establecimientos', icono: MapPinned },
  { recurso: 'usuarios', icono: Users },
]

const numero = new Intl.NumberFormat('es-AR')

/**
 * Mi plan (#35): el plan que rige, cuánto se usa de cada límite, la suscripción con Mercado Pago y
 * los planes para comparar y contratar.
 */
export function PlanPage() {
  const { data, isPending, error } = usePlan()
  const volviendo = useVueltaDeMercadoPago()

  return (
    <>
      <PageHeader title="Mi plan" description="Lo que incluye tu cuenta y cuánto estás usando." />
      {error && <FormError message={getErrorMessage(error)} />}
      {isPending && (
        <div className="grid place-items-center py-16">
          <LoaderCircle className="size-6 animate-spin text-stone-400" />
        </div>
      )}
      {volviendo && (
        <div
          role="status"
          className="mb-4 flex items-center gap-2 rounded-lg border border-sky-200 bg-sky-50 px-4 py-3 text-sm text-sky-900 dark:border-sky-900/60 dark:bg-sky-950/40 dark:text-sky-200"
        >
          <LoaderCircle className="size-4 animate-spin" />
          Estamos confirmando tu pago con Mercado Pago…
        </div>
      )}
      {data?.cobro?.habilitada === false && (
        <div
          role="alert"
          className="mb-6 rounded-xl border border-red-200 bg-red-50 p-5 text-red-900 dark:border-red-900/60 dark:bg-red-950/40 dark:text-red-200"
        >
          <p className="flex items-center gap-2 font-semibold">
            <CircleAlert className="size-5" />
            Tu cuenta está en solo lectura
          </p>
          <p className="mt-1 text-sm">
            No tiene un plan pago vigente. Podés ver todos tus animales, eventos y reportes, pero
            para cargar o modificar datos{' '}
            {data.cobro.habilitado
              ? 'suscribite a uno de los planes de abajo.'
              : 'hablá con Innovax para elegir un plan.'}{' '}
            Lo que se cargue en el campo sin señal se guarda igual.
          </p>
        </div>
      )}
      {data && (
        <div className="space-y-6">
          <Actual datos={data} />
          {data.cobro && <EstadoCobro cobro={data.cobro} />}
          <Planes datos={data} />
        </div>
      )}
    </>
  )
}

function Actual({ datos }: { datos: Plan }) {
  const excedidos = excedidosAlTerminarPrueba(datos)
  return (
    <section className="rounded-xl border border-stone-200 bg-white p-5 dark:border-stone-800 dark:bg-stone-900">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
        <h2 className="text-lg font-semibold">Plan {datos.plan.nombre}</h2>
        {datos.enPrueba && (
          <span className="inline-flex items-center gap-1 rounded-full bg-sky-50 px-2.5 py-0.5 text-xs font-medium text-sky-800 dark:bg-sky-950/60 dark:text-sky-200">
            <Clock className="size-3.5" />
            Prueba gratis ·{' '}
            {datos.diasDePrueba === 1 ? 'último día' : `quedan ${datos.diasDePrueba} días`}
          </span>
        )}
      </div>
      {datos.enPrueba && datos.pruebaHasta && (
        <p className="mt-1 text-sm text-stone-600 dark:text-stone-400">
          La prueba termina el {formatearFecha(datos.pruebaHasta)}.{' '}
          {pagaDespuesDeLaPrueba(datos) ? (
            <>
              Después rige el plan <b>{datos.contratado.nombre}</b>.
              {excedidos.length > 0 &&
                ` Vas a tener más ${NOMBRE[excedidos[0]].varios} de los que permite: lo cargado se sigue viendo, pero no vas a poder agregar más.`}
            </>
          ) : datos.cobro?.habilitado ? (
            'Para seguir cargando datos después, suscribite a un plan: el primer cobro es recién cuando termina la prueba.'
          ) : (
            'Para seguir cargando datos después, hablá con nosotros para elegir un plan.'
          )}
        </p>
      )}
      {datos.cobro?.pagoManualHasta && (
        <p className="mt-1 text-sm text-stone-600 dark:text-stone-400">
          Pagado por transferencia hasta el {formatearFecha(datos.cobro.pagoManualHasta)}.
        </p>
      )}

      <div className="mt-5 grid gap-4 sm:grid-cols-3">
        {RECURSOS.map(({ recurso, icono }) => (
          <Medidor key={recurso} datos={datos} recurso={recurso} icono={icono} />
        ))}
      </div>
    </section>
  )
}

/** Ya tiene con qué seguir cuando termine la prueba: suscripción o pago manual. */
function pagaDespuesDeLaPrueba(datos: Plan) {
  const s = datos.cobro?.suscripcion
  return (
    !!datos.cobro?.pagoManualHasta || (!!s && (s.estado === 'ACTIVA' || s.estado === 'PAUSADA'))
  )
}

function Medidor({
  datos,
  recurso,
  icono: Icono,
}: {
  datos: Plan
  recurso: Recurso
  icono: LucideIcon
}) {
  const max = limite(datos.plan, recurso)
  const usa = usado(datos, recurso)
  const proporcion = max ? Math.min(1, usa / max) : 0
  // Rojo si se pasó; ámbar si a los animales les queda poco. Establecimientos y usuarios en el
  // límite (1 de 1) es lo normal en planes chicos: no se marca.
  const color =
    max === null
      ? ''
      : usa > max
        ? 'bg-red-500'
        : recurso === 'animales' && proporcion >= 0.9
          ? 'bg-amber-500'
          : 'bg-brand-600 dark:bg-brand-400'
  const pendientes = recurso === 'usuarios' ? datos.uso.invitacionesPendientes : 0

  return (
    <div className="rounded-lg border border-stone-100 p-4 dark:border-stone-800">
      <p className="flex items-center gap-2 text-sm text-stone-500">
        <Icono className="size-4" />
        {NOMBRE[recurso].varios[0].toUpperCase() + NOMBRE[recurso].varios.slice(1)}
      </p>
      <p className="mt-1.5 tabular-nums">
        <span className="text-2xl font-semibold">{numero.format(usa)}</span>
        <span className="text-sm text-stone-500">
          {max === null ? ' · sin límite' : ` de ${numero.format(max)}`}
        </span>
      </p>
      {max !== null && (
        <div
          className="mt-2 h-2 overflow-hidden rounded-full bg-stone-100 dark:bg-stone-800"
          role="meter"
          aria-valuemin={0}
          aria-valuemax={max}
          aria-valuenow={usa}
          aria-label={`${NOMBRE[recurso].varios}: ${usa} de ${max}`}
        >
          <div
            className={`h-full rounded-full ${color}`}
            style={{ width: `${proporcion * 100}%` }}
          />
        </div>
      )}
      {pendientes > 0 && (
        <p className="mt-2 text-xs text-stone-500">
          Incluye {pendientes}{' '}
          {pendientes === 1 ? 'invitación pendiente' : 'invitaciones pendientes'}
        </p>
      )}
      {recurso === 'animales' && (
        <p className="mt-2 text-xs text-stone-500">Activos, en todos los establecimientos.</p>
      )}
    </div>
  )
}

/**
 * Mercado Pago vuelve a /plan con ?preapproval_id=...: se consulta enseguida, sin esperar su aviso,
 * y se limpia la URL.
 */
function useVueltaDeMercadoPago() {
  const [params, setParams] = useSearchParams()
  const sincronizar = useSincronizarSuscripcion()
  const hecho = useRef(false)
  const vuelve = params.has('preapproval_id')
  useEffect(() => {
    if (!vuelve || hecho.current) return
    hecho.current = true
    sincronizar.mutate(undefined, { onSettled: () => setParams({}, { replace: true }) })
  }, [vuelve, sincronizar, setParams])
  return vuelve || sincronizar.isPending
}

function EstadoCobro({ cobro }: { cobro: Cobro }) {
  const sincronizar = useSincronizarSuscripcion()
  const cancelar = useCancelarSuscripcion()
  const [confirmando, setConfirmando] = useState(false)
  const s = cobro.suscripcion
  const p = cobro.pendiente
  if (!s && !p) return null

  const caja = 'rounded-xl border p-5'
  return (
    <section className="space-y-4" aria-label="Suscripción">
      {p && (
        <div
          className={`${caja} border-amber-200 bg-amber-50 dark:border-amber-900/60 dark:bg-amber-950/30`}
        >
          <p className="flex items-center gap-2 font-medium text-amber-900 dark:text-amber-200">
            <Clock className="size-4" />
            Falta terminar el pago del plan {p.planNombre} ({pesos.format(p.monto)} por mes)
          </p>
          <p className="mt-1 text-sm text-amber-900/80 dark:text-amber-200/80">
            {p.primerCobro && p.primerCobro > hoyISO()
              ? `Cargás tu tarjeta ahora y el primer cobro es el ${formatearFecha(p.primerCobro)}, cuando termina la prueba. `
              : ''}
            Si ya pagaste, puede tardar unos minutos en confirmarse.
          </p>
          <FormError message={sincronizar.error ? getErrorMessage(sincronizar.error) : null} />
          <div className="mt-3 flex flex-wrap gap-2">
            <a
              href={p.url}
              className="bg-brand-600 hover:bg-brand-700 inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium text-white"
            >
              <ExternalLink className="size-4" />
              Ir a pagar
            </a>
            <Button
              variant="secondary"
              loading={sincronizar.isPending}
              onClick={() => sincronizar.mutate()}
            >
              <RefreshCw className="size-4" />
              Ya pagué, actualizar
            </Button>
          </div>
        </div>
      )}

      {s && (
        <div
          className={`${caja} ${
            s.enGracia
              ? 'border-red-200 bg-red-50 dark:border-red-900/60 dark:bg-red-950/30'
              : 'border-stone-200 bg-white dark:border-stone-800 dark:bg-stone-900'
          }`}
        >
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="flex items-center gap-2 font-medium">
                {s.enGracia ? (
                  <CircleAlert className="size-4 text-red-600 dark:text-red-400" />
                ) : (
                  <CreditCard className="size-4 text-stone-500" />
                )}
                Suscripción al plan {s.planNombre} · {pesos.format(s.monto)} por mes
              </p>
              <p className="mt-1 text-sm text-stone-600 dark:text-stone-400">
                {textoSuscripcion(s, cobro.diasGracia)}
              </p>
            </div>
            {(s.estado === 'ACTIVA' || s.estado === 'PAUSADA') && (
              <Button variant="secondary" onClick={() => setConfirmando(true)}>
                Cancelar suscripción
              </Button>
            )}
          </div>
        </div>
      )}

      <ConfirmDialog
        open={confirmando}
        title="Cancelar la suscripción"
        description={
          s?.pagadoHasta
            ? `No se te va a cobrar más. Podés seguir usando la cuenta hasta el ${formatearFecha(s.pagadoHasta)}; después queda en solo lectura hasta que vuelvas a suscribirte. Tus datos no se borran.`
            : 'No se te va a cobrar más y la cuenta queda en solo lectura hasta que vuelvas a suscribirte. Tus datos no se borran.'
        }
        confirmLabel="Cancelar suscripción"
        loading={cancelar.isPending}
        error={cancelar.error ? getErrorMessage(cancelar.error) : null}
        onClose={() => {
          setConfirmando(false)
          cancelar.reset()
        }}
        onConfirm={() => cancelar.mutate(undefined, { onSuccess: () => setConfirmando(false) })}
      />
    </section>
  )
}

function textoSuscripcion(s: NonNullable<Cobro['suscripcion']>, diasGracia: number): string {
  const hasta = s.hasta ? formatearFecha(s.hasta) : null
  const baja = s.hasta ? formatearFecha(diaSiguiente(s.hasta)) : null
  switch (s.estado) {
    case 'VENCIDA':
      return 'Terminó sin pago y la cuenta está en solo lectura. Tus datos están todos: para volver a cargar, suscribite de nuevo.'
    case 'CANCELADA':
      return hasta
        ? `La cancelaste: no se cobra más. Podés seguir usando la cuenta hasta el ${hasta}.`
        : 'La cancelaste: no se cobra más.'
    case 'PAUSADA':
      return `Está pausada en Mercado Pago.${baja ? ` Si no se reactiva, desde el ${baja} la cuenta queda en solo lectura hasta que pagues.` : ''}`
    default:
      if (s.enGracia) {
        return `No pudimos cobrar la cuota de este mes. Revisá tu medio de pago en Mercado Pago: si no se cobra, desde el ${baja} la cuenta queda en solo lectura hasta que pagues.`
      }
      if (s.pagadoHasta) {
        return `Al día: pagado hasta el ${formatearFecha(s.pagadoHasta)}. Se renueva sola todos los meses.`
      }
      return s.primerCobro && s.primerCobro > hoyISO()
        ? `Activa. El primer cobro es el ${formatearFecha(s.primerCobro)}, cuando termina la prueba gratis.`
        : `Activa. Mercado Pago hace el primer cobro en los próximos minutos (tenés ${diasGracia} días de margen).`
  }
}

function Planes({ datos }: { datos: Plan }) {
  const suscribir = useSuscribir()
  const s = datos.cobro?.suscripcion
  const suscriptoA = s && (s.estado === 'ACTIVA' || s.estado === 'PAUSADA') ? s.planCodigo : null
  const habilitado = !!datos.cobro?.habilitado
  return (
    <section>
      <h2 className="mb-3 text-sm font-semibold">Planes</h2>
      <FormError message={suscribir.error ? getErrorMessage(suscribir.error) : null} />
      <div className="grid gap-4 md:grid-cols-3">
        {datos.planes.map((p) => (
          <TarjetaPlan
            key={p.codigo}
            plan={p}
            actual={p.codigo === datos.plan.codigo}
            contratado={datos.enPrueba && p.codigo === datos.contratado.codigo}
            accion={
              suscriptoA === p.codigo ? (
                <p className="text-brand-700 dark:text-brand-300 flex items-center justify-center gap-1.5 py-2 text-sm font-medium">
                  <Check className="size-4" />
                  Suscripto
                </p>
              ) : contratable(datos, p.precioMensual) ? (
                <Button
                  className="w-full"
                  variant={suscriptoA ? 'secondary' : 'primary'}
                  loading={suscribir.isPending && suscribir.variables === p.codigo}
                  disabled={suscribir.isPending}
                  onClick={() => suscribir.mutate(p.codigo)}
                >
                  {suscriptoA ? 'Cambiar a este plan' : 'Suscribirme'}
                </Button>
              ) : null
            }
          />
        ))}
      </div>
      <p className="mt-4 text-sm text-stone-500">
        {habilitado
          ? 'Se paga todos los meses con Mercado Pago (tarjeta o dinero en cuenta). Si estás en la prueba gratis, el primer cobro es cuando termina. Podés cancelar cuando quieras: tus datos nunca se borran.'
          : 'Pronto vas a poder cambiar de plan y pagar desde acá. Mientras tanto, para cambiar de plan hablá con nosotros.'}
      </p>
    </section>
  )
}

function TarjetaPlan({
  plan,
  actual,
  contratado,
  accion,
}: {
  plan: PlanInfo
  actual: boolean
  contratado: boolean
  accion?: React.ReactNode
}) {
  const fila = (recurso: Recurso) => {
    const max = limite(plan, recurso)
    return max === null
      ? `${NOMBRE[recurso].varios[0].toUpperCase()}${NOMBRE[recurso].varios.slice(1)} sin límite`
      : `Hasta ${numero.format(max)} ${max === 1 ? NOMBRE[recurso].uno : NOMBRE[recurso].varios}`
  }
  return (
    <div
      className={`flex flex-col rounded-xl border bg-white p-5 dark:bg-stone-900 ${
        actual
          ? 'border-brand-500 ring-brand-500/30 ring-2'
          : 'border-stone-200 dark:border-stone-800'
      }`}
    >
      <div className="flex items-center justify-between gap-2">
        <h3 className="font-semibold">{plan.nombre}</h3>
        {actual ? (
          <span className="bg-brand-50 text-brand-700 dark:bg-brand-900/40 dark:text-brand-200 rounded-full px-2 py-0.5 text-xs font-medium">
            Tu plan
          </span>
        ) : contratado ? (
          <span className="rounded-full bg-stone-100 px-2 py-0.5 text-xs font-medium text-stone-600 dark:bg-stone-800 dark:text-stone-300">
            Después de la prueba
          </span>
        ) : null}
      </div>
      <p className="mt-2 text-sm text-stone-500">
        {typeof plan.precioMensual === 'number' && plan.precioMensual > 0
          ? `${pesos.format(plan.precioMensual)} por mes`
          : 'Precio a definir'}
      </p>
      <ul className="mt-4 space-y-2 text-sm">
        {(['animales', 'establecimientos', 'usuarios'] as const).map((r) => (
          <li key={r} className="flex items-start gap-2">
            <Check className="text-brand-600 dark:text-brand-400 mt-0.5 size-4 shrink-0" />
            {fila(r)}
          </li>
        ))}
        <li className="flex items-start gap-2">
          <Check className="text-brand-600 dark:text-brand-400 mt-0.5 size-4 shrink-0" />
          Todos los eventos, reportes y la app sin señal
        </li>
      </ul>
      {accion && <div className="mt-auto pt-5">{accion}</div>}
    </div>
  )
}
