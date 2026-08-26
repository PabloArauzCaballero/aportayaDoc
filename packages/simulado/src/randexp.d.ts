// randexp no publica tipos. Se declara lo que este paquete usa y nada mas:
// una declaracion mas ancha que el uso es una promesa que nadie verifica.
declare module 'randexp' {
  export default class RandExp {
    constructor(patron: string | RegExp, banderas?: string)
    randInt: (desde: number, hasta: number) => number
    max: number
    gen(): string
  }
}
