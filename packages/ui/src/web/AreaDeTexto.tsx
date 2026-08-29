import type { TextareaHTMLAttributes } from 'react'
import type { EstadoDeCampo } from './Campo'

/** El campo largo: motivos, descargos, notas. Mismos estados que `Campo`. */
export type PropiedadesDeAreaDeTexto = Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'className'> & {
  estado?: EstadoDeCampo
  ayuda?: string
}

export function AreaDeTexto({ estado = 'normal', ayuda, id, ...resto }: PropiedadesDeAreaDeTexto) {
  const idAyuda = ayuda && id ? `${id}-ayuda` : undefined
  return (
    <>
      <textarea
        {...resto}
        id={id}
        rows={resto.rows ?? 4}
        className={`ay-campo ay-campo--area${estado !== 'normal' ? ` ay-campo--${estado}` : ''}`}
        aria-invalid={estado === 'error' || undefined}
        aria-describedby={idAyuda}
      />
      {ayuda ? (
        <span id={idAyuda} className={`ay-ayuda ay-ayuda--${estado}`} role={estado === 'error' ? 'alert' : undefined}>
          {ayuda}
        </span>
      ) : null}
    </>
  )
}
