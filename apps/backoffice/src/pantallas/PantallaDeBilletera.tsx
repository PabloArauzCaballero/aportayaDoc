import { useSaldo } from '../dominio/saldoDeBilletera'
import { ErrorDeApi } from '../dominio/errores'
import { Cargando, Fallo, Vacio } from '../moleculas/EstadoDePantalla'
import { FichaDeBilletera } from '../organismos/FichaDeBilletera'

/**
 * La pantalla real de la fase F0 del backoffice, contra el servidor simulado y con
 * **los cuatro estados**: cargando, vacio, error y exito.
 *
 * Compone organismos y no tiene logica: la red vive en `dominio/`.
 * **No es una tabla**: la `TablaDeDatos` virtualizada es del carril `F6`, y
 * adelantarla seria disenar sin el sistema de diseno, que todavia no existe.
 */
export function PantallaDeBilletera({ cuentaId }: { cuentaId: string }) {
  const consulta = useSaldo(cuentaId)

  if (consulta.isPending) return <Cargando que="el saldo de la cuenta" />

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

  // Vacio no es «sin datos»: es una cuenta abierta y sin movimientos. Decirlo evita
  // que soporte lo lea como una falla del sistema y abra una incidencia de mas.
  if (consulta.data.disponible.monto === '0.00' && consulta.data.retenido.monto === '0.00') {
    return (
      <Vacio
        titulo="La cuenta está en cero"
        explicacion="No hubo movimientos todavía. No es un error de consulta: la cuenta existe y responde."
      />
    )
  }

  return <FichaDeBilletera saldo={consulta.data} />
}
