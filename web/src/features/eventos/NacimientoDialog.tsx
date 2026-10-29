import { type FormEvent, useState } from 'react'
import { LoaderCircle, Plus, X } from 'lucide-react'
import { Button } from '@/components/Button'
import { Dialog } from '@/components/Dialog'
import { FormError } from '@/components/FormError'
import { SelectField } from '@/components/SelectField'
import { TextAreaField } from '@/components/TextAreaField'
import { TextField } from '@/components/TextField'
import { CategoriaBadge } from '@/features/animales/CategoriaBadge'
import { useAnimales } from '@/features/animales/queries'
import type { Sexo } from '@/features/animales/types'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { getErrorMessage, getFieldErrors } from '@/lib/api'
import { TIPOS_PARTO } from './config'
import { hoyISO } from './fechas'
import { useRegistrarEvento } from './queries'
import type { CriaNacimiento, NacimientoDatos, TipoParto } from './types'

/** Igual que en el backend (NacimientoDatos): hasta trillizos. */
const MAXIMO_CRIAS = 3

interface Madre {
  id: string
  caravana: string
}

interface Props {
  establecimientoId: string
  open: boolean
  /** Si viene (desde la ficha), la madre queda fija. Si no, se elige en el formulario. */
  madre?: Madre
  onClose: () => void
}

/** Registrar un parto: crea las crías vivas y, si la madre era vaquillona, la pasa a vaca. */
export function NacimientoDialog({ establecimientoId, open, madre, onClose }: Props) {
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Nuevo nacimiento"
      description={
        madre
          ? `Parto de ${madre.caravana}. Las crías quedan en su mismo lote y rodeo.`
          : 'Las crías quedan en el mismo lote y rodeo que la madre.'
      }
    >
      {open && <Formulario establecimientoId={establecimientoId} madre={madre} onClose={onClose} />}
    </Dialog>
  )
}

interface CriaForm {
  /** Solo para la key de React. */
  key: string
  sexo: Sexo | ''
  nacidoMuerto: boolean
  caravana: string
  pesoKg: string
}

const criaVacia = (): CriaForm => ({
  key: crypto.randomUUID(),
  sexo: '',
  nacidoMuerto: false,
  caravana: '',
  pesoKg: '',
})

