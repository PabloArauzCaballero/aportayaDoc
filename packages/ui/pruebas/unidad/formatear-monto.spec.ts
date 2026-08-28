import { describe, expect, it } from 'vitest'
import { desformatearMonto, formatearMonto, prefijoDe } from '../../src/dinero/formatear'

describe('formatearMonto', () => {
  it('arma la cifra como la maqueta la muestra', () => {
    expect(formatearMonto({ monto: '1240.00', moneda: 'BOB' })).toBe('Bs 1.240,00')
    expect(formatearMonto({ monto: '48750.00', moneda: 'BOB' })).toBe('Bs 48.750,00')
    expect(formatearMonto({ monto: '250.00', moneda: 'BOB' })).toBe('Bs 250,00')
    expect(formatearMonto({ monto: '0.00', moneda: 'BOB' })).toBe('Bs 0,00')
  })

  it('agrupa de a tres tambien cuando hay millones', () => {
    expect(formatearMonto({ monto: '1234567.89', moneda: 'BOB' })).toBe('Bs 1.234.567,89')
    expect(formatearMonto({ monto: '1000.00', moneda: 'BOB' })).toBe('Bs 1.000,00')
    expect(formatearMonto({ monto: '999.99', moneda: 'BOB' })).toBe('Bs 999,99')
  })

  it('deja el signo delante, donde se lee primero', () => {
    expect(formatearMonto({ monto: '-1240.50', moneda: 'BOB' })).toBe('-Bs 1.240,50')
  })

  it('usa el codigo ISO para el dolar, que no es ambiguo', () => {
    expect(formatearMonto({ monto: '10.00', moneda: 'USD' })).toBe('USD 10,00')
    expect(prefijoDe('BOB')).toBe('Bs')
  })

  it('no conserva ceros que el contrato no trae ni los inventa', () => {
    // El contrato exige exactamente dos decimales; `1.5` no es un importe valido.
    expect(() => formatearMonto({ monto: '1.5', moneda: 'BOB' })).toThrow(/fuera del contrato/)
    expect(() => formatearMonto({ monto: '1240', moneda: 'BOB' })).toThrow(/fuera del contrato/)
    expect(() => formatearMonto({ monto: '1.234,00', moneda: 'BOB' })).toThrow(/fuera del contrato/)
    expect(() => formatearMonto({ monto: '', moneda: 'BOB' })).toThrow(/fuera del contrato/)
  })

  /**
   * La prueba que pide el gate de F1: **`Monto` nunca pierde ni inventa un centavo**.
   *
   * No se comprueba con tres ejemplos elegidos a mano —esos son justamente los que
   * uno acierta—: se generan miles de importes y se exige que formatear y deshacer
   * devuelva exactamente la cadena que entro. Si algun dia alguien mete un `Number`
   * en el camino, el primer importe con mas de quince digitos lo delata.
   */
  it('propiedad: formatear y deshacer devuelve el mismo importe, centavo por centavo', () => {
    // Deterministico a proposito: una prueba que cambia entre corridas es un sorteo.
    let semilla = 20260828
    const siguiente = () => {
      semilla = (semilla * 1103515245 + 12345) % 2147483648
      return semilla / 2147483648
    }
    for (let i = 0; i < 5000; i += 1) {
      const digitos = 1 + Math.floor(siguiente() * 18)
      let enteros = String(1 + Math.floor(siguiente() * 9))
      for (let d = 1; d < digitos; d += 1) enteros += String(Math.floor(siguiente() * 10))
      const centavos = String(Math.floor(siguiente() * 100)).padStart(2, '0')
      const signo = siguiente() < 0.2 ? '-' : ''
      const original = `${signo}${enteros}.${centavos}`
      expect(desformatearMonto(formatearMonto({ monto: original, moneda: 'BOB' }))).toBe(original)
    }
  })
})
