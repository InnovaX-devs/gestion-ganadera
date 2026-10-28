import { type FormEvent, useState } from 'react'
import { Button } from '@/components/Button'
import { Dialog } from '@/components/Dialog'
import { FormError } from '@/components/FormError'
import { TextAreaField } from '@/components/TextAreaField'
import { TextField } from '@/components/TextField'
import { getErrorMessage, getFieldErrors } from '@/lib/api'
import { AGRUPACIONES } from './config'
import { useGuardarAgrupacion } from './queries'
import type { Agrupacion, TipoAgrupacion } from './types'

interface Props {
  tipo: TipoAgrupacion
  establecimientoId: string
  open: boolean
  /** Si viene, se edita; si no, se crea. */
  agrupacion: Agrupacion | null
  onClose: () => void
}

export function AgrupacionFormDialog({
  tipo,
  establecimientoId,
  open,
  agrupacion,
  onClose,
}: Props) {
  const config = AGRUPACIONES[tipo]
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={agrupacion ? `Editar ${config.singular}` : `Nuevo ${config.singular}`}
    >
      <Formulario
        key={agrupacion?.id ?? 'nuevo'}
        tipo={tipo}
        establecimientoId={establecimientoId}
        agrupacion={agrupacion}
        onDone={onClose}
      />
    </Dialog>
  )
}

function Formulario({
  tipo,
  establecimientoId,
  agrupacion,
  onDone,
}: Omit<Props, 'open' | 'onClose'> & { onDone: () => void }) {
  const config = AGRUPACIONES[tipo]
  const guardar = useGuardarAgrupacion(tipo, establecimientoId)
  const [nombre, setNombre] = useState(agrupacion?.nombre ?? '')
  const [descripcion, setDescripcion] = useState(agrupacion?.descripcion ?? '')
  const [error, setError] = useState<string | null>(null)
  const [errores, setErrores] = useState<Record<string, string>>({})

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (!nombre.trim()) {
      setErrores({ nombre: 'Ingresá el nombre' })
      return
    }
    setErrores({})
    try {
      await guardar.mutateAsync({
        id: agrupacion?.id,
        data: { nombre: nombre.trim(), descripcion: descripcion.trim() || null },
      })
      onDone()
    } catch (err) {
      setErrores(getFieldErrors(err))
      setError(getErrorMessage(err))
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-4">
      <FormError message={error} />
      <TextField
        label="Nombre"
        placeholder={config.ejemploNombre}
        autoFocus
        required
        maxLength={100}
        value={nombre}
        onChange={(e) => {
          setNombre(e.target.value)
          setErrores({})
        }}
        error={errores.nombre}
      />
      <TextAreaField
        label="Descripción (opcional)"
        placeholder={config.ejemploDescripcion}
        maxLength={500}
        value={descripcion}
        onChange={(e) => setDescripcion(e.target.value)}
        error={errores.descripcion}
      />
      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onDone} disabled={guardar.isPending}>
          Cancelar
        </Button>
        <Button type="submit" loading={guardar.isPending}>
          {agrupacion ? 'Guardar cambios' : `Crear ${config.singular}`}
        </Button>
      </div>
    </form>
  )
}
