import type { ReactNode } from 'react'
import { useId, useState } from 'react'

/**
 * La aclaración corta.
 *
 * **No guarda nada que haga falta para operar.** Un tooltip no existe en un teléfono
 * —no hay `hover`— así que lo que esté sólo acá, para media plataforma, no está.
 * Sirve para desambiguar una etiqueta, no para explicar una regla.
 *
 * **El disparador es un `<button>`, no un `<span>` con manejadores.** Un elemento no
 * interactivo con `onMouseEnter` es inalcanzable con teclado: se abre con el foco, se
 * cierra con Escape, y eso solo lo da un control de verdad.
 */
export function Tooltip({ texto, children }: { texto: string; children: ReactNode }) {
  const [abierto, abrir] = useState(false)
  const id = useId()
  return (
    <span className="ay-tooltip">
      <button
        type="button"
        className="ay-tooltip__gatillo"
        aria-describedby={id}
        onMouseEnter={() => abrir(true)}
        onMouseLeave={() => abrir(false)}
        onFocus={() => abrir(true)}
        onBlur={() => abrir(false)}
        onKeyDown={(evento) => {
          if (evento.key === 'Escape') abrir(false)
        }}
      >
        {children}
      </button>
      <span id={id} role="tooltip" className="ay-tooltip__globo" hidden={!abierto}>
        {texto}
      </span>
    </span>
  )
}
