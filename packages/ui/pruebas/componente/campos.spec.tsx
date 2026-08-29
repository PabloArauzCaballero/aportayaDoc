import { fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { AreaDeTexto } from '../../src/web/AreaDeTexto'
import { Campo } from '../../src/web/Campo'
import { CampoContrasena } from '../../src/web/CampoContrasena'
import { CampoMonto } from '../../src/web/CampoMonto'
import { CampoOTP } from '../../src/web/CampoOTP'
import { Paso } from '../../src/web/Paso'

describe('Campo', () => {
  it('el error se enlaza al campo y se anuncia solo', () => {
    render(<Campo id="ci" estado="error" ayuda="Ese número ya está registrado" aria-label="Documento" />)
    const campo = screen.getByLabelText('Documento')
    // `aria-invalid` sin `aria-describedby` deja al lector diciendo «invalido» y
    // nada mas: el motivo tiene que viajar con el campo.
    expect(campo).toHaveAttribute('aria-invalid', 'true')
    expect(campo).toHaveAccessibleDescription('Ese número ya está registrado')
    expect(screen.getByRole('alert')).toHaveTextContent('Ese número ya está registrado')
  })

  it('la ayuda que no es error no interrumpe', () => {
    render(<Campo id="tel" ayuda="Con código de país" aria-label="Teléfono" />)
    expect(screen.queryByRole('alert')).toBeNull()
  })
})

describe('CampoMonto', () => {
  it('trae el teclado decimal y el prefijo de la moneda, y nunca es type=number', () => {
    render(<CampoMonto aria-label="Monto a aportar" />)
    const campo = screen.getByLabelText('Monto a aportar')
    expect(campo).toHaveAttribute('inputmode', 'decimal')
    // `type="number"` cambia el importe con la rueda del mouse. En dinero, no.
    expect(campo).toHaveAttribute('type', 'text')
    expect(screen.getByText('Bs')).toBeInTheDocument()
  })

  it('el dólar se dice USD, no con un símbolo ambiguo', () => {
    render(<CampoMonto moneda="USD" aria-label="Monto" />)
    expect(screen.getByText('USD')).toBeInTheDocument()
  })
})

describe('CampoContrasena', () => {
  it('el ojo cambia el type y dice en qué estado está', () => {
    render(<CampoContrasena id="clave" aria-label="Contraseña" />)
    expect(screen.getByLabelText('Contraseña')).toHaveAttribute('type', 'password')

    const ojo = screen.getByRole('button', { name: 'Mostrar la contraseña' })
    expect(ojo).toHaveAttribute('aria-pressed', 'false')
    fireEvent.click(ojo)

    expect(screen.getByLabelText('Contraseña')).toHaveAttribute('type', 'text')
    expect(screen.getByRole('button', { name: 'Ocultar la contraseña' })).toHaveAttribute('aria-pressed', 'true')
  })
})

describe('CampoOTP', () => {
  function Controlado() {
    const [codigo, escribir] = useState('')
    return <CampoOTP valor={codigo} alCambiar={escribir} digitos={4} etiqueta="Código de verificación" />
  }

  it('el foco salta solo al escribir: nadie tabula cuatro veces', () => {
    render(<Controlado />)
    const casillas = screen.getAllByRole('textbox')
    fireEvent.change(casillas[0]!, { target: { value: '7' } })
    expect(casillas[1]).toHaveFocus()
  })

  it('cada casilla dice cuál es, no las cuatro «código»', () => {
    render(<Controlado />)
    expect(screen.getByLabelText('Código de verificación, dígito 3 de 4')).toBeInTheDocument()
  })

  it('solo entran dígitos', () => {
    const escribir = vi.fn()
    render(<CampoOTP valor="" alCambiar={escribir} digitos={4} etiqueta="Código" />)
    fireEvent.change(screen.getAllByRole('textbox')[0]!, { target: { value: 'a' } })
    expect(escribir).toHaveBeenCalledWith('')
  })
})

describe('Paso', () => {
  it('no baja del mínimo ni sube del máximo', () => {
    const cambiar = vi.fn()
    render(<Paso valor={2} alCambiar={cambiar} minimo={2} maximo={12} etiqueta="Participantes" />)
    expect(screen.getByRole('button', { name: 'Restar uno a Participantes' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Sumar uno a Participantes' }))
    expect(cambiar).toHaveBeenCalledWith(3)
  })

  it('el valor se anuncia con su rango', () => {
    render(<Paso valor={6} alCambiar={vi.fn()} minimo={2} maximo={12} etiqueta="Participantes" />)
    const cifra = screen.getByRole('spinbutton', { name: 'Participantes' })
    expect(cifra).toHaveAttribute('aria-valuenow', '6')
    expect(cifra).toHaveAttribute('aria-valuemax', '12')
  })
})

describe('AreaDeTexto', () => {
  it('el error también se enlaza acá', () => {
    render(<AreaDeTexto id="motivo" estado="error" ayuda="Contá qué pasó" aria-label="Motivo" />)
    expect(screen.getByLabelText('Motivo')).toHaveAccessibleDescription('Contá qué pasó')
  })
})
