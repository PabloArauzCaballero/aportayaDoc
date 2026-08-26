import { ScrollView, StyleSheet } from 'react-native'
import { useSaldo } from '../dominio/saldoDeBilletera'
import { ErrorDeApi } from '../dominio/errores'
import { Cargando, Fallo, Vacio } from '../moleculas/EstadoDePantalla'
import { ResumenDeBilletera } from '../organismos/ResumenDeBilletera'
import { andamiaje } from '../tokens/andamiaje'

/**
 * La pantalla real de la fase F0, contra el servidor simulado y con **los cuatro
 * estados**: cargando, vacio, error y exito.
 *
 * Compone organismos y no tiene logica: la red vive en `dominio/`
 * (`arquitectura-atomica`, `movil-expo`).
 */
export function PantallaDeSaldo({ cuentaId }: { cuentaId: string }) {
  const consulta = useSaldo(cuentaId)

  if (consulta.isPending) return <Cargando que="tu saldo" />

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

  // Vacio no es «sin datos»: es una cuenta recien abierta, que responde 200 con
  // el saldo en cero. Decirlo evita que se lea como una falla.
  if (consulta.data.disponible.monto === '0.00' && consulta.data.retenido.monto === '0.00') {
    return (
      <Vacio
        titulo="Todavía no tenés movimientos"
        explicacion="Cuando recargues o recibas un aporte, tu saldo va a aparecer acá."
      />
    )
  }

  return (
    <ScrollView contentContainerStyle={estilos.contenido}>
      <ResumenDeBilletera saldo={consulta.data} />
    </ScrollView>
  )
}

const estilos = StyleSheet.create({
  contenido: { gap: andamiaje.espacio.md, padding: andamiaje.espacio.md },
})
