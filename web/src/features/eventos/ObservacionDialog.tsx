import { type FormEvent, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/Button'
import { Dialog } from '@/components/Dialog'
import { FormError } from '@/components/FormError'
import { SelectField } from '@/components/SelectField'
import { TextAreaField } from '@/components/TextAreaField'
import { TextField } from '@/components/TextField'
import { SelectorArchivos } from '@/features/adjuntos/SelectorArchivos'
import { subirArchivos } from '@/features/adjuntos/subida'
import { useAgrupaciones } from '@/features/agrupaciones/queries'
import { getErrorMessage, getFieldErrors } from '@/lib/api'
import { hoyISO } from './fechas'
import { useRegistrarEvento } from './queries'
import type { ObservacionDatos } from './types'

interface Props {
  establecimientoId: string
  open: boolean
  /** Si viene, la observación es de estos animales (desde la selección del listado). */
  animalIds?: string[]
  onClose: () => void
  onHecho?: () => void
}

/** Nueva observación: de animales seleccionados, de un lote o del establecimiento. */
export function ObservacionDialog({ establecimientoId, open, animalIds, onClose, onHecho }: Props) {
  const cantidad = animalIds?.length ?? 0
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Nueva observación"
      description={
        cantidad > 0
          ? `Para ${cantidad} ${cantidad === 1 ? 'animal seleccionado' : 'animales seleccionados'}.`
          : 'Queda en el historial del establecimiento o del lote que elijas.'
      }
    >
      {open && (
        <Formulario
          establecimientoId={establecimientoId}
          animalIds={animalIds}
          onClose={onClose}
          onHecho={() => {
            onHecho?.()
            onClose()
          }}
        />
      )}
    </Dialog>
  )
}

function Formulario({
  establecimientoId,
  animalIds,
  onClose,
  onHecho,
}: Omit<Props, 'open'> & { onHecho: () => void }) {
  const conAnimales = (animalIds?.length ?? 0) > 0
  const { data: lotes } = useAgrupaciones('lotes', establecimientoId)
  const registrar = useRegistrarEvento<ObservacionDatos>(establecimientoId)
  const queryClient = useQueryClient()
  const [fecha, setFecha] = useState(hoyISO)
  const [texto, setTexto] = useState('')
  const [loteId, setLoteId] = useState('')
  const [archivos, setArchivos] = useState<File[]>([])
  const [subiendo, setSubiendo] = useState(false)
  // La observación ya se guardó pero algún archivo no: reintentar duplicaría la observación
  const [guardadaSinArchivos, setGuardadaSinArchivos] = useState(false)
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const ocupado = registrar.isPending || subiendo

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (!texto.trim()) {
      setErrores({ 'datos.texto': 'Escribí la observación' })
      return
    }
    setErrores({})
    try {
      const operacion = await registrar.mutateAsync({
        // Generado acá: si la red falla y se reintenta, el backend no la duplica
        operacionId: crypto.randomUUID(),
        tipo: 'OBSERVACION',
        fecha,
        animalIds: conAnimales ? animalIds : [],
        loteId: !conAnimales && loteId ? loteId : null,
        datos: { texto: texto.trim() },
      })
      if (archivos.length > 0) {
        // Con la observación ya registrada (o guardada para enviar, sin señal) se suben las fotos
        setSubiendo(true)
        const resultado = await subirArchivos(
          establecimientoId,
          { tipo: 'EVENTO', operacionId: operacion.operacionId },
          archivos,
          'Fotos de la observación',
        )
        setSubiendo(false)
        await queryClient.invalidateQueries({ queryKey: ['eventos'] })
        if (resultado.fallidos.length > 0) {
          setGuardadaSinArchivos(true)
          setError(
            'La observación se guardó, pero no se pudo adjuntar:\n' +
              resultado.fallidos.map((f) => `${f.nombre}: ${f.mensaje}`).join('\n') +
              '\nPodés volver a subirlos más tarde desde la ficha del animal.',
          )
          return
        }
      }
      onHecho()
    } catch (err) {
      setErrores(getFieldErrors(err))
      setError(getErrorMessage(err))
    }
  }

  if (guardadaSinArchivos) {
    return (
      <div className="space-y-4">
        <FormError message={error} />
        <div className="flex justify-end">
          <Button onClick={onHecho}>Cerrar</Button>
        </div>
      </div>
    )
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-4">
      <FormError message={error} />
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
        {!conAnimales && (
          <SelectField label="Sobre" value={loteId} onChange={(e) => setLoteId(e.target.value)}>
            <option value="">Todo el establecimiento</option>
            {lotes?.map((l) => (
              <option key={l.id} value={l.id}>
                Lote {l.nombre}
              </option>
            ))}
          </SelectField>
        )}
      </div>
      <TextAreaField
        label="Observación"
        placeholder="Ej: Se cortó el agua del molino del potrero 4"
        autoFocus
        rows={4}
        maxLength={2000}
        value={texto}
        onChange={(e) => setTexto(e.target.value)}
        error={errores['datos.texto']}
      />
      <SelectorArchivos archivos={archivos} onChange={setArchivos} disabled={ocupado} />
      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onClose} disabled={ocupado}>
          Cancelar
        </Button>
        <Button type="submit" loading={ocupado}>
          Guardar observación
        </Button>
      </div>
    </form>
  )
}
