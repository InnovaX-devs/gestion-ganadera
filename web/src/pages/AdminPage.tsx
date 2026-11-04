import { type FormEvent, useCallback, useRef, useState } from 'react'
import {
  Ban,
  Banknote,
  Clock,
  CreditCard,
  KeyRound,
  LoaderCircle,
  MoreHorizontal,
  Pencil,
  PlayCircle,
  Plus,
  Search,
  Send,
  Tag,
  Trash2,
  X,
} from 'lucide-react'
import { Button } from '@/components/Button'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { Dialog } from '@/components/Dialog'
import { FormError } from '@/components/FormError'
import { PageHeader } from '@/components/PageHeader'
import { SelectField } from '@/components/SelectField'
import { TextField } from '@/components/TextField'
import {
  type OrganizacionAdmin,
  useCambiarPlan,
  useCrearCuenta,
  useEliminarCuenta,
  useRegistrarPago,
  useOrganizacionesAdmin,
  useCambiarPrecio,
  usePlanesAdmin,
  useReenviarActivacion,
  useRestablecerContrasena,
  useSuspender,
} from '@/features/admin/queries'
import { formatearFecha, hoyISO } from '@/features/eventos/fechas'
import { NOMBRE, limite, usado } from '@/features/plan/queries'
import { diaSiguiente, pesos } from '@/features/plan/suscripcion'
import { siguienteVencimiento } from '@/features/plan/cobro'
import type { Plan, PlanInfo, Recurso } from '@/features/plan/types'
import { useDismiss } from '@/hooks/useDismiss'
import { getErrorMessage, getFieldErrors } from '@/lib/api'

const RECURSOS: Recurso[] = ['animales', 'establecimientos', 'usuarios']

/**
 * Panel del equipo de Innovax (#35): todas las cuentas, su plan y su uso. Desde acá se crean
 * cuentas, se cambia el plan o la prueba y se suspenden, sin tocar la base. Tiene su propio login
 * (ver AdminLayout): ADMIN_EMAILS + ADMIN_PASSWORD.
 */
