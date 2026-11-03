import { useState } from 'react'
import { Plus } from 'lucide-react'
import { Button } from '@/components/Button'
import { PageHeader } from '@/components/PageHeader'
import { AlertasSanitarias } from '@/features/calendario/AlertasSanitarias'
import { AvisosPorEmail } from '@/features/calendario/AvisosPorEmail'
import { CalendarioMes } from '@/features/calendario/CalendarioMes'
import { aISO, rangoDelMes } from '@/features/calendario/fechas'
import { PlanDetalleDialog } from '@/features/calendario/PlanDetalleDialog'
import { PlanDialog } from '@/features/calendario/PlanDialog'
import { usePlanSanitario } from '@/features/calendario/queries'
import type { Plan } from '@/features/calendario/types'
import { puede } from '@/features/establecimientos/permisos'
import { useEstablecimientoActivo } from '@/features/establecimientos/useEstablecimientoActivo'
import { getErrorMessage } from '@/lib/api'

/** Calendario sanitario (#34): tratamientos planificados, alertas y avisos por email. */
export function CalendarioSanitarioPage() {
  const { activo } = useEstablecimientoActivo()
  // Se calcula una vez al abrir la página
  const [hoy] = useState(() => aISO(new Date()))
  const [mes, setMes] = useState(hoy.slice(0, 7))
  const { desde, hasta } = rangoDelMes(mes)
  const { data: planes, error } = usePlanSanitario(activo?.id, desde, hasta)
  const [nuevo, setNuevo] = useState<{ fecha: string } | null>(null)
  const [editando, setEditando] = useState<Plan | null>(null)
  const [abierto, setAbierto] = useState<Plan | null>(null)

  if (!activo) {
    return (
      <>
        <PageHeader title="Calendario sanitario" />
        <p className="text-sm text-stone-500">Elegí un establecimiento para ver su calendario.</p>
      </>
    )
  }
  const puedeEditar = puede(activo.rol, 'REGISTRAR_SANIDAD')
  // El detalle muestra siempre la versión más nueva del plan (después de marcarlo, editarlo...)
  const detalle = abierto ? (planes?.find((p) => p.id === abierto.id) ?? null) : null

  return (
    <>
      <PageHeader
        title="Calendario sanitario"
        description="Planificá vacunas y tratamientos por lote o rodeo, y recibí un aviso antes de la fecha."
        actions={
          puedeEditar && (
            <Button onClick={() => setNuevo({ fecha: hoy })}>
              <Plus className="size-4" />
              Planificar
            </Button>
          )
        }
      />

      <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="min-w-0 space-y-3">
          {error && (
            <p className="text-sm text-red-700 dark:text-red-400">
              No se pudo cargar el calendario: {getErrorMessage(error)}
            </p>
          )}
          <CalendarioMes
            mes={mes}
            hoy={hoy}
            planes={planes ?? []}
            onCambiarMes={setMes}
            onDia={puedeEditar ? (fecha) => setNuevo({ fecha }) : undefined}
            onPlan={setAbierto}
          />
          <p className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-stone-500">
            <Referencia clase="bg-brand-100 dark:bg-brand-900/60" texto="Pendiente" />
            <Referencia clase="bg-red-100 dark:bg-red-950" texto="Vencido" />
            <Referencia clase="bg-emerald-100 dark:bg-emerald-950" texto="Hecho" />
            <Referencia clase="bg-stone-200 dark:bg-stone-700" texto="Cancelado" />
          </p>
        </div>
        <div className="space-y-4">
          <AlertasSanitarias establecimientoId={activo.id} onPlan={setAbierto} />
          {puedeEditar && <AvisosPorEmail />}
        </div>
      </div>

      <PlanDialog
        establecimientoId={activo.id}
        open={!!nuevo || !!editando}
        plan={editando ?? undefined}
        fecha={nuevo?.fecha}
        onClose={() => {
          setNuevo(null)
          setEditando(null)
        }}
      />
      <PlanDetalleDialog
        establecimientoId={activo.id}
        plan={detalle ?? abierto}
        hoy={hoy}
        puedeEditar={puedeEditar}
        onEditar={(p) => {
          setAbierto(null)
          setEditando(p)
        }}
        onClose={() => setAbierto(null)}
      />
    </>
  )
}

function Referencia({ clase, texto }: { clase: string; texto: string }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span className={`size-2.5 rounded-sm ${clase}`} aria-hidden />
      {texto}
    </span>
  )
}
