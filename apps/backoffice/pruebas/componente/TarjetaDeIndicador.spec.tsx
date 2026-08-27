import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Indicador } from 'clientes/typescript/auditoria/models'
import { TarjetaDeIndicador } from '../../src/organismos/TarjetaDeIndicador'

/**
 * Las dos afordancias de la tarjeta: ampliar y «qué es esto».
 *
 * Se prueban con teclado y por nombre accesible, no por clase CSS: si el botón no se
 * alcanza tabulando ni se anuncia con su nombre, para quien opera ocho horas con
 * teclado esa función no existe.
 */
const INDICADOR: Indicador = {
  codigo: 'TASA_DE_MOROSIDAD',
  nombre: 'Tasa de morosidad',
  valor: '7.40',
  unidad: 'PORCENTAJE',
  familia: 'RIESGO',
  meta: '5.00',
  cumpleMeta: false,
  variacionPeriodoAnterior: '1.20',
  suprimidoPorPrivacidad: false,
  duenoFamilia: 'Gerencia de riesgos',
  definicionVersion: 'v3',
  serie: [
    { periodo: '2025-11', valor: '5.10' },
    { periodo: '2025-12', valor: '6.20' },
    { periodo: '2026-01', valor: '7.40' },
  ],
} as Indicador

describe('TarjetaDeIndicador', () => {
  it('el botón de ampliar abre la serie a pantalla completa, con la tabla al lado del gráfico', async () => {
    const usuario = userEvent.setup()
    render(<TarjetaDeIndicador indicador={INDICADOR} provisorio={false} />)

    await usuario.click(screen.getByRole('button', { name: /Ampliar Tasa de morosidad/ }))

    const dialogo = screen.getByRole('dialog', { name: 'Tasa de morosidad' })
    // Un gráfico no se puede citar en un acta: la serie va también en números.
    expect(within(dialogo).getByRole('table')).toBeInTheDocument()
    expect(within(dialogo).getByText('2025-11')).toBeInTheDocument()
  })

  it('el botón «i» explica qué mide, cómo se calcula y de dónde sale', async () => {
    const usuario = userEvent.setup()
    render(<TarjetaDeIndicador indicador={INDICADOR} provisorio={false} />)

    await usuario.click(screen.getByRole('button', { name: /Qué es Tasa de morosidad/ }))

    const dialogo = screen.getByRole('dialog', { name: /Qué es Tasa de morosidad/ })
    expect(within(dialogo).getByText('Qué mide')).toBeInTheDocument()
    expect(within(dialogo).getByText('Cómo se calcula')).toBeInTheDocument()
    expect(within(dialogo).getByText('De dónde sale el dato')).toBeInTheDocument()
    expect(within(dialogo).getByText(/obligacion_aporte/)).toBeInTheDocument()
  })

  it('una definición sin revisar del dueño se muestra marcada como propuesta', async () => {
    const usuario = userEvent.setup()
    render(<TarjetaDeIndicador indicador={INDICADOR} provisorio={false} />)

    await usuario.click(screen.getByRole('button', { name: /Qué es Tasa de morosidad/ }))

    expect(screen.getByText(/Pendiente de revisión/)).toBeInTheDocument()
  })

  it('Escape cierra el diálogo: no hay que buscar la cruz con el mouse', async () => {
    const usuario = userEvent.setup()
    render(<TarjetaDeIndicador indicador={INDICADOR} provisorio={false} />)

    await usuario.click(screen.getByRole('button', { name: /Ampliar Tasa de morosidad/ }))
    expect(screen.getByRole('dialog', { name: 'Tasa de morosidad' })).toBeInTheDocument()

    await usuario.keyboard('{Escape}')
    expect(screen.queryByRole('dialog', { name: 'Tasa de morosidad' })).not.toBeInTheDocument()
  })

  it('un indicador que no cumple su meta y no tiene explicación lo dice, no lo maquilla', async () => {
    const usuario = userEvent.setup()
    render(<TarjetaDeIndicador indicador={INDICADOR} provisorio={false} />)

    await usuario.click(screen.getByRole('button', { name: /Ampliar Tasa de morosidad/ }))

    expect(screen.getByText(/todavía no tiene explicación del dueño/)).toBeInTheDocument()
  })

  it('un valor suprimido por privacidad explica por qué no hay número, también al ampliar', async () => {
    const usuario = userEvent.setup()
    // `valor` ausente y no `null`: es como viaja en el contrato cuando se suprime.
    const suprimido: Indicador = { ...INDICADOR, valor: undefined, suprimidoPorPrivacidad: true, casos: 2 }
    render(<TarjetaDeIndicador indicador={suprimido} provisorio={false} />)

    expect(screen.getByText(/la muestra es de 2 casos/)).toBeInTheDocument()

    // Ampliar no es una puerta trasera al valor suprimido: la vista grande también
    // muestra el guion. Si acá apareciera el número, la supresión sería decorativa.
    await usuario.click(screen.getByRole('button', { name: /Ampliar Tasa de morosidad/ }))
    const dialogo = screen.getByRole('dialog', { name: 'Tasa de morosidad' })
    expect(within(dialogo).getByText('—')).toBeInTheDocument()
  })

  it('un tablero provisorio lo dice en cada tarjeta', () => {
    render(<TarjetaDeIndicador indicador={INDICADOR} provisorio />)
    expect(screen.getByText('Provisorio')).toBeInTheDocument()
  })
})
