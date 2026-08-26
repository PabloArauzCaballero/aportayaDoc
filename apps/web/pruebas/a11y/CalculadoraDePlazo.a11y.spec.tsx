import { axe } from 'jest-axe'
import { render, screen } from '@testing-library/react'
import { fijarEscenario } from '@aportaya/simulado'
import { describe, expect, it } from 'vitest'
import { CalculadoraDePlazo } from '../../src/componentes/CalculadoraDePlazo'

/** Accesibilidad como **error**, también en el estado que nadie revisa: el de error. */
describe('CalculadoraDePlazo · accesibilidad', () => {
  it('no tiene violaciones en el estado de éxito', async () => {
    const { container } = render(<CalculadoraDePlazo />)
    await screen.findByText(/el plazo vence/i)
    expect((await axe(container)).violations).toEqual([])
  })

  it('no tiene violaciones en el estado de error', async () => {
    fijarEscenario('calcularPlazoHabil', { tipo: 'error' })
    const { container } = render(<CalculadoraDePlazo />)
    await screen.findByRole('button', { name: 'Volver a intentar' })
    expect((await axe(container)).violations).toEqual([])
  })

  it('el estado de carga se anuncia a un lector de pantalla', () => {
    fijarEscenario('calcularPlazoHabil', { tipo: 'demora', ms: 60 })
    render(<CalculadoraDePlazo />)
    // `aria-live` y no solo texto: sin eso, quien usa lector no se entera de que la
    // página está haciendo algo y la abandona creyendo que no funciona.
    expect(screen.getByRole('status')).toHaveAttribute('aria-live', 'polite')
  })
})
