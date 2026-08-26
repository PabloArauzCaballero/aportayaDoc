import { manejadoresDeTodos } from '@aportaya/simulado'
import { setupServer } from 'msw/node'

/**
 * Un unico servidor simulado para todos los corredores, con los **mismos**
 * manejadores que ve el backoffice en desarrollo. Si cada prueba armara su mock,
 * estaria probando su mock.
 */
export const servidorDePruebas = setupServer(...manejadoresDeTodos())
