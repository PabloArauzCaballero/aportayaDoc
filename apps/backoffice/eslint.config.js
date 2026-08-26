// Configuracion plana. `jsx-a11y` va como ERROR, no como advertencia: una
// advertencia de accesibilidad es una advertencia que nadie lee (planes/11 F0.4).
import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import a11y from 'eslint-plugin-jsx-a11y'
import ganchos from 'eslint-plugin-react-hooks'

export default tseslint.config(
  { ignores: ['dist/**', 'node_modules/**', 'src/arbolDeRutas.gen.ts'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['**/*.{ts,tsx}'],
    plugins: { 'jsx-a11y': a11y, 'react-hooks': ganchos },
    rules: {
      ...a11y.flatConfigs.recommended.rules,
      ...ganchos.configs.recommended.rules,
      // La red vive en src/dominio. Un `fetch` en un componente es el antipatron
      // que hace imposible probar una pantalla sin levantar medio sistema.
      'no-restricted-globals': [
        'error',
        { name: 'fetch', message: 'La red vive en src/dominio (usá llamar()). Ningún componente hace fetch.' },
      ],
      'no-restricted-properties': [
        'error',
        { object: 'Math', property: 'random', message: 'Usá nuevoIdentificador(): el azar es criptográfico.' },
      ],
    },
  },
  { files: ['src/dominio/**/*.ts', 'pruebas/**/*.{ts,tsx}'], rules: { 'no-restricted-globals': 'off' } },
)
