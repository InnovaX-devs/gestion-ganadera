import { Mail } from 'lucide-react'
import { useGuardarPreferencias, usePreferencias } from './queries'

/** Activar o desactivar el aviso diario por email (#34). Es por usuario, para todos sus campos. */
export function AvisosPorEmail() {
  const { data } = usePreferencias()
  const guardar = useGuardarPreferencias()
  if (!data) return null
  return (
    <section className="flex items-start gap-3 rounded-xl border border-stone-200 bg-white p-4 dark:border-stone-800 dark:bg-stone-900">
      <Mail className="mt-0.5 size-5 shrink-0 text-stone-400" />
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium">Avisos por email</p>
        <p className="text-xs text-stone-500">
          Un email el día que hay que avisar: tratamientos que se acercan o quedaron vencidos, y
          animales que terminan la carencia. Llega a quienes pueden registrar sanidad.
        </p>
      </div>
      <label className="flex shrink-0 cursor-pointer items-center gap-2 text-sm">
        <input
          type="checkbox"
          role="switch"
          checked={data.alertasEmail}
          disabled={guardar.isPending}
          onChange={(e) => guardar.mutate({ alertasEmail: e.target.checked })}
          className="accent-brand-600 size-4"
        />
        {data.alertasEmail ? 'Activados' : 'Desactivados'}
      </label>
    </section>
  )
}
