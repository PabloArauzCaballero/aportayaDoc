import type { InputHTMLAttributes, ReactNode } from 'react'

/**
 * El campo de texto, con sus cuatro estados.
 *
 * **`estado` no es decoración.** Un borde rojo sin texto que lo explique no dice qué
 * está mal, así que el error viaja en `ayuda` y se enlaza con `aria-describedby`: el
 * lector de pantalla lo lee al entrar al campo, no después de enviar el formulario.
 *
 * El `addon` es el `Bs` de la izquierda. **Los dos lados no son simétricos**: el ícono
 * de la izquierda es decoración y va `aria-hidden` —lo que informa es la etiqueta, no
 * el dibujo—, pero la ranura de la derecha es donde vive un control: el ojo de la
 * contraseña, la cruz que limpia. Marcarla `aria-hidden` la volvería inalcanzable
 * para el teclado y el lector de pantalla, que es peor que no tenerla. La bóveda ya
 * lo dice a su manera: `.ic.r{cursor:pointer}`.
 */
export type EstadoDeCampo = 'normal' | 'error' | 'exito'

export type PropiedadesDeCampo = Omit<InputHTMLAttributes<HTMLInputElement>, 'className'> & {
  estado?: EstadoDeCampo
  /** Texto de ayuda o de error. Con `estado="error"` se anuncia como alerta. */
  ayuda?: string
  /** Prefijo pegado al campo: `Bs`, `+591`. */
  addon?: string
  iconoIzquierda?: ReactNode
  iconoDerecha?: ReactNode
}

export function Campo({
  estado = 'normal',
  ayuda,
  addon,
  iconoIzquierda,
  iconoDerecha,
  id,
  ...resto
}: PropiedadesDeCampo) {
  const idAyuda = ayuda && id ? `${id}-ayuda` : undefined
  const clases = [
    'ay-campo',
    estado !== 'normal' && `ay-campo--${estado}`,
    iconoIzquierda && 'ay-campo--icono-izq',
    iconoDerecha && 'ay-campo--icono-der',
  ]
    .filter(Boolean)
    .join(' ')

  const entrada = (
    <input
      {...resto}
      id={id}
      className={clases}
      aria-invalid={estado === 'error' || undefined}
      aria-describedby={idAyuda}
    />
  )

  return (
    <>
      {addon ? (
        <span className="ay-campo-grupo">
          <span className="ay-campo-grupo__addon">{addon}</span>
          {entrada}
        </span>
      ) : (
        <span className="ay-campo-marco">
          {iconoIzquierda ? (
            <span className="ay-campo-marco__ic ay-campo-marco__ic--izq" aria-hidden="true">
              {iconoIzquierda}
            </span>
          ) : null}
          {entrada}
          {iconoDerecha ? (
            <span className="ay-campo-marco__ic ay-campo-marco__ic--der">{iconoDerecha}</span>
          ) : null}
        </span>
      )}
      {ayuda ? (
        <span
          id={idAyuda}
          className={`ay-ayuda ay-ayuda--${estado}`}
          // Un error que aparece después de escribir tiene que anunciarse solo; una
          // ayuda estática no debe interrumpir.
          role={estado === 'error' ? 'alert' : undefined}
        >
          {ayuda}
        </span>
      ) : null}
    </>
  )
}
