import { useCallback, useRef, useState } from 'react'
import { Link, NavLink, Outlet } from 'react-router'
import { BadgeCheck, Download, LogOut, Menu, Moon, Sun, X } from 'lucide-react'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { navigation } from '@/config/navigation'
import { useAuth } from '@/features/auth/useAuth'
import { EstablecimientoActivoProvider } from '@/features/establecimientos/EstablecimientoActivoProvider'
import { EstablecimientoSelector } from '@/features/establecimientos/EstablecimientoSelector'
import { BotonLector } from '@/features/lector/BotonLector'
import { LectorProvider } from '@/features/lector/LectorProvider'
import { AvisoPlan } from '@/features/plan/AvisoPlan'
import { AvisoSinConexion } from '@/features/offline/AvisoSinConexion'
import { usePendientes } from '@/features/offline/hooks'
import { IndicadorSync } from '@/features/offline/IndicadorSync'
import { SincronizacionAutomatica } from '@/features/offline/SincronizacionAutomatica'
import { InstalarDialog } from '@/features/pwa/InstalarDialog'
import { useInstalacion } from '@/features/pwa/useInstalacion'
import { useDismiss } from '@/hooks/useDismiss'
import { useTheme } from '@/hooks/useTheme'

export function AppLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(false)

  return (
    <EstablecimientoActivoProvider>
      <LectorProvider>
        <SincronizacionAutomatica />
        <div className="min-h-dvh lg:grid lg:grid-cols-[16rem_1fr]">
          {/* Fondo oscuro del menú en celular */}
          {sidebarOpen && (
            <button
              type="button"
              aria-label="Cerrar menú"
              className="fixed inset-0 z-30 bg-stone-950/40 lg:hidden"
              onClick={() => setSidebarOpen(false)}
            />
          )}

          <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />

          <div className="flex min-w-0 flex-col">
            <Header onOpenMenu={() => setSidebarOpen(true)} />
            <AvisoSinConexion />
            <AvisoPlan />
            <main className="flex-1 px-4 py-6 sm:px-6 lg:px-8">
              <Outlet />
            </main>
          </div>
        </div>
      </LectorProvider>
    </EstablecimientoActivoProvider>
  )
}

