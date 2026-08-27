import { useQuery } from '@tanstack/react-query'
import type { Indicador, SalidaTablero } from 'clientes/typescript/auditoria/models'
import { llamar } from './gateway'
import type { ErrorDeApi } from './errores'

/**
 * `publicarTablero` del contrato de `auditoria` (CU-98).
 *
 * El tipo viene del cliente **generado**. Acá no se calcula ningún indicador: el
 * valor, la meta y la variación salen del servicio. Calcular uno en el frontend sería
 * crear un segundo indicador, y entonces dos personas llegan a la reunión con números
 * distintos de la misma cosa.
 */
export type { Indicador, SalidaTablero }

export type ConsultaDeTablero = {
  periodo: string
  dimension: 'PLATAFORMA' | 'GRUPO' | 'ORGANIZADOR' | 'PRODUCTO'
  dimensionId?: string
}

export const CLAVE_TABLERO = 'tablero-de-indicadores'

export function useTablero(consulta: ConsultaDeTablero) {
  const parametros = new URLSearchParams({ periodo: consulta.periodo, dimension: consulta.dimension })
  if (consulta.dimensionId) parametros.set('dimensionId', consulta.dimensionId)

  return useQuery<SalidaTablero, ErrorDeApi | Error>({
    queryKey: [CLAVE_TABLERO, consulta.periodo, consulta.dimension, consulta.dimensionId ?? null],
    queryFn: ({ signal }) => llamar<SalidaTablero>(`/indicadores?${parametros}`, { senal: signal }),
    // Un tablero es una lectura pesada contra la réplica: no se refresca solo.
    staleTime: 5 * 60_000,
    retry: false,
  })
}
