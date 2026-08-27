/**
 * Cómo se lee un indicador. Funciones puras, probadas aparte.
 *
 * Formatear **no es calcular**: acá no se deriva ningún valor, solo se lo presenta.
 * La única regla de negocio que vive en este archivo es cuándo hay semáforo y cuándo
 * no, y está acá porque es una decisión de presentación: sin meta no se pinta nada.
 */
export type Semaforo = 'CUMPLE' | 'NO_CUMPLE' | 'SIN_META'

export function semaforoDe(cumpleMeta: boolean | null | undefined): Semaforo {
  // `undefined` y `null` son lo mismo acá: no hay meta fijada para el período. Y sin
  // meta no hay semáforo — ponerla después de ver el resultado no es medir.
  if (cumpleMeta === null || cumpleMeta === undefined) return 'SIN_META'
  return cumpleMeta ? 'CUMPLE' : 'NO_CUMPLE'
}

/**
 * El valor con su unidad. Los importes llegan como cadena decimal y **se muestran tal
 * cual**: un cliente que redondea produce un número que no es el del libro contable.
 */
export function valorLegible(
  valor: string | null | undefined,
  unidad: string | undefined,
  moneda?: string | null,
): string {
  if (valor === null || valor === undefined) return '—'
  switch (unidad) {
    case 'PORCENTAJE':
      return `${valor} %`
    case 'MONTO':
      return `${moneda ?? 'BOB'} ${valor}`
    case 'DIAS':
      return `${valor} días`
    default:
      return valor
  }
}

/**
 * La variación, con su signo.
 *
 * `null` no es cero: un indicador nuevo **no tiene** comparación, y mostrar «0 %»
 * afirmaría que se mantuvo igual. Son dos cosas distintas y se ven distinto.
 */
export function variacionLegible(variacion: string | null | undefined): { texto: string; sentido: 'sube' | 'baja' | 'neutro' } {
  if (variacion === null || variacion === undefined || variacion === '') {
    return { texto: 'sin período anterior', sentido: 'neutro' }
  }
  const numero = Number(variacion)
  if (!Number.isFinite(numero) || numero === 0) return { texto: `${variacion} %`, sentido: 'neutro' }
  return { texto: `${numero > 0 ? '+' : ''}${variacion} %`, sentido: numero > 0 ? 'sube' : 'baja' }
}

/**
 * Por qué no hay número.
 *
 * Un promedio de tres personas identifica a las tres (`R-SEG-03`). Cuando la muestra
 * no alcanza, **no se muestra el valor y se dice por qué**: un hueco sin explicación
 * se lee como una falla del sistema, y alguien abre una incidencia por algo que
 * funciona como debe.
 */
export function motivoDeSupresion(casos: number | null | undefined): string {
  const cuantos = casos ?? 0
  return `No se muestra porque la muestra es de ${cuantos} caso${cuantos === 1 ? '' : 's'}: con tan pocos, el promedio identificaría a las personas que lo componen.`
}
