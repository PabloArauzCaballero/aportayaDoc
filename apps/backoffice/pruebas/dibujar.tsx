import type { ReactElement, ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'

/**
 * Un cliente de consultas nuevo por prueba. Compartirlo comparte su caché, y una
 * prueba que pasa por lo que dejó la anterior no prueba nada.
 *
 * `gcTime: 0` en consultas y mutaciones: con el valor por omisión cada cliente deja
 * vivo un temporizador de cinco minutos y el corredor no cierra.
 */
const creados: QueryClient[] = []

export function clienteDePrueba(): QueryClient {
  const cliente = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false, gcTime: 0 } },
  })
  creados.push(cliente)
  return cliente
}

export function envoltorioCon(cliente: QueryClient) {
  return function ConCliente({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={cliente}>{children}</QueryClientProvider>
  }
}

export function dibujar(elemento: ReactElement) {
  return render(elemento, { wrapper: envoltorioCon(clienteDePrueba()) })
}

/** Se llama desde `preparar.ts`: ningún cliente sobrevive a su prueba. */
export function soltarClientes(): void {
  for (const cliente of creados.splice(0)) {
    cliente.clear()
    cliente.unmount()
  }
}