export function AdminPage() {
  const { data, isPending, error } = useOrganizacionesAdmin()
  const [busqueda, setBusqueda] = useState('')
  const [editando, setEditando] = useState<OrganizacionAdmin | null>(null)
  const [creando, setCreando] = useState(false)
  const [precios, setPrecios] = useState(false)
  const [aSuspender, setASuspender] = useState<OrganizacionAdmin | null>(null)
  const [aEliminar, setAEliminar] = useState<OrganizacionAdmin | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const suspender = useSuspender()
  const reenviar = useReenviarActivacion()
  const restablecer = useRestablecerContrasena()
  const registrarPago = useRegistrarPago()

  const q = busqueda.trim().toLowerCase()
  const lista = (data ?? []).filter(
    (o) =>
      !q ||
      o.nombre.toLowerCase().includes(q) ||
      (o.duenoEmail ?? '').toLowerCase().includes(q) ||
      (o.duenoNombre ?? '').toLowerCase().includes(q),
  )
  const enPrueba = (data ?? []).filter((o) => o.plan.enPrueba).length

  return (
    <>
      <PageHeader
        title="Cuentas"
        description={
          data
            ? `${data.length} ${data.length === 1 ? 'cuenta' : 'cuentas'} · ${enPrueba} en prueba gratis`
            : 'Cuentas y planes'
        }
        actions={
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" onClick={() => setPrecios(true)}>
              <Tag className="size-4" />
              Precios
            </Button>
            <Button onClick={() => setCreando(true)}>
              <Plus className="size-4" />
              Nueva cuenta
            </Button>
          </div>
        }
      />

      <div className="relative mb-4 max-w-md">
        <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-stone-400" />
        <input
          type="search"
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
          placeholder="Buscar por cuenta, nombre o email…"
          aria-label="Buscar cuenta"
          className="focus:border-brand-500 focus:ring-brand-100 dark:focus:ring-brand-900 block w-full rounded-lg border border-stone-300 bg-white py-2 pr-3 pl-9 text-sm shadow-xs outline-none focus:ring-2 dark:border-stone-700 dark:bg-stone-900"
        />
      </div>

      {error && <FormError message={getErrorMessage(error)} />}
      {isPending && (
        <div className="grid place-items-center py-16">
          <LoaderCircle className="size-6 animate-spin text-stone-400" />
        </div>
      )}

      {data && (
        <div className="rounded-xl border border-stone-200 bg-white dark:border-stone-800 dark:bg-stone-900">
          <ul className="divide-y divide-stone-100 dark:divide-stone-800">
            {lista.map((o) => (
              <li key={o.id} className="flex flex-wrap items-center gap-x-6 gap-y-3 px-5 py-4">
                <div className="min-w-48 flex-1">
                  <p className="flex flex-wrap items-center gap-2 font-medium">
                    {o.nombre}
                    {o.suspendida && (
                      <span className="rounded-full bg-red-50 px-2 py-0.5 text-xs font-medium text-red-700 dark:bg-red-950/50 dark:text-red-300">
                        Suspendida
                      </span>
                    )}
                    {o.duenoPendiente && (
                      <span className="rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-800 dark:bg-amber-950/50 dark:text-amber-200">
                        Falta que el dueño active la cuenta
                      </span>
                    )}
                  </p>
                  <p className="truncate text-xs text-stone-500">
                    {[o.duenoNombre, o.duenoEmail].filter(Boolean).join(' · ')}
                  </p>
                  <p className="text-xs text-stone-400">
                    Desde el {formatearFecha(o.createdAt.slice(0, 10))}
                  </p>
                </div>
                <div className="w-44">
                  <p className="text-sm font-medium">
                    {o.plan.plan.nombre}
                    {o.plan.enPrueba && o.plan.plan.codigo !== o.plan.contratado.codigo && (
                      <span className="font-normal text-stone-500">
                        {' '}
                        → {o.plan.contratado.nombre}
                      </span>
                    )}
                  </p>
                  {o.plan.enPrueba ? (
                    <p className="flex items-center gap-1 text-xs text-sky-700 dark:text-sky-300">
                      <Clock className="size-3" />
                      Prueba: {o.plan.diasDePrueba} {o.plan.diasDePrueba === 1 ? 'día' : 'días'}
                    </p>
                  ) : o.plan.pruebaHasta ? (
                    <p className="text-xs text-stone-500">
                      Prueba terminada el {formatearFecha(o.plan.pruebaHasta)}
                    </p>
                  ) : null}
                  <EstadoCobroCuenta plan={o.plan} />
                </div>
                <dl className="flex gap-5 text-sm tabular-nums">
                  {RECURSOS.map((r) => {
                    const max = limite(o.plan.plan, r)
                    const usa = usado(o.plan, r)
                    // Rojo solo si se pasó (ej: nacimientos o sincronización después del límite)
                    const lleno = max !== null && usa > max
                    return (
                      <div key={r}>
                        <dt className="text-xs text-stone-500">
                          {NOMBRE[r].varios[0].toUpperCase() + NOMBRE[r].varios.slice(1)}
                        </dt>
                        <dd className={lleno ? 'font-semibold text-red-600 dark:text-red-400' : ''}>
                          {usa}
                          <span className="text-stone-400">/{max ?? '∞'}</span>
                        </dd>
                      </div>
                    )
                  })}
                </dl>
                <div className="flex items-center gap-1">
                  <Button
                    variant="secondary"
                    className="px-3 py-1.5"
                    onClick={() => setEditando(o)}
                    aria-label={`Cambiar plan de ${o.nombre}`}
                  >
                    <Pencil className="size-4" />
                    Cambiar plan
                  </Button>
                  <MasAcciones
                    organizacion={o}
                    onReenviar={() =>
                      reenviar.mutate(o.id, {
                        onSuccess: () => setAviso(`Se mandó un enlace nuevo a ${o.duenoEmail}.`),
                        onError: (e) => setAviso(getErrorMessage(e)),
                      })
                    }
                    onRestablecer={() =>
                      restablecer.mutate(o.id, {
                        onSuccess: (r) =>
                          setAviso(
                            `Se mandó a ${r.email} un enlace para elegir una contraseña nueva.`,
                          ),
                        onError: (e) => setAviso(getErrorMessage(e)),
                      })
                    }
                    onSuspender={() => setASuspender(o)}
                    onEliminar={() => setAEliminar(o)}
                    onRegistrarPago={() =>
                      registrarPago.mutate(o.id, {
                        onSuccess: (a) =>
                          setAviso(
                            `Pago registrado: ${a.nombre} queda al día hasta el ${formatearFecha(a.plan.cobro?.pagoManualHasta ?? '')}.`,
                          ),
                        onError: (e) => setAviso(getErrorMessage(e)),
                      })
                    }
                  />
                </div>
              </li>
            ))}
            {lista.length === 0 && (
              <li className="px-5 py-8 text-center text-sm text-stone-500">
                Ninguna cuenta coincide con la búsqueda.
              </li>
            )}
          </ul>
        </div>
      )}

      <Dialog
        open={editando !== null}
        onClose={() => setEditando(null)}
        title={`Plan de ${editando?.nombre ?? ''}`}
        description="El cambio rige enseguida. La cuenta lo ve en Mi plan."
      >
        {editando && (
          <Formulario key={editando.id} organizacion={editando} onListo={() => setEditando(null)} />
        )}
      </Dialog>

      <Dialog
        open={creando}
        onClose={() => setCreando(false)}
        title="Nueva cuenta"
        description="Al dueño le llega un email con un enlace para elegir su contraseña."
      >
        {creando && (
          <NuevaCuenta
            planes={data?.[0]?.plan.planes ?? []}
            onListo={(email) => {
              setCreando(false)
              setAviso(`Cuenta creada. Le mandamos el enlace a ${email}.`)
            }}
            onCancelar={() => setCreando(false)}
          />
        )}
      </Dialog>

      <Dialog
        open={precios}
        onClose={() => setPrecios(false)}
        title="Precios por mes"
        description="Lo que paga cada plan con Mercado Pago. Sin precio, el plan no se contrata online. Las suscripciones ya hechas siguen con su precio."
      >
        {precios && <Precios onListo={() => setPrecios(false)} />}
      </Dialog>

      {aEliminar && (
        <EliminarCuenta
          organizacion={aEliminar}
          onListo={() => {
            setAviso(`Se eliminó la cuenta ${aEliminar.nombre} con todos sus datos.`)
            setAEliminar(null)
          }}
          onCancelar={() => setAEliminar(null)}
        />
      )}

      <ConfirmDialog
        open={aSuspender !== null}
        title={
          aSuspender?.suspendida
            ? `Reactivar ${aSuspender?.nombre ?? ''}`
            : `Suspender ${aSuspender?.nombre ?? ''}`
        }
        description={
          aSuspender?.suspendida
            ? 'Todos los usuarios de la cuenta vuelven a poder entrar.'
            : 'Nadie de la cuenta va a poder entrar hasta que la reactives. Sus datos no se borran.'
        }
        confirmLabel={aSuspender?.suspendida ? 'Reactivar' : 'Suspender'}
        loading={suspender.isPending}
        error={suspender.error ? getErrorMessage(suspender.error) : null}
        onClose={() => {
          setASuspender(null)
          suspender.reset()
        }}
        onConfirm={() =>
          aSuspender &&
          suspender.mutate(
            { id: aSuspender.id, suspendida: !aSuspender.suspendida },
            { onSuccess: () => setASuspender(null) },
          )
        }
      />

      {aviso && (
        <div
          role="status"
          className="fixed inset-x-4 bottom-4 z-50 mx-auto flex max-w-md items-center gap-3 rounded-xl border border-stone-200 bg-white px-4 py-3 text-sm shadow-lg dark:border-stone-700 dark:bg-stone-900"
        >
          <span className="flex-1">{aviso}</span>
          <button
            type="button"
            onClick={() => setAviso(null)}
            aria-label="Cerrar aviso"
            className="rounded-md p-1 text-stone-400 hover:bg-stone-100 dark:hover:bg-stone-800"
          >
            <X className="size-4" />
          </button>
        </div>
      )}
    </>
  )
}

