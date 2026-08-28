import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Boton } from '../../src/web/Boton'
import { ChipEstado } from '../../src/web/ChipEstado'
import { Monto } from '../../src/web/Monto'

describe('Monto', () => {
  it('muestra la cifra formateada y la anuncia con su concepto', () => {
    render(<Monto valor={{ monto: '1240.00', moneda: 'BOB' }} etiqueta="Saldo disponible" />)
    const monto = screen.getByLabelText('Saldo disponible: Bs 1.240,00')
    expect(monto).toHaveTextContent('Bs 1.240,00')
  })

  it('el signo se lee aunque no se vea el color', () => {
    render(<Monto valor={{ monto: '-80.00', moneda: 'BOB' }} sentido="sale" etiqueta="Comisión" />)
    // El `-` esta en el TEXTO, no solo en la clase: quien no distingue rojo de verde
    // tiene que poder leer la direccion igual.
    expect(screen.getByLabelText(/Comisión/)).toHaveTextContent('-Bs 80,00')
  })
})

describe('Boton', () => {
  it('cargando bloquea el segundo envío', async () => {
    const alPulsar = vi.fn()
    const { rerender } = render(<Boton onClick={alPulsar}>Pagar</Boton>)
    screen.getByRole('button', { name: 'Pagar' }).click()
    expect(alPulsar).toHaveBeenCalledTimes(1)

    // El mismo boton, ahora esperando la respuesta. Un segundo toque no puede
    // producir un segundo cobro (invariante 6).
    rerender(
      <Boton onClick={alPulsar} cargando>
        Pagar
      </Boton>,
    )
    const boton = screen.getByRole('button', { name: 'Pagar' })
    expect(boton).toBeDisabled()
    expect(boton).toHaveAttribute('aria-busy', 'true')
    boton.click()
    expect(alPulsar).toHaveBeenCalledTimes(1)
  })

  it('nace como `button` y no como `submit`', () => {
    // Un boton sin `type` dentro de un formulario lo envia. Es el defecto clasico
    // que manda un aporte al pulsar «cancelar».
    render(<Boton>Cancelar</Boton>)
    expect(screen.getByRole('button')).toHaveAttribute('type', 'button')
  })
})

describe('ChipEstado', () => {
  it('el estado se lee, no solo se ve', () => {
    render(<ChipEstado tono="error">Rechazado</ChipEstado>)
    expect(screen.getByText('Rechazado')).toBeInTheDocument()
  })
})
