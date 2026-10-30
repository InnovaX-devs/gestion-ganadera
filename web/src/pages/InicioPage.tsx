import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { ArrowRight, CircleCheck, CircleX, LoaderCircle, MapPinned } from 'lucide-react'
import { PageHeader } from '@/components/PageHeader'
import { useAuth } from '@/features/auth/useAuth'
import { puede } from '@/features/establecimientos/permisos'
import { useEstablecimientoActivo } from '@/features/establecimientos/useEstablecimientoActivo'
import { AccesosRapidos } from '@/features/inicio/AccesosRapidos'
import { UltimosEventos } from '@/features/inicio/UltimosEventos'
import { StockPanel } from '@/features/stock/StockPanel'
import { api, getErrorMessage } from '@/lib/api'

type Estado = { tipo: 'cargando' } | { tipo: 'ok' } | { tipo: 'error'; mensaje: string }

function saludo() {
  const hora = new Date().getHours()
  if (hora < 13) return 'Buen día'
  if (hora < 20) return 'Buenas tardes'
  return 'Buenas noches'
}

const formatoFecha = new Intl.DateTimeFormat('es-AR', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
})

/** "Jueves 29 de octubre" */
function fechaDeHoy() {
  const texto = formatoFecha.format(new Date()).replace(',', '')
  return texto.charAt(0).toUpperCase() + texto.slice(1)
}

/** Dashboard de inicio (#26): saludo, accesos para registrar, stock y últimos eventos. */
export function InicioPage() {
  const { usuario } = useAuth()
  const { activo, cargando } = useEstablecimientoActivo()

  return (
    <>
      <PageHeader
        title={`${saludo()}, ${usuario?.nombre ?? ''}`}
        description={[fechaDeHoy(), usuario?.organizacionNombre, activo?.nombre]
          .filter(Boolean)
          .join(' · ')}
      />
      {!cargando && !activo && <PrimerosPasos />}
      {/* key: al cambiar de establecimiento todo arranca de cero (diálogos abiertos incluidos) */}
      {activo && (
        <div key={activo.id} className="mb-6 grid grid-cols-1 gap-6">
          <AccesosRapidos establecimientoId={activo.id} rol={activo.rol} />
          <StockPanel
            establecimientoId={activo.id}
            establecimientoNombre={activo.nombre}
            puedeCargarAnimales={puede(activo.rol, 'GESTIONAR_ANIMALES')}
          />
          <UltimosEventos establecimientoId={activo.id} />
        </div>
      )}
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <EstadoBackend />
        <Tarjeta
          titulo="Referencias de mercado"
          texto="Se completa en el milestone M4 - Dashboard."
        />
      </div>
    </>
  )
}

/** Se muestra mientras la organización no tiene ningún establecimiento. */
function PrimerosPasos() {
  return (
    <section className="border-brand-200 bg-brand-50 dark:border-brand-900 dark:bg-brand-900/20 mb-6 flex flex-col gap-4 rounded-xl border p-5 sm:flex-row sm:items-center">
      <span className="text-brand-600 dark:text-brand-200 grid size-10 shrink-0 place-items-center rounded-full bg-white dark:bg-stone-900">
        <MapPinned className="size-5" />
      </span>
      <div className="flex-1">
        <h2 className="font-semibold">Primer paso: cargá tu establecimiento</h2>
        <p className="mt-0.5 text-sm text-stone-600 dark:text-stone-400">
          Todo se organiza por campo: lotes, rodeos, animales y eventos.
        </p>
      </div>
      <Link
        to="/establecimientos"
        state={{ crear: true }}
        className="bg-brand-600 hover:bg-brand-700 inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-medium text-white"
      >
        Crear establecimiento
        <ArrowRight className="size-4" />
      </Link>
    </section>
  )
}

function Tarjeta({ titulo, texto }: { titulo: string; texto: string }) {
  return (
    <section className="rounded-xl border border-stone-200 bg-white p-5 dark:border-stone-800 dark:bg-stone-900">
      <h2 className="text-sm font-medium text-stone-500">{titulo}</h2>
      <p className="mt-2 text-sm text-stone-600 dark:text-stone-400">{texto}</p>
    </section>
  )
}

/** Verifica la conexión con el backend (GET /api/health). Útil para validar el setup. */
function EstadoBackend() {
  const [estado, setEstado] = useState<Estado>({ tipo: 'cargando' })

  useEffect(() => {
    let activo = true
    api
      .get('/health')
      .then(() => activo && setEstado({ tipo: 'ok' }))
      .catch((e) => activo && setEstado({ tipo: 'error', mensaje: getErrorMessage(e) }))
    return () => {
      activo = false
    }
  }, [])

  return (
    <section className="rounded-xl border border-stone-200 bg-white p-5 dark:border-stone-800 dark:bg-stone-900">
      <h2 className="text-sm font-medium text-stone-500">Conexión con el servidor</h2>
      <div className="mt-2 flex items-center gap-2 text-sm">
        {estado.tipo === 'cargando' && (
          <>
            <LoaderCircle className="size-4 animate-spin text-stone-400" /> Verificando…
          </>
        )}
        {estado.tipo === 'ok' && (
          <>
            <CircleCheck className="size-4 text-emerald-600" /> Backend y base de datos OK
          </>
        )}
        {estado.tipo === 'error' && (
          <>
            <CircleX className="size-4 text-red-600" /> {estado.mensaje}
          </>
        )}
      </div>
    </section>
  )
}
