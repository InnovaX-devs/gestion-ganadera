import { useState } from 'react'
import { Button } from '@/components/Button'
import { Dialog } from '@/components/Dialog'
import { FormError } from '@/components/FormError'
import { SelectField } from '@/components/SelectField'
import { useAgrupaciones } from '@/features/agrupaciones/queries'
import { getErrorMessage } from '@/lib/api'
import { useAsignarAnimales } from './queries'

export type CampoAsignacion = 'LOTE' | 'RODEO'

interface Props {
  establecimientoId: string
  campo: CampoAsignacion | null
  animalIds: string[]
  onClose: () => void
  onHecho: () => void
}

/** Elegir un lote o rodeo y asignárselo a todos los animales seleccionados. */
export function AsignarDialog({ establecimientoId, campo, animalIds, onClose, onHecho }: Props) {
  const nombre = campo === 'RODEO' ? 'rodeo' : 'lote'
  const cantidad = animalIds.length
  return (
    <Dialog
      open={campo !== null}
      onClose={onClose}
      size="sm"
      title={`Asignar ${nombre}`}
      description={`A ${cantidad} ${cantidad === 1 ? 'animal seleccionado' : 'animales seleccionados'}.`}
    >
      {campo && (
        <Formulario
          key={campo}
          establecimientoId={establecimientoId}
          campo={campo}
          animalIds={animalIds}
          onClose={onClose}
          onHecho={onHecho}
        />
      )}
    </Dialog>
  )
}

function Formulario({
  establecimientoId,
  campo,
  animalIds,
  onClose,
  onHecho,
}: Omit<Props, 'campo'> & { campo: CampoAsignacion }) {
  const tipo = campo === 'RODEO' ? 'rodeos' : 'lotes'
  const nombre = campo === 'RODEO' ? 'rodeo' : 'lote'
  const { data: opciones } = useAgrupaciones(tipo, establecimientoId)
  const asignar = useAsignarAnimales(establecimientoId)
  const [destino, setDestino] = useState('')

  return (
    <div className="space-y-4">
      <FormError message={asignar.error ? getErrorMessage(asignar.error) : null} />
      <SelectField
        label={`Nuevo ${nombre}`}
        value={destino}
        onChange={(e) => setDestino(e.target.value)}
      >
        <option value="" disabled>
          Elegí un {nombre}…
        </option>
        {opciones?.map((o) => (
          <option key={o.id} value={o.id}>
            {o.nombre}
          </option>
        ))}
        <option value="QUITAR">Quitar {nombre} (sin asignar)</option>
      </SelectField>
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose} disabled={asignar.isPending}>
          Cancelar
        </Button>
        <Button
          disabled={!destino}
          loading={asignar.isPending}
          onClick={() =>
            asignar.mutate(
              { animalIds, campo, destinoId: destino === 'QUITAR' ? null : destino },
              { onSuccess: onHecho },
            )
          }
        >
          Asignar
        </Button>
      </div>
    </div>
  )
}
