import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router'
import { AuthProvider } from './features/auth/AuthProvider'
// Lo antes posible: el aviso de "se puede instalar" puede llegar antes de dibujar la pantalla
import './features/pwa/instalacion'
import { registrarServiceWorker } from './lib/pwa'
import { queryClient } from './lib/query-client'
import { router } from './router'
import './index.css'

registrarServiceWorker()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
)
