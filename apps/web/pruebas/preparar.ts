import * as matchers from '@testing-library/jest-dom/matchers'
import { reiniciarEscenarios } from '@aportaya/simulado'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll, expect } from 'vitest'
import { servidorDePruebas } from './servidorDePruebas'

// Los matchers se registran a mano y no por el atajo `/vitest`: ese atajo depende de
// que el `expect` global exista al importarse, y con proyectos de Vitest ese orden no
// esta garantizado.
expect.extend(matchers)

// `error` y no `bypass`: en pruebas, una peticion que el contrato no declara es un
// defecto, no algo que se deja pasar a la red de verdad.
beforeAll(() => servidorDePruebas.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  servidorDePruebas.resetHandlers()
  reiniciarEscenarios()
})
afterAll(() => servidorDePruebas.close())
