import { type FormEvent, useState } from 'react'
import { Button } from '@/components/Button'
import { Dialog } from '@/components/Dialog'
import { FormError } from '@/components/FormError'
import { TextField } from '@/components/TextField'
import { getErrorMessage, getFieldErrors } from '@/lib/api'
import { useActualizarEstablecimiento, useCrearEstablecimiento } from './queries'
import type { Establecimiento, EstablecimientoInput } from './types'

type Campos = Record<keyof EstablecimientoInput, string>

const aTexto = (valor: string | number | null) => (valor === null ? '' : String(valor))
const aNumero = (texto: string) => {
  const limpio = texto.trim().replace(',', '.')
  return limpio === '' ? null : Number(limpio)
}
const aTextoONull = (texto: string) => (texto.trim() === '' ? null : texto.trim())

function camposIniciales(e?: Establecimiento | null): Campos {
  return {
    nombre: e?.nombre ?? '',
    localidad: aTexto(e?.localidad ?? null),
    provincia: aTexto(e?.provincia ?? null),
    superficieHa: aTexto(e?.superficieHa ?? null),
    renspa: aTexto(e?.renspa ?? null),
    latitud: aTexto(e?.latitud ?? null),
    longitud: aTexto(e?.longitud ?? null),
  }
}

interface Props {
  open: boolean
  /** Si viene, se edita; si no, se crea uno nuevo. */
  establecimiento?: Establecimiento | null
  onClose: () => void
  onGuardado?: (e: Establecimiento) => void
}

export function EstablecimientoFormDialog({ open, establecimiento, onClose, onGuardado }: Props) {
  const editando = !!establecimiento
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={editando ? 'Editar establecimiento' : 'Nuevo establecimiento'}
      description={
        editando ? undefined : 'Solo el nombre es obligatorio; el resto lo podés completar después.'
      }
    >
      {/* key: el formulario arranca de cero cada vez que cambia el establecimiento */}
      <Formulario
        key={establecimiento?.id ?? 'nuevo'}
        establecimiento={establecimiento}
        onCancel={onClose}
        onGuardado={(e) => {
          onGuardado?.(e)
          onClose()
        }}
      />
    </Dialog>
  )
}

function Formulario({
  establecimiento,
  onCancel,
  onGuardado,
}: {
  establecimiento?: Establecimiento | null
  onCancel: () => void
  onGuardado: (e: Establecimiento) => void
}) {
  const [campos, setCampos] = useState(() => camposIniciales(establecimiento))
  const [error, setError] = useState<string | null>(null)
  const [errores, setErrores] = useState<Record<string, string>>({})
  const crear = useCrearEstablecimiento()
  const actualizar = useActualizarEstablecimiento()
  const guardando = crear.isPending || actualizar.isPending

  const campo = (nombre: keyof Campos) => ({
    value: campos[nombre],
    onChange: (e: React.ChangeEvent<HTMLInputElement>) => {
      setCampos((c) => ({ ...c, [nombre]: e.target.value }))
      // Al corregir un campo, su error desaparece
      if (errores[nombre]) {
        setErrores((prev) => {
          const resto = { ...prev }
          delete resto[nombre]
          return resto
        })
      }
    },
    error: errores[nombre],
  })

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setErrores({})

    const data: EstablecimientoInput = {
      nombre: campos.nombre.trim(),
      localidad: aTextoONull(campos.localidad),
      provincia: aTextoONull(campos.provincia),
      superficieHa: aNumero(campos.superficieHa),
      renspa: aTextoONull(campos.renspa),
      latitud: aNumero(campos.latitud),
      longitud: aNumero(campos.longitud),
    }

    // Validación mínima en el navegador; la definitiva la hace el backend
    const locales: Record<string, string> = {}
    if (!data.nombre) locales.nombre = 'Ingresá el nombre del establecimiento'
    for (const k of ['superficieHa', 'latitud', 'longitud'] as const) {
      if (Number.isNaN(data[k])) locales[k] = 'Tiene que ser un número'
    }
    if (Object.keys(locales).length > 0) {
      setErrores(locales)
      return
    }

    try {
      const guardado = establecimiento
        ? await actualizar.mutateAsync({ id: establecimiento.id, data })
        : await crear.mutateAsync(data)
      onGuardado(guardado)
    } catch (err) {
      const porCampo = getFieldErrors(err)
      setErrores(porCampo)
      setError(
        Object.keys(porCampo).length > 0 ? 'Revisá los datos marcados.' : getErrorMessage(err),
      )
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-4">
      <FormError message={error} />
      <TextField
        label="Nombre"
        placeholder="Ej: La Esperanza"
        autoFocus
        required
        maxLength={150}
        {...campo('nombre')}
      />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Localidad" maxLength={100} {...campo('localidad')} />
        <TextField label="Provincia" maxLength={100} {...campo('provincia')} />
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          label="Superficie (ha)"
          inputMode="decimal"
          placeholder="Ej: 1250"
          {...campo('superficieHa')}
        />
        <TextField
          label="RENSPA"
          placeholder="Ej: 01.001.0.00001/00"
          maxLength={30}
          {...campo('renspa')}
        />
      </div>
      <details className="group rounded-lg border border-stone-200 dark:border-stone-800">
        <summary className="cursor-pointer px-3 py-2 text-sm text-stone-600 select-none dark:text-stone-400">
          Ubicación en el mapa (opcional)
        </summary>
        <div className="grid gap-4 border-t border-stone-200 p-3 sm:grid-cols-2 dark:border-stone-800">
          <TextField
            label="Latitud"
            inputMode="decimal"
            placeholder="-33.1234"
            hint="Se usa para el clima del campo."
            {...campo('latitud')}
          />
          <TextField
            label="Longitud"
            inputMode="decimal"
            placeholder="-64.3456"
            {...campo('longitud')}
          />
        </div>
      </details>
      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onCancel} disabled={guardando}>
          Cancelar
        </Button>
        <Button type="submit" loading={guardando}>
          {establecimiento ? 'Guardar cambios' : 'Crear establecimiento'}
        </Button>
      </div>
    </form>
  )
}
