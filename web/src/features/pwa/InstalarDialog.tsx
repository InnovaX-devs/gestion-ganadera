import { useState } from 'react'
import { Download, Share, SquarePlus, WifiOff } from 'lucide-react'
import { Button } from '@/components/Button'
import { Dialog } from '@/components/Dialog'
import { instalar } from './instalacion'
import { useInstalacion } from './useInstalacion'

/** Cómo instalar la app: con un botón (Chrome, Edge, Android) o con los pasos de Safari (iPhone). */
export function InstalarDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { forma, instalada } = useInstalacion()
  const [instalando, setInstalando] = useState(false)

  async function alInstalar() {
    setInstalando(true)
    try {
      if (await instalar()) onClose()
    } finally {
      setInstalando(false)
    }
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Instalar la app"
      description="Queda con su ícono en el celular o la computadora, se abre en su propia ventana y funciona sin conexión."
      size="sm"
    >
      <p className="flex items-start gap-2 rounded-lg bg-stone-100 px-3 py-2 text-sm text-stone-700 dark:bg-stone-800 dark:text-stone-300">
        <WifiOff className="mt-0.5 size-4 shrink-0" aria-hidden />
        En el campo, sin señal, podés seguir registrando eventos: se envían solos cuando vuelve la
        conexión.
      </p>

      {instalada && <p className="mt-4 text-sm">La app ya está instalada en este dispositivo.</p>}

      {forma === 'boton' && (
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>
            Ahora no
          </Button>
          <Button onClick={alInstalar} disabled={instalando}>
            <Download className="size-4" />
            Instalar
          </Button>
        </div>
      )}

      {forma === 'ios' && (
        <ol className="mt-4 grid gap-3 text-sm">
          <Paso numero={1}>
            Abrí esta página en <strong>Safari</strong>.
          </Paso>
          <Paso numero={2}>
            Tocá <Share className="inline size-4 align-text-bottom" aria-label="Compartir" />{' '}
            <strong>Compartir</strong>, abajo en el centro (o arriba en el iPad).
          </Paso>
          <Paso numero={3}>
            Elegí <SquarePlus className="inline size-4 align-text-bottom" aria-hidden />{' '}
            <strong>Agregar a pantalla de inicio</strong> y confirmá con <strong>Agregar</strong>.
          </Paso>
        </ol>
      )}

      {forma === 'no' && !instalada && (
        <p className="mt-4 text-sm text-stone-600 dark:text-stone-400">
          Este navegador no permite instalar la app. Probá con Chrome o Edge en Android o en la
          computadora, o con Safari en iPhone.
        </p>
      )}
    </Dialog>
  )
}

function Paso({ numero, children }: { numero: number; children: React.ReactNode }) {
  return (
    <li className="flex gap-3">
      <span className="bg-brand-100 text-brand-800 dark:bg-brand-900/50 dark:text-brand-200 grid size-6 shrink-0 place-items-center rounded-full text-xs font-semibold">
        {numero}
      </span>
      <span className="pt-0.5">{children}</span>
    </li>
  )
}
