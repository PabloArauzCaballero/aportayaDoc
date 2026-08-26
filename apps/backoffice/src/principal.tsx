import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider, createRouter } from '@tanstack/react-router'
import { routeTree } from './arbolDeRutas.gen'
import { arrancarSimulado } from './simulado/servidor'

const router = createRouter({ routeTree })

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}

const contenedor = document.getElementById('raiz')
if (!contenedor) throw new Error('falta #raiz en index.html')

// El simulado arranca ANTES de dibujar: si la primera consulta sale mientras el
// trabajador todavia se registra, se va a la red de verdad y la pantalla muestra un
// error que no existe.
void arrancarSimulado().then(() => {
  createRoot(contenedor).render(
    <StrictMode>
      <RouterProvider router={router} />
    </StrictMode>,
  )
})
