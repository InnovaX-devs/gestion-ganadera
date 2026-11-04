import { type FormEvent, useState } from 'react'
import { Button } from '@/components/Button'
import { Dialog } from '@/components/Dialog'
import { FormError } from '@/components/FormError'
import { SelectField } from '@/components/SelectField'
import { TextAreaField } from '@/components/TextAreaField'
import { TextField } from '@/components/TextField'
import { useAgrupaciones } from '@/features/agrupaciones/queries'
import { useLectura } from '@/features/lector/contexto'
import { getErrorMessage, getFieldErrors } from '@/lib/api'
import { CATEGORIAS, RAZAS_SUGERIDAS, SEXOS, categoriasDe } from './config'
import { useAnimales, useGuardarAnimal } from './queries'
import type { Animal, AnimalInput, Categoria, Referencia, Sexo } from './types'

interface Props {
  establecimientoId: string
  open: boolean
  /** Si viene, se edita; si no, se crea. */
  animal: Animal | null
  /** Para un alta: caravana electrónica ya leída por el lector (#33) */
  caravanaElectronica?: string
  onClose: () => void
}

export function AnimalFormDialog({
  establecimientoId,
  open,
  animal,
  caravanaElectronica,
  onClose,
}: Props) {
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={animal ? `Editar ${animal.caravana}` : 'Nuevo animal'}
      description={animal ? undefined : 'Caravana, sexo y categoría son obligatorios.'}
    >
      <Formulario
        key={animal?.id ?? 'nuevo'}
        establecimientoId={establecimientoId}
        animal={animal}
        caravanaElectronica={caravanaElectronica}
        onDone={onClose}
      />
    </Dialog>
  )
}

interface Campos {
  caravana: string
  caravanaElectronica: string
  sexo: Sexo
  categoria: Categoria
  raza: string
  fechaNacimiento: string
  fechaNacimientoEstimada: boolean
  castrado: boolean
  loteId: string
  rodeoId: string
  madreId: string
  padreId: string
  observaciones: string
}

function iniciales(a: Animal | null): Campos {
  return {
    caravana: a?.caravana ?? '',
    caravanaElectronica: a?.caravanaElectronica ?? '',
    sexo: a?.sexo ?? 'HEMBRA',
    categoria: a?.categoria ?? 'VACA',
    raza: a?.raza ?? '',
    fechaNacimiento: a?.fechaNacimiento ?? '',
    fechaNacimientoEstimada: a?.fechaNacimientoEstimada ?? false,
    castrado: a?.castrado ?? false,
    loteId: a?.lote?.id ?? '',
    rodeoId: a?.rodeo?.id ?? '',
    madreId: a?.madre?.id ?? '',
    padreId: a?.padre?.id ?? '',
    observaciones: a?.observaciones ?? '',
  }
}

