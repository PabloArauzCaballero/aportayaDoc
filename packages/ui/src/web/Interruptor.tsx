/**
 * El interruptor: encendido o apagado, y el cambio surte efecto ya.
 *
 * **No se usa para confirmar nada.** Un interruptor promete que lo que se ve es lo que
 * está pasando; si la acción necesita un «guardar», va una casilla, no esto.
 *
 * Es un `<input type="checkbox">` de verdad debajo del dibujo: el teclado, el lector
 * de pantalla y los formularios ya saben tratarlo, y un `<div>` con `role="switch"`
 * hay que enseñarle todo eso de nuevo.
 */
export type PropiedadesDeInterruptor = {
  encendido: boolean
  alCambiar: (encendido: boolean) => void
  etiqueta: string
  deshabilitado?: boolean
}

export function Interruptor({ encendido, alCambiar, etiqueta, deshabilitado = false }: PropiedadesDeInterruptor) {
  return (
    <label className="ay-interruptor">
      <input
        type="checkbox"
        role="switch"
        checked={encendido}
        disabled={deshabilitado}
        aria-label={etiqueta}
        onChange={(evento) => alCambiar(evento.target.checked)}
      />
      <span className="ay-interruptor__pista" aria-hidden="true" />
    </label>
  )
}