/** Suscripción con Mercado Pago de una cuenta, en una línea. */
function EstadoCobroCuenta({ plan }: { plan: Plan }) {
  const s = plan.cobro?.suscripcion
  const p = plan.cobro?.pendiente
  const sinPago =
    plan.cobro?.habilitada === false ? (
      <p className="flex items-center gap-1 text-xs font-medium text-red-600 dark:text-red-400">
        <CreditCard className="size-3" />
        Sin pago: solo lectura
      </p>
    ) : null
  const manual = plan.cobro?.pagoManualHasta
  if (manual && (!s || s.estado === 'VENCIDA') && !sinPago) {
    return (
      <p className="flex items-center gap-1 text-xs text-emerald-700 dark:text-emerald-400">
        <CreditCard className="size-3" />
        Pago manual hasta el {formatearFecha(manual)}
      </p>
    )
  }
  if (sinPago && !p) return sinPago
  if (s && s.estado !== 'VENCIDA') {
    const [texto, color] = s.enGracia
      ? [
          `Impaga · baja el ${formatearFecha(diaSiguiente(s.hasta!))}`,
          'text-red-600 dark:text-red-400',
        ]
      : s.estado === 'CANCELADA'
        ? [`Cancelada · rige hasta el ${formatearFecha(s.hasta!)}`, 'text-stone-500']
        : s.estado === 'PAUSADA'
          ? ['Pausada en Mercado Pago', 'text-amber-700 dark:text-amber-300']
          : [
              s.pagadoHasta
                ? `Pagado hasta el ${formatearFecha(s.pagadoHasta)}`
                : 'Esperando el primer cobro',
              'text-emerald-700 dark:text-emerald-400',
            ]
    return (
      <p className={`flex items-center gap-1 text-xs ${color}`}>
        <CreditCard className="size-3" />
        {texto}
      </p>
    )
  }
  if (p) {
    return (
      <p className="flex items-center gap-1 text-xs text-amber-700 dark:text-amber-300">
        <CreditCard className="size-3" />
        Pago pendiente ({p.planNombre})
      </p>
    )
  }
  if (s?.estado === 'VENCIDA') {
    return (
      <p className="flex items-center gap-1 text-xs text-stone-500">
        <CreditCard className="size-3" />
        Suscripción vencida
      </p>
    )
  }
  return null
}

