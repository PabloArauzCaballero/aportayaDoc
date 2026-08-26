import { useQuery } from '@tanstack/react-query'
import type { SaldoBilletera } from 'clientes/typescript/nucleo-financiero/models'
import { llamar } from './gateway'
import { ErrorDeApi } from './errores'

/**
 * `consultarSaldo` del contrato de `nucleo-financiero`.
 *
 * El tipo viene del cliente **generado**: no se declara a mano. Si el contrato
 * cambia y esta pantalla ya no encaja, deja de compilar — que es exactamente
 * cuando conviene enterarse.
 *
 * El saldo **no se calcula aca**: se deriva del libro contable y llega ya hecho.
 */
export const CLAVE_SALDO = 'saldo-de-billetera'

export function useSaldo(cuentaId: string) {
  return useQuery<SaldoBilletera, ErrorDeApi | Error>({
    queryKey: [CLAVE_SALDO, cuentaId],
    queryFn: ({ signal }) => llamar<SaldoBilletera>(`/billetera/${cuentaId}/saldo`, { senal: signal }),
    enabled: cuentaId.length > 0,
    // La app abre mostrando el ultimo estado conocido y dice de cuando es; una
    // billetera en blanco cada vez que se pierde la senal no ayuda a nadie.
    staleTime: 30_000,
    // Nada de reintentos silenciosos sobre dinero: el reintento es del usuario y
    // es visible.
    retry: false,
  })
}
