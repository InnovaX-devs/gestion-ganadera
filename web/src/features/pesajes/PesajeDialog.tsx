import { type FormEvent, useState } from 'react'
import { Button } from '@/components/Button'
import { Dialog } from '@/components/Dialog'
import { FormError } from '@/components/FormError'
import { TextField } from '@/components/TextField'
import { hoyISO } from '@/features/eventos/fechas'
import { useRegistrarEvento } from '@/features/eventos/queries'
import type { PesajeDatos } from '@/features/eventos/types'
import { getErrorMessage, getFieldErrors } from '@/lib/api'
import { leerKg } from './formato'

interface Props {
  establecimientoId: string
  open: boolean
  animalId: string
  caravana: string
  onClose: () => void
}

/** Pesaje de un solo animal (desde su ficha). Para muchos a la vez está la grilla. */
export function PesajeDialog({ establecimientoId, open, animalId, caravana, onClose }: Props) {
  return (
    <Dialog
      open={open}
      onClose={onClose}
      size="sm"
      title="Nuevo pesaje"
      description={`Para ${caravana}.`}
    >
      {open && (
        <Formulario establecimientoId={establecimientoId} animalId={animalId} onClose={onClose} />
      )}
    </Dialog>
  )
}

function Formulario({
  establecimientoId,
  animalId,
  onClose,
}: Pick<Props, 'establecimientoId' | 'animalId' | 'onClose'>) {
  const registrar = useRegistrarEvento<Partial<PesajeDatos>>(establecimientoId)
  const [fecha, setFecha] = useState(hoyISO)
  const [kg, setKg] = useState('')
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const valor = leerKg(kg)
    if (valor === null || valor <= 0 || valor > 1500) {
      setErrores({ 'datos.kg': 'Ingresá un peso entre 0 y 1500 kg' })
      return
    }
    setErrores({})
    try {
      await registrar.mutateAsync({
        operacionId: crypto.randomUUID(),
        tipo: 'PESAJE',
        fecha,
        animalIds: [animalId],
        datos: { kg: valor },
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
      <TextField
        label="Peso (kg)"
        inputMode="decimal"
        autoFocus
        required
        placeholder="Ej: 412,5"
        value={kg}
        onChange={(e) => setKg(e.target.value)}
        error={errores['datos.kg']}
      />
      <TextField
        label="Fecha"
        type="date"
        required
        max={hoyISO()}
        value={fecha}
        onChange={(e) => setFecha(e.target.value)}
        error={errores.fecha}
      />
      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onClose} disabled={registrar.isPending}>
          Cancelar
        </Button>
        <Button type="submit" loading={registrar.isPending}>
          Guardar pesaje
        </Button>
      </div>
    </form>
  )
}
