import { fijarEscenario } from '@aportaya/simulado'
import { screen, waitFor } from '@testing-library/react-native'
import { PantallaDeSaldo } from '../../src/pantallas/PantallaDeSaldo'
import { dibujar } from '../dibujar'

/**
 * Los cuatro estados de la pantalla real de la fase F0, contra el servidor
 * simulado — el mismo que ve la app en desarrollo.
 *
 * `movil-expo` los exige en toda pantalla con datos. Una pantalla probada solo
 * en su camino feliz es una pantalla que en la calle se queda en blanco.
 */
const CUENTA = '11111111-1111-4111-8111-111111111111'

describe('PantallaDeSaldo · los cuatro estados', () => {
  it('cargando: lo dice mientras espera, sin pantalla en blanco', async () => {
    // Una demora corta alcanza para ver el estado y no deja un temporizador
    // vivo despues de la prueba, que es lo que hace que Jest no cierre.
    fijarEscenario('consultarSaldo', { tipo: 'demora', ms: 40 })
    await dibujar(<PantallaDeSaldo cuentaId={CUENTA} />)
    expect(screen.getByLabelText('Cargando tu saldo')).toBeTruthy()
  })

  it('exito: muestra el saldo que respondio el contrato, sin recalcular nada', async () => {
    await dibujar(<PantallaDeSaldo cuentaId={CUENTA} />)
    const disponible = await screen.findByLabelText(/^Saldo disponible: /)
    expect(disponible).toBeTruthy()
    // El importe se muestra tal cual llego: cadena decimal con dos decimales.
    expect(disponible.props.accessibilityLabel).toMatch(/^Saldo disponible: (BOB|USD) -?\d+\.\d{2}$/)
  })

  it('vacio: una cuenta sin movimientos dice por que no hay nada', async () => {
    fijarEscenario('consultarSaldo', { tipo: 'vacio' })
    await dibujar(<PantallaDeSaldo cuentaId={CUENTA} />)
    expect(await screen.findByText('Todavía no tenés movimientos')).toBeTruthy()
  })

  it('error: mensaje humano, reintento a la vista y traza para soporte', async () => {
    fijarEscenario('consultarSaldo', { tipo: 'error', estado: 401 })
    await dibujar(<PantallaDeSaldo cuentaId={CUENTA} />)
    await waitFor(() => expect(screen.getByLabelText('Volver a intentar')).toBeTruthy())
    expect(screen.getByText(/Código de seguimiento:/)).toBeTruthy()
  })

  it('sin red: no se queda cargando para siempre', async () => {
    fijarEscenario('consultarSaldo', { tipo: 'sinRed' })
    await dibujar(<PantallaDeSaldo cuentaId={CUENTA} />)
    await waitFor(() => expect(screen.getByLabelText('Volver a intentar')).toBeTruthy())
  })
})
