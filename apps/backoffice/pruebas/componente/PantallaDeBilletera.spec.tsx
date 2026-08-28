import { fijarEscenario } from '@aportaya/simulado'
import { screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { PantallaDeBilletera } from '../../src/pantallas/PantallaDeBilletera'
import { dibujar } from '../dibujar'

/**
 * Los cuatro estados de la pantalla real de la fase F0, contra el servidor simulado
 * —el mismo que ve el backoffice en desarrollo—.
 *
 * En un backoffice el estado vacío importa el doble: «la cuenta está en cero» y «la
 * consulta falló» se leen distinto y llevan a acciones distintas.
 */
const CUENTA = '11111111-1111-4111-8111-111111111111'

describe('PantallaDeBilletera · los cuatro estados', () => {
  it('cargando: lo dice mientras espera, sin pantalla en blanco', () => {
    fijarEscenario('consultarSaldo', { tipo: 'demora', ms: 60 })
    dibujar(<PantallaDeBilletera cuentaId={CUENTA} />)
    expect(screen.getByRole('status')).toHaveTextContent('Cargando el saldo de la cuenta')
  })

  it('éxito: muestra el saldo que respondió el contrato, sin recalcular nada', async () => {
    dibujar(<PantallaDeBilletera cuentaId={CUENTA} />)
    const disponible = await screen.findByLabelText(/^Saldo disponible: /)
    // Desde F1 el importe se presenta como lo muestra la maqueta —`Bs 1.240,00`—, no
    // como viaja en el contrato. Sigue sin recalcularse: `Monto` reagrupa la cadena
    // que respondio el servidor y no la convierte a numero en ningun punto.
    expect(disponible.getAttribute('aria-label')).toMatch(/^Saldo disponible: (Bs|USD) -?[\d.]+,\d{2}$/)
  })

  it('vacío: una cuenta en cero lo dice, y aclara que no es una falla de consulta', async () => {
    fijarEscenario('consultarSaldo', { tipo: 'vacio' })
    dibujar(<PantallaDeBilletera cuentaId={CUENTA} />)
    expect(await screen.findByText('La cuenta está en cero')).toBeInTheDocument()
    expect(screen.getByText(/no es un error de consulta/i)).toBeInTheDocument()
  })

  it('error: mensaje humano, reintento a la vista y traza para soporte', async () => {
    fijarEscenario('consultarSaldo', { tipo: 'error', estado: 401 })
    dibujar(<PantallaDeBilletera cuentaId={CUENTA} />)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Volver a intentar' })).toBeInTheDocument())
    expect(screen.getByText(/Código de seguimiento:/)).toBeInTheDocument()
  })

  it('sin red: no se queda cargando para siempre', async () => {
    fijarEscenario('consultarSaldo', { tipo: 'sinRed' })
    dibujar(<PantallaDeBilletera cuentaId={CUENTA} />)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Volver a intentar' })).toBeInTheDocument())
  })

  it('un 403 no dice más que «no tenés acceso»: detallarlo confirma que el recurso existe', async () => {
    fijarEscenario('consultarSaldo', { tipo: 'error', estado: 403 })
    dibujar(<PantallaDeBilletera cuentaId={CUENTA} />)
    expect(await screen.findByText('No tenés acceso a esto.')).toBeInTheDocument()
  })
})
