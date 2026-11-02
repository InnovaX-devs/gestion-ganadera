import { type FormEvent, useState } from 'react'
import { CalendarHeart } from 'lucide-react'
import { Button } from '@/components/Button'
import { Dialog } from '@/components/Dialog'
import { FormError } from '@/components/FormError'
import { SegmentedField } from '@/components/SegmentedField'
import { SelectField } from '@/components/SelectField'
import { TextAreaField } from '@/components/TextAreaField'
import { TextField } from '@/components/TextField'
import { formatearFecha, hoyISO } from '@/features/eventos/fechas'
import { useRegistrarEvento } from '@/features/eventos/queries'
import { getErrorMessage, getFieldErrors } from '@/lib/api'
import { RESULTADOS_TACTO, textoMeses } from './config'
import { DESTINO_VACIO, type Destino, destinoAPedido, errorDeDestino } from './destino'
import { DestinoReproductivo } from './DestinoReproductivo'
import { fechaEstimadaParto } from './gestacion'
import type { ResultadoTacto, TactoDatos } from './types'

const MESES = [1, 2, 3, 4, 5, 6, 7, 8, 9]

interface Props {
  establecimientoId: string
  open: boolean
  /** Si viene (desde la ficha), el tacto es de esa hembra. */
  hembra?: { id: string; caravana: string }
  onClose: () => void
}

/** Diagnóstico de preñez. Con la preñez, calcula la fecha estimada de parto. */
export function TactoDialog({ establecimientoId, open, hembra, onClose }: Props) {
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Nuevo tacto"
      description={
        hembra
          ? `Tacto de ${hembra.caravana}.`
          : 'Elegí varias si tienen el mismo resultado, o cargalas de a una y seguí con la próxima.'
      }
    >
      {open && (
        <Formulario establecimientoId={establecimientoId} hembra={hembra} onClose={onClose} />
      )}
    </Dialog>
  )
}

function Formulario({ establecimientoId, hembra, onClose }: Omit<Props, 'open'>) {
  const registrar = useRegistrarEvento<TactoDatos>(establecimientoId)
  const [destino, setDestino] = useState<Destino>(DESTINO_VACIO)
  const [fecha, setFecha] = useState(hoyISO)
  const [resultado, setResultado] = useState<ResultadoTacto>('PRENADA')
  const [meses, setMeses] = useState('')
  const [veterinario, setVeterinario] = useState('')
  const [observaciones, setObservaciones] = useState('')
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [guardadas, setGuardadas] = useState(0)
  const prenada = resultado === 'PRENADA'
  const parto = prenada && meses ? fechaEstimadaParto(fecha, Number(meses)) : null

  async function guardar(seguir: boolean) {
    setError(null)
    const nuevos: Record<string, string> = {}
    if (!hembra) {
      const falta = errorDeDestino(destino)
      if (falta) nuevos.destino = falta
    }
    if (prenada && !meses) nuevos['datos.mesesGestacion'] = 'Indicá los meses de gestación'
    setErrores(nuevos)
    if (Object.keys(nuevos).length > 0) return

    try {
      const operacion = await registrar.mutateAsync({
        operacionId: crypto.randomUUID(),
        tipo: 'TACTO',
        fecha,
        ...(hembra ? { animalIds: [hembra.id] } : destinoAPedido(destino)),
        datos: {
          resultado,
          mesesGestacion: prenada ? Number(meses) : null,
          veterinario: veterinario.trim() || null,
          observaciones: observaciones.trim() || null,
        },
      })
      if (!seguir) return onClose()
      // Para la próxima vaca quedan la fecha y el veterinario
      setGuardadas((n) => n + Math.max(operacion.cantidad, destino.animales.length))
      setDestino(DESTINO_VACIO)
      setMeses('')
      setObservaciones('')
    } catch (err) {
      setErrores(getFieldErrors(err))
      setError(getErrorMessage(err))
    }
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    void guardar(false)
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-4">
      <FormError message={error} />
      {guardadas > 0 && (
        <p
          role="status"
          className="rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300"
        >
          {guardadas === 1 ? 'Se guardó 1 tacto.' : `Se guardaron ${guardadas} tactos.`} Seguí con
          la próxima.
        </p>
      )}

      {!hembra && (
        <DestinoReproductivo
          establecimientoId={establecimientoId}
          valor={destino}
          onChange={(d) => {
            setDestino(d)
            setErrores({})
          }}
          error={errores.destino}
        />
      )}

      <SegmentedField
        label="Resultado"
        value={resultado}
        onChange={(r) => {
          setResultado(r)
          setErrores({})
        }}
        opciones={[
          { valor: 'PRENADA', etiqueta: RESULTADOS_TACTO.PRENADA },
          { valor: 'VACIA', etiqueta: RESULTADOS_TACTO.VACIA },
        ]}
      />

      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          label="Fecha"
          type="date"
          required
          max={hoyISO()}
          value={fecha}
          onChange={(e) => setFecha(e.target.value)}
          error={errores.fecha}
        />
        {prenada && (
          <SelectField
            label="Meses de gestación"
            value={meses}
            onChange={(e) => setMeses(e.target.value)}
            error={errores['datos.mesesGestacion']}
          >
            <option value="">Elegí</option>
            {MESES.map((m) => (
              <option key={m} value={m}>
                {textoMeses(m)}
              </option>
            ))}
          </SelectField>
        )}
        <TextField
          label="Veterinario (opcional)"
          maxLength={100}
          value={veterinario}
          onChange={(e) => setVeterinario(e.target.value)}
          error={errores['datos.veterinario']}
          className={prenada ? 'sm:col-span-2' : undefined}
        />
      </div>

      {parto && (
        <p className="flex items-center gap-2 rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200">
          <CalendarHeart className="size-4 shrink-0" />
          Parto estimado: {formatearFecha(parto)}
        </p>
      )}

      <TextAreaField
        label="Observaciones (opcional)"
        rows={2}
        maxLength={500}
        value={observaciones}
        onChange={(e) => setObservaciones(e.target.value)}
        error={errores['datos.observaciones']}
      />

      <div className="flex flex-wrap justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onClose} disabled={registrar.isPending}>
          {guardadas > 0 ? 'Cerrar' : 'Cancelar'}
        </Button>
        {!hembra && (
          <Button
            variant="secondary"
            onClick={() => void guardar(true)}
            disabled={registrar.isPending}
          >
            Guardar y cargar otra
          </Button>
        )}
        <Button type="submit" loading={registrar.isPending}>
          Guardar tacto
        </Button>
      </div>
    </form>
  )
}
