import { type FormEvent, useState } from 'react'
import { Button } from '@/components/Button'
import { Dialog } from '@/components/Dialog'
import { FormError } from '@/components/FormError'
import { SelectField } from '@/components/SelectField'
import { TextField } from '@/components/TextField'
import { useAgrupaciones } from '@/features/agrupaciones/queries'
import type { Referencia } from '@/features/animales/types'
import { hoyISO } from '@/features/eventos/fechas'
import { useRegistrarEvento } from '@/features/eventos/queries'
import type { PesajeDatos } from '@/features/eventos/types'
import { ElegirAnimales } from '@/features/sanidad/ElegirAnimales'
import { getErrorMessage, getFieldErrors } from '@/lib/api'
import { leerKg } from './formato'

interface Props {
  establecimientoId: string
  open: boolean
  /** Si viene, el pesaje es de esos animales (selección del listado) y no se elige a quién. */
  animalIds?: string[]
  onClose: () => void
  onHecho?: () => void
}

type Destino = 'LOTE' | 'RODEO' | 'ANIMALES'

const promedio = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 2 })

/**
 * Pesaje grupal: cuando no se pesa animal por animal, se pesa el grupo entero. Con el peso total y
 * la cantidad pesada se calcula el promedio, que queda como peso de cada animal del lote, rodeo o
 * selección (marcado como grupal en su historial).
 */
export function PesajeGrupalDialog({
  establecimientoId,
  open,
  animalIds,
  onClose,
  onHecho,
}: Props) {
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Pesaje grupal"
      description="Pesaste el grupo entero: con el peso total y la cantidad se calcula el promedio de cada animal."
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
  const registrar = useRegistrarEvento<Partial<PesajeDatos>>(establecimientoId)

  const [destino, setDestino] = useState<Destino>('LOTE')
  const [loteId, setLoteId] = useState('')
  const [rodeoId, setRodeoId] = useState('')
  const [elegidos, setElegidos] = useState<Referencia[]>([])
  const [fecha, setFecha] = useState(hoyISO)
  const [total, setTotal] = useState('')
  // null: todavía no tocó la cantidad, se propone la de animales del grupo
  const [cantidadEscrita, setCantidadEscrita] = useState<string | null>(null)
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)

  const tamanioGrupo = animalesFijos
    ? animalIds!.length
    : destino === 'ANIMALES'
      ? elegidos.length
      : ((destino === 'LOTE' ? lotes : rodeos)?.find(
          (g) => g.id === (destino === 'LOTE' ? loteId : rodeoId),
        )?.cantidadAnimales ?? 0)
  const cantidad = cantidadEscrita ?? (tamanioGrupo > 0 ? String(tamanioGrupo) : '')

  const totalNumero = leerKg(total)
  const cantidadNumero = /^\d+$/.test(cantidad.trim()) ? Number(cantidad) : null
  const promedioKg =
    totalNumero && cantidadNumero && cantidadNumero > 0 ? totalNumero / cantidadNumero : null

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const nuevos: Record<string, string> = {}
    if (!totalNumero || totalNumero <= 0) nuevos['datos.pesoTotal'] = 'Ingresá el peso total'
    if (!cantidadNumero || cantidadNumero < 1)
      nuevos['datos.cantidad'] = 'Ingresá la cantidad pesada'
    if (!animalesFijos) {
      if (destino === 'LOTE' && !loteId) nuevos.destino = 'Elegí el lote'
      if (destino === 'RODEO' && !rodeoId) nuevos.destino = 'Elegí el rodeo'
      if (destino === 'ANIMALES' && elegidos.length === 0)
        nuevos.destino = 'Elegí al menos un animal'
    }
    setErrores(nuevos)
    if (Object.keys(nuevos).length > 0) return

    try {
      await registrar.mutateAsync({
        operacionId: crypto.randomUUID(),
        tipo: 'PESAJE',
        fecha,
        animalIds: animalesFijos
          ? animalIds
          : destino === 'ANIMALES'
            ? elegidos.map((a) => a.id)
            : [],
        loteId: !animalesFijos && destino === 'LOTE' ? loteId : null,
        rodeoId: !animalesFijos && destino === 'RODEO' ? rodeoId : null,
        datos: { pesoTotal: totalNumero!, cantidad: cantidadNumero! },
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

      {animalesFijos ? (
        <p className="text-sm text-stone-600 dark:text-stone-400">
          Para {animalIds!.length}{' '}
          {animalIds!.length === 1 ? 'animal seleccionado' : 'animales seleccionados'}.
        </p>
      ) : (
        <div className="space-y-3">
          <SelectField
            label="Se pesó"
            value={destino}
            onChange={(e) => {
              setDestino(e.target.value as Destino)
              setCantidadEscrita(null)
              setErrores({})
            }}
          >
            <option value="LOTE">Un lote</option>
            <option value="RODEO">Un rodeo</option>
            <option value="ANIMALES">Animales puntuales</option>
          </SelectField>
          {destino === 'LOTE' && (
            <SelectField
              label="Lote"
              value={loteId}
              onChange={(e) => {
                setLoteId(e.target.value)
                setCantidadEscrita(null)
              }}
              error={errores.destino}
            >
              <option value="">Elegí un lote</option>
              {lotes?.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.nombre} ({l.cantidadAnimales})
                </option>
              ))}
            </SelectField>
          )}
          {destino === 'RODEO' && (
            <SelectField
              label="Rodeo"
              value={rodeoId}
              onChange={(e) => {
                setRodeoId(e.target.value)
                setCantidadEscrita(null)
              }}
              error={errores.destino}
            >
              <option value="">Elegí un rodeo</option>
              {rodeos?.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.nombre} ({r.cantidadAnimales})
                </option>
              ))}
            </SelectField>
          )}
          {destino === 'ANIMALES' && (
            <ElegirAnimales
              establecimientoId={establecimientoId}
              elegidos={elegidos}
              onChange={(a) => {
                setElegidos(a)
                setCantidadEscrita(null)
              }}
              error={errores.destino}
            />
          )}
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-3">
        <TextField
          label="Fecha"
          type="date"
          required
          max={hoyISO()}
          value={fecha}
          onChange={(e) => setFecha(e.target.value)}
          error={errores.fecha}
        />
        <TextField
          label="Peso total (kg)"
          inputMode="decimal"
          required
          placeholder="Ej: 8240"
          value={total}
          onChange={(e) => setTotal(e.target.value)}
          error={errores['datos.pesoTotal']}
        />
        <TextField
          label="Cantidad pesada"
          inputMode="numeric"
          required
          value={cantidad}
          onChange={(e) => setCantidadEscrita(e.target.value)}
          error={errores['datos.cantidad']}
        />
      </div>

      <p
        aria-live="polite"
        className="bg-brand-50 text-brand-700 dark:bg-brand-900/30 dark:text-brand-200 rounded-lg px-3 py-2 text-sm"
      >
        {promedioKg !== null ? (
          <>
            Promedio: <strong className="tabular-nums">{promedio.format(promedioKg)} kg</strong> por
            animal
          </>
        ) : (
          'Completá el peso total y la cantidad para ver el promedio.'
        )}
      </p>

      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onClose} disabled={registrar.isPending}>
          Cancelar
        </Button>
        <Button type="submit" loading={registrar.isPending}>
          Guardar pesaje grupal
        </Button>
      </div>
    </form>
  )
}
