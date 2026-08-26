import { useQuery } from '@tanstack/react-query'
import type { SaldoBilletera } from 'clientes/typescript/nucleo-financiero/models'
import { llamar } from './gateway'
import type { ErrorDeApi } from './errores'

/**
 * `consultarSaldo` del contrato de `nucleo-financiero`, visto desde soporte.
 *
 * El tipo viene del cliente **generado**: no se declara a mano. Si el contrato
 * cambia y esta pantalla ya no encaja, deja de compilar — que es exactamente cuando
 * conviene enterarse.
 */
export const CLAVE_SALDO = 'saldo-de-billetera'

export function useSaldo(cuentaId: string) {
  return useQuery<SaldoBilletera, ErrorDeApi | Error>({
    queryKey: [CLAVE_SALDO, cuentaId],
    queryFn: ({ signal }) => llamar<SaldoBilletera>(`/billetera/${cuentaId}/saldo`, { senal: signal }),
    enabled: cuentaId.length > 0,
    // Un saldo cacheado en una consulta de soporte es un saldo que se le lee al
    // titular por telefono: se refresca al volver a la pantalla.
    staleTime: 0,
    retry: false,
  })
}
