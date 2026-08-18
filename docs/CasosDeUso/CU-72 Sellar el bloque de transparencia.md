---
tags:
  - caso-uso
  - modulo/06-transparencia-y-reputacion
codigo: CU-72
criticidad: alta
actores: [Sistema]
normas: [Transparencia, integridad de la evidencia, ISO 27001 A.5.33]
---

# CU-72 — Sellar el bloque de transparencia

> **Objetivo.** Que el grupo pueda demostrar **que su historia no fue reescrita**.
> Cada período se cierra en un bloque encadenado por hash: alterar un aporte
> antiguo obliga a rehacer todos los bloques posteriores, y eso se nota.

## Actores y disparador

- **Actor principal:** el sistema, al cerrar un período.
- **Disparadores:** cierre de [[periodo]]; entrega ejecutada; hitos del grupo
  (constitución, sorteo, disolución).

## Precondiciones

1. El período está cerrado y sus [[obligacion_aporte]] tienen estado definitivo.
2. Existe el bloque anterior del grupo, o este es el bloque génesis.

## Flujo principal

1. Se arma el contenido del bloque: aportes acreditados del período, entregas
   ejecutadas con sus deducciones, coberturas del fondo, altas y bajas de
   participantes, acuerdos resueltos y el resultado del sorteo si lo hubo.
2. Se calcula `hash_contenido` sobre esa estructura canónica —orden fijo de
   campos, importes como cadena, fechas en UTC—, porque **un hash solo sirve si
   dos implementaciones producen el mismo**.
3. Se crea [[bloque_transparencia]] con `numero_bloque`, `hash_anterior`,
   `hash_contenido` y `hash_bloque = SHA256(numero || hash_anterior || hash_contenido)`.
4. Se registran los [[registro_sellado]] de las entidades incluidas, para poder ir
   del bloque al hecho y del hecho al bloque.
5. Se publica el bloque en la vista del grupo y se emite `evento_dominio`
   `bloque.sellado`.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | El período tiene excepciones de conciliación abiertas | **No se sella**: primero se resuelve. Un bloque con datos provisorios miente con firma |
| 3a | El bloque anterior no existe o su hash no coincide | Se detiene el sellado y se abre [[incidente_operativo]]: la cadena está rota y eso es grave |
| — | Se corrige un hecho ya sellado | El bloque no se reescribe: la corrección entra al bloque siguiente como movimiento compensatorio, y ambos quedan visibles |
| — | Sellado concurrente del mismo grupo | `UNIQUE (grupo_id, numero_bloque)` lo impide (`R-REP-04`) |

## Postcondiciones

- La historia del grupo queda encadenada y verificable por cualquiera.
- Ningún bloque se emitió sobre datos sin cuadrar.

## Contrato · `openapi/transparencia.yaml`

```ts
export const EntradaCU72 = z.object({
  grupoId:  z.string().uuid(),
  periodoId: z.string().uuid().optional(),   // ausente = bloque de hito
  motivo:   z.enum(['CIERRE_PERIODO','ENTREGA','HITO','DISOLUCION']),
}).strict()

export const SalidaCU72 = z.object({
  bloqueId:     z.string().uuid(),
  numeroBloque: z.number().int(),
  hashAnterior: z.string().length(64).nullable(),
  hashContenido:z.string().length(64),
  hashBloque:   z.string().length(64),
  entidadesSelladas: z.number().int(),
}).strict()

export const ErroresCU72 = {
  PERIODO_CON_EXCEPCIONES: 'AP-CU72-01',
  CADENA_ROTA:             'AP-CU72-02',
  BLOQUE_DUPLICADO:        'AP-CU72-03',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `PERIODO_CON_EXCEPCIONES` | Quedan excepciones de conciliación abiertas: un bloque con datos provisorios miente con firma |
| `CADENA_ROTA` | El `hash_bloque` del anterior no coincide con lo encadenado. **Detiene el sellado y abre incidente** |
| `BLOQUE_DUPLICADO` | Ya existe ese `numero_bloque` para el grupo (`R-REP-04`) |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `serializarCanonico(contenido)` | Estructura estable y determinista; **si esto no es puro, la cadena no sirve** |
| Átomo | `hashDeBloque(numero, hashAnterior, hashContenido)` | Puro |
| Molécula | `BloqueRepositorio` · `SelladoRepositorio` | |
| Organismo | `CU72SellarBloque` | Transacción y encadenamiento |
| Página | `GET /publico/grupos/:codigo/bloques` | Lectura pública |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `bloque.sellado` | Publicación en la vista del grupo | Interno |
| `bloque.cadena_rota` | Incidente operativo y aviso al equipo | — |
| — | Trabajo de cierre de período que lo invoca | — |

## Interfaz

- **App:** *Grupo → Historia*: los bloques en orden, con lo que contiene cada uno y
  el botón **Verificar cadena**.
- **Público:** la misma vista sin sesión, para que cualquiera compruebe.

## Restricciones aplicables

`R-REP-04` · `R-AUD-01` · `R-AUD-02` · `R-BIL-12`

## Evidencia que deja

[[bloque_transparencia]] (*append-only*) · [[registro_sellado]] ·
`evento_dominio`

## Criterios de aceptación

```gherkin
Dado un período cerrado sin excepciones
Cuando se sella el bloque
Entonces hash_anterior es el hash_bloque del bloque previo
Y hash_bloque se recomputa igual desde sus tres componentes

Dado un período con una excepción de conciliación abierta
Cuando se intenta sellar
Entonces se rechaza con PERIODO_CON_EXCEPCIONES

Dado un intento de sellar dos veces el mismo número de bloque
Cuando se ejecuta
Entonces la base lo rechaza por unicidad
```

## Ver también

[[CU-51 Ejecutar el cierre diario]] · [[CU-71 Recalcular el puntaje de reputación]] · [[CU-73 Verificar la cadena de transparencia]]
