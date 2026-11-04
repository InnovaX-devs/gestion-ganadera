import { type FormEvent, useState } from 'react'
import { Button } from '@/components/Button'
import { Dialog } from '@/components/Dialog'
import { FormError } from '@/components/FormError'
import { SelectField } from '@/components/SelectField'
import { TextField } from '@/components/TextField'
import { useAgrupaciones } from '@/features/agrupaciones/queries'
import type { Referencia } from '@/features/animales/types'
import { puede } from '@/features/establecimientos/permisos'
import { useEstablecimientos } from '@/features/establecimientos/queries'
import { hoyISO } from '@/features/eventos/fechas'
import { useRegistrarEvento } from '@/features/eventos/queries'
import { ElegirAnimales } from '@/features/sanidad/ElegirAnimales'
import { getErrorMessage, getFieldErrors } from '@/lib/api'
import type { MovimientoDatos } from './types'

interface Props {
  establecimientoId: string
  open: boolean
  /**
   * Si viene, se mueven esos animales (desde la ficha o la selección del listado) y no se elige
   * cuáles. Si no, se elige: animales sueltos, un lote o un rodeo.
   */
  animalIds?: string[]
  onClose: () => void
  onHecho?: () => void
}

type Grupo = 'ANIMALES' | 'LOTE' | 'RODEO'

/** En los selects de lote y rodeo: no tocar el actual, o dejar al animal sin asignar. */
const NO_CAMBIAR = ''
const QUITAR = 'QUITAR'

/**
 * Mover animales de lote, de rodeo o de establecimiento: uno, varios, o todo un lote o rodeo.
 * Queda en el historial de cada animal, con de dónde a dónde se movió.
 */
