/**
 * Cargando, vacio y error, escritos una vez.
 *
 * `web-backoffice` los exige en toda vista con datos, y agrega algo que la app no
 * necesita: **«no hay nada» y «el filtro no devolvio nada» son mensajes distintos**.
 * Por eso `Vacio` recibe el motivo y no una frase generica.
 */
export function Cargando({ que }: { que: string }) {
  return (
    <p role="status" aria-live="polite" style={{ color: 'var(--color-texto-suave)' }}>
      Cargando {que}…
    </p>
  )
}

export function Vacio({ titulo, explicacion }: { titulo: string; explicacion: string }) {
  return (
    <section style={{ padding: 'var(--espacio-lg)', background: 'var(--color-superficie)', borderRadius: 'var(--radio-md)' }}>
      <h2 style={{ margin: 0, fontSize: 'var(--tipo-titulo)' }}>{titulo}</h2>
      <p style={{ color: 'var(--color-texto-suave)' }}>{explicacion}</p>
    </section>
  )
}

export function Fallo({ mensaje, trazaId, alReintentar }: { mensaje: string; trazaId?: string; alReintentar: () => void }) {
  return (
    <section style={{ padding: 'var(--espacio-lg)' }}>
      <p role="alert" style={{ color: 'var(--color-error)', margin: 0 }}>
        {mensaje}
      </p>
      <button type="button" onClick={alReintentar} style={{ minHeight: 'var(--area-tactil)', marginTop: 'var(--espacio-md)' }}>
        Volver a intentar
      </button>
      {/* La traza no es decoracion: es el hilo con el que soporte encuentra esta
          peticion exacta en el log del backend. */}
      {trazaId ? (
        <p style={{ color: 'var(--color-texto-suave)', fontSize: 'var(--tipo-pie)' }}>Código de seguimiento: {trazaId}</p>
      ) : null}
    </section>
  )
}
