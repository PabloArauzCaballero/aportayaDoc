/**
 * El formateo de importes, en un solo lugar del proyecto.
 *
 * Invariante 5 del frontend: **ningún importe se formatea a mano**. `toFixed`,
 * `Intl.NumberFormat` y concatenar `'Bs '` están prohibidos fuera de este módulo y
 * del átomo `Monto` que lo usa (`planes/10` §6, regla `aportaya/sin-formato-de-dinero`).
 *
 * ## Por qué es puro trabajo de cadenas
 *
 * El contrato define el importe como **cadena**, no como número:
 * `monto: { type: string, pattern: '^-?\d+\.\d{2}$' }`. El comentario del propio
 * contrato dice por qué: «CADENA: un `number` JSON es un doble». Pasar por `Number`
 * para volver a texto reintroduce exactamente el error que el contrato evita, y en
 * una billetera esa diferencia siempre aparece en el peor momento. Acá **no se
 * convierte a número en ningún punto**: se separan los enteros de los centavos, se
 * agrupan los miles y se vuelve a unir. Un centavo que entra es un centavo que sale,
 * y hay una prueba de propiedad que lo comprueba sobre miles de valores.
 */

/** El importe tal como viaja en el contrato. */
export type ImporteDelContrato = { monto: string; moneda: string }

/** `^-?\d+\.\d{2}$` — la misma forma que declara el esquema `Dinero` de los OpenAPI. */
const FORMA_DEL_CONTRATO = /^-?\d+\.\d{2}$/

/**
 * El prefijo por moneda. `Bs` es el del boliviano en el habla y en la maqueta
 * (`docs/Views/AportaYa-Maqueta.html`). Para el dólar se usa el código ISO y no
 * `$us`: en una pantalla de dinero, un símbolo ambiguo es un riesgo, no un ahorro.
 */
const PREFIJO: Readonly<Record<string, string>> = { BOB: 'Bs', USD: 'USD' }

export function prefijoDe(moneda: string): string {
  return PREFIJO[moneda] ?? moneda
}

/** Agrupa de a tres desde la derecha con `.`, sobre la cadena de enteros. */
function agruparMiles(enteros: string): string {
  let salida = ''
  for (let i = 0; i < enteros.length; i += 1) {
    // La posición contada desde la derecha; cada tres, un punto — salvo al inicio.
    const desdeLaDerecha = enteros.length - i
    if (i > 0 && desdeLaDerecha % 3 === 0) salida += '.'
    salida += enteros[i]
  }
  return salida
}

/**
 * `{ monto: '1240.00', moneda: 'BOB' }` → `'Bs 1.240,00'`.
 *
 * **Lanza** si el importe no tiene la forma del contrato. No es rigidez: un importe
 * deforme significa que la respuesta no encaja en su esquema, y mostrarlo a medias en
 * una billetera es peor que fallar. La capa de dominio ya valida la respuesta contra
 * el OpenAPI; esto es el último cinturón.
 */
export function formatearMonto(valor: ImporteDelContrato): string {
  if (!FORMA_DEL_CONTRATO.test(valor.monto)) {
    throw new Error(
      `Importe fuera del contrato: "${valor.monto}". El esquema Dinero exige ^-?\\d+\\.\\d{2}$.`,
    )
  }
  const negativo = valor.monto.startsWith('-')
  const sinSigno = negativo ? valor.monto.slice(1) : valor.monto
  const punto = sinSigno.indexOf('.')
  const enteros = sinSigno.slice(0, punto)
  const centavos = sinSigno.slice(punto + 1)
  const signo = negativo ? '-' : ''
  return `${signo}${prefijoDe(valor.moneda)} ${agruparMiles(enteros)},${centavos}`
}

/**
 * El camino de vuelta, **solo para pruebas y para la prueba de propiedad**: deshace
 * el formateo y devuelve la cadena del contrato. Existe para que «no se pierde ni se
 * inventa un centavo» sea una afirmación verificable y no una promesa.
 */
export function desformatearMonto(texto: string): string {
  const negativo = texto.startsWith('-')
  const cuerpo = (negativo ? texto.slice(1) : texto).replace(/^\S+\s/, '')
  const enteros = cuerpo.slice(0, cuerpo.indexOf(',')).replaceAll('.', '')
  const centavos = cuerpo.slice(cuerpo.indexOf(',') + 1)
  return `${negativo ? '-' : ''}${enteros}.${centavos}`
}

/**
 * Cómo se lee el importe en voz alta. Un lector de pantalla que dice «be ese uno
 * punto dos cuatro cero» no informó nada: el importe se anuncia con su concepto y con
 * la moneda dicha entera.
 */
export function montoParaLectura(valor: ImporteDelContrato, etiqueta?: string): string {
  const texto = formatearMonto(valor)
  return etiqueta ? `${etiqueta}: ${texto}` : texto
}
