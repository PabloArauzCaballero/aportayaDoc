import { HttpResponse, http } from 'msw'
import { renderHook, waitFor } from '@testing-library/react-native'
import { useRegistro } from '../../src/dominio/registro'
import { clienteDePrueba, envoltorioCon } from '../dibujar'
import { servidorDePruebas } from '../servidorDePruebas'

/**
 * El invariante 7 visto desde el cliente: **la misma clave en el reintento**.
 *
 * El usuario en mala senal toca dos veces. Si el segundo toque lleva una clave
 * nueva, el backend abre dos cuentas y ya no hay nada que lo impida despues.
 */
const ENTRADA = {
  telefonoE164: '+59170000000',
  nombres: 'Ana',
  apellidos: 'Quispe',
  fechaNacimiento: '1995-04-12',
  documento: { tipo: 'CI' as const, numero: '1234567' },
  aceptaContratos: ['11111111-1111-4111-8111-111111111111'],
}

describe('CU-01 · clave de idempotencia', () => {
  it('el reintento del mismo registro reutiliza la clave', async () => {
    const claves: string[] = []
    servidorDePruebas.use(
      http.post('*/api/v1/usuarios', ({ request }) => {
        claves.push(request.headers.get('Idempotency-Key') ?? '')
        return HttpResponse.json({ codigo: 'AP-CU01-01', mensaje: 'kyc', trazaId: 'traza' }, { status: 422 })
      }),
    )

    const { result } = await renderHook(() => useRegistro(), { wrapper: envoltorioCon(clienteDePrueba()) })

    result.current.mutate(ENTRADA)
    await waitFor(() => expect(result.current.isError).toBe(true))

    // El segundo toque de la persona sobre el mismo formulario.
    result.current.mutate(ENTRADA)
    await waitFor(() => expect(claves).toHaveLength(2))

    expect(claves[0]).toBeTruthy()
    expect(claves[0]).toBe(claves[1])
  })

  it('toda operacion con efecto la envia', async () => {
    const cabeceras: (string | null)[] = []
    servidorDePruebas.use(
      http.post('*/api/v1/usuarios', ({ request }) => {
        cabeceras.push(request.headers.get('Idempotency-Key'))
        return HttpResponse.json({ codigo: 'AP-CU01-01', mensaje: 'kyc', trazaId: 'traza' }, { status: 422 })
      }),
    )

    const { result } = await renderHook(() => useRegistro(), { wrapper: envoltorioCon(clienteDePrueba()) })
    result.current.mutate(ENTRADA)
    await waitFor(() => expect(cabeceras).toHaveLength(1))

    expect(cabeceras[0]).toMatch(/^[0-9a-f-]{36}$/)
  })
})
