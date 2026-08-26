import { setupServer } from 'msw/native'
import { manejadoresDeTodos } from '@aportaya/simulado'

/**
 * El servidor simulado dentro de la app, en desarrollo.
 *
 * El interruptor vive en `app.json` (`extra.simulado`) y no en un `if` perdido en
 * una pantalla: un simulado que se enciende solo es un simulado que alguien va a
 * publicar sin darse cuenta.
 */
let servidor: ReturnType<typeof setupServer> | null = null

export function arrancarSimulado(): void {
  if (servidor) return
  servidor = setupServer(...manejadoresDeTodos())
  // `bypass`: lo que el contrato no declara sale a la red de verdad, en vez de
  // fallar. Asi el simulado convive con un servicio ya implementado.
  servidor.listen({ onUnhandledRequest: 'bypass' })
}

export function detenerSimulado(): void {
  servidor?.close()
  servidor = null
}
