import { type FormEvent, useState } from 'react'
import { Button } from '@/components/Button'
import { Dialog } from '@/components/Dialog'
import { FormError } from '@/components/FormError'
import { SelectField } from '@/components/SelectField'
import { TextField } from '@/components/TextField'
import { hoyISO } from '@/features/eventos/fechas'
import { useRegistrarEvento } from '@/features/eventos/queries'
import { getErrorMessage, getFieldErrors } from '@/lib/api'
import { MOTIVOS_BAJA } from './config'
import { ElegirOrigen } from './ElegirOrigen'
import { ORIGEN_VACIO, errorDeOrigen, origenAPedido, type Origen } from './origen'
import type { BajaDatos, MotivoBaja } from './types'

interface Props {
  establecimientoId: string
  open: boolean
  /** Si viene, se da de baja a esos animales (desde la ficha o la selección del listado). Si no, se elige. */
  animalIds?: string[]
  onClose: () => void
  onHecho?: () => void
}

/**
 * Dar de baja animales por muerte (con su causa) o pérdida. Salen del stock; si se cargó por
 * error, se anula la baja y vuelven.
 */
export function BajaDialog({ establecimientoId, open, animalIds, onClose, onHecho }: Props) {
  const cantidad = animalIds?.length ?? 0
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Dar de baja"
      description={
        cantidad > 0
          ? `Para ${cantidad} ${cantidad === 1 ? 'animal seleccionado' : 'animales seleccionados'}. Salen del stock.`
          : 'Muerte o pérdida de animales. Salen del stock.'
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
  const animalesFijos = (animalIds?.length ?? 0) > 0
  const registrar = useRegistrarEvento<BajaDatos>(establecimientoId)

  const [origen, setOrigen] = useState<Origen>(ORIGEN_VACIO)
  const [fecha, setFecha] = useState(hoyISO)
  const [motivo, setMotivo] = useState<MotivoBaja>('MUERTE')
  const [causa, setCausa] = useState('')
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const nuevos: Record<string, string> = {}
    if (!animalesFijos) {
      const falta = errorDeOrigen(origen)
      if (falta) nuevos.origen = falta
    }
    if (motivo === 'MUERTE' && !causa.trim()) nuevos['datos.causa'] = 'Indicá la causa de la muerte'
    setErrores(nuevos)
    if (Object.keys(nuevos).length > 0) return

    try {
      await registrar.mutateAsync({
        operacionId: crypto.randomUUID(),
        tipo: 'BAJA',
        fecha,
        ...(animalesFijos ? { animalIds } : origenAPedido(origen)),
        datos: { motivo, causa: causa.trim() || null },
      })
      onHecho()
    } catch (err) {
      setErrores(getFieldErrors(err))
      setError(getErrorMessage(err))
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-4">
      <FormError message={error} />

      {!animalesFijos && (
        <ElegirOrigen
          establecimientoId={establecimientoId}
          valor={origen}
          onChange={(o) => {
            setOrigen(o)
            setErrores({})
          }}
          error={errores.origen}
        />
      )}

      <div className="grid gap-3 sm:grid-cols-2">
        <SelectField
          label="Motivo"
          value={motivo}
          onChange={(e) => setMotivo(e.target.value as MotivoBaja)}
        >
          {Object.entries(MOTIVOS_BAJA).map(([valor, etiqueta]) => (
            <option key={valor} value={valor}>
              {etiqueta}
            </option>
          ))}
        </SelectField>
        <TextField
          label="Fecha"
          type="date"
          required
          max={hoyISO()}
          value={fecha}
          onChange={(e) => setFecha(e.target.value)}
          error={errores.fecha}
        />
      </div>

      <TextField
        label={motivo === 'MUERTE' ? 'Causa' : 'Detalle (opcional)'}
        required={motivo === 'MUERTE'}
        maxLength={200}
        value={causa}
        onChange={(e) => setCausa(e.target.value)}
        error={errores['datos.causa']}
        hint={
          motivo === 'MUERTE'
            ? 'Ej: meteorismo, mordedura de víbora, tormenta'
            : 'Ej: se extravió en el monte, robo'
        }
      />

      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onClose} disabled={registrar.isPending}>
          Cancelar
        </Button>
        <Button type="submit" loading={registrar.isPending}>
          Dar de baja
        </Button>
      </div>
    </form>
  )
}