function Precios({ onListo }: { onListo: () => void }) {
  const { data: planes, isPending, error } = usePlanesAdmin()
  const cambiar = useCambiarPrecio()
  const [valores, setValores] = useState<Record<string, string>>({})
  const [errorGuardar, setErrorGuardar] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  if (isPending) {
    return (
      <div className="grid place-items-center py-8">
        <LoaderCircle className="size-5 animate-spin text-stone-400" />
      </div>
    )
  }
  if (error || !planes) return <FormError message={getErrorMessage(error)} />

  const valor = (p: PlanInfo) =>
    valores[p.codigo] ?? (p.precioMensual ? String(p.precioMensual) : '')

  async function guardar(e: FormEvent) {
    e.preventDefault()
    setErrorGuardar(null)
    const cambios = planes!.filter((p) => {
      const nuevo = valores[p.codigo]
      if (nuevo === undefined) return false
      return (nuevo.trim() === '' ? null : Number(nuevo)) !== (p.precioMensual || null)
    })
    for (const p of cambios) {
      const texto = valores[p.codigo].trim().replace(',', '.')
      if (texto !== '' && (!Number.isFinite(Number(texto)) || Number(texto) < 0)) {
        setErrorGuardar(`El precio de ${p.nombre} no es válido.`)
        return
      }
    }
    setGuardando(true)
    try {
      for (const p of cambios) {
        const texto = valores[p.codigo].trim().replace(',', '.')
        await cambiar.mutateAsync({
          codigo: p.codigo,
          precioMensual: texto === '' ? null : Number(texto),
        })
      }
      onListo()
    } catch (err) {
      setErrorGuardar(getErrorMessage(err))
      setGuardando(false)
    }
  }

  return (
    <form onSubmit={guardar} noValidate className="space-y-4">
      <FormError message={errorGuardar} />
      {planes.map((p) => (
        <div key={p.codigo} className="flex items-end gap-3">
          <div className="flex-1">
            <TextField
              label={`${p.nombre} · ${RECURSOS.map((r) => limite(p, r) ?? '∞').join(' / ')}`}
              inputMode="decimal"
              placeholder="Sin precio"
              value={valor(p)}
              onChange={(e) => setValores((v) => ({ ...v, [p.codigo]: e.target.value }))}
              hint={
                valor(p).trim() && Number(valor(p).replace(',', '.')) > 0
                  ? `${pesos.format(Number(valor(p).replace(',', '.')))} por mes`
                  : 'No se contrata online'
              }
            />
          </div>
        </div>
      ))}
      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onListo} disabled={guardando}>
          Cancelar
        </Button>
        <Button type="submit" loading={guardando}>
          Guardar precios
        </Button>
      </div>
    </form>
  )
}