function Sidebar({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <aside
      className={`fixed inset-y-0 left-0 z-40 flex w-64 flex-col border-r border-stone-200 bg-white transition-transform lg:sticky lg:top-0 lg:h-dvh lg:translate-x-0 dark:border-stone-800 dark:bg-stone-900 ${
        open ? 'translate-x-0' : '-translate-x-full'
      }`}
    >
      <div className="flex h-16 shrink-0 items-center justify-between px-5">
        <span className="flex items-center gap-2 font-semibold tracking-tight">
          <img src="/favicon.svg" alt="" className="size-7" />
          Gestión Ganadera
        </span>
        <button
          type="button"
          onClick={onClose}
          aria-label="Cerrar menú"
          className="rounded-md p-1 text-stone-500 hover:bg-stone-100 lg:hidden dark:hover:bg-stone-800"
        >
          <X className="size-5" />
        </button>
      </div>

      <nav className="flex-1 overflow-y-auto px-3 pb-6">
        {navigation.map((section, i) => (
          <div key={section.title ?? i} className="mt-4 first:mt-1">
            {section.title && (
              <p className="px-2 pb-1 text-xs font-medium tracking-wide text-stone-500 uppercase">
                {section.title}
              </p>
            )}
            <ul className="space-y-0.5">
              {section.items.map(({ label, path, icon: Icon }) => (
                <li key={path}>
                  <NavLink
                    to={path}
                    end={path === '/'}
                    onClick={onClose}
                    className={({ isActive }) =>
                      `flex items-center gap-3 rounded-lg px-2 py-2 text-sm transition-colors ${
                        isActive
                          ? 'bg-brand-50 text-brand-700 dark:bg-brand-900/40 dark:text-brand-200 font-medium'
                          : 'text-stone-700 hover:bg-stone-100 dark:text-stone-300 dark:hover:bg-stone-800'
                      }`
                    }
                  >
                    <Icon className="size-4.5 shrink-0" />
                    {label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </nav>
    </aside>
  )
}

function Header({ onOpenMenu }: { onOpenMenu: () => void }) {
  const { isDark, toggle } = useTheme()

  return (
    <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-stone-200 bg-white/80 px-4 backdrop-blur sm:px-6 lg:px-8 dark:border-stone-800 dark:bg-stone-900/80">
      <button
        type="button"
        onClick={onOpenMenu}
        aria-label="Abrir menú"
        className="rounded-md p-1.5 text-stone-600 hover:bg-stone-100 lg:hidden dark:text-stone-300 dark:hover:bg-stone-800"
      >
        <Menu className="size-5" />
      </button>

      <EstablecimientoSelector />

      <div className="ml-auto flex items-center gap-2">
        <button
          type="button"
          onClick={toggle}
          aria-label={isDark ? 'Usar tema claro' : 'Usar tema oscuro'}
          className="rounded-md p-2 text-stone-600 hover:bg-stone-100 dark:text-stone-300 dark:hover:bg-stone-800"
        >
          {isDark ? <Sun className="size-5" /> : <Moon className="size-5" />}
        </button>
        <BotonLector />
        <IndicadorSync />
        <UserMenu />
      </div>
    </header>
  )
}

function UserMenu() {
  const { usuario, logout } = useAuth()
  const { forma } = useInstalacion()
  const pendientes = usePendientes().length
  const [abierto, setAbierto] = useState(false)
  const [instalarAbierto, setInstalarAbierto] = useState(false)
  const [confirmarSalida, setConfirmarSalida] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const cerrar = useCallback(() => setAbierto(false), [])
  useDismiss(ref, abierto, cerrar)

  if (!usuario) return null
  const iniciales = `${usuario.nombre[0] ?? ''}${usuario.apellido?.[0] ?? ''}`.toUpperCase()

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setAbierto((a) => !a)}
        aria-haspopup="menu"
        aria-expanded={abierto}
        aria-label="Menú de usuario"
        className="bg-brand-600 focus-visible:ring-brand-300 grid size-8 place-items-center rounded-full text-sm font-semibold text-white outline-none focus-visible:ring-2"
      >
        {iniciales}
      </button>

      {abierto && (
        <div
          role="menu"
          className="absolute right-0 mt-2 w-64 overflow-hidden rounded-xl border border-stone-200 bg-white shadow-lg dark:border-stone-800 dark:bg-stone-900"
        >
          <div className="border-b border-stone-200 px-4 py-3 dark:border-stone-800">
            <p className="truncate text-sm font-medium">
              {usuario.nombre} {usuario.apellido}
            </p>
            <p className="truncate text-xs text-stone-500">{usuario.email}</p>
            <p className="mt-1 truncate text-xs text-stone-500">{usuario.organizacionNombre}</p>
          </div>
          <Link
            to="/plan"
            role="menuitem"
            onClick={() => setAbierto(false)}
            className="flex w-full items-center gap-2 px-4 py-2.5 text-left text-sm text-stone-700 hover:bg-stone-100 dark:text-stone-300 dark:hover:bg-stone-800"
          >
            <BadgeCheck className="size-4" />
            Mi plan
          </Link>
          {forma !== 'no' && (
            <button
              type="button"
              role="menuitem"
              onClick={() => {
                setAbierto(false)
                setInstalarAbierto(true)
              }}
              className="flex w-full items-center gap-2 px-4 py-2.5 text-left text-sm text-stone-700 hover:bg-stone-100 dark:text-stone-300 dark:hover:bg-stone-800"
            >
              <Download className="size-4" />
              Instalar app
            </button>
          )}
          <button
            type="button"
            role="menuitem"
            // Cerrar sesión borra lo guardado en el dispositivo: si hay cargas sin enviar, se avisa
            onClick={() =>
              pendientes > 0 ? (setAbierto(false), setConfirmarSalida(true)) : logout()
            }
            className="flex w-full items-center gap-2 px-4 py-2.5 text-left text-sm text-stone-700 hover:bg-stone-100 dark:text-stone-300 dark:hover:bg-stone-800"
          >
            <LogOut className="size-4" />
            Cerrar sesión
          </button>
        </div>
      )}
      <InstalarDialog open={instalarAbierto} onClose={() => setInstalarAbierto(false)} />
      <ConfirmDialog
        open={confirmarSalida}
        title="Hay cargas sin enviar"
        description={`${pendientes === 1 ? 'Hay 1 carga que todavía no se envió' : `Hay ${pendientes} cargas que todavía no se enviaron`} al servidor. Si cerrás la sesión se pierden. Conectate a internet y esperá a que se envíen antes de salir.`}
        confirmLabel="Cerrar sesión igual"
        onConfirm={() => {
          setConfirmarSalida(false)
          logout()
        }}
        onClose={() => setConfirmarSalida(false)}
      />
    </div>
  )
}
