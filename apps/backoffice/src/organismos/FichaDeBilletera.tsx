import type { SaldoBilletera } from 'clientes/typescript/nucleo-financiero/models'
import { Monto } from '../atomos/Monto'

/**
 * Lo que ve soporte cuando la consulta salio bien. No hace red ni decide estados:
 * los recibe resueltos. Es lo que permite probarlo sin levantar nada.
 */
export function FichaDeBilletera({ saldo }: { saldo: SaldoBilletera }) {
  return (
    <article
      style={{
        border: '1px solid var(--color-borde)',
        borderRadius: 'var(--radio-md)',
        padding: 'var(--espacio-lg)',
        display: 'grid',
        gap: 'var(--espacio-sm)',
        maxWidth: '32rem',
      }}
    >
      <h2 style={{ margin: 0, fontSize: 'var(--tipo-titulo)' }}>Disponible</h2>
      <Monto valor={saldo.disponible} etiqueta="Saldo disponible" grande />

      <dl style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: 'var(--espacio-sm)', margin: 0 }}>
        <dt style={{ color: 'var(--color-texto-suave)' }}>Retenido</dt>
        <dd style={{ margin: 0, textAlign: 'right' }}>
          <Monto valor={saldo.retenido} etiqueta="Saldo retenido" />
        </dd>
        <dt style={{ color: 'var(--color-texto-suave)' }}>Cuenta</dt>
        <dd style={{ margin: 0, textAlign: 'right', fontFamily: 'ui-monospace, monospace' }}>{saldo.cuentaId}</dd>
      </dl>

      {/* Fecha con zona explicita: en un expediente, una fecha ambigua no sirve. */}
      <p style={{ color: 'var(--color-texto-suave)', fontSize: 'var(--tipo-pie)', margin: 0 }}>
        Al corte de {saldo.alCorteDe}
      </p>
    </article>
  )
}
