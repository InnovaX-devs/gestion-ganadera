import { type FormEvent, useState } from 'react'
import { Button } from '@/components/Button'
import { Dialog } from '@/components/Dialog'
import { FormError } from '@/components/FormError'
import { SegmentedField } from '@/components/SegmentedField'
import { TextAreaField } from '@/components/TextAreaField'
import { TextField } from '@/components/TextField'
import type { Referencia } from '@/features/animales/types'
import { formatearFecha, hoyISO } from '@/features/eventos/fechas'
import { useRegistrarEvento } from '@/features/eventos/queries'
import { ElegirAnimales } from '@/features/sanidad/ElegirAnimales'
import { getErrorMessage, getFieldErrors } from '@/lib/api'
import { MAX_TOROS, TIPOS_SERVICIO, esToro } from './config'
import { DESTINO_VACIO, type Destino, destinoAPedido, errorDeDestino } from './destino'
import { DestinoReproductivo } from './DestinoReproductivo'
import type { ServicioDatos, TipoServicio } from './types'

interface Props {
  establecimientoId: string
  open: boolean
  /** Si viene (desde la ficha), el servicio es de esa hembra. Si no, se elige: puntuales o un grupo. */
  hembra?: { id: string; caravana: string }
  onClose: () => void
}

/** Servicio natural o inseminación, de una vaca, de varias o de todo un rodeo. */
export function ServicioDialog({ establecimientoId, open, hembra, onClose }: Props) {
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Nuevo servicio"
      description={
        hembra
          ? `Servicio de ${hembra.caravana}.`
          : 'Natural o por inseminación. Queda en el historial de cada vaca y vaquillona.'
      }
    >
      {open && (
        <Formulario establecimientoId={establecimientoId} hembra={hembra} onClose={onClose} />
      )}
    </Dialog>
  )
}

function Formulario({ establecimientoId, hembra, onClose }: Omit<Props, 'open'>) {
  const registrar = useRegistrarEvento<ServicioDatos>(establecimientoId)
  const [destino, setDestino] = useState<Destino>(DESTINO_VACIO)
  const [tipo, setTipo] = useState<TipoServicio>('NATURAL')
  const [fecha, setFecha] = useState(hoyISO)
  const [fechaFin, setFechaFin] = useState('')
  const [toros, setToros] = useState<Referencia[]>([])
  const [pajuela, setPajuela] = useState('')
  const [observaciones, setObservaciones] = useState('')
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const natural = tipo === 'NATURAL'

  function cambiarTipo(nuevo: TipoServicio) {
    setTipo(nuevo)
    setErrores({})
    // En una inseminación va un solo toro (el del semen)
    if (nuevo === 'INSEMINACION') setToros((t) => t.slice(0, 1))
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const nuevos: Record<string, string> = {}
    if (!hembra) {
      const falta = errorDeDestino(destino)
      if (falta) nuevos.destino = falta
    }
    if (natural && fechaFin && fechaFin < fecha) {
      nuevos['datos.fechaFin'] = 'No puede ser anterior al inicio'
    }
    if (!natural && !pajuela.trim() && toros.length === 0) {
      nuevos['datos.pajuela'] = 'Indicá la pajuela o el toro del semen'
    }
    setErrores(nuevos)
    if (Object.keys(nuevos).length > 0) return

    try {
      await registrar.mutateAsync({
        operacionId: crypto.randomUUID(),
        tipo: 'SERVICIO',
        fecha,
        ...(hembra ? { animalIds: [hembra.id] } : destinoAPedido(destino)),
        datos: {
          tipo,
          fechaFin: natural && fechaFin ? fechaFin : null,
          toroIds: toros.map((t) => t.id),
          pajuela: natural ? null : pajuela.trim() || null,
          observaciones: observaciones.trim() || null,
        },
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

      {!hembra && (
        <DestinoReproductivo
          establecimientoId={establecimientoId}
          valor={destino}
          onChange={(d) => {
            setDestino(d)
            setErrores({})
          }}
          error={errores.destino}
          grupos
        />
      )}

      <SegmentedField
        label="Servicio"
        value={tipo}
        onChange={cambiarTipo}
        opciones={[
          { valor: 'NATURAL', etiqueta: TIPOS_SERVICIO.NATURAL },
          { valor: 'INSEMINACION', etiqueta: TIPOS_SERVICIO.INSEMINACION },
        ]}
      />

      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          label={natural ? 'Inicio' : 'Fecha'}
          type="date"
          required
          max={hoyISO()}
          value={fecha}
          onChange={(e) => setFecha(e.target.value)}
          error={errores.fecha}
          hint={natural ? 'Cuando entraron los toros' : undefined}
        />
        {natural && (
          <TextField
            label="Fin (opcional)"
            type="date"
            min={fecha}
            value={fechaFin}
            onChange={(e) => setFechaFin(e.target.value)}
            error={errores['datos.fechaFin']}
            hint={
              fechaFin
                ? `Toros afuera el ${formatearFecha(fechaFin)}`
                : 'Puede ser la prevista. Sin fin, queda en servicio hasta el tacto.'
            }
          />
        )}
        {!natural && (
          <TextField
            label="Pajuela"
            placeholder="Ej: Angus Rito 707, partida 1234"
            maxLength={100}
            value={pajuela}
            onChange={(e) => setPajuela(e.target.value)}
            error={errores['datos.pajuela']}
            hint="Toro, central o partida del semen"
          />
        )}
      </div>

      <ElegirAnimales
        establecimientoId={establecimientoId}
        etiqueta={
          natural ? 'Toros (opcional)' : 'Toro del semen (opcional si indicaste la pajuela)'
        }
        filtro={{ sexo: 'MACHO' }}
        aplica={esToro}
        maximo={natural ? MAX_TOROS : 1}
        elegidos={toros}
        onChange={setToros}
        error={errores['datos.toroIds']}
        hint={natural ? 'Toros y toritos del establecimiento' : 'Si el semen es de un toro propio'}
      />

      <TextAreaField
        label="Observaciones (opcional)"
        rows={2}
        maxLength={500}
        value={observaciones}
        onChange={(e) => setObservaciones(e.target.value)}
        error={errores['datos.observaciones']}
      />

      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onClose} disabled={registrar.isPending}>
          Cancelar
        </Button>
        <Button type="submit" loading={registrar.isPending}>
          Guardar servicio
        </Button>
      </div>
    </form>
  )
}
