import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { tanstackRouter } from '@tanstack/router-plugin/vite'
import { fileURLToPath } from 'node:url'

const raiz = fileURLToPath(new URL('../..', import.meta.url))

export default defineConfig({
  plugins: [
    // Enrutamiento por sistema de archivos: el arbol de rutas se GENERA de
    // `src/rutas/**`. Agregar una pantalla no toca ningun registro compartido, que
    // es lo que permite que varios carriles trabajen sin chocar (planes/16 §4).
    tanstackRouter({ target: 'react', routesDirectory: 'src/rutas', generatedRouteTree: 'src/arbolDeRutas.gen.ts' }),
    react(),
  ],
  resolve: {
    alias: {
      // `clientes/typescript` es GENERADO y no es un paquete del monorepo: se
      // alcanza por alias, nunca por copia.
      clientes: `${raiz}clientes`,
    },
  },
  server: { port: 5174 },
})
