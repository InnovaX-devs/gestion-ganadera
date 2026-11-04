import { createBrowserRouter, Navigate } from 'react-router'
import { allNavItems } from '@/config/navigation'
import { PublicOnly, RequireAuth } from '@/features/auth/RouteGuards'
import { AppLayout } from '@/layouts/AppLayout'
import { AgrupacionesPage } from '@/pages/AgrupacionesPage'
import { ImportarAnimalesPage } from '@/pages/ImportarAnimalesPage'
import { ActivarCuentaPage } from '@/pages/ActivarCuentaPage'
import { OlvideContrasenaPage } from '@/pages/OlvideContrasenaPage'
import { RestablecerContrasenaPage } from '@/pages/RestablecerContrasenaPage'
import { AdminLayout } from '@/layouts/AdminLayout'
import { AdminLoginPage } from '@/pages/AdminLoginPage'
import { AdminPage } from '@/pages/AdminPage'
import { PlanPage } from '@/pages/PlanPage'
import { AnimalPage } from '@/pages/AnimalPage'
import { AnimalesPage } from '@/pages/AnimalesPage'
import { CalendarioSanitarioPage } from '@/pages/CalendarioSanitarioPage'
import { EstablecimientosPage } from '@/pages/EstablecimientosPage'
import { AceptarInvitacionPage } from '@/pages/AceptarInvitacionPage'
import { InicioPage } from '@/pages/InicioPage'
import { InvitacionesPage } from '@/pages/InvitacionesPage'
import { LoginPage } from '@/pages/LoginPage'
import { MovimientosPage } from '@/pages/MovimientosPage'
import { NacimientosPage } from '@/pages/NacimientosPage'
import { ObservacionesPage } from '@/pages/ObservacionesPage'
import { NotFoundPage } from '@/pages/NotFoundPage'
import { PesajesPage } from '@/pages/PesajesPage'
import { PlaceholderPage } from '@/pages/PlaceholderPage'
import { RegisterPage } from '@/pages/RegisterPage'
import { ReportesPage } from '@/pages/ReportesPage'
import { TratamientosPage } from '@/pages/TratamientosPage'
import { VentasPage } from '@/pages/VentasPage'

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
  '/movimientos',
  '/ventas',
  '/importar',
  '/reportes',
  '/calendario-sanitario',
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
  // Link para elegir la contraseña de una cuenta creada por el equipo de Innovax (#35)
  { path: '/activar/:token', element: <ActivarCuentaPage /> },
  // "Olvidé mi contraseña": se abren con o sin sesión (el link puede llegar estando logueado)
  { path: '/olvide-contrasena', element: <OlvideContrasenaPage /> },
  { path: '/restablecer/:token', element: <RestablecerContrasenaPage /> },
  // Panel del equipo de Innovax (#35): login y sesión propios, aparte de las cuentas de clientes
  { path: '/admin/login', element: <AdminLoginPage /> },
  { path: '/admin', element: <AdminLayout />, children: [{ index: true, element: <AdminPage /> }] },
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
          { path: 'animales/importar', element: <ImportarAnimalesPage /> },
          // El menú tiene "Importar" aparte: por ahora solo se importan animales
          { path: 'importar', element: <Navigate to="/animales/importar" replace /> },
          { path: 'animales/:animalId', element: <AnimalPage /> },
          { path: 'observaciones', element: <ObservacionesPage /> },
          { path: 'nacimientos', element: <NacimientosPage /> },
          { path: 'tratamientos', element: <TratamientosPage /> },
          { path: 'pesajes', element: <PesajesPage /> },
          { path: 'movimientos', element: <MovimientosPage /> },
          { path: 'ventas', element: <VentasPage /> },
          { path: 'reportes', element: <ReportesPage /> },
          { path: 'calendario-sanitario', element: <CalendarioSanitarioPage /> },
          ...placeholderRoutes,
          { path: 'plan', element: <PlanPage /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
])
