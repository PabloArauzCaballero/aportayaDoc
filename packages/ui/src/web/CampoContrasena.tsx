import { useState } from 'react'
import type { PropiedadesDeCampo } from './Campo'
import { Campo } from './Campo'

/**
 * La contraseña, con el ojo para verla.
 *
 * **El ojo no es una comodidad: es accesibilidad.** Escribir a ciegas una contraseña
 * larga en un teclado de teléfono es la razón principal por la que la gente elige
 * contraseñas cortas. Verla un segundo es más seguro que acortarla.
 *
 * El botón dice qué hace y en qué estado está (`aria-pressed`), y el campo cambia de
 * `type`, no de `-webkit-text-security`: el gestor de contraseñas del navegador tiene
 * que seguir reconociéndolo.
 */
export function CampoContrasena(props: Omit<PropiedadesDeCampo, 'type' | 'iconoDerecha'>) {
  const [visible, verla] = useState(false)
  return (
    <Campo
      {...props}
      type={visible ? 'text' : 'password'}
      autoComplete={props.autoComplete ?? 'current-password'}
      iconoDerecha={
        <button
          type="button"
          className="ay-ojo"
          aria-label={visible ? 'Ocultar la contraseña' : 'Mostrar la contraseña'}
          aria-pressed={visible}
          onClick={() => verla(!visible)}
        >
          {visible ? '🙈' : '👁'}
        </button>
      }
    />
  )
}
