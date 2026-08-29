import type { PropiedadesDeCampo } from './Campo'
import { Campo } from './Campo'
import { prefijoDe } from '../dinero/formatear'

/**
 * El campo para escribir un importe.
 *
 * **No formatea mientras se escribe.** Reformatear en cada tecla mueve el cursor y
 * pelea con quien está tipeando; el formato es de `Monto`, que es donde se *muestra*
 * dinero. Acá lo que importa es que entre lo correcto: teclado numérico en el
 * teléfono, coma y punto aceptados, y el prefijo de la moneda a la vista para que
 * nadie tenga que adivinar en qué está poniendo la cifra.
 *
 * `inputMode="decimal"` y no `type="number"`: los `number` de HTML traen flechitas
 * que cambian el importe con la rueda del mouse, y en una pantalla de dinero eso es
 * un error esperando ocurrir.
 */
export type PropiedadesDeCampoMonto = Omit<PropiedadesDeCampo, 'addon' | 'type' | 'inputMode'> & {
  moneda?: string
}

export function CampoMonto({ moneda = 'BOB', ...resto }: PropiedadesDeCampoMonto) {
  return (
    <Campo
      {...resto}
      addon={prefijoDe(moneda)}
      type="text"
      inputMode="decimal"
      autoComplete="off"
      placeholder={resto.placeholder ?? '0,00'}
    />
  )
}
