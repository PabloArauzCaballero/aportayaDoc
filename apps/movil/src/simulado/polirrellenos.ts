/**
 * Los globales del navegador que MSW da por sentados y Hermes no trae.
 *
 * `msw/native` arma sus interceptores en el momento del import y ahi toca `Event`,
 * `EventTarget` y `MessageEvent`. Hermes no define ninguno. Sin esto el import
 * lanza `ReferenceError`, `arrancarSimulado()` nunca vuelve, `listo` se queda en
 * `false` y la app **abre en blanco**: el fallo no se ve como un error, se ve como
 * una pantalla vacia, que es la peor forma de fallar.
 *
 * Va en su propio modulo y se importa **primero**, porque los imports de ES se
 * evaluan en orden de declaracion: puesto arriba de `msw/native`, los globales
 * existen antes de que MSW los busque. Escrito dentro de `servidor.ts` no serviria:
 * el import de MSW se evaluaria antes que cualquier sentencia del archivo.
 *
 * **Por que a mano y no `event-target-shim`**, que ya esta en `node_modules`:
 * porque llega como dependencia transitiva de React Native, y este monorepo
 * prohibe apoyarse en lo que no se declara (`.yarnrc.yml`,
 * `enableTransparentWorkspaces: false`). Declararlo seria un micro-PR a
 * `package.json`, que no es de este carril; estas cuarenta lineas cubren lo que
 * MSW usa y no le deben nada a nadie.
 *
 * Solo se carga con el simulado encendido, que ya vive detras de `__DEV__`: nada
 * de esto viaja en el paquete de la tienda.
 */

const global_ = globalThis as Record<string, unknown>

type Escucha = ((evento: unknown) => void) | { handleEvent(evento: unknown): void }

if (typeof global_.Event === 'undefined') {
  class EventoPolirrelleno {
    readonly type: string
    readonly bubbles: boolean
    readonly cancelable: boolean
    defaultPrevented = false
    target: unknown = null
    currentTarget: unknown = null

    constructor(tipo: string, inicio: { bubbles?: boolean; cancelable?: boolean } = {}) {
      this.type = tipo
      this.bubbles = inicio.bubbles ?? false
      this.cancelable = inicio.cancelable ?? false
    }

    preventDefault(): void {
      if (this.cancelable) this.defaultPrevented = true
    }

    stopPropagation(): void {}
    stopImmediatePropagation(): void {}
  }
  global_.Event = EventoPolirrelleno
}

if (typeof global_.EventTarget === 'undefined') {
  class BlancoDeEventosPolirrelleno {
    // Un Map por tipo, con Set adentro: `addEventListener` con la misma funcion
    // dos veces registra una sola, que es lo que manda la especificacion.
    private escuchas = new Map<string, Set<Escucha>>()

    addEventListener(tipo: string, escucha: Escucha | null): void {
      if (!escucha) return
      const conjunto = this.escuchas.get(tipo) ?? new Set<Escucha>()
      conjunto.add(escucha)
      this.escuchas.set(tipo, conjunto)
    }

    removeEventListener(tipo: string, escucha: Escucha | null): void {
      if (!escucha) return
      this.escuchas.get(tipo)?.delete(escucha)
    }

    dispatchEvent(evento: { type: string; target?: unknown; currentTarget?: unknown }): boolean {
      const conjunto = this.escuchas.get(evento.type)
      if (!conjunto) return true
      evento.target = this
      evento.currentTarget = this
      // Copia antes de recorrer: una escucha que se desuscribe a si misma mientras
      // se despacha no debe saltearse la siguiente.
      for (const escucha of [...conjunto]) {
        if (typeof escucha === 'function') escucha.call(this, evento)
        else escucha.handleEvent(evento)
      }
      return true
    }
  }
  global_.EventTarget = BlancoDeEventosPolirrelleno
}

if (typeof global_.MessageEvent === 'undefined') {
  const Base = global_.Event as new (tipo: string, inicio?: object) => object

  class MensajePolirrelleno extends Base {
    readonly data: unknown
    readonly origin: string
    readonly lastEventId: string
    readonly source: unknown
    readonly ports: readonly unknown[]

    constructor(
      tipo: string,
      inicio: {
        data?: unknown
        origin?: string
        lastEventId?: string
        source?: unknown
        ports?: readonly unknown[]
      } = {},
    ) {
      super(tipo, inicio)
      this.data = inicio.data ?? null
      this.origin = inicio.origin ?? ''
      this.lastEventId = inicio.lastEventId ?? ''
      this.source = inicio.source ?? null
      this.ports = inicio.ports ?? []
    }
  }
  global_.MessageEvent = MensajePolirrelleno
}

if (typeof global_.BroadcastChannel === 'undefined') {
  const Blanco = global_.EventTarget as new () => {
    addEventListener(tipo: string, escucha: Escucha | null): void
    removeEventListener(tipo: string, escucha: Escucha | null): void
    dispatchEvent(evento: { type: string }): boolean
  }
  const Mensaje = global_.MessageEvent as new (tipo: string, inicio?: { data?: unknown }) => {
    type: string
  }

  // MSW lo usa para sincronizar entre pestanas del navegador. En un telefono no
  // hay pestanas: los canales de este proceso se hablan entre si y nada mas. Se
  // implementa igual porque MSW lo construye al importarse, y sin el no arranca.
  const abiertos = new Map<string, Set<CanalPolirrelleno>>()

  class CanalPolirrelleno extends Blanco {
    readonly name: string
    onmessage: ((evento: unknown) => void) | null = null
    onmessageerror: ((evento: unknown) => void) | null = null
    private cerrado = false

    constructor(nombre: string) {
      super()
      this.name = nombre
      const grupo = abiertos.get(nombre) ?? new Set<CanalPolirrelleno>()
      grupo.add(this)
      abiertos.set(nombre, grupo)
    }

    postMessage(datos: unknown): void {
      if (this.cerrado) return
      for (const otro of abiertos.get(this.name) ?? []) {
        // El canal que publica no se escucha a si mismo, como en la especificacion.
        if (otro === this || otro.cerrado) continue
        const evento = new Mensaje('message', { data: datos })
        otro.dispatchEvent(evento)
        otro.onmessage?.(evento)
      }
    }

    close(): void {
      this.cerrado = true
      const grupo = abiertos.get(this.name)
      grupo?.delete(this)
      if (grupo && grupo.size === 0) abiertos.delete(this.name)
    }
  }

  global_.BroadcastChannel = CanalPolirrelleno
}
