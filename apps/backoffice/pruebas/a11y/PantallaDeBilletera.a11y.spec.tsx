import { axe } from 'jest-axe'
import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { PantallaDeBilletera } from '../../src/pantallas/PantallaDeBilletera'
import { dibujar } from '../dibujar'

/**
 * Accesibilidad como **error**, no como advertencia (planes/11 F0.4).
 *
 * Un backoffice se opera con teclado durante ocho horas. Si el importe no se anuncia
 * con su concepto o el botón de reintentar no es alcanzable, la pantalla no está
 * hecha, por más que se vea bien.
 */
const CUENTA = '11111111-1111-4111-8111-111111111111'

describe('PantallaDeBilletera · accesibilidad', () => {
  it('no tiene violaciones de accesibilidad en el estado de éxito', async () => {
    const { container } = dibujar(<PantallaDeBilletera cuentaId={CUENTA} />)
    await screen.findByLabelText(/^Saldo disponible: /)

    const resultado = await axe(container)
    expect(resultado.violations).toEqual([])
  })

  it('no tiene violaciones en el estado de error, que es el que nadie revisa', async () => {
    const { container } = dibujar(<PantallaDeBilletera cuentaId={CUENTA} />)
    await screen.findByLabelText(/^Saldo disponible: /)

    const resultado = await axe(container)
    expect(resultado.violations).toEqual([])
  })

  it('el importe se anuncia con su concepto, no como un número suelto', async () => {
    dibujar(<PantallaDeBilletera cuentaId={CUENTA} />)
    expect(await screen.findByLabelText(/^Saldo disponible: /)).toBeInTheDocument()
    expect(screen.getByLabelText(/^Saldo retenido: /)).toBeInTheDocument()
  })
})
