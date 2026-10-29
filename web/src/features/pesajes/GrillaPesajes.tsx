import { type FormEvent, type KeyboardEvent, useRef, useState } from 'react'
import { CircleCheck, Plus, X } from 'lucide-react'
import { Button } from '@/components/Button'
import { FormError } from '@/components/FormError'
import { TextField } from '@/components/TextField'
import { hoyISO } from '@/features/eventos/fechas'
import { getErrorMessage, getFieldErrors } from '@/lib/api'
import { leerKg } from './formato'
import { useRegistrarGrilla } from './queries'

interface Fila {
  id: number
  caravana: string
  kg: string
}

const MAX_FILAS = 500

/**
 * Carga rápida de pesajes: caravana + peso por fila. Enter pasa de la caravana al peso y del peso a
 * la caravana de la fila siguiente (creándola si hace falta), para cargar toda una balanza sin
 * soltar el teclado. Se guarda todo junto: si una caravana no existe, no se guarda ninguna y la
 * fila con el problema queda marcada.
 */
export function GrillaPesajes({ establecimientoId }: { establecimientoId: string }) {
  const registrar = useRegistrarGrilla(establecimientoId)
  const siguienteId = useRef(2)
  const campos = useRef(new Map<string, HTMLInputElement>())
  const [fecha, setFecha] = useState(hoyISO)
  const [filas, setFilas] = useState<Fila[]>([{ id: 1, caravana: '', kg: '' }])
  const [errores, setErrores] = useState<Record<number, { caravana?: string; kg?: string }>>({})
  const [error, setError] = useState<string | null>(null)
  const [guardados, setGuardados] = useState<number | null>(null)

  const enfocar = (id: number, campo: 'caravana' | 'kg') =>
    // Se espera al render: si la fila es nueva, su input todavía no existe
    requestAnimationFrame(() => campos.current.get(`${id}-${campo}`)?.focus())

  const cambiar = (id: number, cambios: Partial<Fila>) => {
    setFilas((fs) => fs.map((f) => (f.id === id ? { ...f, ...cambios } : f)))
    setErrores((e) => {
      if (!e[id]) return e
      const resto = { ...e }
      delete resto[id]
      return resto
    })
    setGuardados(null)
  }

  const agregarFila = () => {
    const id = siguienteId.current++
    setFilas((fs) => [...fs, { id, caravana: '', kg: '' }])
    return id
  }

  function alApretarEnter(
    e: KeyboardEvent<HTMLInputElement>,
    fila: Fila,
    campo: 'caravana' | 'kg',
  ) {
    if (e.key !== 'Enter') return
    e.preventDefault() // Enter no envía el formulario: se guarda con el botón
    if (campo === 'caravana') return enfocar(fila.id, 'kg')
    const indice = filas.findIndex((f) => f.id === fila.id)
    const siguiente = filas[indice + 1]
    if (siguiente) return enfocar(siguiente.id, 'caravana')
    if (filas.length < MAX_FILAS) enfocar(agregarFila(), 'caravana')
  }

  const quitarFila = (id: number) =>
    setFilas((fs) =>
      fs.length === 1 ? [{ id, caravana: '', kg: '' }] : fs.filter((f) => f.id !== id),
    )

  // Una fila totalmente vacía (la de "siguiente") no cuenta
  const conDatos = filas.filter((f) => f.caravana.trim() || f.kg.trim())

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setGuardados(null)

    const nuevos: Record<number, { caravana?: string; kg?: string }> = {}
    const aEnviar: { fila: Fila; caravana: string; kg: number }[] = []
    for (const fila of conDatos) {
      const kg = leerKg(fila.kg)
      const problemas: { caravana?: string; kg?: string } = {}
      if (!fila.caravana.trim()) problemas.caravana = 'Ingresá la caravana'
      if (kg === null || kg <= 0) problemas.kg = 'Peso inválido'
      else if (kg > 1500) problemas.kg = 'Máximo 1500 kg'
      if (problemas.caravana || problemas.kg) nuevos[fila.id] = problemas
      else aEnviar.push({ fila, caravana: fila.caravana.trim(), kg: kg! })
    }
    setErrores(nuevos)
    if (conDatos.length === 0) return setError('Cargá al menos un pesaje')
    if (Object.keys(nuevos).length > 0) return

    try {
      await registrar.mutateAsync({
        // Generado acá: si la red falla y se reintenta, el backend no la duplica
        operacionId: crypto.randomUUID(),
        fecha,
        pesajes: aEnviar.map(({ caravana, kg }) => ({ caravana, kg })),
      })
      setGuardados(aEnviar.length)
      siguienteId.current = 2
      setFilas([{ id: 1, caravana: '', kg: '' }])
      enfocar(1, 'caravana')
    } catch (err) {
      // El backend numera las filas como las recibió: se vuelve a la fila de la pantalla
      const porFila: Record<number, { caravana?: string; kg?: string }> = {}
      for (const [campo, mensaje] of Object.entries(getFieldErrors(err))) {
        const m = /^pesajes\[(\d+)\]\.(caravana|kg)$/.exec(campo)
        const fila = m && aEnviar[Number(m[1])]?.fila
        if (m && fila) porFila[fila.id] = { ...porFila[fila.id], [m[2]]: mensaje }
      }
      setErrores(porFila)
      setError(
        Object.keys(porFila).length > 0
          ? 'No se guardó ningún pesaje. Corregí las filas marcadas.'
          : getErrorMessage(err),
      )
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      noValidate
      className="rounded-xl border border-stone-200 bg-white p-5 dark:border-stone-800 dark:bg-stone-900"
    >
      <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-sm font-semibold">Carga rápida</h2>
          <p className="mt-0.5 text-xs text-stone-500">
            Caravana, Enter, peso, Enter: pasa a la fila siguiente.
          </p>
        </div>
        <TextField
          label="Fecha"
          type="date"
          required
          max={hoyISO()}
          value={fecha}
          onChange={(e) => setFecha(e.target.value)}
        />
      </div>

      <FormError message={error} />
      {guardados !== null && (
        <div
          role="status"
          className="mb-3 flex items-center gap-2 rounded-lg border border-green-200 bg-green-50 px-3 py-2.5 text-sm text-green-800 dark:border-green-900 dark:bg-green-950/40 dark:text-green-300"
        >
          <CircleCheck className="size-4 shrink-0" />
          {guardados === 1 ? 'Se guardó 1 pesaje.' : `Se guardaron ${guardados} pesajes.`}
        </div>
      )}

      <ul className="space-y-2">
        {filas.map((fila, i) => (
          <li key={fila.id} className="flex items-start gap-2">
            <span className="w-6 pt-2.5 text-right text-xs text-stone-400 tabular-nums">
              {i + 1}
            </span>
            <div className="min-w-0 flex-1">
              <input
                ref={(el) => {
                  if (el) campos.current.set(`${fila.id}-caravana`, el)
                  else campos.current.delete(`${fila.id}-caravana`)
                }}
                value={fila.caravana}
                onChange={(e) => cambiar(fila.id, { caravana: e.target.value })}
                onKeyDown={(e) => alApretarEnter(e, fila, 'caravana')}
                placeholder="Caravana"
                aria-label={`Caravana de la fila ${i + 1}`}
                aria-invalid={!!errores[fila.id]?.caravana}
                autoCapitalize="characters"
                autoComplete="off"
                maxLength={30}
                autoFocus={i === 0}
                className={clasesInput(!!errores[fila.id]?.caravana)}
              />
              {errores[fila.id]?.caravana && <Mensaje texto={errores[fila.id]!.caravana!} />}
            </div>
            <div className="w-32 shrink-0">
              <input
                ref={(el) => {
                  if (el) campos.current.set(`${fila.id}-kg`, el)
                  else campos.current.delete(`${fila.id}-kg`)
                }}
                value={fila.kg}
                onChange={(e) => cambiar(fila.id, { kg: e.target.value })}
                onKeyDown={(e) => alApretarEnter(e, fila, 'kg')}
                placeholder="Peso (kg)"
                aria-label={`Peso de la fila ${i + 1}`}
                aria-invalid={!!errores[fila.id]?.kg}
                inputMode="decimal"
                autoComplete="off"
                className={`${clasesInput(!!errores[fila.id]?.kg)} text-right tabular-nums`}
              />
              {errores[fila.id]?.kg && <Mensaje texto={errores[fila.id]!.kg!} />}
            </div>
            <button
              type="button"
              onClick={() => quitarFila(fila.id)}
              aria-label={`Quitar la fila ${i + 1}`}
              tabIndex={-1}
              className="mt-1 rounded-md p-1.5 text-stone-400 hover:bg-stone-100 hover:text-stone-600 dark:hover:bg-stone-800"
            >
              <X className="size-4" />
            </button>
          </li>
        ))}
      </ul>

      <div className="mt-4 flex flex-wrap items-center justify-between gap-2">
        <Button
          variant="secondary"
          onClick={() => enfocar(agregarFila(), 'caravana')}
          disabled={filas.length >= MAX_FILAS}
        >
          <Plus className="size-4" />
          Agregar fila
        </Button>
        <Button type="submit" loading={registrar.isPending} disabled={conDatos.length === 0}>
          {conDatos.length > 1 ? `Guardar ${conDatos.length} pesajes` : 'Guardar pesaje'}
        </Button>
      </div>
    </form>
  )
}

const clasesInput = (conError: boolean) =>
  `block w-full rounded-lg border bg-white px-3 py-2 text-sm shadow-xs outline-none placeholder:text-stone-400 focus:ring-2 dark:bg-stone-900 ${
    conError
      ? 'border-red-400 focus:ring-red-200 dark:border-red-500 dark:focus:ring-red-900'
      : 'focus:border-brand-500 focus:ring-brand-100 dark:focus:ring-brand-900 border-stone-300 dark:border-stone-700'
  }`

function Mensaje({ texto }: { texto: string }) {
  return <p className="mt-1 text-xs text-red-600 dark:text-red-400">{texto}</p>
}
