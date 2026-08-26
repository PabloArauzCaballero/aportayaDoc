import { manejadoresDeTodos } from '@aportaya/simulado'
import { setupServer } from 'msw/node'

/** Un único servidor simulado para los tres corredores, con los mismos manejadores. */
export const servidorDePruebas = setupServer(...manejadoresDeTodos())
