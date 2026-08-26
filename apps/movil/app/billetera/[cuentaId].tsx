import { Stack, useLocalSearchParams } from 'expo-router'
import { PantallaDeSaldo } from '../../src/pantallas/PantallaDeSaldo'

/**
 * `/billetera/<cuentaId>`. La ruta sale del nombre del archivo: no hay
 * `routes.tsx` que editar, y por eso agregar una pantalla no produce conflicto.
 */
export default function RutaDeSaldo() {
  const { cuentaId } = useLocalSearchParams<{ cuentaId: string }>()
  return (
    <>
      <Stack.Screen options={{ title: 'Mi saldo' }} />
      <PantallaDeSaldo cuentaId={cuentaId ?? ''} />
    </>
  )
}
