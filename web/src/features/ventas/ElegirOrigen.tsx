import { SelectField } from '@/components/SelectField'
import { useAgrupaciones } from '@/features/agrupaciones/queries'
import { ElegirAnimales } from '@/features/sanidad/ElegirAnimales'
import { type Grupo, type Origen } from './origen'

/**
 * Elegir sobre qué animales se registra el evento: animales puntuales, un lote entero o un rodeo
 * entero. Para los diálogos que no reciben los animales ya elegidos.
 */
export function ElegirOrigen({
  establecimientoId,
  valor,
  onChange,
  error,
}: {
  establecimientoId: string
  valor: Origen
  onChange: (o: Origen) => void
  error?: string
}) {
  const { data: lotes } = useAgrupaciones('lotes', establecimientoId)
  const { data: rodeos } = useAgrupaciones('rodeos', establecimientoId)
  const grupos = valor.grupo === 'LOTE' ? lotes : rodeos
  const elegidoId = valor.grupo === 'LOTE' ? valor.loteId : valor.rodeoId
  const elegido = grupos?.find((g) => g.id === elegidoId)

  return (
    <div className="space-y-3">
      <SelectField
        label="Animales"
        value={valor.grupo}
        onChange={(e) => onChange({ ...valor, grupo: e.target.value as Grupo })}
      >
        <option value="ANIMALES">Animales puntuales</option>
        <option value="LOTE">Todo un lote</option>
        <option value="RODEO">Todo un rodeo</option>
      </SelectField>
      {valor.grupo === 'ANIMALES' ? (
        <ElegirAnimales
          establecimientoId={establecimientoId}
          elegidos={valor.elegidos}
          onChange={(elegidos) => onChange({ ...valor, elegidos })}
          error={error}
        />
      ) : (
        <SelectField
          label={valor.grupo === 'LOTE' ? 'Lote' : 'Rodeo'}
          value={elegidoId}
          onChange={(e) =>
            onChange(
              valor.grupo === 'LOTE'
                ? { ...valor, loteId: e.target.value }
                : { ...valor, rodeoId: e.target.value },
            )
          }
          error={error}
          hint={elegido ? textoGrupo(elegido.cantidadAnimales) : undefined}
        >
          <option value="">{valor.grupo === 'LOTE' ? 'Elegí un lote' : 'Elegí un rodeo'}</option>
          {grupos?.map((g) => (
            <option key={g.id} value={g.id}>
              {g.nombre}
            </option>
          ))}
        </SelectField>
      )}
    </div>
  )
}

function textoGrupo(cantidad: number) {
  if (cantidad === 0) return 'No tiene animales'
  return `Se incluyen los ${cantidad} ${cantidad === 1 ? 'animal' : 'animales'} del grupo`
}
