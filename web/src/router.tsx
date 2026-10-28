import { createBrowserRouter } from 'react-router'
import { allNavItems } from '@/config/navigation'
import { PublicOnly, RequireAuth } from '@/features/auth/RouteGuards'
import { AppLayout } from '@/layouts/AppLayout'
import { InicioPage } from '@/pages/InicioPage'
import { LoginPage } from '@/pages/LoginPage'
import { NotFoundPage } from '@/pages/NotFoundPage'
import { PlaceholderPage } from '@/pages/PlaceholderPage'
import { RegisterPage } from '@/pages/RegisterPage'

// Secciones del menú que todavía no tienen página propia: se reemplazan a medida que se implementan.
const placeholderRoutes = allNavItems
  .filter((item) => item.path !== '/')
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
          ...placeholderRoutes,
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
])
