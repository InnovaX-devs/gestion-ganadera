import { type ChangeEvent, type DragEvent, type ReactNode, useRef, useState } from 'react'
import { Link } from 'react-router'
import {
  ArrowLeft,
  CircleAlert,
  CircleCheck,
  Download,
  FileSpreadsheet,
  LoaderCircle,
  Lock,
  Upload,
} from 'lucide-react'
import { Button } from '@/components/Button'
import { EmptyState } from '@/components/EmptyState'
import { FormError } from '@/components/FormError'
import { PageHeader } from '@/components/PageHeader'
import { puede } from '@/features/establecimientos/permisos'
import { formatearFecha } from '@/features/eventos/fechas'
import { useEstablecimientoActivo } from '@/features/establecimientos/useEstablecimientoActivo'
import { type Campo, TITULO_DE } from '@/features/importacion/columnas'
import { PlanillaInvalida, descargarPlantilla, leerPlanilla } from '@/features/importacion/planilla'
import { useImportarAnimales } from '@/features/importacion/queries'
import type { FilaImportacion, ResultadoImportacion } from '@/features/importacion/types'
import { getErrorMessage } from '@/lib/api'

const ERRORES_VISIBLES = 200
const FILAS_DE_MUESTRA = 8

export function ImportarAnimalesPage() {
  const { activo, cargando } = useEstablecimientoActivo()
  if (cargando) return <Cargando />
  if (!activo || !puede(activo.rol, 'GESTIONAR_ANIMALES')) {
    return (
      <>
        <Volver />
        <EmptyState
          icono={Lock}
          titulo={
            activo
              ? 'No podés cargar animales en este establecimiento'
              : 'Primero creá un establecimiento'
          }
          texto={
            activo
              ? 'Pedile al dueño que te dé el rol de encargado.'
              : 'Los animales se importan a un establecimiento.'
          }
        />
      </>
    )
  }
  return (
    <Importador
      key={activo.id}
      establecimientoId={activo.id}
      establecimientoNombre={activo.nombre}
    />
  )
}

type Paso =
  | { tipo: 'elegir' }
  | { tipo: 'leyendo'; archivo: string }
  | { tipo: 'revisado'; archivo: string; filas: FilaImportacion[]; resultado: ResultadoImportacion }
  | { tipo: 'listo'; resultado: ResultadoImportacion }

