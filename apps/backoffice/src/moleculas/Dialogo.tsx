import { useEffect, useRef, type ReactNode } from 'react'

/**
 * Un diálogo modal, sobre el `<dialog>` del navegador.
 *
 * Se usa el elemento nativo y no un `<div>` con `role="dialog"` porque el navegador
 * ya resuelve —bien y gratis— las cuatro cosas que casi siempre se implementan mal a
 * mano: el foco queda atrapado adentro, `Escape` cierra, el fondo se vuelve inerte
 * para el lector de pantalla, y al cerrar el foco **vuelve** a donde estaba.
 *
 * Ese último punto es el que más se olvida y el que más molesta: un oficial que abre
 * el detalle de un indicador con el teclado y al cerrarlo aparece al principio de la
 * página tiene que volver a tabular hasta donde estaba, en cada tarjeta.
 */
export type PropiedadesDeDialogo = {
  abierto: boolean
  alCerrar: () => void
  titulo: string
  /** Ocupa la pantalla entera. Es lo que hace el botón de ampliar de una tarjeta. */
  pantallaCompleta?: boolean
  children: ReactNode
}

export function Dialogo({ abierto, alCerrar, titulo, pantallaCompleta = false, children }: PropiedadesDeDialogo) {
  const referencia = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialogo = referencia.current
    if (!dialogo) return
    // `showModal` y no `show`: es la diferencia entre un modal de verdad —con foco
    // atrapado y fondo inerte— y una caja que flota encima.
    if (abierto && !dialogo.open) dialogo.showModal()
    if (!abierto && dialogo.open) dialogo.close()
  }, [abierto])

  useEffect(() => {
    const dialogo = referencia.current
    if (!dialogo) return
    // `Escape` cierra el `<dialog>` sin avisarle a React. Sin esto, el estado queda
    // diciendo «abierto» sobre un diálogo cerrado y el botón deja de responder.
    const alCancelar = () => alCerrar()
    dialogo.addEventListener('close', alCancelar)
    return () => dialogo.removeEventListener('close', alCancelar)
  }, [alCerrar])

  return (
    <dialog
      ref={referencia}
      aria-label={titulo}
      style={{
        border: '1px solid var(--color-borde)',
        borderRadius: 'var(--radio-md)',
        padding: 0,
        width: pantallaCompleta ? '96vw' : 'min(34rem, 92vw)',
        maxWidth: pantallaCompleta ? 'none' : undefined,
        height: pantallaCompleta ? '92vh' : undefined,
        color: 'var(--color-texto)',
        background: 'var(--color-fondo)',
      }}
    >
      <header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 'var(--espacio-md)',
          padding: 'var(--espacio-md)',
          borderBottom: '1px solid var(--color-borde)',
          position: 'sticky',
          top: 0,
          background: 'var(--color-fondo)',
        }}
      >
        <h2 style={{ margin: 0, fontSize: 'var(--tipo-titulo)' }}>{titulo}</h2>
        <button
          type="button"
          onClick={alCerrar}
          aria-label={`Cerrar ${titulo}`}
          style={{ minHeight: 'var(--area-tactil)', minWidth: 'var(--area-tactil)' }}
        >
          ✕
        </button>
      </header>
      <div style={{ padding: 'var(--espacio-lg)', overflow: 'auto', maxHeight: pantallaCompleta ? '78vh' : '70vh' }}>
        {children}
      </div>
    </dialog>
  )
}
