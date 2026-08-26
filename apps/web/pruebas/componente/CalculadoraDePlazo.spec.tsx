import { fijarEscenario } from '@aportaya/simulado'
import { render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { CalculadoraDePlazo } from '../../src/componentes/CalculadoraDePlazo'

/**
 * Los cuatro estados de la isla, contra el servidor simulado.
 *
 * La regla no cambia por ser un sitio público: una página que se queda en blanco
 * cuando el servicio no responde es una página rota.
 */
describe('CalculadoraDePlazo · los cuatro estados', () => {
  it('cargando: lo dice mientras espera, sin bloque en blanco', () => {
    fijarEscenario('calcularPlazoHabil', { tipo: 'demora', ms: 60 })
    render(<CalculadoraDePlazo />)
    expect(screen.getByRole('status')).toHaveTextContent('Calculando el plazo')
  })

  it('éxito: muestra la fecha límite y los días que se saltearon', async () => {
    render(<CalculadoraDePlazo />)
    await waitFor(() => expect(screen.getByText(/el plazo vence/i)).toBeInTheDocument())
    expect(screen.getByRole('heading', { name: 'Días que se saltearon' })).toBeInTheDocument()
  })

  it('vacío: sin días salteados lo dice, y aclara que no faltó calcular nada', async () => {
    fijarEscenario('calcularPlazoHabil', { tipo: 'vacio' })
    render(<CalculadoraDePlazo />)
    expect(await screen.findByText(/no se salteó ningún día/i)).toBeInTheDocument()
  })

  it('error: mensaje humano, reintento a la vista y traza para soporte', async () => {
    fijarEscenario('calcularPlazoHabil', { tipo: 'error' })
    render(<CalculadoraDePlazo />)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Volver a intentar' })).toBeInTheDocument())
    expect(screen.getByText(/Código de seguimiento:/)).toBeInTheDocument()
  })

  it('sin red: no se queda calculando para siempre', async () => {
    fijarEscenario('calcularPlazoHabil', { tipo: 'sinRed' })
    render(<CalculadoraDePlazo />)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Volver a intentar' })).toBeInTheDocument())
    // Sin traza: un fallo de red no tiene código ni llegó al backend, y fingir uno
    // mandaría a soporte a buscar en el log una petición que nunca existió.
    expect(screen.queryByText(/Código de seguimiento:/)).not.toBeInTheDocument()
  })
})