function MasAcciones({
  organizacion,
  onReenviar,
  onRestablecer,
  onSuspender,
  onEliminar,
  onRegistrarPago,
}: {
  organizacion: OrganizacionAdmin
  onReenviar: () => void
  onRestablecer: () => void
  onSuspender: () => void
  onEliminar: () => void
  onRegistrarPago: () => void
}) {
  const cobro = organizacion.plan.cobro
  const quedaHasta = siguienteVencimiento(
    cobro?.pagoManualHasta,
    hoyISO(),
    cobro?.diaVencimientoManual ?? 10,
  )
  const [abierto, setAbierto] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const cerrar = useCallback(() => setAbierto(false), [])
  useDismiss(ref, abierto, cerrar)

  const item =
    'flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm hover:bg-stone-100 dark:hover:bg-stone-800'
  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setAbierto((a) => !a)}
        aria-haspopup="menu"
        aria-expanded={abierto}
        aria-label={`Más acciones para ${organizacion.nombre}`}
        className="rounded-lg p-2 text-stone-500 hover:bg-stone-100 dark:hover:bg-stone-800"
      >
        <MoreHorizontal className="size-4" />
      </button>
      {abierto && (
        <div
          role="menu"
          className="absolute right-0 z-20 mt-1 w-60 rounded-xl border border-stone-200 bg-white p-1 shadow-lg dark:border-stone-700 dark:bg-stone-900"
        >
          <button
            type="button"
            role="menuitem"
            className={`${item} items-start`}
            onClick={() => {
              setAbierto(false)
              onRegistrarPago()
            }}
          >
            <Banknote className="mt-0.5 size-4 text-stone-500" />
            <span>
              Registrar pago
              <span className="block text-xs text-stone-500">
                Queda pagada hasta el {formatearFecha(quedaHasta)}
              </span>
            </span>
          </button>
          {organizacion.duenoPendiente && (
            <button
              type="button"
              role="menuitem"
              className={item}
              onClick={() => {
                setAbierto(false)
                onReenviar()
              }}
            >
              <Send className="size-4 text-stone-500" />
              Reenviar enlace de activación
            </button>
          )}
          {!organizacion.duenoPendiente && (
            <button
              type="button"
              role="menuitem"
              className={`${item} items-start`}
              onClick={() => {
                setAbierto(false)
                onRestablecer()
              }}
            >
              <KeyRound className="mt-0.5 size-4 text-stone-500" />
              <span>
                Enviar enlace para cambiar contraseña
                <span className="block text-xs text-stone-500">
                  Al dueño. La contraseña la elige él.
                </span>
              </span>
            </button>
          )}
          <button
            type="button"
            role="menuitem"
            className={`${item} ${organizacion.suspendida ? '' : 'text-red-600 dark:text-red-400'}`}
            onClick={() => {
              setAbierto(false)
              onSuspender()
            }}
          >
            {organizacion.suspendida ? (
              <PlayCircle className="size-4" />
            ) : (
              <Ban className="size-4" />
            )}
            {organizacion.suspendida ? 'Reactivar cuenta' : 'Suspender cuenta'}
          </button>
          <button
            type="button"
            role="menuitem"
            className={`${item} text-red-600 dark:text-red-400`}
            onClick={() => {
              setAbierto(false)
              onEliminar()
            }}
          >
            <Trash2 className="size-4" />
            Eliminar cuenta
          </button>
        </div>
      )}
    </div>
  )
}

