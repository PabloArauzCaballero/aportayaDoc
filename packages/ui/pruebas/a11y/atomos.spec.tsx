import { render } from '@testing-library/react'
import { axe } from 'jest-axe'
import { describe, expect, it } from 'vitest'
import { Boton } from '../../src/web/Boton'
import { ChipEstado } from '../../src/web/ChipEstado'
import { Monto } from '../../src/web/Monto'

describe('accesibilidad de los átomos', () => {
  it('sin violaciones serias, pieza por pieza', async () => {
    const { container } = render(
      <main>
        <h1>Catálogo</h1>
        <Monto valor={{ monto: '1240.00', moneda: 'BOB' }} etiqueta="Saldo" tamano="titular" />
        <Boton variante="primario">Aportar</Boton>
        <Boton variante="secundario" cargando>
          Enviando
        </Boton>
        <Boton variante="fantasma" disabled>
          Cancelar
        </Boton>
        <ChipEstado tono="ok">Al día</ChipEstado>
      </main>,
    )
    const resultado = await axe(container)
    expect(resultado.violations).toEqual([])
  })
})
