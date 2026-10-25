import { useEffect, useState } from 'react'
import { CircleCheck, CircleX, LoaderCircle } from 'lucide-react'
import { PageHeader } from '@/components/PageHeader'
import { api, getErrorMessage } from '@/lib/api'

type Estado = { tipo: 'cargando' } | { tipo: 'ok' } | { tipo: 'error'; mensaje: string }

export function InicioPage() {
  return (
    <>
      <PageHeader title="Inicio" description="Resumen del establecimiento." />
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        <EstadoBackend />
        <Tarjeta titulo="Stock total" texto="Se completa en el milestone M4 - Dashboard." />
        <Tarjeta
          titulo="Referencias de mercado"
          texto="Se completa en el milestone M4 - Dashboard."
        />
      </div>
    </>
  )
}

function Tarjeta({ titulo, texto }: { titulo: string; texto: string }) {
  return (
    <section className="rounded-xl border border-stone-200 bg-white p-5 dark:border-stone-800 dark:bg-stone-900">
      <h2 className="text-sm font-medium text-stone-500">{titulo}</h2>
      <p className="mt-2 text-sm text-stone-600 dark:text-stone-400">{texto}</p>
    </section>
  )
}

/** Verifica la conexión con el backend (GET /api/health). Útil para validar el setup. */
function EstadoBackend() {
  const [estado, setEstado] = useState<Estado>({ tipo: 'cargando' })

  useEffect(() => {
    let activo = true
    api
      .get('/health')
      .then(() => activo && setEstado({ tipo: 'ok' }))
      .catch((e) => activo && setEstado({ tipo: 'error', mensaje: getErrorMessage(e) }))
    return () => {
      activo = false
    }
  }, [])

  return (
    <section className="rounded-xl border border-stone-200 bg-white p-5 dark:border-stone-800 dark:bg-stone-900">
      <h2 className="text-sm font-medium text-stone-500">Conexión con el servidor</h2>
      <div className="mt-2 flex items-center gap-2 text-sm">
        {estado.tipo === 'cargando' && (
          <>
            <LoaderCircle className="size-4 animate-spin text-stone-400" /> Verificando…
          </>
        )}
        {estado.tipo === 'ok' && (
          <>
            <CircleCheck className="size-4 text-emerald-600" /> Backend y base de datos OK
          </>
        )}
        {estado.tipo === 'error' && (
          <>
            <CircleX className="size-4 text-red-600" /> {estado.mensaje}
          </>
        )}
      </div>
    </section>
  )
}
