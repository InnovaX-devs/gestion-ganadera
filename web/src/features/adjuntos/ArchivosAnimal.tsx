import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { LoaderCircle, Plus } from 'lucide-react'
import { Button } from '@/components/Button'
import { FormError } from '@/components/FormError'
import { esErrorDeRed } from '@/features/offline/red'
import { GaleriaAdjuntos } from './GaleriaAdjuntos'
import { SelectorArchivos } from './SelectorArchivos'
import { useAdjuntosAnimal } from './queries'
import { subirArchivos } from './subida'
import { MAXIMO_ARCHIVOS } from './validacion'

interface Props {
  establecimientoId: string
  animal: { id: string; caravana: string }
  /** Puede agregar y eliminar archivos (el backend igual lo controla). */
  puedeEditar: boolean
}

/** "Fotos y archivos" de la ficha del animal: los que se le adjuntaron directamente (#43). */
export function ArchivosAnimal({ establecimientoId, animal, puedeEditar }: Props) {
  const { data, isPending, error } = useAdjuntosAnimal(establecimientoId, animal.id)
  const queryClient = useQueryClient()
  const [agregando, setAgregando] = useState(false)
  const [elegidos, setElegidos] = useState<File[]>([])
  const [subiendo, setSubiendo] = useState(false)
  const [problema, setProblema] = useState<string | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)

  const cantidad = data?.length ?? 0
  const lugar = Math.max(0, MAXIMO_ARCHIVOS - cantidad)

  const cerrar = () => {
    setAgregando(false)
    setElegidos([])
    setProblema(null)
  }

  async function subir() {
    setSubiendo(true)
    setProblema(null)
    setAviso(null)
    const resultado = await subirArchivos(
      establecimientoId,
      { tipo: 'ANIMAL', animalId: animal.id },
      elegidos,
      `Archivos de ${animal.caravana}`,
    )
    setSubiendo(false)
    await queryClient.invalidateQueries({ queryKey: ['adjuntos-animal'] })

    if (resultado.fallidos.length > 0) {
      // Lo que sí se subió ya no se vuelve a mandar: queda solo lo que falló
      const fallidos = new Set(resultado.fallidos.map((f) => f.nombre))
      setElegidos(elegidos.filter((f) => fallidos.has(f.name)))
      setProblema(resultado.fallidos.map((f) => `${f.nombre}: ${f.mensaje}`).join('\n'))
      return
    }
    if (resultado.paraDespues > 0) {
      setAviso(
        `${resultado.paraDespues === 1 ? 'Se guardó 1 archivo' : `Se guardaron ${resultado.paraDespues} archivos`} en este dispositivo. Se ${resultado.paraDespues === 1 ? 'sube' : 'suben'} solos cuando vuelva la señal.`,
      )
    }
    cerrar()
  }

  return (
    <section className="rounded-xl border border-stone-200 bg-white dark:border-stone-800 dark:bg-stone-900">
      <div className="flex items-center justify-between gap-3 px-5 pt-4 pb-3">
        <h2 className="text-sm font-semibold">
          Fotos y archivos
          {cantidad > 0 && <span className="font-normal text-stone-500"> ({cantidad})</span>}
        </h2>
        {puedeEditar && !agregando && (
          <Button variant="secondary" onClick={() => setAgregando(true)} disabled={lugar === 0}>
            <Plus className="size-4" />
            Agregar
          </Button>
        )}
      </div>

      <div className="px-5 pb-5">
        {aviso && (
          <p className="mb-3 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:bg-amber-950/30 dark:text-amber-200">
            {aviso}
          </p>
        )}

        {agregando && (
          <div className="mb-4 space-y-3 rounded-lg border border-stone-200 p-3 dark:border-stone-700">
            <FormError message={problema} />
            <SelectorArchivos
              archivos={elegidos}
              onChange={setElegidos}
              maximo={lugar}
              disabled={subiendo}
            />
            <div className="flex justify-end gap-2">
              <Button variant="secondary" onClick={cerrar} disabled={subiendo}>
                Cancelar
              </Button>
              <Button onClick={subir} loading={subiendo} disabled={elegidos.length === 0}>
                Subir {elegidos.length > 1 ? `${elegidos.length} archivos` : 'archivo'}
              </Button>
            </div>
          </div>
        )}

        {isPending && <LoaderCircle className="size-4 animate-spin text-stone-400" />}
        {error && (
          <p className="text-sm text-stone-500">
            {esErrorDeRed(error)
              ? 'Sin conexión: las fotos se ven cuando vuelva la señal.'
              : 'No se pudieron cargar los archivos.'}
          </p>
        )}
        {data && data.length === 0 && !agregando && (
          <p className="text-sm text-stone-500">
            Todavía no hay fotos ni archivos.
            {puedeEditar &&
              ' Podés sumar fotos del animal, de la caravana o un informe del veterinario.'}
          </p>
        )}
        {data && (
          <GaleriaAdjuntos
            establecimientoId={establecimientoId}
            adjuntos={data}
            puedeEliminar={puedeEditar}
          />
        )}
      </div>
    </section>
  )
}
