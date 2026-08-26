import { screen } from '@testing-library/react-native'
import { PantallaDeSaldo } from '../../src/pantallas/PantallaDeSaldo'
import { dibujar } from '../dibujar'

/**
 * Accesibilidad, como error y no como advertencia (planes/11 F0.4).
 *
 * Una billetera la usa gente de todas las edades: si el monto no esta anunciado
 * o el boton de reintentar no se alcanza con el dedo, la pantalla no esta hecha.
 */
const CUENTA = '11111111-1111-4111-8111-111111111111'

describe('PantallaDeSaldo · accesibilidad', () => {
  it('el importe se anuncia con su concepto, no como un numero suelto', async () => {
    await dibujar(<PantallaDeSaldo cuentaId={CUENTA} />)
    expect(await screen.findByLabelText(/^Saldo disponible: /)).toBeTruthy()
    expect(screen.getByLabelText(/^Saldo retenido: /)).toBeTruthy()
  })

  it('el encabezado esta marcado como encabezado', async () => {
    await dibujar(<PantallaDeSaldo cuentaId={CUENTA} />)
    expect(await screen.findByRole('header', { name: 'Disponible' })).toBeTruthy()
  })
})
