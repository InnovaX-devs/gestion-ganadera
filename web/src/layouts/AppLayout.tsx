import { useState } from 'react'
import { NavLink, Outlet } from 'react-router'
import { ChevronDown, Menu, Moon, Sun, X } from 'lucide-react'
import { navigation } from '@/config/navigation'
import { useTheme } from '@/hooks/useTheme'

export function AppLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(false)

  return (
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
        <main className="flex-1 px-4 py-6 sm:px-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
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

      {/* Selector de establecimiento activo: se conecta en el issue de Establecimientos */}
      <button
        type="button"
        className="flex items-center gap-2 rounded-lg border border-stone-200 px-3 py-1.5 text-sm font-medium hover:bg-stone-50 dark:border-stone-700 dark:hover:bg-stone-800"
      >
        Sin establecimiento
        <ChevronDown className="size-4 text-stone-500" />
      </button>

      <div className="ml-auto flex items-center gap-2">
        <button
          type="button"
          onClick={toggle}
          aria-label={isDark ? 'Usar tema claro' : 'Usar tema oscuro'}
          className="rounded-md p-2 text-stone-600 hover:bg-stone-100 dark:text-stone-300 dark:hover:bg-stone-800"
        >
          {isDark ? <Sun className="size-5" /> : <Moon className="size-5" />}
        </button>
        <span className="bg-brand-600 grid size-8 place-items-center rounded-full text-sm font-semibold text-white">
          U
        </span>
      </div>
    </header>
  )
}
