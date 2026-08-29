/**
 * Cuánto falta — en barra o en anillo.
 *
 * **El número va al lado, siempre.** Una barra al 70 % y una al 80 % se distinguen
 * mirando; al oído no se distinguen nunca, y en un pasanaku «cuánto se juntó» es el
 * dato, no la ilustración.
 *
 * `progressbar` con sus tres atributos: sin `aria-valuenow` la barra existe pero no
 * informa.
 */
export type PropiedadesDeProgreso = {
  /** De 0 a 100. Se acota: un valor fuera de rango es un error de quien llama. */
  porcentaje: number
  etiqueta: string
  /** Oculta el número. Solo cuando el mismo dato ya está escrito al lado. */
  sinCifra?: boolean
}

const acotar = (porcentaje: number) => Math.min(100, Math.max(0, Math.round(porcentaje)))

export function BarraDeProgreso({ porcentaje, etiqueta, sinCifra = false }: PropiedadesDeProgreso) {
  const valor = acotar(porcentaje)
  return (
    <span className="ay-progreso">
      <span
        className="ay-barra"
        role="progressbar"
        aria-label={etiqueta}
        aria-valuenow={valor}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <span className="ay-barra__lleno" style={{ width: `${valor}%` }} />
      </span>
      {sinCifra ? null : <span className="ay-progreso__cifra">{valor}%</span>}
    </span>
  )
}

/**
 * El mismo dato, en círculo. Se dibuja con SVG y `stroke-dasharray`: es una sola
 * figura que escala sin pixelarse y no necesita una imagen por tamaño.
 */
export function AnilloDeProgreso({ porcentaje, etiqueta, sinCifra = false }: PropiedadesDeProgreso) {
  const valor = acotar(porcentaje)
  const radio = 26
  const vuelta = 2 * Math.PI * radio
  return (
    <span
      className="ay-anillo"
      role="progressbar"
      aria-label={etiqueta}
      aria-valuenow={valor}
      aria-valuemin={0}
      aria-valuemax={100}
    >
      <svg viewBox="0 0 64 64" aria-hidden="true">
        <circle className="ay-anillo__pista" cx="32" cy="32" r={radio} />
        <circle
          className="ay-anillo__lleno"
          cx="32"
          cy="32"
          r={radio}
          strokeDasharray={`${(vuelta * valor) / 100} ${vuelta}`}
        />
      </svg>
      {sinCifra ? null : <span className="ay-anillo__cifra">{valor}%</span>}
    </span>
  )
}