export function MovimientoDialog({ establecimientoId, open, animalIds, onClose, onHecho }: Props) {
  const cantidad = animalIds?.length ?? 0
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Mover animales"
      description={
        cantidad > 0
          ? `Para ${cantidad} ${cantidad === 1 ? 'animal seleccionado' : 'animales seleccionados'}.`
          : 'Cambialos de lote, de rodeo o de establecimiento. Queda en el historial de cada animal.'
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
  const { data: establecimientos } = useEstablecimientos()
  const { data: lotesDeOrigen } = useAgrupaciones('lotes', establecimientoId)
  const { data: rodeosDeOrigen } = useAgrupaciones('rodeos', establecimientoId)
  const registrar = useRegistrarEvento<MovimientoDatos>(establecimientoId)

  const [grupo, setGrupo] = useState<Grupo>('ANIMALES')
  const [elegidos, setElegidos] = useState<Referencia[]>([])
  const [loteOrigenId, setLoteOrigenId] = useState('')
  const [rodeoOrigenId, setRodeoOrigenId] = useState('')
  const [fecha, setFecha] = useState(hoyISO)
  const [destinoId, setDestinoId] = useState(establecimientoId)
  const [lote, setLote] = useState(NO_CAMBIAR)
  const [rodeo, setRodeo] = useState(NO_CAMBIAR)
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)

  // Solo se puede mover a campos donde también se pueden gestionar animales (el backend lo controla)
  const destinos = (establecimientos ?? []).filter(
    (e) => e.id === establecimientoId || puede(e.rol, 'GESTIONAR_ANIMALES'),
  )
  const otroCampo = destinoId !== establecimientoId
  const { data: lotesDeDestino } = useAgrupaciones('lotes', destinoId)
  const { data: rodeosDeDestino } = useAgrupaciones('rodeos', destinoId)
  const grupoElegido =
    grupo === 'LOTE'
      ? lotesDeOrigen?.find((l) => l.id === loteOrigenId)
      : rodeosDeOrigen?.find((r) => r.id === rodeoOrigenId)

  function elegirDestino(id: string) {
    setDestinoId(id)
    // Los lotes y rodeos son de cada establecimiento: lo elegido para el anterior no vale
    setLote(NO_CAMBIAR)
    setRodeo(NO_CAMBIAR)
    setErrores({})
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const nuevos: Record<string, string> = {}
    if (!animalesFijos) {
      if (grupo === 'ANIMALES' && elegidos.length === 0) nuevos.origen = 'Elegí al menos un animal'
      if (grupo === 'LOTE' && !loteOrigenId) nuevos.origen = 'Elegí el lote'
      if (grupo === 'RODEO' && !rodeoOrigenId) nuevos.origen = 'Elegí el rodeo'
    }
    if (!otroCampo && lote === NO_CAMBIAR && rodeo === NO_CAMBIAR) {
      nuevos.destino = 'Elegí un lote, un rodeo u otro establecimiento'
    }
    setErrores(nuevos)
    if (Object.keys(nuevos).length > 0) return

    try {
      await registrar.mutateAsync({
        // Generado acá: si la red falla y se reintenta, el backend no la duplica
        operacionId: crypto.randomUUID(),
        tipo: 'MOVIMIENTO',
        fecha,
        animalIds: animalesFijos
          ? animalIds
          : grupo === 'ANIMALES'
            ? elegidos.map((a) => a.id)
            : [],
        loteId: !animalesFijos && grupo === 'LOTE' ? loteOrigenId : null,
        rodeoId: !animalesFijos && grupo === 'RODEO' ? rodeoOrigenId : null,
        datos: {
          establecimientoDestinoId: otroCampo ? destinoId : null,
          loteDestinoId: lote !== NO_CAMBIAR && lote !== QUITAR ? lote : null,
          rodeoDestinoId: rodeo !== NO_CAMBIAR && rodeo !== QUITAR ? rodeo : null,
          // En otro campo lo que no se elige queda sin asignar solo: no hace falta pedirlo
          quitarLote: !otroCampo && lote === QUITAR,
          quitarRodeo: !otroCampo && rodeo === QUITAR,
        },
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
        <div className="space-y-3">
          <SelectField
            label="Se mueve"
            value={grupo}
            onChange={(e) => {
              setGrupo(e.target.value as Grupo)
              setErrores({})
            }}
          >
            <option value="ANIMALES">Animales puntuales</option>
            <option value="LOTE">Todo un lote</option>
            <option value="RODEO">Todo un rodeo</option>
          </SelectField>
          {grupo === 'ANIMALES' && (
            <ElegirAnimales
              establecimientoId={establecimientoId}
              elegidos={elegidos}
              onChange={setElegidos}
              error={errores.origen}
            />
          )}
          {grupo === 'LOTE' && (
            <SelectField
              label="Lote de origen"
              value={loteOrigenId}
              onChange={(e) => setLoteOrigenId(e.target.value)}
              error={errores.origen}
              hint={grupoElegido ? textoGrupo(grupoElegido.cantidadAnimales) : undefined}
            >
              <option value="">Elegí un lote</option>
              {lotesDeOrigen?.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.nombre}
                </option>
              ))}
            </SelectField>
          )}
          {grupo === 'RODEO' && (
            <SelectField
              label="Rodeo de origen"
              value={rodeoOrigenId}
              onChange={(e) => setRodeoOrigenId(e.target.value)}
              error={errores.origen}
              hint={grupoElegido ? textoGrupo(grupoElegido.cantidadAnimales) : undefined}
            >
              <option value="">Elegí un rodeo</option>
              {rodeosDeOrigen?.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.nombre}
                </option>
              ))}
            </SelectField>
          )}
        </div>
      )}

      <fieldset className="space-y-3 rounded-xl border border-stone-200 p-4 dark:border-stone-800">
        <legend className="px-1 text-sm font-medium">Destino</legend>
        {destinos.length > 1 && (
          <SelectField
            label="Establecimiento"
            value={destinoId}
            onChange={(e) => elegirDestino(e.target.value)}
            hint={
              otroCampo
                ? 'Los animales dejan su lote y rodeo actuales: quedan en los que elijas acá abajo, o sin asignar.'
                : undefined
            }
          >
            {destinos.map((d) => (
              <option key={d.id} value={d.id}>
                {d.nombre}
                {d.id === establecimientoId ? ' (este)' : ''}
              </option>
            ))}
          </SelectField>
        )}
        <div className="grid gap-3 sm:grid-cols-2">
          <SelectField label="Lote" value={lote} onChange={(e) => setLote(e.target.value)}>
            <option value={NO_CAMBIAR}>{otroCampo ? 'Sin lote' : 'No cambiar'}</option>
            {lotesDeDestino?.map((l) => (
              <option key={l.id} value={l.id}>
                {l.nombre}
              </option>
            ))}
            {!otroCampo && <option value={QUITAR}>Quitar lote (sin asignar)</option>}
          </SelectField>
          <SelectField label="Rodeo" value={rodeo} onChange={(e) => setRodeo(e.target.value)}>
            <option value={NO_CAMBIAR}>{otroCampo ? 'Sin rodeo' : 'No cambiar'}</option>
            {rodeosDeDestino?.map((r) => (
              <option key={r.id} value={r.id}>
                {r.nombre}
              </option>
            ))}
            {!otroCampo && <option value={QUITAR}>Quitar rodeo (sin asignar)</option>}
          </SelectField>
        </div>
        {errores.destino && (
          <p className="text-xs text-red-600 dark:text-red-400">{errores.destino}</p>
        )}
      </fieldset>

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
          Mover
        </Button>
      </div>
    </form>
  )
}

function textoGrupo(cantidad: number) {
  if (cantidad === 0) return 'No tiene animales'
  return `Se mueven los ${cantidad} ${cantidad === 1 ? 'animal' : 'animales'} del grupo`
}
