import Constants from 'expo-constants'
import { Stack } from 'expo-router'
import { StatusBar } from 'expo-status-bar'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { arrancarSimulado, detenerSimulado } from '../src/simulado/servidor'

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
  const simulado = Constants.expoConfig?.extra?.simulado === true
  const [listo, setListo] = useState(!simulado)

  useEffect(() => {
    if (!simulado) return
    arrancarSimulado()
    setListo(true)
    return detenerSimulado
  }, [simulado])

  if (!listo) return null

  return (
    <QueryClientProvider client={cliente}>
      <StatusBar style="auto" />
      <Stack screenOptions={{ headerTitleAlign: 'center' }} />
    </QueryClientProvider>
  )
}