function NuevaCuenta({
  planes,
  onListo,
  onCancelar,
}: {
  planes: PlanInfo[]
  onListo: (email: string) => void
  onCancelar: () => void
}) {
  const crear = useCrearCuenta()
  const [c, setC] = useState({
    nombreOrganizacion: '',
    nombre: '',
    apellido: '',
    email: '',
    planCodigo: 'INICIAL',
  })
  const [conPrueba, setConPrueba] = useState(true)
  const [pruebaHasta, setPruebaHasta] = useState(() => sumarDias(hoyISO(), 29))
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const set = (campo: keyof typeof c, valor: string) => {
    setC((prev) => ({ ...prev, [campo]: valor }))
    setErrores((e) => ({ ...e, [campo]: '' }))
  }

  async function guardar(e: FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      const nueva = await crear.mutateAsync({
        ...c,
        apellido: c.apellido.trim() || null,
        pruebaHasta: conPrueba ? pruebaHasta : null,
      })
      onListo(nueva.duenoEmail ?? c.email)
    } catch (err) {
      setErrores(getFieldErrors(err))
      setError(getErrorMessage(err))
    }
  }

  return (
    <form onSubmit={guardar} noValidate className="space-y-4">
      <FormError message={error} />
      <TextField
        label="Nombre del campo o empresa"
        autoFocus
        value={c.nombreOrganizacion}
        onChange={(e) => set('nombreOrganizacion', e.target.value)}
        error={errores.nombreOrganizacion}
      />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          label="Nombre del dueño"
          value={c.nombre}
          onChange={(e) => set('nombre', e.target.value)}
          error={errores.nombre}
        />
        <TextField
          label="Apellido"
          value={c.apellido}
          onChange={(e) => set('apellido', e.target.value)}
        />
      </div>
      <TextField
        label="Email del dueño"
        type="email"
        value={c.email}
        onChange={(e) => set('email', e.target.value)}
        error={errores.email}
        hint="Le llega el enlace para elegir la contraseña."
      />
      <SelectField
        label="Plan"
        value={c.planCodigo}
        onChange={(e) => set('planCodigo', e.target.value)}
      >
        {planes.map((p) => (
          <option key={p.codigo} value={p.codigo}>
            {p.nombre} · {RECURSOS.map((r) => limite(p, r) ?? '∞').join(' / ')}
          </option>
        ))}
      </SelectField>
      <div className="space-y-3 rounded-lg border border-stone-200 p-4 dark:border-stone-800">
        <label className="flex items-center gap-2 text-sm font-medium">
          <input
            type="checkbox"
            checked={conPrueba}
            onChange={(e) => setConPrueba(e.target.checked)}
            className="accent-brand-600 size-4"
          />
          Prueba gratis
        </label>
        {conPrueba && (
          <TextField
            label="Hasta (inclusive)"
            type="date"
            value={pruebaHasta}
            onChange={(e) => setPruebaHasta(e.target.value)}
          />
        )}
      </div>
      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onCancelar} disabled={crear.isPending}>
          Cancelar
        </Button>
        <Button type="submit" loading={crear.isPending}>
          Crear y mandar enlace
        </Button>
      </div>
    </form>
  )
}

