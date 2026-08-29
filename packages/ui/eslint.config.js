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
    // **Cero hex fuera de `tokens/`** — el gate de salida de F1, verificado por lint y
    // no por disciplina. Un color inventado en esta fase se propaga a los tres
    // productos y ya no se saca; el momento de decirlo es al escribirlo.
    //
    // `paleta.ts` y `temas.ts` estan excluidos porque son, por definicion, los dos
    // unicos archivos donde un literal es correcto. El barrido de
    // `pruebas/unidad/sin-literales.spec.ts` cubre lo que el lint no ve: el CSS.
    files: ['src/**/*.{ts,tsx}'],
    ignores: ['src/tokens/**'],
    rules: {
      'no-restricted-syntax': [
        'error',
        {
          selector: "Literal[value=/^#(?:[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/]",
          message: 'Un hex acá es un color inventado. Pedí el rol (var(--brand)) o agregalo a tokens/.',
        },
        {
          selector: "TemplateElement[value.raw=/#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{3})\\b/]",
          message: 'Un hex acá es un color inventado. Pedí el rol (var(--brand)) o agregalo a tokens/.',
        },
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
