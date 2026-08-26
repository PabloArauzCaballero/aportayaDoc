import { Link, createFileRoute } from '@tanstack/react-router'

/** Punto de entrada del andamiaje. Las pantallas de operación llegan en F7. */
export const Route = createFileRoute('/')({
  component: function Inicio() {
    return (
      <>
        <h1 style={{ fontSize: 'var(--tipo-titulo)' }}>Backoffice</h1>
        <p style={{ color: 'var(--color-texto-suave)' }}>
          Andamiaje de la fase F0. La pantalla real de abajo consulta el servicio de billetera contra el
          servidor simulado.
        </p>
        <Link to="/billetera/$cuentaId" params={{ cuentaId: '11111111-1111-4111-8111-111111111111' }}>
          Consultar una billetera
        </Link>
      </>
    )
  },
})
