import { type FormEvent, useState } from 'react'
import { Button } from '@/components/Button'
import { Dialog } from '@/components/Dialog'
import { FormError } from '@/components/FormError'
import { SelectField } from '@/components/SelectField'
import { TextAreaField } from '@/components/TextAreaField'
import { TextField } from '@/components/TextField'
import { useAgrupaciones } from '@/features/agrupaciones/queries'
import { useProductos } from '@/features/sanidad/queries'
import { getErrorMessage, getFieldErrors } from '@/lib/api'
import { useGuardarPlan } from './queries'
import type { Plan } from './types'

type Destino = 'ESTABLECIMIENTO' | 'LOTE' | 'RODEO'

const AVISOS = [
  { dias: 0, texto: 'El mismo día' },
  { dias: 1, texto: '1 día antes' },
  { dias: 2, texto: '2 días antes' },
  { dias: 3, texto: '3 días antes' },
  { dias: 7, texto: '1 semana antes' },
  { dias: 15, texto: '15 días antes' },
  { dias: 30, texto: '30 días antes' },
]

/** Planificar (o editar) un tratamiento para una fecha: de un lote, un rodeo o todo el campo (#34). */
export function PlanDialog({
  establecimientoId,
  open,
  plan,
  fecha,
  onClose,
}: {
  establecimientoId: string
  open: boolean
  /** Para editar */
  plan?: Plan
  /** Fecha con la que arranca un tratamiento nuevo (el día tocado en el calendario) */
  fecha?: string
  onClose: () => void
}) {
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={plan ? 'Editar tratamiento planificado' : 'Planificar tratamiento'}
      description="Aparece en el calendario y en las alertas, y llega un aviso por email antes de la fecha."
    >
      {open && (
        <Formulario
          establecimientoId={establecimientoId}
          plan={plan}
          fecha={fecha}
          onClose={onClose}
        />
      )}
    </Dialog>
  )
}

