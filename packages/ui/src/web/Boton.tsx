import type { ButtonHTMLAttributes, ReactNode } from 'react'

/**
 * El botón, con sus siete estados. Los primeros seis los da el CSS
 * (normal, hover, active, foco, deshabilitado); el séptimo —**cargando**— es
 * propiedad, porque es el que evita el doble envío.
 *
 * **`cargando` deshabilita.** Invariante 6: toda operación de dinero manda clave de
 * idempotencia *y* el botón se bloquea. Dejar el botón vivo mientras la petición
 * viaja es pedirle al usuario que la mande dos veces.
 *
 * Las variantes `icono` y `fab` **no llevan texto visible**, así que exigen
 * `aria-label`: un botón cuyo nombre accesible es «×» no se puede usar sin ver la
 * pantalla. Está comprobado en desarrollo, donde se puede arreglar, y no en
 * producción, donde ya es tarde.
 */
export type PropiedadesDeBoton = ButtonHTMLAttributes<HTMLButtonElement> & {
  variante?: 'primario' | 'secundario' | 'fantasma' | 'peligro' | 'enlace' | 'icono' | 'fab'
  tamano?: 'sm' | 'base' | 'lg'
  cargando?: boolean
  /** Ocupa todo el ancho. En móvil y dentro de un formulario, es lo normal. */
  bloque?: boolean
  children: ReactNode
}

export function Boton({
  variante = 'primario',
  tamano = 'base',
  cargando = false,
  bloque = false,
  disabled,
  children,
  className,
  ...resto
}: PropiedadesDeBoton) {
  if (
    process.env.NODE_ENV !== 'production' &&
    (variante === 'icono' || variante === 'fab') &&
    !resto['aria-label'] &&
    !resto['aria-labelledby']
  ) {
    throw new Error(`Boton variante="${variante}" necesita aria-label: su contenido es un ícono, no un nombre.`)
  }
  const clases = [
    'ay-boton',
    `ay-boton--${variante}`,
    `ay-boton--${tamano}`,
    bloque && 'ay-boton--bloque',
    className,
  ]
    .filter(Boolean)
    .join(' ')
  return (
    <button
      type="button"
      {...resto}
      className={clases}
      disabled={disabled || cargando}
      // `aria-busy` es lo que le dice al lector de pantalla que la accion esta en
      // curso. Sin esto, «deshabilitado» se anuncia como «no podes», no como «espera».
      aria-busy={cargando || undefined}
    >
      {cargando ? <span className="ay-boton__spinner" aria-hidden="true" /> : null}
      {children}
    </button>
  )
}
