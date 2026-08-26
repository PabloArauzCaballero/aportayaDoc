import { useMutation } from '@tanstack/react-query'
import { useRef } from 'react'
import { llamar } from './gateway'
import { nuevoIdentificador } from './identificadores'
import type { ErrorDeApi } from './errores'

/**
 * CU-01 · registro y apertura de billetera. `POST /usuarios` del contrato de
 * `identidad` — la unica ruta publica: es el momento en que todavia no hay sesion.
 *
 * Los tipos del cliente generado todavia no existen para esta operacion porque el
 * borrador de la Fase 0 no la genero con modelos propios; se declaran contra el
 * mismo contrato y se **verifican** en `CU01.contrato.spec.ts`.
 */
export type EntradaRegistro = {
  telefonoE164: string
  nombres: string
  apellidos: string
  fechaNacimiento: string
  documento: { tipo: 'CI' | 'CEX' | 'PASAPORTE'; numero: string }
  aceptaContratos: string[]
}

export type SalidaRegistro = {
  usuarioId: string
  cuentaBilleteraId: string
  nivelDiligencia: 'SIMPLIFICADA' | 'ESTANDAR' | 'AMPLIADA' | 'REFORZADA'
  limites: { concepto: string; ventana: string; monto: { monto: string; moneda: 'BOB' | 'USD' } }[]
}

export function useRegistro() {
  // La clave se genera UNA vez por intento del usuario y se reutiliza en cada
  // reenvio. Generarla dentro de `mutationFn` la haria nueva en cada toque, que
  // es exactamente lo que la idempotencia existe para impedir.
  const clave = useRef<string | null>(null)

  return useMutation<SalidaRegistro, ErrorDeApi | Error, EntradaRegistro>({
    mutationFn: (entrada) => {
      clave.current ??= nuevoIdentificador()
      return llamar<SalidaRegistro>('/usuarios', {
        metodo: 'POST',
        cuerpo: entrada,
        claveIdempotencia: clave.current,
      })
    },
    // Una vez aceptado, el siguiente registro es otro hecho y merece otra clave.
    onSuccess: () => {
      clave.current = null
    },
  })
}
