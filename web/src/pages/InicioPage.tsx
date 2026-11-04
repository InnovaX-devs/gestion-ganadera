import { Link } from 'react-router'
import { ArrowRight, MapPinned } from 'lucide-react'
import { PageHeader } from '@/components/PageHeader'
import { useAuth } from '@/features/auth/useAuth'
import { puede } from '@/features/establecimientos/permisos'
import { AlertasSanitarias } from '@/features/calendario/AlertasSanitarias'
import { ClimaPanel } from '@/features/clima/ClimaPanel'
import { useEstablecimientoActivo } from '@/features/establecimientos/useEstablecimientoActivo'
import { AccesosRapidos } from '@/features/inicio/AccesosRapidos'
import { UltimosEventos } from '@/features/inicio/UltimosEventos'
import { MercadoPanel } from '@/features/mercado/MercadoPanel'
import { InstalarTarjeta } from '@/features/pwa/InstalarTarjeta'
import { StockPanel } from '@/features/stock/StockPanel'

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

/** Dashboard de inicio (#26): saludo, accesos para registrar, stock, últimos eventos, clima (#29) y mercado (#28). */
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
      {/* Se oculta sola si no se puede instalar, ya está instalada o se descartó */}
      <div className="mb-6 empty:hidden">
        <InstalarTarjeta />
      </div>
      {!cargando && !activo && <PrimerosPasos />}
      {/* key: al cambiar de establecimiento todo arranca de cero (diálogos abiertos incluidos) */}
      {activo && (
        <div key={activo.id} className="mb-6 grid grid-cols-1 gap-6">
          <AccesosRapidos establecimientoId={activo.id} rol={activo.rol} />
          {/* Solo aparece si hay algo que avisar (#34) */}
          <AlertasSanitarias establecimientoId={activo.id} compacta />
          <StockPanel
            establecimientoId={activo.id}
            establecimientoNombre={activo.nombre}
            puedeCargarAnimales={puede(activo.rol, 'GESTIONAR_ANIMALES')}
          />
          <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-3">
            <div className="lg:col-span-2">
              <UltimosEventos establecimientoId={activo.id} />
            </div>
            <ClimaPanel
              establecimientoId={activo.id}
              puedeEditarUbicacion={puede(activo.rol, 'EDITAR_ESTABLECIMIENTO')}
            />
          </div>
        </div>
      )}
      {/* El mercado es el mismo para todos: se muestra aunque no haya establecimiento */}
      <MercadoPanel />
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
