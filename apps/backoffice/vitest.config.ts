import { defineConfig, mergeConfig } from 'vitest/config'
import configuracionDeVite from './vite.config'

// Tres proyectos, como en movil: logica pura, componente y accesibilidad se piden
// por separado porque el gate del frontend los pide por separado (planes/11 F0.4).
const comun = {
  environment: 'jsdom',
  globals: true,
  setupFiles: ['./pruebas/preparar.ts'],
}

export default mergeConfig(
  configuracionDeVite,
  defineConfig({
    test: {
      projects: [
        { extends: true, test: { ...comun, name: 'unidad', include: ['pruebas/unidad/**/*.spec.ts', 'pruebas/contrato/**/*.spec.ts'] } },
        { extends: true, test: { ...comun, name: 'componente', include: ['pruebas/componente/**/*.spec.tsx'] } },
        { extends: true, test: { ...comun, name: 'a11y', include: ['pruebas/a11y/**/*.spec.tsx'] } },
      ],
    },
  }),
)
