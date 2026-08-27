import { createFileRoute } from '@tanstack/react-router'
import { PantallaDeIndicadores } from '../pantallas/PantallaDeIndicadores'

/**
 * `/indicadores`. El período va en la URL: un oficial tiene que poder pegar el enlace
 * del tablero que está mirando dentro de un acta de comité.
 */
type Busqueda = { periodo: string }

export const Route = createFileRoute('/indicadores')({
  validateSearch: (entrada: Record<string, unknown>): Busqueda => ({
    periodo: typeof entrada.periodo === 'string' ? entrada.periodo : '2026-01',
  }),
  component: function RutaDeIndicadores() {
    const { periodo } = Route.useSearch()
    return (
      <>
        <h1 style={{ fontSize: 'var(--tipo-titulo)' }}>Indicadores · {periodo}</h1>
        <PantallaDeIndicadores periodo={periodo} />
      </>
    )
  },
})
