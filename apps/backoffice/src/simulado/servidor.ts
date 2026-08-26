import { manejadoresDeTodos } from '@aportaya/simulado'

/**
 * El servidor simulado en el navegador, **solo en desarrollo**.
 *
 * El `import` es dinamico y vive dentro de la guarda: asi Vite no mete `msw` en el
 * paquete de produccion. Un servidor simulado publicado dentro del backoffice es una
 * forma de que alguien vea datos que no son.
 */
export async function arrancarSimulado(): Promise<void> {
  if (!import.meta.env.DEV) return
  const { setupWorker } = await import('msw/browser')
  const worker = setupWorker(...manejadoresDeTodos())
  // `bypass`: lo que el contrato no declara sale a la red de verdad, en vez de
  // fallar. Asi el simulado convive con un servicio ya implementado.
  await worker.start({ onUnhandledRequest: 'bypass', quiet: true })
}
