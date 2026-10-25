import { Construction } from 'lucide-react'
import { PageHeader } from '@/components/PageHeader'

/** Página provisoria para las secciones que todavía no se implementaron. */
export function PlaceholderPage({ title }: { title: string }) {
  return (
    <>
      <PageHeader title={title} />
      <div className="grid place-items-center rounded-xl border border-dashed border-stone-300 bg-white px-6 py-16 text-center dark:border-stone-700 dark:bg-stone-900">
        <Construction className="size-8 text-stone-400" />
        <p className="mt-3 font-medium">En construcción</p>
        <p className="mt-1 text-sm text-stone-500">
          Esta sección se desarrolla en su issue correspondiente.
        </p>
      </div>
    </>
  )
}
