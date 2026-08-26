// `jsx-a11y` como ERROR. Un sitio publico que no es accesible deja gente afuera de
// la unica superficie que se puede usar sin instalar nada.
import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import a11y from 'eslint-plugin-jsx-a11y'
import astro from 'eslint-plugin-astro'

export default tseslint.config(
  { ignores: ['dist/**', 'node_modules/**', '.astro/**'] },
  {
    // La configuracion de Astro corre en Node, no en el navegador.
    files: ['*.mjs', '*.ts'],
    languageOptions: { globals: { URL: 'readonly', process: 'readonly', console: 'readonly' } },
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...astro.configs.recommended,
  {
    files: ['**/*.tsx'],
    plugins: { 'jsx-a11y': a11y },
    rules: { ...a11y.flatConfigs.recommended.rules },
  },
  {
    files: ['**/*.{ts,tsx}'],
    rules: {
      'no-restricted-globals': [
        'error',
        { name: 'fetch', message: 'La red vive en src/dominio (usá consultar()).' },
      ],
      'no-restricted-properties': [
        'error',
        { object: 'Math', property: 'random', message: 'El azar es criptográfico: crypto.randomUUID().' },
      ],
    },
  },
  { files: ['src/dominio/**/*.ts', 'pruebas/**/*.{ts,tsx}'], rules: { 'no-restricted-globals': 'off' } },
)
