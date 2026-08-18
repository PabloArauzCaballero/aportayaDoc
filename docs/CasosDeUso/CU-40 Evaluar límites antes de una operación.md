---
tags:
  - caso-uso
  - modulo/10-billetera-custodia-y-dinero-electronico
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
codigo: CU-40
criticidad: alta
actores: [Sistema]
normas: [Límites BCB para dinero electrónico, UIF enfoque basado en riesgo]
---

# CU-40 — Evaluar límites antes de una operación

> **Objetivo.** Que ninguna operación supere el techo que le corresponde al
> usuario por su nivel de conocimiento, y que el usuario **sepa antes de intentar**
> cuánto le queda disponible.

## Actores y disparador

- **Actor principal:** el sistema, en línea.
- **Disparadores:** recarga, retiro, transferencia, aporte, y consulta previa
  desde la app.

## Precondiciones

1. La cuenta tiene `nivel_debida_diligencia` resuelto.
2. Existen filas activas de [[limite_operativo_billetera]] para ese nivel.

## Flujo principal

1. Se seleccionan los límites aplicables por `concepto` y `nivel_debida_diligencia`
   con `vigente_desde <= hoy` y sin `vigente_hasta` vencido.
2. Para cada límite se resuelve la ventana (`OPERACION`, `DIA`, `SEMANA`, `MES`,
   `ANIO`) y se lee o crea [[consumo_limite]] para
   `(cuenta, limite, ventana_inicio)` (`R-LIM-02`).
3. Se evalúa: `monto_acumulado + monto_operacion <= monto_maximo` y
   `cantidad_acumulada + 1 <= cantidad_maxima`.
4. Se evalúa además `SALDO_MAXIMO` sobre el saldo resultante.
5. Si todo pasa, la operación continúa y, **en la misma transacción** que la
   aplica, se incrementa el consumo.
6. Si la operación se reversa, el consumo se devuelve en la misma transacción del
   reverso.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 3a | Supera el límite | Se rechaza con un mensaje que dice **cuánto queda disponible** y ofrece [[CU-02 Elevar nivel de debida diligencia]] |
| 1a | No hay límite configurado para ese concepto y nivel | **La operación se rechaza por defecto** (denegar por omisión), no se permite sin techo |
| 2a | Cambia la ventana (nuevo día/mes) | Se abre una fila de consumo nueva; la anterior queda como histórico |
| — | Cambia el límite por norma | Nueva fila con `vigente_desde`; las operaciones pasadas se siguen explicando con el límite que regía (`R-LIM-03`) |

## Postcondiciones

- Ninguna operación aplicada excede el límite vigente al momento de aplicarse.
- El consumo por ventana es reconstruible y auditable.

## Contrato · `openapi/cumplimiento.yaml`

```ts
export const EntradaCU40 = z.object({
  cuentaBilleteraId: z.string().uuid(),
  concepto: z.string().max(25),
  monto: MontoSchema,
}).strict()

export const SalidaCU40 = z.object({
  permitido: z.boolean(),
  limitesEvaluados: z.array(z.object({ ventana: z.string(), tope: MontoSchema, consumido: MontoSchema, disponible: MontoSchema })),
  motivoRechazo: z.string().nullable(),
}).strict()

export const ErroresCU40 = {
  SIN_LIMITE_CONFIGURADO: 'AP-CU40-01',
  LIMITE_EXCEDIDO: 'AP-CU40-02',
  CANTIDAD_EXCEDIDA: 'AP-CU40-03',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SIN_LIMITE_CONFIGURADO` | No hay límite para ese concepto y nivel: se deniega por omisión (R-LIM-01) |
| `LIMITE_EXCEDIDO` | El acumulado más la operación supera el tope |
| `CANTIDAD_EXCEDIDA` | Superó la cantidad máxima de operaciones de la ventana |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `evaluarTope` | Compara acumulado más monto contra el tope; nunca contra NULL; puro |
| Átomo | `resolverVentana` | Calcula inicio y fin de la ventana vigente |
| Molécula | `LimiteRepositorio` | Límites vigentes por concepto y nivel |
| Molécula | `ConsumoLimiteRepositorio` | Acumulado de la ventana, con bloqueo |
| Organismo | `CU40EvaluarLimites` | Se ejecuta dentro de la transacción de la operación |
| Página | — | Sin endpoint: lo dispara el planificador o un evento |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `limite.rechazo` | Aviso con el disponible restante | Interno |

## Interfaz

- **App:** Antes de operar se muestra cuánto queda del límite del mes.
- **Backoffice:** Consulta de límites y consumo por usuario, para atención.

## Restricciones aplicables

`R-LIM-01` · `R-LIM-02` · `R-LIM-03` · `R-BIL-02`

## Evidencia que deja

[[consumo_limite]] · [[limite_operativo_billetera]] · [[bitacora_evento]] (rechazos)

## Criterios de aceptación

```gherkin
Dado un límite mensual de retiro y un consumo acumulado cercano al tope
Cuando el usuario intenta retirar un monto que lo supera
Entonces la operación se rechaza indicando el disponible restante

Dado un concepto sin límite configurado para el nivel del usuario
Cuando se evalúa una operación
Entonces se rechaza por ausencia de límite (denegar por omisión)

Dada una operación aplicada y luego reversada
Cuando se consulta el consumo de la ventana
Entonces el importe reversado no cuenta contra el límite
```

## Ver también

[[CU-02 Elevar nivel de debida diligencia]] · [[CU-10 Recargar saldo]] · [[CU-11 Retirar saldo]] · [[CU-48 Calibrar reglas de cumplimiento y triar sus alertas]] · [[Restricciones]]
