import { type FormEvent, useState } from 'react'
import { Button } from '@/components/Button'
import { Dialog } from '@/components/Dialog'
import { FormError } from '@/components/FormError'
import { SelectField } from '@/components/SelectField'
import { TextField } from '@/components/TextField'
import { useAgrupaciones } from '@/features/agrupaciones/queries'
import { hoyISO } from '@/features/eventos/fechas'
import { useRegistrarEvento } from '@/features/eventos/queries'
import { leerKg } from '@/features/pesajes/formato'
import { getErrorMessage, getFieldErrors } from '@/lib/api'
import { MODALIDADES, formatearPesos } from './config'
import { ElegirOrigen } from './ElegirOrigen'
import { ORIGEN_VACIO, cantidadDeOrigen, errorDeOrigen, origenAPedido, type Origen } from './origen'
import type { ModalidadPrecio, VentaDatos } from './types'

interface Props {
  establecimientoId: string
  open: boolean
  /** Si viene, se venden esos animales (desde la ficha o la selección del listado). Si no, se elige. */
  animalIds?: string[]
  onClose: () => void
  onHecho?: () => void
}

/**
 * Vender animales: uno, varios (venta masiva) o todo un lote o rodeo. Los animales salen del
 * stock; si fue un error, se anula la venta y vuelven.
 */
export function VentaDialog({ establecimientoId, open, animalIds, onClose, onHecho }: Props) {
  const cantidad = animalIds?.length ?? 0
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Registrar venta"
      description={
        cantidad > 0
          ? `Para ${cantidad} ${cantidad === 1 ? 'animal seleccionado' : 'animales seleccionados'}. Salen del stock.`
          : 'Los animales vendidos salen del stock.'
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
  const { data: lotes } = useAgrupaciones('lotes', establecimientoId)
  const { data: rodeos } = useAgrupaciones('rodeos', establecimientoId)
  const registrar = useRegistrarEvento<VentaDatos>(establecimientoId)

  const [origen, setOrigen] = useState<Origen>(ORIGEN_VACIO)
  const [fecha, setFecha] = useState(hoyISO)
  const [comprador, setComprador] = useState('')
  const [modalidad, setModalidad] = useState<ModalidadPrecio>('POR_KG')
  const [precio, setPrecio] = useState('')
  const [peso, setPeso] = useState('')
  const [documento, setDocumento] = useState('')
  const [confirmarCarencia, setConfirmarCarencia] = useState(false)
  // Se pide recién cuando el servidor avisa que hay animales en carencia
  const [pideConfirmarCarencia, setPideConfirmarCarencia] = useState(false)
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)

  const cantidad = animalesFijos ? animalIds!.length : cantidadDeOrigen(origen, lotes, rodeos)
  // Un animal: su peso. Varios: el peso total del grupo, que se reparte en partes iguales
  const esUnAnimal = animalesFijos ? cantidad === 1 : origen.grupo === 'ANIMALES' && cantidad === 1
  const precioNum = leerKg(precio)
  const pesoNum = leerKg(peso)

  const importe =
    precioNum && precioNum > 0
      ? modalidad === 'POR_CABEZA'
        ? cantidad
          ? precioNum * cantidad
          : null
        : pesoNum && pesoNum > 0
          ? precioNum * pesoNum
          : null
      : null

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const nuevos: Record<string, string> = {}
    if (!animalesFijos) {
      const falta = errorDeOrigen(origen)
      if (falta) nuevos.origen = falta
    }
    if (!comprador.trim()) nuevos['datos.comprador'] = 'Indicá el comprador'
    if (precioNum === null || precioNum <= 0)
      nuevos['datos.precio'] = 'Indicá el precio (hasta 2 decimales)'
    if (peso.trim() && (pesoNum === null || pesoNum <= 0)) {
      nuevos[esUnAnimal ? 'datos.kg' : 'datos.pesoTotal'] =
        'Indicá un peso válido (hasta 2 decimales)'
    } else if (modalidad === 'POR_KG' && !pesoNum) {
      nuevos[esUnAnimal ? 'datos.kg' : 'datos.pesoTotal'] = 'Para vender por kg indicá el peso'
    }
    setErrores(nuevos)
    if (Object.keys(nuevos).length > 0) return

    try {
      await registrar.mutateAsync({
        // Generado acá: si la red falla y se reintenta, el backend no la duplica
        operacionId: crypto.randomUUID(),
        tipo: 'VENTA',
        fecha,
        ...(animalesFijos ? { animalIds } : origenAPedido(origen)),
        datos: {
          comprador: comprador.trim(),
          modalidad,
          precio: precioNum!,
          kg: pesoNum && esUnAnimal ? pesoNum : null,
          pesoTotal: pesoNum && !esUnAnimal ? pesoNum : null,
          documento: documento.trim() || null,
          confirmarCarencia: confirmarCarencia || null,
        },
      })
      onHecho()
    } catch (err) {
      const mensaje = getErrorMessage(err)
      setErrores(getFieldErrors(err))
      setError(mensaje)
      if (/carencia/i.test(mensaje)) setPideConfirmarCarencia(true)
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
        <TextField
          label="Comprador"
          required
          maxLength={120}
          value={comprador}
          onChange={(e) => setComprador(e.target.value)}
          error={errores['datos.comprador']}
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
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <SelectField
          label="El precio es"
          value={modalidad}
          onChange={(e) => setModalidad(e.target.value as ModalidadPrecio)}
        >
          {Object.entries(MODALIDADES).map(([valor, etiqueta]) => (
            <option key={valor} value={valor}>
              {etiqueta}
            </option>
          ))}
        </SelectField>
        <TextField
          label={modalidad === 'POR_KG' ? 'Precio por kg ($)' : 'Precio por cabeza ($)'}
          inputMode="decimal"
          required
          value={precio}
          onChange={(e) => setPrecio(e.target.value)}
          error={errores['datos.precio']}
        />
      </div>

      <TextField
        label={esUnAnimal ? 'Peso (kg)' : 'Peso total (kg)'}
        inputMode="decimal"
        required={modalidad === 'POR_KG'}
        value={peso}
        onChange={(e) => setPeso(e.target.value)}
        error={errores['datos.kg'] ?? errores['datos.pesoTotal']}
        hint={
          esUnAnimal
            ? modalidad === 'POR_CABEZA'
              ? 'Opcional'
              : undefined
            : `Peso de todo el grupo${modalidad === 'POR_CABEZA' ? ' (opcional)' : ''}: cada animal queda con el promedio.`
        }
      />

      <TextField
        label="DT-e / guía"
        maxLength={60}
        value={documento}
        onChange={(e) => setDocumento(e.target.value)}
        hint="Número del documento de tránsito electrónico o de la guía. Opcional."
        error={errores['datos.documento']}
      />

      {importe !== null && (
        <p className="rounded-lg bg-stone-100 px-3 py-2 text-sm dark:bg-stone-800">
          Importe estimado: <span className="font-semibold">{formatearPesos(importe)}</span>
        </p>
      )}

      {pideConfirmarCarencia && (
        <label className="flex items-start gap-2 rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm dark:border-amber-900 dark:bg-amber-950/40">
          <input
            type="checkbox"
            className="mt-0.5"
            checked={confirmarCarencia}
            onChange={(e) => setConfirmarCarencia(e.target.checked)}
          />
          <span>Vender igual los animales en carencia sanitaria. Queda anotado en la venta.</span>
        </label>
      )}

      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onClose} disabled={registrar.isPending}>
          Cancelar
        </Button>
        <Button type="submit" loading={registrar.isPending}>
          Registrar venta
        </Button>
      </div>
    </form>
  )
}
