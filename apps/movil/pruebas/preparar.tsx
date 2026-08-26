import { reiniciarEscenarios } from '@aportaya/simulado'
import { soltarClientes } from './dibujar'
import { servidorDePruebas } from './servidorDePruebas'

// `error` y no `bypass`: en pruebas, una peticion que el contrato no declara es
// un defecto, no algo que se deja pasar a la red de verdad.
beforeAll(() => servidorDePruebas.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  servidorDePruebas.resetHandlers()
  soltarClientes()
  // Un escenario que sobrevive a su prueba contamina la siguiente, y la que
  // falla no es la que tiene el defecto.
  reiniciarEscenarios()
})
afterAll(() => servidorDePruebas.close())

// `expo-crypto` es un modulo nativo: en Jest no hay nativo que llamar. El doble
// devuelve identificadores validos y distintos, que es lo unico que la prueba
// necesita saber de el.
jest.mock('expo-crypto', () => {
  let contador = 0
  return {
    randomUUID: () => {
      contador += 1
      return `00000000-0000-4000-8000-${String(contador).padStart(12, '0')}`
    },
  }
})

// El almacen seguro tampoco existe fuera del dispositivo.
jest.mock('expo-secure-store', () => {
  const memoria = new Map<string, string>()
  return {
    getItemAsync: async (clave: string) => memoria.get(clave) ?? null,
    setItemAsync: async (clave: string, valor: string) => void memoria.set(clave, valor),
    deleteItemAsync: async (clave: string) => void memoria.delete(clave),
  }
})
