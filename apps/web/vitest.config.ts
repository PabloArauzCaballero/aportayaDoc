import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'

const raiz = fileURLToPath(new URL('../..', import.meta.url))

// Tres proyectos, como en movil y backoffice: los tres corredores se piden por
// separado porque el gate del frontend los pide por separado (planes/11 F0.4).
const comun = {
  environment: 'jsdom' as const,
  globals: true,
  setupFiles: ['./pruebas/preparar.ts'],
}

export default defineConfig({
  plugins: [react()],
  resolve: { alias: { clientes: `${raiz}clientes` } },
  test: {
    projects: [
      { extends: true, test: { ...comun, name: 'unidad', include: ['pruebas/unidad/**/*.spec.ts', 'pruebas/contrato/**/*.spec.ts'] } },
      { extends: true, test: { ...comun, name: 'componente', include: ['pruebas/componente/**/*.spec.tsx'] } },
      { extends: true, test: { ...comun, name: 'a11y', include: ['pruebas/a11y/**/*.spec.tsx'] } },
    ],
  },
})
