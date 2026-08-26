#!/usr/bin/env node
// Los contratos viven en YAML dentro de cada servicio, que es donde manda ADR-020.
// Metro y Jest empaquetan JSON, no YAML: esto los convierte, y NADA MAS. No
// interpreta, no completa y no corrige — si el YAML esta mal, el JSON sale mal y
// la prueba de contrato lo dice.
//
//   yarn workspace @aportaya/simulado contratos
//
// La salida es generada: vive en `generado/` y no se versiona.
import { readFileSync, writeFileSync, mkdirSync, readdirSync, existsSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse } from 'yaml'

const AQUI = dirname(fileURLToPath(import.meta.url))
const RAIZ = resolve(AQUI, '../../..')
const SERVICIOS = join(RAIZ, 'servicios')
const DESTINO = join(AQUI, '..', 'generado')

if (!existsSync(SERVICIOS)) {
  console.error(`no encuentro servicios/ en ${RAIZ}`)
  process.exit(1)
}

mkdirSync(DESTINO, { recursive: true })

const escritos = []
for (const servicio of readdirSync(SERVICIOS)) {
  const yaml = join(SERVICIOS, servicio, 'src/main/resources/openapi', `${servicio}.yaml`)
  if (!existsSync(yaml)) continue
  const documento = parse(readFileSync(yaml, 'utf8'))
  const rutas = Object.keys(documento?.paths ?? {})
  if (rutas.length === 0) continue // borrador vacio de la Fase 0: no es un error
  writeFileSync(join(DESTINO, `${servicio}.json`), `${JSON.stringify(documento, null, 2)}\n`, 'utf8')
  escritos.push(`${servicio} (${rutas.length} rutas)`)
}

if (escritos.length === 0) {
  console.error('ningun contrato con operaciones: no hay nada que simular')
  process.exit(1)
}
console.log(`contratos convertidos: ${escritos.join(' · ')}`)