function Formulario({ establecimientoId, madre: madreFija, onClose }: Omit<Props, 'open'>) {
  const registrar = useRegistrarEvento<NacimientoDatos>(establecimientoId)
  const [madre, setMadre] = useState<Madre | null>(madreFija ?? null)
  const [fecha, setFecha] = useState(hoyISO)
  const [tipoParto, setTipoParto] = useState<TipoParto>('NORMAL')
  const [crias, setCrias] = useState<CriaForm[]>(() => [criaVacia()])
  const [observaciones, setObservaciones] = useState('')
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)

  const cambiarCria = (i: number, cambios: Partial<CriaForm>) =>
    setCrias((cs) => cs.map((c, j) => (j === i ? { ...c, ...cambios } : c)))

  function validar(): Record<string, string> {
    const e: Record<string, string> = {}
    if (!madre) e.madre = 'Elegí la madre'
    const caravanas = new Set<string>()
    crias.forEach((c, i) => {
      const campo = `datos.crias[${i}]`
      if (!c.sexo) e[`${campo}.sexo`] = 'Elegí el sexo'
      if (!c.nacidoMuerto) {
        const caravana = c.caravana.trim().toUpperCase()
        if (!caravana) e[`${campo}.caravana`] = 'Ingresá la caravana de la cría'
        else if (caravanas.has(caravana)) e[`${campo}.caravana`] = 'Está repetida en otra cría'
        caravanas.add(caravana)
      }
      if (c.pesoKg) {
        const peso = Number(c.pesoKg.replace(',', '.'))
        if (!(peso > 0)) e[`${campo}.pesoKg`] = 'El peso tiene que ser mayor a 0'
        else if (peso > 100) e[`${campo}.pesoKg`] = 'Revisá el peso'
      }
    })
    return e
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const encontrados = validar()
    setErrores(encontrados)
    if (Object.keys(encontrados).length > 0 || !madre) return
    try {
      await registrar.mutateAsync({
        // Generado acá: si la red falla y se reintenta, el backend no lo duplica
        operacionId: crypto.randomUUID(),
        tipo: 'NACIMIENTO',
        fecha,
        animalIds: [madre.id],
        datos: {
          tipoParto,
          crias: crias.map((c): CriaNacimiento => ({
            sexo: c.sexo as Sexo,
            nacidoMuerto: c.nacidoMuerto,
            caravana: c.nacidoMuerto ? null : c.caravana.trim(),
            pesoKg: c.pesoKg ? Number(c.pesoKg.replace(',', '.')) : null,
          })),
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

      {!madreFija && (
        <SelectorMadre
          establecimientoId={establecimientoId}
          madre={madre}
          onElegir={setMadre}
          error={errores.madre}
        />
      )}

      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          label="Fecha"
          type="date"
          required
          max={hoyISO()}
          value={fecha}
          onChange={(e) => setFecha(e.target.value)}
          error={errores.fecha}
        />
        <SelectField
          label="Tipo de parto"
          value={tipoParto}
          onChange={(e) => setTipoParto(e.target.value as TipoParto)}
          error={errores['datos.tipoParto']}
        >
          {(Object.keys(TIPOS_PARTO) as TipoParto[]).map((t) => (
            <option key={t} value={t}>
              {TIPOS_PARTO[t]}
            </option>
          ))}
        </SelectField>
      </div>

      <fieldset className="space-y-3">
        <legend className="mb-1.5 text-sm font-medium">
          {crias.length === 1 ? 'Cría' : `Crías (${crias.length})`}
        </legend>
        {crias.map((c, i) => (
          <div key={c.key} className="rounded-lg border border-stone-200 p-3 dark:border-stone-800">
            <div className="mb-2 flex items-center justify-between gap-2">
              <span className="text-xs font-medium text-stone-500">
                {crias.length > 1 ? `Cría ${i + 1}` : 'Datos de la cría'}
              </span>
              {crias.length > 1 && (
                <button
                  type="button"
                  onClick={() => setCrias((cs) => cs.filter((_, j) => j !== i))}
                  aria-label={`Quitar cría ${i + 1}`}
                  title="Quitar"
                  className="rounded-md p-1 text-stone-400 hover:bg-stone-100 hover:text-stone-700 dark:hover:bg-stone-800"
                >
                  <X className="size-4" />
                </button>
              )}
            </div>
            <div className="grid gap-3 sm:grid-cols-3">
              <SelectField
                label="Sexo"
                value={c.sexo}
                onChange={(e) => cambiarCria(i, { sexo: e.target.value as Sexo })}
                error={errores[`datos.crias[${i}].sexo`]}
              >
                <option value="">Elegir</option>
                <option value="HEMBRA">Hembra</option>
                <option value="MACHO">Macho</option>
              </SelectField>
              <TextField
                label="Caravana"
                maxLength={30}
                disabled={c.nacidoMuerto}
                placeholder={c.nacidoMuerto ? 'No lleva' : 'Ej: 1024'}
                value={c.nacidoMuerto ? '' : c.caravana}
                onChange={(e) => cambiarCria(i, { caravana: e.target.value })}
                error={errores[`datos.crias[${i}].caravana`]}
              />
              <TextField
                label="Peso al nacer (kg)"
                type="number"
                inputMode="decimal"
                min={0}
                max={100}
                step={0.1}
                placeholder="Opcional"
                value={c.pesoKg}
                onChange={(e) => cambiarCria(i, { pesoKg: e.target.value })}
                error={errores[`datos.crias[${i}].pesoKg`]}
              />
            </div>
            <label className="mt-3 flex items-center gap-2 text-sm text-stone-700 dark:text-stone-300">
              <input
                type="checkbox"
                checked={c.nacidoMuerto}
                onChange={(e) => cambiarCria(i, { nacidoMuerto: e.target.checked })}
                className="accent-brand-600 size-4 rounded"
              />
              Nació muerta
            </label>
          </div>
        ))}
        {crias.length < MAXIMO_CRIAS && (
          <Button variant="secondary" onClick={() => setCrias((cs) => [...cs, criaVacia()])}>
            <Plus className="size-4" />
            {crias.length === 1 ? 'Agregar mellizo' : 'Agregar otra cría'}
          </Button>
        )}
      </fieldset>

      <TextAreaField
        label="Observaciones"
        placeholder="Opcional. Ej: venía de cola, la vaca no lo aceptaba"
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
          Guardar nacimiento
        </Button>
      </div>
    </form>
  )
}

/** Búsqueda de hembras activas del establecimiento por caravana. */
function SelectorMadre({
  establecimientoId,
  madre,
  onElegir,
  error,
}: {
  establecimientoId: string
  madre: Madre | null
  onElegir: (madre: Madre | null) => void
  error?: string
}) {
  const [busqueda, setBusqueda] = useState('')
  const q = useDebouncedValue(busqueda.trim())
  const { data, isFetching } = useAnimales(madre ? undefined : establecimientoId, {
    q,
    sexo: 'HEMBRA',
    tamanio: 6,
  })

  if (madre) {
    return (
      <div>
        <p className="mb-1.5 text-sm font-medium">Madre</p>
        <div className="flex items-center justify-between rounded-lg border border-stone-300 px-3 py-2 text-sm dark:border-stone-700">
          <span className="font-medium tabular-nums">{madre.caravana}</span>
          <button
            type="button"
            onClick={() => onElegir(null)}
            className="text-brand-700 dark:text-brand-300 text-sm hover:underline"
          >
            Cambiar
          </button>
        </div>
      </div>
    )
  }

  const hembras = data?.contenido ?? []
  return (
    <div>
      <TextField
        label="Madre"
        placeholder="Buscá por caravana"
        autoFocus
        value={busqueda}
        onChange={(e) => setBusqueda(e.target.value)}
        error={error}
      />
      <div className="mt-2 rounded-lg border border-stone-200 dark:border-stone-800">
        {!data && isFetching ? (
          <LoaderCircle className="m-3 size-4 animate-spin text-stone-400" />
        ) : hembras.length === 0 ? (
          <p className="px-3 py-2 text-sm text-stone-500">
            {q ? `No hay hembras con la caravana “${q}”` : 'No hay hembras cargadas'}
          </p>
        ) : (
          <ul className="divide-y divide-stone-100 dark:divide-stone-800">
            {hembras.map((h) => (
              <li key={h.id}>
                <button
                  type="button"
                  onClick={() => onElegir({ id: h.id, caravana: h.caravana })}
                  className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-stone-50 dark:hover:bg-stone-800"
                >
                  <span className="font-medium tabular-nums">{h.caravana}</span>
                  <CategoriaBadge animal={h} />
                  {h.lote && (
                    <span className="ml-auto text-xs text-stone-500">Lote {h.lote.nombre}</span>
                  )}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
