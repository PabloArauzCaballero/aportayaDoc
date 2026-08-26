// El simulado no dibuja nada, pero comparte el estandar del frontend: una regla
// que vale en `apps/movil` y no aca es una regla que alguien va a discutir.
const expo = require('eslint-config-expo/flat')

module.exports = [
  ...expo,
  { ignores: ['generado/**', 'node_modules/**'] },
]
