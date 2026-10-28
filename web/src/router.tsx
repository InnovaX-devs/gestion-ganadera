import { createBrowserRouter } from 'react-router'
import { allNavItems } from '@/config/navigation'
import { PublicOnly, RequireAuth } from '@/features/auth/RouteGuards'
import { AppLayout } from '@/layouts/AppLayout'
import { EstablecimientosPage } from '@/pages/EstablecimientosPage'
import { InicioPage } from '@/pages/InicioPage'
import { LoginPage } from '@/pages/LoginPage'
import { NotFoundPage } from '@/pages/NotFoundPage'
import { PlaceholderPage } from '@/pages/PlaceholderPage'
import { RegisterPage } from '@/pages/RegisterPage'

// Páginas ya implementadas. Al terminar una sección, agregala acá y a `children` de abajo.
const IMPLEMENTADAS = new Set(['/', '/establecimientos'])

// Secciones del menú que todavía no tienen página propia
const placeholderRoutes = allNavItems
  .filter((item) => !IMPLEMENTADAS.has(item.path))
  .map((item) => ({
    path: item.path.slice(1),
    element: <PlaceholderPage title={item.label} />,
  }))

export const router = createBrowserRouter([
  // Públicas: solo sin sesión
  {
    element: <PublicOnly />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/registro', element: <RegisterPage /> },
    ],
  },
  // Privadas: requieren sesión
  {
    element: <RequireAuth />,
    children: [
      {
        path: '/',
        element: <AppLayout />,
        children: [
          { index: true, element: <InicioPage /> },
          { path: 'establecimientos', element: <EstablecimientosPage /> },
          ...placeholderRoutes,
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
])
