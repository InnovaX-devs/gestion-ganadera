import { type FormEvent, useState } from 'react'
import { Button } from '@/components/Button'
import { Dialog } from '@/components/Dialog'
import { FormError } from '@/components/FormError'
import { SelectField } from '@/components/SelectField'
import { TextField } from '@/components/TextField'
import { getErrorMessage, getFieldErrors } from '@/lib/api'
import { LISTA_TIPOS_PRODUCTO, LISTA_VIAS, MAX_DIAS_CARENCIA, TIPOS_PRODUCTO, VIAS } from './config'
import { useGuardarProducto } from './queries'
import type { ProductoSanitario, TipoProducto, ViaAdministracion } from './types'

interface Props {
  establecimientoId: string
  open: boolean
  /** Si viene, se edita; si no, se crea. */
  producto: ProductoSanitario | null
  onClose: () => void
  /** Con el producto ya guardado (para elegirlo enseguida en el formulario de tratamiento). */
  onGuardado?: (producto: ProductoSanitario) => void
}

export function ProductoFormDialog({
  establecimientoId,
  open,
  producto,
  onClose,
  onGuardado,
}: Props) {
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={producto ? 'Editar producto' : 'Nuevo producto'}
      description="Queda en el catálogo para reutilizarlo en cada tratamiento. Dosis, vía y carencia se pueden cambiar al aplicarlo."
    >
      <Formulario
        key={producto?.id ?? 'nuevo'}
        establecimientoId={establecimientoId}
        producto={producto}
        onClose={onClose}
        onGuardado={onGuardado}
      />
    </Dialog>
  )
}

function Formulario({ establecimientoId, producto, onClose, onGuardado }: Omit<Props, 'open'>) {
  const guardar = useGuardarProducto(establecimientoId)
  const [nombre, setNombre] = useState(producto?.nombre ?? '')
  const [tipo, setTipo] = useState<TipoProducto>(producto?.tipo ?? 'VACUNA')
  const [dias, setDias] = useState(String(producto?.diasCarencia ?? 0))
  const [dosis, setDosis] = useState(producto?.dosisHabitual ?? '')
  const [via, setVia] = useState<ViaAdministracion | ''>(producto?.viaHabitual ?? '')
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const diasNumero = Number(dias)
    const nuevos: Record<string, string> = {}
    if (!nombre.trim()) nuevos.nombre = 'Ingresá el nombre'
    if (!Number.isInteger(diasNumero) || diasNumero < 0 || diasNumero > MAX_DIAS_CARENCIA) {
      nuevos.diasCarencia = `Un número de 0 a ${MAX_DIAS_CARENCIA}`
    }
    setErrores(nuevos)
    if (Object.keys(nuevos).length > 0) return

    try {
      const guardado = await guardar.mutateAsync({
        id: producto?.id,
        data: {
          nombre: nombre.trim(),
          tipo,
          diasCarencia: diasNumero,
          dosisHabitual: dosis.trim() || null,
          viaHabitual: via || null,
        },
      })
      onGuardado?.(guardado)
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
        label="Nombre"
        placeholder="Ej: Ivomec, Aftosa, Oxitetraciclina"
        autoFocus
        required
        maxLength={100}
        value={nombre}
        onChange={(e) => setNombre(e.target.value)}
        error={errores.nombre}
      />
      <div className="grid gap-4 sm:grid-cols-2">
        <SelectField
          label="Tipo"
          value={tipo}
          onChange={(e) => setTipo(e.target.value as TipoProducto)}
        >
          {LISTA_TIPOS_PRODUCTO.map((t) => (
            <option key={t} value={t}>
              {TIPOS_PRODUCTO[t].etiqueta}
            </option>
          ))}
        </SelectField>
        <TextField
          label="Días de carencia"
          type="number"
          inputMode="numeric"
          min={0}
          max={MAX_DIAS_CARENCIA}
          value={dias}
          onChange={(e) => setDias(e.target.value)}
          hint="0 si no tiene"
          error={errores.diasCarencia}
        />
        <TextField
          label="Dosis habitual (opcional)"
          placeholder="Ej: 1 ml cada 50 kg"
          maxLength={100}
          value={dosis}
          onChange={(e) => setDosis(e.target.value)}
          error={errores.dosisHabitual}
        />
        <SelectField
          label="Vía habitual (opcional)"
          value={via}
          onChange={(e) => setVia(e.target.value as ViaAdministracion | '')}
        >
          <option value="">Sin definir</option>
          {LISTA_VIAS.map((v) => (
            <option key={v} value={v}>
              {VIAS[v]}
            </option>
          ))}
        </SelectField>
      </div>
      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onClose} disabled={guardar.isPending}>
          Cancelar
        </Button>
        <Button type="submit" loading={guardar.isPending}>
          {producto ? 'Guardar cambios' : 'Crear producto'}
        </Button>
      </div>
    </form>
  )
}
