import { Navigate, Outlet, useLocation } from 'react-router'
import { LoaderCircle } from 'lucide-react'
import { useAuth } from './useAuth'

function PantallaDeCarga() {
  return (
    <div className="grid min-h-dvh place-items-center">
      <LoaderCircle className="size-6 animate-spin text-stone-400" aria-label="Cargando" />
    </div>
  )
}

/** Rutas que requieren sesión. Si no hay, manda al login y recuerda a dónde se quería ir. */
export function RequireAuth() {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') return <PantallaDeCarga />
  if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: location }} />
  return <Outlet />
}

/**
 * Login y registro: si ya hay sesión no se muestran. Al loguearse, vuelve a la página que se
 * quería abrir antes de que pidiera el login.
 */
export function PublicOnly() {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') return <PantallaDeCarga />
  if (status === 'authenticated') {
    const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname
    return <Navigate to={from && from !== '/login' ? from : '/'} replace />
  }
  return <Outlet />
}
