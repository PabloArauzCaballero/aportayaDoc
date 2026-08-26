import type { ReactElement, ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react-native'

/**
 * Un cliente de consultas nuevo por prueba. Compartirlo entre pruebas comparte su
 * caché, y una prueba que pasa por lo que dejó la anterior no prueba nada.
 *
 * `gcTime: 0` en consultas **y** mutaciones: con el valor por omisión, cada cliente
 * deja un temporizador de cinco minutos vivo después de la prueba, y el corredor
 * termina avisando que un proceso no pudo cerrarse.
 */
const creados: QueryClient[] = []

export function clienteDePrueba(): QueryClient {
  const cliente = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false, gcTime: 0 },
    },
  })
  creados.push(cliente)
  return cliente
}

export function Envoltorio({ cliente, children }: { cliente: QueryClient; children: ReactNode }) {
  return <QueryClientProvider client={cliente}>{children}</QueryClientProvider>
}

export function envoltorioCon(cliente: QueryClient) {
  return function ConCliente({ children }: { children: ReactNode }) {
    return <Envoltorio cliente={cliente}>{children}</Envoltorio>
  }
}

/** `render` es asíncrono desde RNTL 14: React 19 monta en concurrente. */
export async function dibujar(elemento: ReactElement) {
  const cliente = clienteDePrueba()
  return render(elemento, { wrapper: envoltorioCon(cliente) })
}

/** Se llama desde `preparar.tsx`: ningún cliente sobrevive a su prueba. */
export function soltarClientes(): void {
  for (const cliente of creados.splice(0)) {
    cliente.clear()
    cliente.unmount()
  }
}
