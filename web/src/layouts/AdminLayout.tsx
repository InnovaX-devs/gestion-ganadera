import { LogOut, Moon, Sun } from 'lucide-react'
import { Navigate, Outlet, useNavigate } from 'react-router'
import { RUTA_LOGIN_ADMIN } from '@/features/admin/admin-api'
import { sesionAdmin } from '@/features/admin/sesion'
import { useTheme } from '@/hooks/useTheme'
import { queryClient } from '@/lib/query-client'

/** Marco del panel de Innovax: sin el menú de la app, con su propia sesión. */
export function AdminLayout() {
  const navigate = useNavigate()
  const { isDark, toggle } = useTheme()
  const sesion = sesionAdmin.leer()
  if (!sesion) return <Navigate to={RUTA_LOGIN_ADMIN} replace />

  function salir() {
    sesionAdmin.borrar()
    queryClient.removeQueries({ queryKey: ['admin'] })
    navigate(RUTA_LOGIN_ADMIN, { replace: true })
  }

  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-30 border-b border-stone-200 bg-white/90 backdrop-blur dark:border-stone-800 dark:bg-stone-950/90">
        <div className="mx-auto flex h-16 max-w-6xl items-center gap-3 px-4">
          <img src="/favicon.svg" alt="" className="size-8" />
          <div className="min-w-0 flex-1 leading-tight">
            <p className="font-semibold tracking-tight">Panel de Innovax</p>
            <p className="truncate text-xs text-stone-500 dark:text-stone-400">{sesion.email}</p>
          </div>
          <button
            type="button"
            onClick={toggle}
            aria-label={isDark ? 'Tema claro' : 'Tema oscuro'}
            className="rounded-lg p-2 text-stone-500 hover:bg-stone-100 dark:hover:bg-stone-800"
          >
            {isDark ? <Sun className="size-5" /> : <Moon className="size-5" />}
          </button>
          <button
            type="button"
            onClick={salir}
            aria-label="Salir"
            className="flex items-center gap-2 rounded-lg px-3 py-2 text-sm text-stone-600 hover:bg-stone-100 dark:text-stone-300 dark:hover:bg-stone-800"
          >
            <LogOut className="size-4" />
            <span className="hidden sm:inline">Salir</span>
          </button>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6 sm:py-8">
        <Outlet />
      </main>
    </div>
  )
}
