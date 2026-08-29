import type { ReactNode } from 'react'

/**
 * El chip de filtro: se prende, se apaga, y a veces se quita.
 *
 * Distinto de `ChipEstado`, que **informa** un estado y no se toca. Este es un
 * control: `aria-pressed` dice si está activo, y el botón de quitar lleva su propio
 * nombre —«Quitar Vencidos», no «×»— porque una lista de cinco filtros con cinco
 * botones llamados «×» es indistinguible al oído.
 */
export type PropiedadesDeChip = {
  children: ReactNode
  activo?: boolean
  alAlternar?: () => void
  /** Si se pasa, el chip muestra la cruz y la llama al quitarlo. */
  alQuitar?: () => void
  /** Cómo nombrarlo cuando se lo quita. Por omisión, su propio texto. */
  nombre?: string
}

export function Chip({ children, activo = false, alAlternar, alQuitar, nombre }: PropiedadesDeChip) {
  const texto = nombre ?? (typeof children === 'string' ? children : 'el filtro')
  return (
    <span className={`ay-filtro${activo ? ' ay-filtro--activo' : ''}`}>
      <button type="button" className="ay-filtro__cuerpo" aria-pressed={activo} onClick={alAlternar}>
        {children}
      </button>
      {alQuitar ? (
        <button type="button" className="ay-filtro__x" aria-label={`Quitar ${texto}`} onClick={alQuitar}>
          ×
        </button>
      ) : null}
    </span>
  )
}
