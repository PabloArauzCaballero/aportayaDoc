import * as AlmacenSeguro from 'expo-secure-store'

/**
 * El token vive en el almacen seguro del sistema, nunca en almacenamiento plano
 * (`movil-expo`, antipatrones). Esta capa no sabe de pantallas: solo guarda,
 * lee y borra.
 */
const ACCESO = 'aportaya.token.acceso'
const REFRESCO = 'aportaya.token.refresco'

type Escucha = () => void
const escuchas = new Set<Escucha>()

export async function tokenDeAcceso(): Promise<string | null> {
  return AlmacenSeguro.getItemAsync(ACCESO)
}

export async function tokenDeRefresco(): Promise<string | null> {
  return AlmacenSeguro.getItemAsync(REFRESCO)
}

export async function guardarSesion(acceso: string, refresco: string): Promise<void> {
  await AlmacenSeguro.setItemAsync(ACCESO, acceso)
  await AlmacenSeguro.setItemAsync(REFRESCO, refresco)
}

/**
 * Cierre global. Se llama cuando el refresco falla: quedarse con un token que el
 * servidor ya rechazo produce una app que reintenta para siempre contra un 401.
 */
export async function cerrarSesion(): Promise<void> {
  await AlmacenSeguro.deleteItemAsync(ACCESO)
  await AlmacenSeguro.deleteItemAsync(REFRESCO)
  escuchas.forEach((avisar) => avisar())
}

export function alCerrarSesion(escucha: Escucha): () => void {
  escuchas.add(escucha)
  return () => escuchas.delete(escucha)
}
