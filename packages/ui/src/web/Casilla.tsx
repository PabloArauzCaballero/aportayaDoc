import type { InputHTMLAttributes, ReactNode } from 'react'

/**
 * Casilla y opción — checkbox y radio, que son el mismo átomo con distinto `type`.
 *
 * **El `<label>` envuelve al control.** Así el área de toque es la etiqueta entera y
 * no un cuadradito de 18 px: en un teléfono, apuntarle a 18 px es fallar.
 */
type Base = Omit<InputHTMLAttributes<HTMLInputElement>, 'className' | 'type'> & { children: ReactNode }

function Marca({ tipo, children, ...resto }: Base & { tipo: 'checkbox' | 'radio' }) {
  return (
    <label className="ay-marca">
      <input {...resto} type={tipo} />
      <span>{children}</span>
    </label>
  )
}

export function Casilla(props: Base) {
  return <Marca {...props} tipo="checkbox" />
}

/** Una de varias, excluyentes. Comparten `name` o no son un grupo. */
export function Opcion(props: Base) {
  return <Marca {...props} tipo="radio" />
}
