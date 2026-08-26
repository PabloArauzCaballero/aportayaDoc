import { createFileRoute } from '@tanstack/react-router'
import { PantallaDeBilletera } from '../pantallas/PantallaDeBilletera'

/**
 * `/billetera/<cuentaId>`. La ruta sale del nombre del archivo: no hay `rutas.tsx`
 * que editar, y por eso agregar una pantalla no produce conflicto.
 *
 * El identificador va en la URL a proposito: un oficial tiene que poder pegar el
 * enlace de lo que esta mirando dentro de un expediente.
 */
export const Route = createFileRoute('/billetera/$cuentaId')({
  component: function RutaDeBilletera() {
    const { cuentaId } = Route.useParams()
    return (
      <>
        <h1 style={{ fontSize: 'var(--tipo-titulo)' }}>Billetera</h1>
        <PantallaDeBilletera cuentaId={cuentaId} />
      </>
    )
  },
})
