import { manejadoresDeTodos } from '@aportaya/simulado'
// `msw/native` y no `msw/node`: el corredor resuelve modulos con las
// condiciones de React Native, donde el punto de entrada de node no existe.
import { setupServer } from 'msw/native'

/**
 * Un unico servidor simulado para todas las pruebas de componente, con los
 * **mismos** manejadores que ve la app en desarrollo. Si cada prueba armara su
 * mock, estaria probando su mock.
 *
 * Una prueba que necesita un caso que el contrato no expresa lo agrega con
 * `servidorDePruebas.use(...)`, y `preparar.tsx` lo revierte al terminar.
 */
export const servidorDePruebas = setupServer(...manejadoresDeTodos())
