import { SelectField } from '@/components/SelectField'
import { useAgrupaciones } from '@/features/agrupaciones/queries'
import { ElegirAnimales } from '@/features/sanidad/ElegirAnimales'
import { esHembraDeCria } from './config'
import type { Destino, TipoDestino } from './destino'
import { textoHembras, useHembrasDeCria } from './useHembrasDeCria'

interface Props {
  establecimientoId: string
  valor: Destino
  onChange: (destino: Destino) => void
  error?: string
  /** Si se ofrece cargar para un grupo entero (el servicio sí; el tacto, de a una). */
  grupos?: boolean
}

/** A quién va un servicio o tacto: vacas y vaquillonas puntuales, o todas las de un rodeo o lote. */
export function DestinoReproductivo({ establecimientoId, valor, onChange, error, grupos }: Props) {
  const { data: rodeos } = useAgrupaciones('rodeos', establecimientoId)
  const { data: lotes } = useAgrupaciones('lotes', establecimientoId)
  const cantidad = useHembrasDeCria(establecimientoId, {
    rodeo: valor.tipo === 'RODEO' ? valor.rodeoId || undefined : undefined,
    lote: valor.tipo === 'LOTE' ? valor.loteId || undefined : undefined,
  })

  return (
    <div className="space-y-3">
      {grupos && (
        <SelectField
          label="Se registra en"
          value={valor.tipo}
          onChange={(e) => onChange({ ...valor, tipo: e.target.value as TipoDestino })}
        >
          <option value="ANIMALES">Vacas y vaquillonas puntuales</option>
          <option value="RODEO">Todo un rodeo</option>
          <option value="LOTE">Todo un lote</option>
        </SelectField>
      )}
      {valor.tipo === 'ANIMALES' && (
        <ElegirAnimales
          establecimientoId={establecimientoId}
          etiqueta="Vacas y vaquillonas"
          filtro={{ sexo: 'HEMBRA' }}
          aplica={esHembraDeCria}
          elegidos={valor.animales}
          onChange={(animales) => onChange({ ...valor, animales })}
          error={error}
        />
      )}
      {valor.tipo === 'RODEO' && (
        <SelectField
          label="Rodeo"
          value={valor.rodeoId}
          onChange={(e) => onChange({ ...valor, rodeoId: e.target.value })}
          error={error}
          hint={valor.rodeoId ? textoHembras(cantidad, 'rodeo') : undefined}
        >
          <option value="">Elegí un rodeo</option>
          {rodeos?.map((r) => (
            <option key={r.id} value={r.id}>
              {r.nombre}
            </option>
          ))}
        </SelectField>
      )}
      {valor.tipo === 'LOTE' && (
        <SelectField
          label="Lote"
          value={valor.loteId}
          onChange={(e) => onChange({ ...valor, loteId: e.target.value })}
          error={error}
          hint={valor.loteId ? textoHembras(cantidad, 'lote') : undefined}
        >
          <option value="">Elegí un lote</option>
          {lotes?.map((l) => (
            <option key={l.id} value={l.id}>
              {l.nombre}
            </option>
          ))}
        </SelectField>
      )}
    </div>
  )
}
