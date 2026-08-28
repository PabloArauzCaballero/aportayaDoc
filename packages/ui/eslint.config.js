// Mismo estandar que el backoffice: `jsx-a11y` va como ERROR. Una advertencia de
// accesibilidad es una advertencia que nadie lee (planes/11 F0.4).
import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import a11y from 'eslint-plugin-jsx-a11y'

export default tseslint.config(
  { ignores: ['generado/**', 'node_modules/**'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    // El emisor de tokens corre en Node, no en el navegador ni en la app.
    files: ['scripts/**/*.mjs'],
    languageOptions: { globals: { URL: 'readonly', process: 'readonly', console: 'readonly' } },
  },
  {
    files: ['**/*.{ts,tsx}'],
    plugins: { 'jsx-a11y': a11y },
    rules: {
      ...a11y.flatConfigs.recommended.rules,
      // Invariante 1: el sistema de diseno no llama a la red. Nunca.
      'no-restricted-globals': [
        'error',
        { name: 'fetch', message: 'El sistema de diseño no habla con la red. Eso es de dominio/.' },
      ],
    },
  },
  {
    // Invariante 5: `toFixed` e `Intl.NumberFormat` estan prohibidos en todo el
    // proyecto salvo en el atomo `Monto` — y ni siquiera ahi hacen falta, porque el
    // importe se formatea sobre la cadena del contrato. La regla se declara igual:
    // el dia que alguien los escriba, el lint lo dice antes que la revision.
    files: ['src/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-properties': [
        'error',
        { property: 'toFixed', message: 'Los importes no se redondean en el cliente. Usá formatearMonto().' },
        { object: 'Intl', property: 'NumberFormat', message: 'El formato de dinero vive en dinero/formatear.ts.' },
        { object: 'Math', property: 'random', message: 'Usá un identificador criptográfico.' },
      ],
    },
  },
)