/** 'YYYY-MM-DD' + n meses (el 31 de un mes corto queda en el último día) */
function sumarMeses(fecha: string, meses: number) {
  const [a, m, d] = fecha.split('-').map(Number)
  const ultimo = new Date(Date.UTC(a, m - 1 + meses + 1, 0)).getUTCDate()
  return new Date(Date.UTC(a, m - 1 + meses, Math.min(d, ultimo))).toISOString().slice(0, 10)
}

function sumarDias(iso: string, dias: number) {
  const d = new Date(`${iso}T00:00:00`)
  d.setDate(d.getDate() + dias)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function Formulario({
  organizacion,
  onListo,
}: {
  organizacion: OrganizacionAdmin
  onListo: () => void
}) {
  const cambiar = useCambiarPlan()
  const [planCodigo, setPlanCodigo] = useState(organizacion.plan.contratado.codigo)
  const [conPrueba, setConPrueba] = useState(organizacion.plan.enPrueba)
  const [pruebaHasta, setPruebaHasta] = useState(
    organizacion.plan.enPrueba && organizacion.plan.pruebaHasta
      ? organizacion.plan.pruebaHasta
      : sumarDias(hoyISO(), 29),
  )
  const manualActual = organizacion.plan.cobro?.pagoManualHasta ?? null
  const [conPagoManual, setConPagoManual] = useState(!!manualActual)
  const [pagoManualHasta, setPagoManualHasta] = useState(
    manualActual ??
      siguienteVencimiento(null, hoyISO(), organizacion.plan.cobro?.diaVencimientoManual ?? 10),
  )
  const [error, setError] = useState<string | null>(null)

  async function guardar(e: FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      await cambiar.mutateAsync({
        id: organizacion.id,
        planCodigo,
        pruebaHasta: conPrueba ? pruebaHasta : null,
        pagoManualHasta: conPagoManual ? pagoManualHasta : null,
      })
      onListo()
    } catch (err) {
      setError(getErrorMessage(err))
    }
  }

  return (
    <form onSubmit={guardar} className="space-y-4">
      <FormError message={error} />
      <SelectField
        label="Plan contratado"
        value={planCodigo}
        onChange={(e) => setPlanCodigo(e.target.value)}
      >
        {organizacion.plan.planes.map((p) => (
          <option key={p.codigo} value={p.codigo}>
            {p.nombre} · {RECURSOS.map((r) => limite(p, r) ?? '∞').join(' / ')}
          </option>
        ))}
      </SelectField>
      <p className="-mt-2 text-xs text-stone-500">Animales / establecimientos / usuarios.</p>

      <fieldset className="space-y-3 rounded-lg border border-stone-200 p-4 dark:border-stone-800">
        <label className="flex items-center gap-2 text-sm font-medium">
          <input
            type="checkbox"
            checked={conPrueba}
            onChange={(e) => setConPrueba(e.target.checked)}
            className="accent-brand-600 size-4"
          />
          Prueba gratis
        </label>
        {conPrueba ? (
          <>
            <TextField
              label="Hasta (inclusive)"
              type="date"
              value={pruebaHasta}
              onChange={(e) => setPruebaHasta(e.target.value)}
            />
            <div className="flex flex-wrap gap-2">
              {[7, 15, 30].map((d) => (
                <Button
                  key={d}
                  variant="secondary"
                  className="px-2.5 py-1 text-xs"
                  onClick={() => setPruebaHasta(sumarDias(pruebaHasta, d))}
                >
                  +{d} días
                </Button>
              ))}
            </div>
            <p className="text-xs text-stone-500">
              Mientras dura, rigen los límites del plan de prueba (o los del contratado, si es
              mayor).
            </p>
          </>
        ) : (
          <p className="text-xs text-stone-500">
            Sin prueba: para usar la cuenta necesita una suscripción o un pago manual.
          </p>
        )}
      </fieldset>

      <fieldset className="space-y-3 rounded-lg border border-stone-200 p-4 dark:border-stone-800">
        <label className="flex items-center gap-2 text-sm font-medium">
          <input
            type="checkbox"
            checked={conPagoManual}
            onChange={(e) => setConPagoManual(e.target.checked)}
            className="accent-brand-600 size-4"
          />
          Pago manual (transferencia, efectivo)
        </label>
        {conPagoManual ? (
          <>
            <TextField
              label="Pagado hasta (inclusive)"
              type="date"
              value={pagoManualHasta}
              onChange={(e) => setPagoManualHasta(e.target.value)}
            />
            <div className="flex flex-wrap gap-2">
              {[1, 3, 12].map((m) => (
                <Button
                  key={m}
                  variant="secondary"
                  className="px-2.5 py-1 text-xs"
                  onClick={() => setPagoManualHasta(sumarMeses(pagoManualHasta, m))}
                >
                  +{m} {m === 1 ? 'mes' : 'meses'}
                </Button>
              ))}
            </div>
            <p className="text-xs text-stone-500">
              Hasta ese día la cuenta se usa sin suscripción de Mercado Pago.
            </p>
          </>
        ) : (
          <p className="text-xs text-stone-500">
            Para clientes que te pagan por fuera de Mercado Pago.
          </p>
        )}
      </fieldset>

      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onListo} disabled={cambiar.isPending}>
          Cancelar
        </Button>
        <Button type="submit" loading={cambiar.isPending}>
          Guardar
        </Button>
      </div>
    </form>
  )
}

