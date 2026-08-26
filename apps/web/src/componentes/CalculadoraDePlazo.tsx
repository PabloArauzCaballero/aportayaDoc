import { useCallback, useEffect, useState } from 'react'
import { ErrorDeApi, ErrorDeRed } from '../dominio/gateway'
import { calcularPlazo, type SalidaPlazoHabil } from '../dominio/plazoHabil'

/**
 * La isla React de la fase F0: **la única parte de esta página que lleva JavaScript
 * al navegador**. Todo lo que la rodea es HTML estático.
 *
 * Implementa los cuatro estados —cargando, vacío, error y éxito— porque la regla no
 * cambia por ser un sitio público: una página que se queda en blanco cuando el
 * servicio no responde es una página rota, se llame como se llame.
 */
type Estado =
  | { fase: 'cargando' }
  | { fase: 'exito'; datos: SalidaPlazoHabil }
  | { fase: 'error'; mensaje: string; trazaId?: string }

const CONSULTA = { desde: '2026-01-15', dias: 5, alcance: 'NACIONAL' } as const

export function CalculadoraDePlazo() {
  const [estado, setEstado] = useState<Estado>({ fase: 'cargando' })
  const [intento, setIntento] = useState(0)

  useEffect(() => {
    const control = new AbortController()
    setEstado({ fase: 'cargando' })
    calcularPlazo(CONSULTA, control.signal)
      .then((datos) => setEstado({ fase: 'exito', datos }))
      .catch((error: unknown) => {
        if (control.signal.aborted) return
        if (error instanceof ErrorDeApi) {
          setEstado({ fase: 'error', mensaje: error.message, trazaId: error.trazaId })
        } else if (error instanceof ErrorDeRed) {
          setEstado({ fase: 'error', mensaje: error.message })
        } else {
          setEstado({ fase: 'error', mensaje: 'No pudimos completar la consulta.' })
        }
      })
    return () => control.abort()
  }, [intento])

  const reintentar = useCallback(() => setIntento((n) => n + 1), [])

  if (estado.fase === 'cargando') {
    return (
      <p role="status" aria-live="polite" style={{ color: 'var(--color-texto-suave)' }}>
        Calculando el plazo…
      </p>
    )
  }

  if (estado.fase === 'error') {
    return (
      <section>
        <p role="alert" style={{ color: 'var(--color-error)' }}>
          {estado.mensaje}
        </p>
        <button type="button" onClick={reintentar} style={{ minHeight: 'var(--area-tactil)' }}>
          Volver a intentar
        </button>
        {estado.trazaId ? (
          <p style={{ color: 'var(--color-texto-suave)', fontSize: '0.85rem' }}>
            Código de seguimiento: {estado.trazaId}
          </p>
        ) : null}
      </section>
    )
  }

  const { fechaLimite, diasSalteados } = estado.datos

  return (
    <section>
      <p>
        Contando <strong>{CONSULTA.dias} días hábiles</strong> desde el {CONSULTA.desde}, el plazo vence
        el <time dateTime={fechaLimite}>{fechaLimite}</time>.
      </p>

      {/* Vacío no es un error: que no se haya salteado ningún día es una respuesta
          correcta, y decirlo evita que se lea como que faltó calcular algo. */}
      {diasSalteados.length === 0 ? (
        <p style={{ color: 'var(--color-texto-suave)' }}>
          No se salteó ningún día: en ese tramo no hubo feriados ni fines de semana declarados.
        </p>
      ) : (
        <>
          <h2>Días que se saltearon</h2>
          <ul>
            {diasSalteados.map((dia) => (
              <li key={dia.fecha}>
                <time dateTime={dia.fecha}>{dia.fecha}</time> — {dia.motivo}
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  )
}
