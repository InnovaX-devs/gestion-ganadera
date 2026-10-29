import { createBrowserRouter } from 'react-router'
import { allNavItems } from '@/config/navigation'
import { PublicOnly, RequireAuth } from '@/features/auth/RouteGuards'
import { AppLayout } from '@/layouts/AppLayout'
import { AgrupacionesPage } from '@/pages/AgrupacionesPage'
import { AnimalPage } from '@/pages/AnimalPage'
import { AnimalesPage } from '@/pages/AnimalesPage'
import { EstablecimientosPage } from '@/pages/EstablecimientosPage'
import { AceptarInvitacionPage } from '@/pages/AceptarInvitacionPage'
import { InicioPage } from '@/pages/InicioPage'
import { InvitacionesPage } from '@/pages/InvitacionesPage'
import { LoginPage } from '@/pages/LoginPage'
import { NacimientosPage } from '@/pages/NacimientosPage'
import { ObservacionesPage } from '@/pages/ObservacionesPage'
import { NotFoundPage } from '@/pages/NotFoundPage'
import { PesajesPage } from '@/pages/PesajesPage'
import { PlaceholderPage } from '@/pages/PlaceholderPage'
import { RegisterPage } from '@/pages/RegisterPage'
import { TratamientosPage } from '@/pages/TratamientosPage'

// Páginas ya implementadas. Al terminar una sección, agregala acá y a `children` de abajo.
const IMPLEMENTADAS = new Set([
  '/',
  '/establecimientos',
  '/lotes',
  '/rodeos',
  '/animales',
  '/invitaciones',
  '/observaciones',
  '/nacimientos',
  '/tratamientos',
  '/pesajes',
])

// Secciones del menú que todavía no tienen página propia
const placeholderRoutes = allNavItems
  .filter((item) => !IMPLEMENTADAS.has(item.path))
  .map((item) => ({
    path: item.path.slice(1),
    element: <PlaceholderPage title={item.label} />,
  }))

export const router = createBrowserRouter([
  // El link de una invitación se abre con o sin sesión (la página decide qué mostrar)
  { path: '/invitacion/:token', element: <AceptarInvitacionPage /> },
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
          // key: al pasar de lotes a rodeos la página arranca de cero
          { path: 'lotes', element: <AgrupacionesPage key="lotes" tipo="lotes" /> },
          { path: 'rodeos', element: <AgrupacionesPage key="rodeos" tipo="rodeos" /> },
          { path: 'animales', element: <AnimalesPage /> },
          { path: 'invitaciones', element: <InvitacionesPage /> },
          { path: 'animales/:animalId', element: <AnimalPage /> },
          { path: 'observaciones', element: <ObservacionesPage /> },
          { path: 'nacimientos', element: <NacimientosPage /> },
          { path: 'tratamientos', element: <TratamientosPage /> },
          { path: 'pesajes', element: <PesajesPage /> },
          ...placeholderRoutes,
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
])
