import { contratoDe, muestraDe, type Esquema } from '@aportaya/simulado'

/**
 * El generador de respuestas del simulado, probado donde se consume.
 *
 * Es la pieza de la que dependen las pantallas de los tres productos: si deja de
 * ser determinista, las pruebas de todo el frontend se vuelven un sorteo; si
 * deja de respetar el patron, el simulado miente sobre el contrato.
 */
const nucleo = contratoDe('nucleo-financiero')
const saldo = { $ref: '#/components/schemas/SaldoBilletera' } as Esquema
const dinero = { $ref: '#/components/schemas/Dinero' } as Esquema

describe('muestraDe', () => {
  it('es determinista: dos corridas dan exactamente lo mismo', () => {
    expect(muestraDe(nucleo, saldo)).toEqual(muestraDe(nucleo, saldo))
  })

  it('respeta el patron del contrato: el importe es cadena decimal', () => {
    const valor = muestraDe(nucleo, dinero) as { monto: string; moneda: string }
    expect(typeof valor.monto).toBe('string')
    expect(valor.monto).toMatch(/^-?\d+\.\d{2}$/)
    expect(['BOB', 'USD']).toContain(valor.moneda)
  })

  it('el modo representativo no devuelve cero: si lo hiciera, no habria estado de exito', () => {
    const valor = muestraDe(nucleo, dinero, 'raiz', 'representativo') as { monto: string }
    expect(Number(valor.monto)).toBeGreaterThan(0)
  })

  it('el modo minimo devuelve el vacio del contrato, y sigue cumpliendolo', () => {
    const valor = muestraDe(nucleo, dinero, 'raiz', 'minimo') as { monto: string }
    expect(valor.monto).toBe('0.00')
    expect(valor.monto).toMatch(/^-?\d+\.\d{2}$/)
  })

  it('dos campos distintos reciben identificadores distintos', () => {
    const valor = muestraDe(nucleo, saldo) as { cuentaId: string }
    const otro = muestraDe(nucleo, { type: 'string', format: 'uuid' }, 'otra.cosa') as string
    expect(valor.cuentaId).not.toBe(otro)
    expect(valor.cuentaId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
  })

  it('una referencia rota se denuncia, no se completa con lo que sea', () => {
    expect(() => muestraDe(nucleo, { $ref: '#/components/schemas/NoExiste' })).toThrow(/referencia rota/)
  })
})
