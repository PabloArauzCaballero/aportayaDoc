/**
 * El cambio entre vistas equivalentes: «Todos / Entradas / Salidas».
 *
 * **Equivalentes es la palabra.** Si una de las opciones hace algo distinto —guarda,
 * envía, cambia de pantalla— esto es el control equivocado: son pestañas o son
 * botones.
 *
 * `role="tablist"` con flechas del teclado, porque un grupo de botones sueltos obliga
 * a tabular por cada opción para llegar a la última.
 */
export type OpcionSegmentada<T extends string> = { valor: T; etiqueta: string }

export type PropiedadesDeSelectorSegmentado<T extends string> = {
  opciones: ReadonlyArray<OpcionSegmentada<T>>
  valor: T
  alCambiar: (valor: T) => void
  etiqueta: string
}

export function SelectorSegmentado<T extends string>({
  opciones,
  valor,
  alCambiar,
  etiqueta,
}: PropiedadesDeSelectorSegmentado<T>) {
  const mover = (desde: number, paso: number) => {
    const siguiente = opciones[(desde + paso + opciones.length) % opciones.length]
    if (siguiente) alCambiar(siguiente.valor)
  }
  return (
    <span className="ay-segmentado" role="tablist" aria-label={etiqueta}>
      {opciones.map((opcion, indice) => (
        <button
          key={opcion.valor}
          type="button"
          role="tab"
          aria-selected={opcion.valor === valor}
          tabIndex={opcion.valor === valor ? 0 : -1}
          className={opcion.valor === valor ? 'ay-segmentado__on' : undefined}
          onClick={() => alCambiar(opcion.valor)}
          onKeyDown={(evento) => {
            if (evento.key === 'ArrowRight') mover(indice, 1)
            if (evento.key === 'ArrowLeft') mover(indice, -1)
          }}
        >
          {opcion.etiqueta}
        </button>
      ))}
    </span>
  )
}
