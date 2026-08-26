// La porcion de OpenAPI 3.1 que el servidor simulado necesita leer. No es el
// estandar entero a proposito: lo que no esta aca es lo que este paquete no
// interpreta, y verlo escrito evita que alguien suponga que si.
export type Esquema = {
  $ref?: string
  type?: string | string[]
  format?: string
  pattern?: string
  enum?: unknown[]
  const?: unknown
  properties?: Record<string, Esquema>
  required?: string[]
  items?: Esquema
  minItems?: number
  minLength?: number
  maxLength?: number
  minimum?: number
  maximum?: number
  allOf?: Esquema[]
  oneOf?: Esquema[]
  anyOf?: Esquema[]
  example?: unknown
  examples?: unknown[]
  additionalProperties?: boolean | Esquema
}

export type Respuesta = {
  $ref?: string
  description?: string
  content?: Record<string, { schema?: Esquema }>
}

export type Operacion = {
  operationId?: string
  summary?: string
  responses?: Record<string, Respuesta>
}

export type Contrato = {
  openapi: string
  info: { title: string; version: string }
  servers?: { url: string }[]
  paths: Record<string, Record<string, Operacion>>
  components?: Record<string, Record<string, unknown>>
}

export const METODOS = ['get', 'post', 'put', 'patch', 'delete'] as const
export type Metodo = (typeof METODOS)[number]
