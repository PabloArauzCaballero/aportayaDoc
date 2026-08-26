import { randomUUID } from 'expo-crypto'

/**
 * Identificadores del cliente. Salen del generador criptografico de la
 * plataforma, no de un contador ni de un pseudoaleatorio: una clave de
 * idempotencia adivinable es una operacion ajena que se puede pisar.
 */
export function nuevoIdentificador(): string {
  return randomUUID()
}
