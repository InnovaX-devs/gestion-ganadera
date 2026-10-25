import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <div className="grid min-h-[60vh] place-items-center text-center">
      <div>
        <p className="text-brand-600 text-sm font-semibold">404</p>
        <h1 className="mt-2 text-2xl font-semibold">Página no encontrada</h1>
        <Link
          to="/"
          className="bg-brand-600 hover:bg-brand-700 mt-6 inline-block rounded-lg px-4 py-2 text-sm font-medium text-white"
        >
          Volver al inicio
        </Link>
      </div>
    </div>
  )
}