function Importador({
  establecimientoId,
  establecimientoNombre,
}: {
  establecimientoId: string
  establecimientoNombre: string
}) {
  const [paso, setPaso] = useState<Paso>({ tipo: 'elegir' })
  const [error, setError] = useState<string | null>(null)
  const importar = useImportarAnimales(establecimientoId)

  async function revisar(archivo: File) {
    setError(null)
    setPaso({ tipo: 'leyendo', archivo: archivo.name })
    try {
      const filas = await leerPlanilla(archivo)
      const resultado = await importar.mutateAsync({ filas, confirmar: false })
      setPaso({ tipo: 'revisado', archivo: archivo.name, filas, resultado })
    } catch (e) {
      setError(e instanceof PlanillaInvalida ? e.message : getErrorMessage(e))
      setPaso({ tipo: 'elegir' })
    }
  }

  async function confirmar(filas: FilaImportacion[]) {
    setError(null)
    try {
      const resultado = await importar.mutateAsync({ filas, confirmar: true })
      setPaso({ tipo: 'listo', resultado })
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  const reiniciar = () => {
    setError(null)
    setPaso({ tipo: 'elegir' })
  }

  return (
    <>
      <Volver />
      <PageHeader
        title="Importar animales"
        description={`Cargá toda la hacienda de ${establecimientoNombre} desde una planilla de Excel.`}
      />

      <div className="mb-4">
        <FormError message={error} />
      </div>

      {paso.tipo === 'listo' ? (
        <Listo resultado={paso.resultado} onOtra={reiniciar} />
      ) : paso.tipo === 'revisado' ? (
        <Revision
          archivo={paso.archivo}
          filas={paso.filas}
          resultado={paso.resultado}
          importando={importar.isPending}
          onConfirmar={() => confirmar(paso.filas)}
          onOtro={reiniciar}
        />
      ) : (
        <Instrucciones
          leyendo={paso.tipo === 'leyendo' ? paso.archivo : null}
          onArchivo={revisar}
        />
      )}
    </>
  )
}

// ---------- Paso 1: plantilla y archivo ----------

function Instrucciones({
  leyendo,
  onArchivo,
}: {
  leyendo: string | null
  onArchivo: (archivo: File) => void
}) {
  const input = useRef<HTMLInputElement>(null)
  const [arrastrando, setArrastrando] = useState(false)
  const [descargando, setDescargando] = useState(false)

  const elegido = (e: ChangeEvent<HTMLInputElement>) => {
    const archivo = e.target.files?.[0]
    e.target.value = ''
    if (archivo) onArchivo(archivo)
  }
  const soltado = (e: DragEvent) => {
    e.preventDefault()
    setArrastrando(false)
    const archivo = e.dataTransfer.files[0]
    if (archivo) onArchivo(archivo)
  }

  return (
    <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
      <section className="rounded-xl border border-stone-200 bg-white p-5 dark:border-stone-800 dark:bg-stone-900">
        <h2 className="font-semibold">Cómo se hace</h2>
        <ol className="mt-3 space-y-3 text-sm text-stone-700 dark:text-stone-300">
          <PasoItem n={1}>
            Descargá la plantilla. Trae una hoja con lo que va en cada columna.
          </PasoItem>
          <PasoItem n={2}>
            Completala con una fila por animal. Solo la <b>caravana</b> y la <b>categoría</b> son
            obligatorias.
          </PasoItem>
          <PasoItem n={3}>
            Subila acá. Antes de guardar te mostramos qué filas tienen errores; no se carga nada
            hasta que confirmes.
          </PasoItem>
        </ol>
        <Button
          variant="secondary"
          className="mt-5"
          loading={descargando}
          onClick={async () => {
            setDescargando(true)
            try {
              await descargarPlantilla()
            } finally {
              setDescargando(false)
            }
          }}
        >
          <Download className="size-4" />
          Descargar plantilla
        </Button>
        <p className="mt-4 text-xs text-stone-500">
          ¿Ya tenés una planilla propia? También sirve, si la primera fila tiene los títulos
          (Caravana, Categoría, Sexo, Lote…). Los lotes y rodeos que no existan se crean solos.
        </p>
      </section>

      <button
        type="button"
        disabled={!!leyendo}
        onClick={() => input.current?.click()}
        onDragOver={(e) => {
          e.preventDefault()
          setArrastrando(true)
        }}
        onDragLeave={() => setArrastrando(false)}
        onDrop={soltado}
        className={`grid min-h-64 place-items-center rounded-xl border-2 border-dashed p-8 text-center transition ${
          arrastrando
            ? 'border-brand-500 bg-brand-50 dark:bg-brand-900/20'
            : 'hover:border-brand-400 border-stone-300 bg-white hover:bg-stone-50 dark:border-stone-700 dark:bg-stone-900 dark:hover:bg-stone-800/60'
        }`}
      >
        {leyendo ? (
          <div>
            <LoaderCircle className="text-brand-600 mx-auto size-8 animate-spin" />
            <p className="mt-3 font-medium">Revisando {leyendo}…</p>
          </div>
        ) : (
          <div>
            <span className="bg-brand-50 text-brand-600 dark:bg-brand-900/40 dark:text-brand-200 mx-auto grid size-12 place-items-center rounded-full">
              <Upload className="size-6" />
            </span>
            <p className="mt-4 font-medium">Arrastrá la planilla acá o tocá para elegirla</p>
            <p className="mt-1 text-sm text-stone-500">Excel (.xlsx), hasta 2000 animales</p>
          </div>
        )}
      </button>
      <input
        ref={input}
        type="file"
        accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        className="hidden"
        onChange={elegido}
        aria-label="Elegir planilla"
      />
    </div>
  )
}

function PasoItem({ n, children }: { n: number; children: ReactNode }) {
  return (
    <li className="flex gap-3">
      <span className="bg-brand-600 grid size-6 shrink-0 place-items-center rounded-full text-xs font-semibold text-white">
        {n}
      </span>
      <span className="pt-0.5">{children}</span>
    </li>
  )
}

// ---------- Paso 2: revisión ----------

function Revision({
  archivo,
  filas,
  resultado,
  importando,
  onConfirmar,
  onOtro,
}: {
  archivo: string
  filas: FilaImportacion[]
  resultado: ResultadoImportacion
  importando: boolean
  onConfirmar: () => void
  onOtro: () => void
}) {
  const { total, validas, errores, lotesNuevos, rodeosNuevos } = resultado
  const conErrores = total - validas
  const ok = errores.length === 0

  return (
    <div className="space-y-4">
      <section className="rounded-xl border border-stone-200 bg-white p-5 dark:border-stone-800 dark:bg-stone-900">
        <div className="flex flex-wrap items-center gap-x-6 gap-y-3">
          <div className="flex min-w-0 items-center gap-2 text-sm text-stone-500">
            <FileSpreadsheet className="size-4 shrink-0" />
            <span className="truncate">{archivo}</span>
          </div>
          <Cifra valor={total} texto={total === 1 ? 'animal' : 'animales'} />
          <Cifra valor={validas} texto={validas === 1 ? 'listo' : 'listos'} tono="ok" />
          {conErrores > 0 && (
            <Cifra
              valor={conErrores}
              texto={conErrores === 1 ? 'con errores' : 'con errores'}
              tono="error"
            />
          )}
        </div>

        {(lotesNuevos.length > 0 || rodeosNuevos.length > 0) && (
          <div className="mt-4 space-y-1 border-t border-stone-100 pt-4 text-sm dark:border-stone-800">
            {lotesNuevos.length > 0 && (
              <Nuevos
                titulo={lotesNuevos.length === 1 ? 'Lote nuevo' : 'Lotes nuevos'}
                nombres={lotesNuevos}
              />
            )}
            {rodeosNuevos.length > 0 && (
              <Nuevos
                titulo={rodeosNuevos.length === 1 ? 'Rodeo nuevo' : 'Rodeos nuevos'}
                nombres={rodeosNuevos}
              />
            )}
            <p className="text-xs text-stone-500">
              Se crean al importar. Si alguno está mal escrito, corregilo en la planilla.
            </p>
          </div>
        )}

        <div className="mt-5 flex flex-wrap gap-2">
          {ok ? (
            <Button onClick={onConfirmar} loading={importando}>
              <CircleCheck className="size-4" />
              Importar {total} {total === 1 ? 'animal' : 'animales'}
            </Button>
          ) : null}
          <Button variant={ok ? 'secondary' : 'primary'} onClick={onOtro} disabled={importando}>
            <Upload className="size-4" />
            {ok ? 'Elegir otro archivo' : 'Subir la planilla corregida'}
          </Button>
        </div>
      </section>

      {ok ? <Muestra filas={filas} /> : <Errores errores={errores} />}
    </div>
  )
}

function Cifra({ valor, texto, tono }: { valor: number; texto: string; tono?: 'ok' | 'error' }) {
  const color =
    tono === 'ok'
      ? 'text-emerald-700 dark:text-emerald-400'
      : tono === 'error'
        ? 'text-red-700 dark:text-red-400'
        : ''
  return (
    <div className="flex items-baseline gap-1.5">
      <span className={`text-2xl font-semibold tabular-nums ${color}`}>{valor}</span>
      <span className="text-sm text-stone-500">{texto}</span>
    </div>
  )
}

function Nuevos({ titulo, nombres }: { titulo: string; nombres: string[] }) {
  return (
    <p>
      <span className="text-stone-500">{titulo}: </span>
      {nombres.join(', ')}
    </p>
  )
}

function Errores({ errores }: { errores: ResultadoImportacion['errores'] }) {
  const visibles = errores.slice(0, ERRORES_VISIBLES)
  return (
    <section className="overflow-hidden rounded-xl border border-red-200 bg-white dark:border-red-900/60 dark:bg-stone-900">
      <div className="flex items-start gap-2 border-b border-red-100 bg-red-50 px-5 py-3 text-sm text-red-800 dark:border-red-900/60 dark:bg-red-950/40 dark:text-red-200">
        <CircleAlert className="mt-0.5 size-4 shrink-0" />
        <p>
          Hay {errores.length} {errores.length === 1 ? 'problema' : 'problemas'} para corregir. No
          se importó nada: arreglá la planilla y volvé a subirla.
        </p>
      </div>
      <div className="max-h-[28rem] overflow-auto">
        <table className="w-full text-left text-sm">
          <thead className="sticky top-0 bg-stone-50 text-xs text-stone-500 uppercase dark:bg-stone-900">
            <tr>
              <th className="w-16 px-5 py-2.5 font-medium">Fila</th>
              <th className="w-44 px-3 py-2.5 font-medium">Columna</th>
              <th className="px-3 py-2.5 font-medium">Problema</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-stone-100 dark:divide-stone-800">
            {visibles.map((e, i) => (
              <tr key={i}>
                <td className="px-5 py-2.5 font-medium tabular-nums">{e.fila}</td>
                <td className="px-3 py-2.5 text-stone-600 dark:text-stone-400">
                  {e.campo ? (TITULO_DE[e.campo as Campo] ?? e.campo) : '—'}
                </td>
                <td className="px-3 py-2.5">{e.mensaje}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {errores.length > visibles.length && (
        <p className="border-t border-stone-100 px-5 py-3 text-sm text-stone-500 dark:border-stone-800">
          Y {errores.length - visibles.length} más. Corregí estos y volvé a subirla para ver el
          resto.
        </p>
      )}
    </section>
  )
}

const COLUMNAS_MUESTRA: Campo[] = [
  'caravana',
  'categoria',
  'sexo',
  'fechaNacimiento',
  'lote',
  'rodeo',
  'madre',
]

function Muestra({ filas }: { filas: FilaImportacion[] }) {
  return (
    <section className="overflow-hidden rounded-xl border border-stone-200 bg-white dark:border-stone-800 dark:bg-stone-900">
      <h2 className="px-5 pt-4 pb-3 text-sm font-semibold">
        Así se ven las primeras filas
        <span className="ml-2 font-normal text-stone-500">
          {filas.length > FILAS_DE_MUESTRA && `${FILAS_DE_MUESTRA} de ${filas.length}`}
        </span>
      </h2>
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm whitespace-nowrap">
          <thead className="bg-stone-50 text-xs text-stone-500 uppercase dark:bg-stone-800/50">
            <tr>
              <th className="px-5 py-2.5 font-medium">Fila</th>
              {COLUMNAS_MUESTRA.map((c) => (
                <th key={c} className="px-3 py-2.5 font-medium">
                  {TITULO_DE[c]}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-stone-100 dark:divide-stone-800">
            {filas.slice(0, FILAS_DE_MUESTRA).map((f) => (
              <tr key={f.fila}>
                <td className="px-5 py-2.5 text-stone-500 tabular-nums">{f.fila}</td>
                {COLUMNAS_MUESTRA.map((c) => (
                  <td key={c} className="px-3 py-2.5">
                    {(c === 'fechaNacimiento' ? mostrarFecha(f[c]) : f[c]) ?? (
                      <span className="text-stone-300 dark:text-stone-600">—</span>
                    )}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}

/** "2020-09-10" o "10/09/2020" → "10 sept 2020". Si no se entiende, se muestra como vino. */
function mostrarFecha(valor: string | undefined) {
  if (!valor) return valor
  const dma = /^(\d{1,2})[/-](\d{1,2})[/-](\d{4})$/.exec(valor)
  const iso = dma ? `${dma[3]}-${dma[2].padStart(2, '0')}-${dma[1].padStart(2, '0')}` : valor
  if (!/^\d{4}-\d{2}-\d{2}$/.test(iso) || Number.isNaN(Date.parse(iso))) return valor
  return formatearFecha(iso)
}

// ---------- Paso 3: listo ----------

function Listo({ resultado, onOtra }: { resultado: ResultadoImportacion; onOtra: () => void }) {
  const { creados, lotesNuevos, rodeosNuevos } = resultado
  const extra = [
    lotesNuevos.length > 0 &&
      `${lotesNuevos.length} ${lotesNuevos.length === 1 ? 'lote nuevo' : 'lotes nuevos'}`,
    rodeosNuevos.length > 0 &&
      `${rodeosNuevos.length} ${rodeosNuevos.length === 1 ? 'rodeo nuevo' : 'rodeos nuevos'}`,
  ].filter(Boolean)
  return (
    <EmptyState
      icono={CircleCheck}
      titulo={`Se importaron ${creados} ${creados === 1 ? 'animal' : 'animales'}`}
      texto={extra.length > 0 ? `También se crearon ${extra.join(' y ')}.` : undefined}
      accion={
        <div className="flex flex-wrap justify-center gap-2">
          <Link
            to="/animales"
            className="bg-brand-600 hover:bg-brand-700 inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium text-white"
          >
            Ver animales
          </Link>
          <Button variant="secondary" onClick={onOtra}>
            Importar otra planilla
          </Button>
        </div>
      }
    />
  )
}

function Volver() {
  return (
    <Link
      to="/animales"
      className="mb-4 inline-flex items-center gap-1.5 text-sm text-stone-500 hover:text-stone-800 dark:hover:text-stone-200"
    >
      <ArrowLeft className="size-4" />
      Animales
    </Link>
  )
}

function Cargando() {
  return (
    <div className="grid place-items-center py-16">
      <LoaderCircle className="size-6 animate-spin text-stone-400" />
    </div>
  )
}
