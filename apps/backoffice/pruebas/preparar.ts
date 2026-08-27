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

// jsdom no implementa `<dialog>`: `showModal` y `close` no existen y toda prueba que
// abra un dialogo revienta. Se rellenan aca, lo minimo para que el elemento se
// comporte como el navegador en lo que las pruebas observan —el atributo `open`, el
// evento `close` y `Escape`—.
//
// Lo que NO se rellena, y hay que saberlo: el foco atrapado y la inercia del fondo.
// Eso lo da el navegador de verdad y ninguna prueba de jsdom lo comprueba; se verifica
// con Playwright cuando ese corredor exista.
if (typeof HTMLDialogElement !== 'undefined' && !HTMLDialogElement.prototype.showModal) {
  HTMLDialogElement.prototype.showModal = function abrir(this: HTMLDialogElement) {
    this.open = true
  }
  HTMLDialogElement.prototype.close = function cerrar(this: HTMLDialogElement) {
    this.open = false
    this.dispatchEvent(new Event('close'))
  }
  document.addEventListener('keydown', (evento) => {
    if (evento.key !== 'Escape') return
    document.querySelectorAll('dialog[open]').forEach((d) => (d as HTMLDialogElement).close())
  })
}
