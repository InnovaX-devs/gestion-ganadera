import { type FormEvent, useCallback, useRef, useState } from 'react'
import { Bluetooth, LoaderCircle, ScanLine, Usb } from 'lucide-react'
import { Button } from '@/components/Button'
import { useDismiss } from '@/hooks/useDismiss'
import { VELOCIDADES, hayBluetooth, haySerie } from './conexiones'
import { useLector } from './contexto'
import { formatearEid } from './lectura'

const ORIGEN = {
  teclado: 'modo teclado',
  serie: 'puerto serie',
  bluetooth: 'Bluetooth',
  simulada: 'simulada',
} as const

/** Encabezado: estado del lector de caravanas y cómo conectarlo. */
export function BotonLector() {
  const lector = useLector()
  const [abierto, setAbierto] = useState(false)
  const [velocidad, setVelocidad] = useState<number>(9600)
  const [prueba, setPrueba] = useState('982 000123456789')
  const ref = useRef<HTMLDivElement>(null)
  const cerrar = useCallback(() => setAbierto(false), [])
  useDismiss(ref, abierto, cerrar)

  const conectado = !!lector.conexion
  const serie = haySerie()
  const bluetooth = hayBluetooth()

  const simular = (e: FormEvent) => {
    e.preventDefault()
    setAbierto(false)
    lector.simular(prueba)
  }

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setAbierto((a) => !a)}
        aria-haspopup="dialog"
        aria-expanded={abierto}
        aria-label={
          conectado ? `Lector conectado: ${lector.conexion!.nombre}` : 'Lector de caravanas'
        }
        title={conectado ? `Lector conectado: ${lector.conexion!.nombre}` : 'Lector de caravanas'}
        className="focus-visible:ring-brand-300 relative grid size-9 place-items-center rounded-lg text-stone-600 outline-none hover:bg-stone-100 focus-visible:ring-2 dark:text-stone-300 dark:hover:bg-stone-800"
      >
        <ScanLine className="size-5" />
        {conectado && (
          <span
            aria-hidden
            className="absolute top-1 right-1 size-2 rounded-full bg-emerald-500 ring-2 ring-white dark:ring-stone-900"
          />
        )}
      </button>

      {abierto && (
        <div
          role="dialog"
          aria-label="Lector de caravanas"
          className="absolute right-0 z-30 mt-2 w-80 max-w-[calc(100vw-2rem)] rounded-xl border border-stone-200 bg-white p-4 text-sm shadow-lg dark:border-stone-700 dark:bg-stone-900"
        >
          <h2 className="font-semibold">Lector de caravanas</h2>
          <p className="mt-1 text-stone-600 dark:text-stone-400">
            Emparejá el bastón con este dispositivo como <b>teclado Bluetooth</b> y leé: no hace
            falta nada más. Abre la ficha del animal, o lo suma a la carga que tengas abierta.
          </p>

          {(serie || bluetooth) && (
            <div className="mt-4 border-t border-stone-100 pt-4 dark:border-stone-800">
              {conectado ? (
                <div className="flex items-center justify-between gap-2">
                  <span className="flex min-w-0 items-center gap-2">
                    <span className="size-2 shrink-0 rounded-full bg-emerald-500" />
                    <span className="truncate font-medium">{lector.conexion!.nombre}</span>
                  </span>
                  <Button variant="secondary" className="px-3 py-1.5" onClick={lector.desconectar}>
                    Desconectar
                  </Button>
                </div>
              ) : (
                <>
                  <p className="mb-2 text-xs text-stone-500">
                    ¿El bastón no escribe solo? Conectalo directo:
                  </p>
                  <div className="space-y-2">
                    {serie && (
                      <div className="flex gap-2">
                        <Button
                          variant="secondary"
                          className="flex-1 px-3 py-1.5"
                          disabled={lector.conectando}
                          onClick={() => lector.conectarSerie(velocidad)}
                        >
                          <Usb className="size-4" />
                          Cable o puerto serie
                        </Button>
                        <select
                          value={velocidad}
                          onChange={(e) => setVelocidad(Number(e.target.value))}
                          aria-label="Velocidad del puerto"
                          title="Velocidad (baudios). Si llegan caracteres raros, probá otra."
                          className="rounded-lg border border-stone-300 bg-white px-2 text-xs dark:border-stone-700 dark:bg-stone-900"
                        >
                          {VELOCIDADES.map((v) => (
                            <option key={v} value={v}>
                              {v}
                            </option>
                          ))}
                        </select>
                      </div>
                    )}
                    {bluetooth && (
                      <Button
                        variant="secondary"
                        className="w-full px-3 py-1.5"
                        disabled={lector.conectando}
                        onClick={lector.conectarBluetooth}
                      >
                        <Bluetooth className="size-4" />
                        Bluetooth
                      </Button>
                    )}
                  </div>
                  {lector.conectando && (
                    <p className="mt-2 flex items-center gap-2 text-xs text-stone-500">
                      <LoaderCircle className="size-3.5 animate-spin" />
                      Conectando…
                    </p>
                  )}
                </>
              )}
            </div>
          )}
          {!serie && !bluetooth && (
            <p className="mt-3 text-xs text-stone-500">
              En este dispositivo solo funciona el modo teclado.
            </p>
          )}

          {lector.ultima && (
            <p className="mt-4 border-t border-stone-100 pt-3 text-xs text-stone-500 dark:border-stone-800">
              Última lectura:{' '}
              <span className="font-medium text-stone-800 tabular-nums dark:text-stone-200">
                {formatearEid(lector.ultima.eid)}
              </span>
              {lector.ultima.kg !== undefined && ` · ${lector.ultima.kg} kg`} ·{' '}
              {lector.ultima.hora.toLocaleTimeString('es-AR', {
                hour: '2-digit',
                minute: '2-digit',
              })}{' '}
              ({ORIGEN[lector.ultima.origen]})
            </p>
          )}

          {import.meta.env.DEV && (
            <form
              onSubmit={simular}
              className="mt-4 flex gap-2 border-t border-dashed border-stone-200 pt-3 dark:border-stone-700"
            >
              <input
                value={prueba}
                onChange={(e) => setPrueba(e.target.value)}
                aria-label="Lectura de prueba"
                className="min-w-0 flex-1 rounded-lg border border-stone-300 bg-white px-2 py-1 text-xs dark:border-stone-700 dark:bg-stone-900"
              />
              <Button type="submit" variant="secondary" className="px-2.5 py-1 text-xs">
                Simular
              </Button>
            </form>
          )}
        </div>
      )}
    </div>
  )
}
