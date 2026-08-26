// Tres corredores, no uno. Un `test` que mezcla logica pura, componentes y
// accesibilidad no se puede pedir por separado, y el gate del frontend los pide
// por separado (planes/11 F0.4).
//
// Los tres usan el preset de Expo a proposito: dos configuraciones de
// transformacion distintas en el mismo paquete son dos formas de que una prueba
// pase en un corredor y falle en el otro.
const path = require('node:path')
const preset = require('jest-expo/jest-preset')

const raiz = path.resolve(__dirname, '../..')

// El preset transforma `.js`, `.jsx`, `.ts` y `.tsx`, pero no `.mjs`. `msw`
// depende de paquetes que publican solo ESM con esa extension: sin esto, Jest
// los lee como CommonJS y muere en el primer `import`.
const claveDeJavaScript = Object.keys(preset.transform).find((clave) => clave.includes('[jt]sx'))
if (!claveDeJavaScript) throw new Error('el preset de Expo cambio: ya no declara transformacion de JS/TS')

const transform = {
  ...preset.transform,
  '^.+\.mjs$': preset.transform[claveDeJavaScript],
}

// Los paquetes que `msw` arrastra y publica solo como ESM. Se AGREGAN a la lista
// del preset en vez de reemplazarla: escribir la lista entera a mano la deja
// vieja en cuanto Expo cambie la suya.
const ESM_QUE_SUMA_MSW = [
  'msw',
  '@mswjs',
  '@bundled-es-modules',
  '@open-draft',
  'until-async',
  'outvariant',
  'strict-event-emitter',
  'headers-polyfill',
  'rettime',
  'is-node-process',
  'graphql',
  'tough-cookie',
]

const transformIgnorePatterns = preset.transformIgnorePatterns.map((patron) =>
  patron.includes('(?!(') ? patron.replace('))', `|${ESM_QUE_SUMA_MSW.join('|')}))`) : patron,
)

const comun = {
  preset: 'jest-expo',
  transform,
  transformIgnorePatterns,
  moduleFileExtensions: ['ts', 'tsx', 'js', 'jsx', 'mjs', 'json', 'node'],
  moduleNameMapper: {
    '^@aportaya/simulado$': `${raiz}/packages/simulado/src/index.ts`,
    '^@aportaya/simulado/(.*)$': `${raiz}/packages/simulado/src/$1`,
    '^clientes/typescript/(.*)$': `${raiz}/clientes/typescript/$1`,
    '^@/(.*)$': '<rootDir>/src/$1',
  },
}

module.exports = {
  projects: [
    {
      ...comun,
      displayName: 'front-unit',
      setupFilesAfterEnv: ['<rootDir>/pruebas/preparar.tsx'],
      testMatch: ['<rootDir>/pruebas/unidad/**/*.spec.ts', '<rootDir>/pruebas/contrato/**/*.spec.ts'],
    },
    {
      ...comun,
      displayName: 'front-componente',
      setupFilesAfterEnv: ['<rootDir>/pruebas/preparar.tsx'],
      testMatch: ['<rootDir>/pruebas/componente/**/*.spec.tsx'],
    },
    {
      ...comun,
      displayName: 'front-a11y',
      setupFilesAfterEnv: ['<rootDir>/pruebas/preparar.tsx'],
      testMatch: ['<rootDir>/pruebas/a11y/**/*.spec.tsx'],
    },
  ],
}
