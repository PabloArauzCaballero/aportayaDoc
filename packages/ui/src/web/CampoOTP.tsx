import { useRef } from 'react'

/**
 * Las casillas de un código: PIN, OTP, o el código de una invitación.
 *
 * **Una sola casilla por dígito, pero un solo valor.** El foco salta solo al escribir
 * y vuelve al borrar, porque un código de seis dígitos con seis campos que hay que
 * tabular a mano se abandona a la mitad.
 *
 * `autoComplete="one-time-code"` es lo que hace que el teléfono ofrezca el código del
 * SMS sin que la persona lo copie. `type="password"` cuando es un PIN: un PIN visible
 * en pantalla se lee por encima del hombro.
 */
export type PropiedadesDeCampoOTP = {
  valor: string
  alCambiar: (valor: string) => void
  /** Cuántos dígitos. 4 o 6 según el flujo; el contrato manda. */
  digitos?: number
  etiqueta: string
  /** `true` para un PIN: se oculta mientras se escribe. */
  secreto?: boolean
  estado?: 'normal' | 'error'
}

export function CampoOTP({
  valor,
  alCambiar,
  digitos = 6,
  etiqueta,
  secreto = false,
  estado = 'normal',
}: PropiedadesDeCampoOTP) {
  const casillas = useRef<Array<HTMLInputElement | null>>([])

  const escribir = (indice: number, texto: string) => {
    const digito = texto.replace(/\D/g, '').slice(-1)
    const siguiente = (valor.padEnd(digitos, ' ').slice(0, indice) + (digito || ' ') + valor.padEnd(digitos, ' ').slice(indice + 1))
      .trimEnd()
      .slice(0, digitos)
    alCambiar(siguiente.replace(/ /g, ''))
    if (digito && indice + 1 < digitos) casillas.current[indice + 1]?.focus()
  }

  return (
    <span className={`ay-otp${estado === 'error' ? ' ay-otp--error' : ''}`} role="group" aria-label={etiqueta}>
      {Array.from({ length: digitos }, (_, indice) => (
        <input
          key={indice}
          ref={(nodo) => {
            casillas.current[indice] = nodo
          }}
          type={secreto ? 'password' : 'text'}
          inputMode="numeric"
          maxLength={1}
          autoComplete={indice === 0 ? 'one-time-code' : 'off'}
          aria-label={`${etiqueta}, dígito ${indice + 1} de ${digitos}`}
          aria-invalid={estado === 'error' || undefined}
          value={valor[indice] ?? ''}
          onChange={(evento) => escribir(indice, evento.target.value)}
          onKeyDown={(evento) => {
            // Borrar en una casilla vacía retrocede: es lo que hace todo el mundo.
            if (evento.key === 'Backspace' && !valor[indice] && indice > 0) {
              casillas.current[indice - 1]?.focus()
            }
          }}
        />
      ))}
    </span>
  )
}
