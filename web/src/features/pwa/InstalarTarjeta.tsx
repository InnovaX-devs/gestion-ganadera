import { useState } from 'react'
import { Download, X } from 'lucide-react'
import { InstalarDialog } from './InstalarDialog'
import { useInstalacion } from './useInstalacion'

const CLAVE_DESCARTADA = 'instalar_app_descartada'

function leerDescartada() {
  try {
    return localStorage.getItem(CLAVE_DESCARTADA) === 'si'
  } catch {
    return false
  }
}

/**
 * Invitación a instalar la app en el inicio. Aparece solo si este navegador lo permite y todavía no
 * está instalada; con la X no vuelve a aparecer en este dispositivo (sigue en el menú del usuario).
 */
export function InstalarTarjeta() {
  const { forma } = useInstalacion()
  const [descartada, setDescartada] = useState(leerDescartada)
  const [abierto, setAbierto] = useState(false)

  if (forma === 'no' || descartada) return null

  function descartar() {
    setDescartada(true)
    try {
      localStorage.setItem(CLAVE_DESCARTADA, 'si')
    } catch {
      /* sin persistencia */
    }
  }

  return (
    <section className="border-brand-200 bg-brand-50 dark:border-brand-900 dark:bg-brand-900/20 flex items-center gap-3 rounded-xl border p-4">
      <span className="bg-brand-600 grid size-10 shrink-0 place-items-center rounded-lg text-white">
        <Download className="size-5" />
      </span>
      <div className="min-w-0 flex-1">
        <h2 className="text-sm font-semibold">Instalá la app en este dispositivo</h2>
        <p className="text-xs text-stone-600 dark:text-stone-400">
          Para abrirla con un toque y registrar eventos en el campo, aunque no haya señal.
        </p>
      </div>
      <button
        type="button"
        onClick={() => setAbierto(true)}
        className="bg-brand-600 hover:bg-brand-700 shrink-0 rounded-lg px-3 py-2 text-sm font-medium text-white"
      >
        {forma === 'ios' ? 'Ver cómo' : 'Instalar'}
      </button>
      <button
        type="button"
        onClick={descartar}
        aria-label="No mostrar más"
        className="shrink-0 rounded-md p-1 text-stone-400 hover:bg-stone-200/60 hover:text-stone-600 dark:hover:bg-stone-800"
      >
        <X className="size-4" />
      </button>
      <InstalarDialog open={abierto} onClose={() => setAbierto(false)} />
    </section>
  )
}
