import * as matchers from '@testing-library/jest-dom/matchers'
import { reiniciarEscenarios } from '@aportaya/simulado'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll, expect } from 'vitest'

// Los matchers se registran a mano y no por el atajo `/vitest`: el atajo depende de
// que `expect` global ya exista cuando se importa, y con proyectos de Vitest ese
// orden no esta garantizado. Registrarlos explicitamente funciona siempre.
expect.extend(matchers)
import { servidorDePruebas } from './servidorDePruebas'
import { soltarClientes } from './dibujar'

// `error` y no `bypass`: en pruebas, una peticion que el contrato no declara es un
// defecto, no algo que se deja pasar a la red de verdad.
beforeAll(() => servidorDePruebas.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  servidorDePruebas.resetHandlers()
  // Un escenario que sobrevive a su prueba contamina la siguiente, y la que falla
  // no es la que tiene el defecto.
  reiniciarEscenarios()
  soltarClientes()
})
afterAll(() => servidorDePruebas.close())
