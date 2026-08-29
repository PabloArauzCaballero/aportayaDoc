/**
 * El contador de a uno: cuántos participantes, cuántos períodos.
 *
 * Los dos botones tienen etiqueta de texto para el lector de pantalla, y el valor se
 * anuncia como `spinbutton` con su mínimo y su máximo. Un `+` y un `−` sin nombre son
 * dos botones idénticos para quien no ve la pantalla.
 */
export type PropiedadesDePaso = {
  valor: number
  alCambiar: (valor: number) => void
  minimo?: number
  maximo?: number
  paso?: number
  etiqueta: string
  deshabilitado?: boolean
}

export function Paso({
  valor,
  alCambiar,
  minimo = 0,
  maximo = Number.MAX_SAFE_INTEGER,
  paso = 1,
  etiqueta,
  deshabilitado = false,
}: PropiedadesDePaso) {
  const acotar = (siguiente: number) => Math.min(maximo, Math.max(minimo, siguiente))
  return (
    <span className="ay-paso" role="group" aria-label={etiqueta}>
      <button
        type="button"
        aria-label={`Restar uno a ${etiqueta}`}
        disabled={deshabilitado || valor <= minimo}
        onClick={() => alCambiar(acotar(valor - paso))}
      >
        −
      </button>
      <input
        type="text"
        inputMode="numeric"
        role="spinbutton"
        aria-valuenow={valor}
        aria-valuemin={minimo}
        aria-valuemax={maximo === Number.MAX_SAFE_INTEGER ? undefined : maximo}
        aria-label={etiqueta}
        value={valor}
        disabled={deshabilitado}
        onChange={(evento) => {
          const leido = Number.parseInt(evento.target.value, 10)
          if (!Number.isNaN(leido)) alCambiar(acotar(leido))
        }}
      />
      <button
        type="button"
        aria-label={`Sumar uno a ${etiqueta}`}
        disabled={deshabilitado || valor >= maximo}
        onClick={() => alCambiar(acotar(valor + paso))}
      >
        +
      </button>
    </span>
  )
}
