import type { ReactNode, SelectHTMLAttributes } from 'react'
import type { EstadoDeCampo } from './Campo'

/**
 * El desplegable.
 *
 * Es un `<select>` nativo a propósito: en el teléfono abre la rueda del sistema, con
 * el tamaño de letra que la persona configuró y el gesto que ya conoce. Un
 * desplegable dibujado a mano se ve igual en la maqueta y peor en la mano.
 */
export type PropiedadesDeSeleccion = Omit<SelectHTMLAttributes<HTMLSelectElement>, 'className'> & {
  estado?: EstadoDeCampo
  children: ReactNode
}

export function Seleccion({ estado = 'normal', children, ...resto }: PropiedadesDeSeleccion) {
  return (
    <select
      {...resto}
      className={`ay-campo ay-campo--seleccion${estado !== 'normal' ? ` ay-campo--${estado}` : ''}`}
      aria-invalid={estado === 'error' || undefined}
    >
      {children}
    </select>
  )
}
