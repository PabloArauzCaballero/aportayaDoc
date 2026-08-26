import Constants from 'expo-constants'
import { Stack } from 'expo-router'
import { StatusBar } from 'expo-status-bar'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useEffect, useState } from 'react'

/**
 * Raiz de la app. Expo Router arma la navegacion **desde los archivos de esta
 * carpeta**: agregar una pantalla no toca ningun registro compartido, que es lo
 * que hace que dos carriles no colisionen (planes/16 §4).
 */
const cliente = new QueryClient({
  defaultOptions: {
    queries: {
      // Sobre dinero no hay reintento silencioso: el reintento lo pide la persona
      // y se ve en pantalla.
      retry: false,
      refetchOnWindowFocus: false,
    },
  },
})

export default function Raiz() {
  // `__DEV__` primero, y no solo el interruptor de `app.json`: es lo que permite a
  // Metro eliminar la rama entera en una compilacion de produccion. Con un `import`
  // arriba, MSW y sus interceptores viajarian dentro del paquete de la tienda aunque
  // nunca se ejecutaran — 300 KB de servidor simulado en la app de una billetera.
  const simulado = __DEV__ && Constants.expoConfig?.extra?.simulado === true
  const [listo, setListo] = useState(!simulado)

  useEffect(() => {
    // El `require` va LITERALMENTE dentro de `if (__DEV__)`, no detrás de una
    // variable: Metro pliega la constante y descarta la dependencia **antes** de
    // armar el grafo. Detrás de una variable el pliegue no puede probar nada y el
    // módulo entra igual al paquete, aunque nunca se ejecute.
    if (__DEV__) {
      if (!simulado) return
      // eslint-disable-next-line @typescript-eslint/no-require-imports
      const { arrancarSimulado, detenerSimulado } = require('../src/simulado/servidor')
      arrancarSimulado()
      setListo(true)
      return detenerSimulado
    }
  }, [simulado])

  if (!listo) return null

  return (
    <QueryClientProvider client={cliente}>
      <StatusBar style="auto" />
      <Stack screenOptions={{ headerTitleAlign: 'center' }} />
    </QueryClientProvider>
  )
}
