import { useTablero } from '../dominio/tablero'
import { ErrorDeApi } from '../dominio/errores'
import { Cargando, Fallo, Vacio } from '../moleculas/EstadoDePantalla'
import { TarjetaDeIndicador } from '../organismos/TarjetaDeIndicador'

/**
 * El tablero: los mismos números para todos, con su meta al lado.
 *
 * No calcula nada. Pide el período y dibuja lo que `auditoria` devolvió; la única
 * decisión que toma es de presentación —cómo se agrupan y cómo se ve un provisorio—.
 */
export function PantallaDeIndicadores({ periodo }: { periodo: string }) {
  const consulta = useTablero({ periodo, dimension: 'PLATAFORMA' })

  if (consulta.isPending) return <Cargando que="el tablero del período" />

  if (consulta.isError) {
    const error = consulta.error
    return (
      <Fallo
        mensaje={error.message}
        trazaId={error instanceof ErrorDeApi ? error.trazaId : undefined}
        alReintentar={() => void consulta.refetch()}
      />
    )
  }

  const { indicadores, provisorio } = consulta.data

  if (indicadores.length === 0) {
    return (
      <Vacio
        titulo="El período no tiene indicadores publicados"
        explicacion="No es un error de consulta: el período existe y respondió. Todavía no se calculó ningún indicador para él."
      />
    )
  }

  return (
    <section>
      {provisorio ? (
        <p
          role="note"
          style={{
            border: '1px solid var(--color-borde)',
            borderLeft: '3px solid var(--color-error)',
            borderRadius: 'var(--radio-md)',
            padding: 'var(--espacio-md)',
            margin: '0 0 var(--espacio-lg)',
          }}
        >
          <strong>Todo este tablero es provisorio.</strong> El período todavía no cuadró, y un número sobre datos
          sin cuadrar es una opinión. Se puede mirar; no se puede citar.
        </p>
      ) : null}

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(17rem, 1fr))',
          gap: 'var(--espacio-md)',
        }}
      >
        {indicadores.map((indicador) => (
          <TarjetaDeIndicador key={indicador.codigo} indicador={indicador} provisorio={provisorio} />
        ))}
      </div>
    </section>
  )
}
