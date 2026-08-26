// Configuracion plana de ESLint 9. El preset de Expo trae React, React Native y
// las reglas de import; lo que se agrega aca son las que este proyecto no
// negocia y ninguna herramienta trae por omision.
const expo = require('eslint-config-expo/flat')

module.exports = [
  ...expo,
  {
    ignores: ['node_modules/**', '.expo/**', 'coverage/**'],
  },
  {
    files: ['**/*.ts', '**/*.tsx'],
    rules: {
      // Un `fetch` dentro de un componente es el antipatron n.º 1 de `movil-expo`:
      // la red vive en `dominio/`, y por eso existe `llamar()`.
      'no-restricted-globals': [
        'error',
        { name: 'fetch', message: 'La red vive en src/dominio (usá llamar()). Ningún componente hace fetch.' },
      ],
      // El azar de una clave de idempotencia no puede ser adivinable.
      'no-restricted-properties': [
        'error',
        { object: 'Math', property: 'random', message: 'Usá nuevoIdentificador(): el azar es criptográfico.' },
      ],
    },
  },
  {
    // La capa de dominio SI usa fetch: es la unica que puede.
    files: ['src/dominio/**/*.ts'],
    rules: { 'no-restricted-globals': 'off' },
  },
  {
    files: ['pruebas/**/*.ts', 'pruebas/**/*.tsx'],
    rules: { 'no-restricted-globals': 'off' },
  },
  {
    // Los archivos de configuracion corren en Node, no en el dispositivo.
    files: ['*.js'],
    languageOptions: {
      sourceType: 'commonjs',
      globals: { __dirname: 'readonly', module: 'writable', require: 'readonly', process: 'readonly' },
    },
  },
]
