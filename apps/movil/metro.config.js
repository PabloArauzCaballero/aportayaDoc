// Metro en un monorepo: por omision mira solo la carpeta de la app y no encuentra
// ni `packages/` ni el cliente generado. Estas tres lineas son la diferencia
// entre `yarn start` y una pantalla roja de «Unable to resolve module».
const { getDefaultConfig } = require('expo/metro-config')
const path = require('node:path')

const app = __dirname
const raiz = path.resolve(app, '../..')

const config = getDefaultConfig(app)

config.watchFolders = [raiz]
config.resolver.nodeModulesPaths = [path.resolve(app, 'node_modules'), path.resolve(raiz, 'node_modules')]
config.resolver.disableHierarchicalLookup = true
// `clientes/typescript` es GENERADO y no es un paquete del monorepo: no tiene
// `package.json` ni dueno de carril. Se alcanza por alias, nunca por copia.
config.resolver.extraNodeModules = {
  clientes: path.resolve(raiz, 'clientes'),
}

module.exports = config
