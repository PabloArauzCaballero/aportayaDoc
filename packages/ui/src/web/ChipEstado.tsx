/**
 * El estado de algo, dicho con palabra y con color — **en ese orden**.
 *
 * El color no informa solo: quien no distingue verde de rojo tiene que poder leer el
 * estado igual. Por eso el texto es obligatorio y el tono es decoración.
 */
export type TonoDeEstado = 'ok' | 'aviso' | 'error' | 'info' | 'neutro'

export function ChipEstado({ tono = 'neutro', children }: { tono?: TonoDeEstado; children: string }) {
  return (
    <span className={`ay-chip ay-chip--${tono}`}>
      <span className="ay-chip__punto" aria-hidden="true" />
      {children}
    </span>
  )
}
