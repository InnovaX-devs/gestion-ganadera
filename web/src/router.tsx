import { createBrowserRouter } from 'react-router'
import { allNavItems } from '@/config/navigation'
import { AppLayout } from '@/layouts/AppLayout'
import { InicioPage } from '@/pages/InicioPage'
import { NotFoundPage } from '@/pages/NotFoundPage'
import { PlaceholderPage } from '@/pages/PlaceholderPage'

// Secciones del menú que todavía no tienen página propia: se reemplazan a medida que se implementan.
const placeholderRoutes = allNavItems
  .filter((item) => item.path !== '/')
  .map((item) => ({
    path: item.path.slice(1),
    element: <PlaceholderPage title={item.label} />,
  }))

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <InicioPage /> },
      ...placeholderRoutes,
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