function Formulario({
  establecimientoId,
  plan,
  fecha: fechaInicial,
  onClose,
}: {
  establecimientoId: string
  plan?: Plan
  fecha?: string
  onClose: () => void
}) {
  const { data: lotes } = useAgrupaciones('lotes', establecimientoId)
  const { data: rodeos } = useAgrupaciones('rodeos', establecimientoId)
  const { data: productos } = useProductos(establecimientoId)
  const guardar = useGuardarPlan(establecimientoId)

  const [fecha, setFecha] = useState(plan?.fecha ?? fechaInicial ?? '')
  const [destino, setDestino] = useState<Destino>(plan?.destino.tipo ?? 'ESTABLECIMIENTO')
  const [grupoId, setGrupoId] = useState(plan?.destino.id ?? '')
  const [productoId, setProductoId] = useState(plan?.producto?.id ?? '')
  const [descripcion, setDescripcion] = useState(plan?.descripcion ?? '')
  const [dosis, setDosis] = useState(plan?.dosis ?? '')
  const [notas, setNotas] = useState(plan?.notas ?? '')
  const [avisar, setAvisar] = useState(plan?.avisarDiasAntes ?? 1)
  const [repetir, setRepetir] = useState(false)
  const [veces, setVeces] = useState('2')
  const [cadaDias, setCadaDias] = useState('30')
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)

  const grupos = destino === 'LOTE' ? lotes : destino === 'RODEO' ? rodeos : undefined

  /** Elegir un producto completa la descripción y la dosis (si están vacías). */
  function elegirProducto(id: string) {
    setProductoId(id)
    const producto = productos?.find((p) => p.id === id)
    if (!producto) return
    if (!descripcion.trim()) setDescripcion(producto.nombre)
    if (!dosis.trim() && producto.dosisHabitual) setDosis(producto.dosisHabitual)
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const nuevos: Record<string, string> = {}
    if (!fecha) nuevos.fecha = 'Indicá la fecha'
    if (destino !== 'ESTABLECIMIENTO' && !grupoId)
      nuevos.destino = destino === 'LOTE' ? 'Elegí el lote' : 'Elegí el rodeo'
    if (!descripcion.trim()) nuevos.descripcion = 'Describí el tratamiento'
    const vecesNumero = Number(veces)
    const cadaNumero = Number(cadaDias)
    if (repetir && (!Number.isInteger(vecesNumero) || vecesNumero < 2 || vecesNumero > 12))
      nuevos.repeticiones = 'De 2 a 12 veces'
    if (repetir && (!Number.isInteger(cadaNumero) || cadaNumero < 1 || cadaNumero > 365))
      nuevos.cadaDias = 'De 1 a 365 días'
    setErrores(nuevos)
    if (Object.keys(nuevos).length > 0) return

    try {
      await guardar.mutateAsync({
        id: plan?.id,
        data: {
          fecha,
          loteId: destino === 'LOTE' ? grupoId : null,
          rodeoId: destino === 'RODEO' ? grupoId : null,
          productoId: productoId || null,
          descripcion: descripcion.trim(),
          dosis: dosis.trim() || null,
          notas: notas.trim() || null,
          avisarDiasAntes: avisar,
          ...(repetir && !plan ? { repeticiones: vecesNumero, cadaDias: cadaNumero } : {}),
        },
      })
      onClose()
    } catch (err) {
      setErrores(getFieldErrors(err))
      setError(getErrorMessage(err))
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-4">
      <FormError message={error} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          label="Fecha"
          type="date"
          value={fecha}
          onChange={(e) => setFecha(e.target.value)}
          error={errores.fecha}
          required
        />
        <SelectField
          label="Avisar por email"
          value={avisar}
          onChange={(e) => setAvisar(Number(e.target.value))}
          error={errores.avisarDiasAntes}
        >
          {AVISOS.map((a) => (
            <option key={a.dias} value={a.dias}>
              {a.texto}
            </option>
          ))}
        </SelectField>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <SelectField
          label="Se aplica a"
          value={destino}
          onChange={(e) => {
            setDestino(e.target.value as Destino)
            setGrupoId('')
          }}
        >
          <option value="ESTABLECIMIENTO">Todo el establecimiento</option>
          <option value="LOTE">Un lote</option>
          <option value="RODEO">Un rodeo</option>
        </SelectField>
        {destino !== 'ESTABLECIMIENTO' && (
          <SelectField
            label={destino === 'LOTE' ? 'Lote' : 'Rodeo'}
            value={grupoId}
            onChange={(e) => setGrupoId(e.target.value)}
            error={errores.destino ?? errores.loteId ?? errores.rodeoId}
          >
            <option value="">Elegí…</option>
            {grupos?.map((g) => (
              <option key={g.id} value={g.id}>
                {g.nombre}
              </option>
            ))}
          </SelectField>
        )}
      </div>

      <SelectField
        label="Producto (opcional)"
        value={productoId}
        onChange={(e) => elegirProducto(e.target.value)}
        hint="Si lo elegís, al registrar el tratamiento viene cargado."
      >
        <option value="">Sin producto</option>
        {productos?.map((p) => (
          <option key={p.id} value={p.id}>
            {p.nombre}
          </option>
        ))}
      </SelectField>
      <TextField
        label="Qué se hace"
        value={descripcion}
        onChange={(e) => setDescripcion(e.target.value)}
        placeholder="Ej: Vacuna aftosa, segunda dosis"
        maxLength={200}
        error={errores.descripcion}
        required
      />
      <TextField
        label="Dosis (opcional)"
        value={dosis}
        onChange={(e) => setDosis(e.target.value)}
        maxLength={100}
        error={errores.dosis}
      />
      <TextAreaField
        label="Notas (opcional)"
        value={notas}
        onChange={(e) => setNotas(e.target.value)}
        maxLength={500}
        rows={2}
        error={errores.notas}
      />

      {!plan && (
        <fieldset className="rounded-lg border border-stone-200 p-3 dark:border-stone-800">
          <label className="flex items-center gap-2 text-sm font-medium">
            <input
              type="checkbox"
              checked={repetir}
              onChange={(e) => setRepetir(e.target.checked)}
              className="accent-brand-600 size-4"
            />
            Repetir (ej: segunda dosis, refuerzos)
          </label>
          {repetir && (
            <div className="mt-3 grid grid-cols-2 gap-3">
              <TextField
                label="Cantidad de veces"
                type="number"
                min={2}
                max={12}
                value={veces}
                onChange={(e) => setVeces(e.target.value)}
                error={errores.repeticiones}
              />
              <TextField
                label="Cada cuántos días"
                type="number"
                min={1}
                max={365}
                value={cadaDias}
                onChange={(e) => setCadaDias(e.target.value)}
                error={errores.cadaDias}
              />
            </div>
          )}
        </fieldset>
      )}

      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="secondary" onClick={onClose}>
          Cancelar
        </Button>
        <Button type="submit" loading={guardar.isPending}>
          {plan ? 'Guardar cambios' : 'Planificar'}
        </Button>
      </div>
    </form>
  )
}