/** Borrar una cuenta no se puede deshacer: hay que escribir su nombre para confirmar. */
function EliminarCuenta({
  organizacion,
  onListo,
  onCancelar,
}: {
  organizacion: OrganizacionAdmin
  onListo: () => void
  onCancelar: () => void
}) {
  const eliminar = useEliminarCuenta()
  const [confirmacion, setConfirmacion] = useState('')
  const normalizar = (t: string) => t.trim().replace(/\s+/g, ' ').toLowerCase()
  const coincide = normalizar(confirmacion) === normalizar(organizacion.nombre)

  function enviar(e: FormEvent) {
    e.preventDefault()
    if (!coincide) return
    eliminar.mutate({ id: organizacion.id, confirmacion }, { onSuccess: onListo })
  }

  return (
    <Dialog
      open
      onClose={onCancelar}
      title={`Eliminar ${organizacion.nombre}`}
      description="Se borran la cuenta, sus usuarios y todos sus datos: establecimientos, animales, eventos y suscripciones. No se puede deshacer."
      size="sm"
    >
      <form onSubmit={enviar} noValidate className="space-y-4">
        <FormError message={eliminar.error ? getErrorMessage(eliminar.error) : null} />
        {organizacion.duenoEmail && (
          <p className="text-sm text-stone-600 dark:text-stone-400">
            El email {organizacion.duenoEmail} queda libre para crear otra cuenta.
          </p>
        )}
        <TextField
          label={`Escribí "${organizacion.nombre}" para confirmar`}
          value={confirmacion}
          onChange={(e) => setConfirmacion(e.target.value)}
          autoComplete="off"
          autoFocus
        />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onCancelar} disabled={eliminar.isPending}>
            Cancelar
          </Button>
          <Button type="submit" variant="danger" disabled={!coincide} loading={eliminar.isPending}>
            Eliminar para siempre
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
