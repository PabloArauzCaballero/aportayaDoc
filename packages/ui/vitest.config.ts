import { defineConfig } from 'vitest/config'

// Tres proyectos, como en `apps/movil` y `apps/backoffice`: logica pura, componente y
// accesibilidad se piden por separado porque el gate del frontend los pide por
// separado (planes/11 F0.4).
const comun = {
  environment: 'jsdom',
  globals: true,
  setupFiles: ['./pruebas/preparar.ts'],
}

export default defineConfig({
  test: {
    projects: [
      { extends: true, test: { ...comun, name: 'unidad', include: ['pruebas/unidad/**/*.spec.ts'] } },
      { extends: true, test: { ...comun, name: 'componente', include: ['pruebas/componente/**/*.spec.tsx'] } },
      { extends: true, test: { ...comun, name: 'a11y', include: ['pruebas/a11y/**/*.spec.tsx'] } },
    ],
  },
})