const vacioANull = (texto: string) => (texto.trim() === '' ? null : texto.trim())
/** Fecha local de hoy en formato YYYY-MM-DD (toISOString usaría UTC y después de las 21 h daría mañana). */
const hoy = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function Formulario({
  establecimientoId,
  animal,
  caravanaElectronica,
  onDone,
}: {
  establecimientoId: string
  animal: Animal | null
  caravanaElectronica?: string
  onDone: () => void
}) {
  const [c, setC] = useState(() => ({
    ...iniciales(animal),
    ...(caravanaElectronica && !animal ? { caravanaElectronica } : {}),
  }))
  const [error, setError] = useState<string | null>(null)
  const [errores, setErrores] = useState<Record<string, string>>({})
  const guardar = useGuardarAnimal(establecimientoId)

  const { data: lotes } = useAgrupaciones('lotes', establecimientoId)
  const { data: rodeos } = useAgrupaciones('rodeos', establecimientoId)
  // Posibles madres y padres: hembras y machos activos del establecimiento (después se filtran los adultos)
  const { data: hembras } = useAnimales(establecimientoId, { sexo: 'HEMBRA', tamanio: 200 })
  const { data: machos } = useAnimales(establecimientoId, { sexo: 'MACHO', tamanio: 200 })

  // Con el formulario abierto, una lectura del bastón completa la caravana electrónica (#33)
  useLectura((lectura) => {
    if (lectura.campo && !lectura.campo.closest('[data-lector="electronica"]')) return false
    setC((prev) => ({ ...prev, caravanaElectronica: lectura.eid }))
    return true
  })

  const set = <K extends keyof Campos>(campo: K, valor: Campos[K]) => {
    setC((prev) => ({ ...prev, [campo]: valor }))
    if (errores[campo]) {
      setErrores((prev) => {
        const resto = { ...prev }
        delete resto[campo]
        return resto
      })
    }
  }

  const cambiarSexo = (sexo: Sexo) => {
    // Si la categoría elegida no corresponde al nuevo sexo, se pasa a la primera que sí
    setC((prev) => ({
      ...prev,
      sexo,
      categoria: CATEGORIAS[prev.categoria].sexo === sexo ? prev.categoria : categoriasDe(sexo)[0],
    }))
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (!c.caravana.trim()) {
      setErrores({ caravana: 'Ingresá la caravana' })
      return
    }
    setErrores({})

    const data: AnimalInput = {
      caravana: c.caravana.trim(),
      caravanaElectronica: vacioANull(c.caravanaElectronica),
      sexo: c.sexo,
      categoria: c.categoria,
      raza: vacioANull(c.raza),
      fechaNacimiento: c.fechaNacimiento || null,
      fechaNacimientoEstimada: !!c.fechaNacimiento && c.fechaNacimientoEstimada,
      castrado: c.castrado,
      loteId: c.loteId || null,
      rodeoId: c.rodeoId || null,
      madreId: c.madreId || null,
      padreId: c.padreId || null,
      observaciones: vacioANull(c.observaciones),
    }

    try {
      await guardar.mutateAsync({ id: animal?.id, data })
      onDone()
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

      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          label="Caravana"
          placeholder="Ej: A-123"
          autoFocus
          required
          maxLength={30}
          value={c.caravana}
          onChange={(e) => set('caravana', e.target.value)}
          error={errores.caravana}
        />
        <TextField
          label="Caravana electrónica"
          placeholder="Opcional"
          inputMode="numeric"
          maxLength={30}
          value={c.caravanaElectronica}
          onChange={(e) => set('caravanaElectronica', e.target.value)}
          data-lector="electronica"
          error={errores.caravanaElectronica}
        />
      </div>

      <fieldset>
        <legend className="mb-1.5 text-sm font-medium">Sexo</legend>
        <div className="grid grid-cols-2 gap-2">
          {(Object.keys(SEXOS) as Sexo[]).map((s) => (
            <label
              key={s}
              className={`flex cursor-pointer items-center justify-center rounded-lg border px-3 py-2 text-sm font-medium transition ${
                c.sexo === s
                  ? 'border-brand-500 bg-brand-50 text-brand-700 dark:bg-brand-900/40 dark:text-brand-200'
                  : 'border-stone-300 hover:bg-stone-50 dark:border-stone-700 dark:hover:bg-stone-800'
              }`}
            >
              <input
                type="radio"
                name="sexo"
                value={s}
                checked={c.sexo === s}
                onChange={() => cambiarSexo(s)}
                className="sr-only"
              />
              {SEXOS[s]}
            </label>
          ))}
        </div>
      </fieldset>

      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <SelectField
            label="Categoría"
            value={c.categoria}
            onChange={(e) => set('categoria', e.target.value as Categoria)}
            error={errores.categoria}
          >
            {categoriasDe(c.sexo).map((cat) => (
              <option key={cat} value={cat}>
                {CATEGORIAS[cat].etiqueta}
              </option>
            ))}
          </SelectField>
          {c.categoria === 'TERNERO' && (
            <Check
              className="mt-2"
              label="Castrado"
              checked={c.castrado}
              onChange={(v) => set('castrado', v)}
            />
          )}
        </div>
        <div>
          <TextField
            label="Raza"
            placeholder="Opcional"
            list="razas-sugeridas"
            maxLength={50}
            value={c.raza}
            onChange={(e) => set('raza', e.target.value)}
            error={errores.raza}
          />
          <datalist id="razas-sugeridas">
            {RAZAS_SUGERIDAS.map((r) => (
              <option key={r} value={r} />
            ))}
          </datalist>
        </div>
      </div>

      <div>
        <TextField
          label="Fecha de nacimiento"
          type="date"
          max={hoy()}
          value={c.fechaNacimiento}
          onChange={(e) => set('fechaNacimiento', e.target.value)}
          error={errores.fechaNacimiento}
        />
        {c.fechaNacimiento && (
          <Check
            className="mt-2"
            label="Es aproximada (animal comprado o sin registro)"
            checked={c.fechaNacimientoEstimada}
            onChange={(v) => set('fechaNacimientoEstimada', v)}
          />
        )}
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <SelectField label="Lote" value={c.loteId} onChange={(e) => set('loteId', e.target.value)}>
          <option value="">Sin lote</option>
          {lotes?.map((l) => (
            <option key={l.id} value={l.id}>
              {l.nombre}
            </option>
          ))}
        </SelectField>
        <SelectField
          label="Rodeo"
          value={c.rodeoId}
          onChange={(e) => set('rodeoId', e.target.value)}
        >
          <option value="">Sin rodeo</option>
          {rodeos?.map((r) => (
            <option key={r.id} value={r.id}>
              {r.nombre}
            </option>
          ))}
        </SelectField>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <SelectField
          label="Madre"
          value={c.madreId}
          onChange={(e) => set('madreId', e.target.value)}
        >
          <option value="">Sin registrar</option>
          {opcionesProgenitor(hembras?.contenido, MADRES, animal?.id, animal?.madre)}
        </SelectField>
        <SelectField
          label="Padre"
          value={c.padreId}
          onChange={(e) => set('padreId', e.target.value)}
        >
          <option value="">Sin registrar</option>
          {opcionesProgenitor(machos?.contenido, PADRES, animal?.id, animal?.padre)}
        </SelectField>
      </div>

      <TextAreaField
        label="Observaciones"
        placeholder="Opcional"
        maxLength={500}
        rows={2}
        value={c.observaciones}
        onChange={(e) => set('observaciones', e.target.value)}
      />

      {/* Botones fijos al pie: el formulario es largo y en pantallas bajas quedarían fuera de vista */}
      <div className="sticky bottom-0 -mx-6 -mb-6 flex justify-end gap-2 border-t border-stone-200 bg-white px-6 py-4 dark:border-stone-800 dark:bg-stone-900">
        <Button variant="secondary" onClick={onDone} disabled={guardar.isPending}>
          Cancelar
        </Button>
        <Button type="submit" loading={guardar.isPending}>
          {animal ? 'Guardar cambios' : 'Crear animal'}
        </Button>
      </div>
    </form>
  )
}

/** Solo adultos reproductivos: una ternera no puede ser madre ni un novillo (castrado) padre. */
const MADRES: Categoria[] = ['VACA', 'VAQUILLONA']
const PADRES: Categoria[] = ['TORO', 'TORITO']

/**
 * Opciones de madre/padre. Solo las categorías indicadas y excluye al propio animal y, si el progenitor actual no está en la lista
 * (por ejemplo, está en otro establecimiento), lo agrega para no perderlo al guardar.
 */
function opcionesProgenitor(
  candidatos: Animal[] | undefined,
  categorias: Categoria[],
  propioId: string | undefined,
  actual: Referencia | null | undefined,
) {
  const lista = (candidatos ?? []).filter(
    (a) => a.id !== propioId && categorias.includes(a.categoria),
  )
  const opciones = lista.map((a) => (
    <option key={a.id} value={a.id}>
      {a.caravana} · {CATEGORIAS[a.categoria].etiqueta}
    </option>
  ))
  if (actual && !lista.some((a) => a.id === actual.id)) {
    opciones.unshift(
      <option key={actual.id} value={actual.id}>
        {actual.nombre}
      </option>,
    )
  }
  return opciones
}

function Check({
  label,
  checked,
  onChange,
  className = '',
}: {
  label: string
  checked: boolean
  onChange: (v: boolean) => void
  className?: string
}) {
  return (
    <label
      className={`flex items-center gap-2 text-sm text-stone-700 dark:text-stone-300 ${className}`}
    >
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        className="accent-brand-600 size-4 rounded"
      />
      {label}
    </label>
  )
}
