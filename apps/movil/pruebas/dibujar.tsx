import type { ReactElement, ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react-native'

/**
 * Un cliente de consultas nuevo por prueba. Compartirlo entre pruebas comparte
 * su cache, y una prueba que pasa por lo que dejo la anterior no prueba nada.
 */
export async function dibujar(elemento: ReactElement) {
  const cliente = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  })
  const Envoltorio = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={cliente}>{children}</QueryClientProvider>
  )
  // `render` es asincrono desde RNTL 14: React 19 monta en concurrente y la
  // prueba tiene que esperar a que el arbol exista antes de consultarlo.
  return render(elemento, { wrapper: Envoltorio })
}
