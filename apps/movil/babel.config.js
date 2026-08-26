// El preset de Expo trae lo que Expo Router necesita. Nada mas aca: cada plugin
// de Babel que se agrega es una diferencia entre lo que corre en el emulador y
// lo que corre en Jest.
module.exports = function (api) {
  api.cache(true)
  return { presets: ['babel-preset-expo'] }
}
