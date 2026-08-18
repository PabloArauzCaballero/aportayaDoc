---
tags:
  - caso-uso
  - modulo/10-billetera-custodia-y-dinero-electronico
codigo: CU-50
criticidad: alta
actores: [Sistema, Tesorería]
normas: [Respaldo de fondos de clientes, ASFI riesgo operativo]
---

# CU-50 — Conciliar la custodia y verificar el encaje

> **Objetivo.** Probar todos los días, con una cifra, que **el dinero de los
> usuarios existe**: la suma de todos los saldos de billetera contra el saldo real
> de la cuenta de custodia.

## Actores y disparador

- **Actor principal:** proceso programado diario.
- **Actor secundario:** tesorería, que resuelve diferencias.
- **Disparador:** cierre del día operativo.

## Precondiciones

1. Los [[saldo_diario_billetera]] del día están cerrados.
2. Se recibió el extracto de la [[cuenta_custodia]] y se cargaron los
   [[movimiento_custodia]].

## Flujo principal

1. Se calcula `saldo_dinero_electronico` = suma de `saldo_total` de todas las
   [[cuenta_billetera]] de tipo `USUARIO`, `GRUPO` y `FONDO_GARANTIA` (el pasivo
   con terceros).
2. Se toma `saldo_custodia` del banco y se calcula `saldo_en_transito`
   (movimientos ordenados y aún no impactados).
3. Se crea [[conciliacion_custodia]] con `diferencia` y `ratio_cobertura`
   (generadas) y `cumple_encaje = ratio_cobertura >= 1`.
4. Si cuadra: `estado='CUADRADA'` y se habilita el [[cierre_diario]]
   ([[CU-51 Ejecutar el cierre diario]]).
5. Si no cuadra: `estado='DESCUADRADA'`, se crea [[descuadre_custodia]] con `tipo`
   (`FALTANTE`, `SOBRANTE`, `DESFASE_TEMPORAL`, `ERROR_REGISTRO`) y `severidad`.

## Flujos alternativos — modo restringido

| # | Situación | Resultado |
| :-: | --- | --- |
| 5a | `ratio_cobertura < 1` | **Modo restringido automático**: se siguen aceptando recargas y aportes (aumentan cobertura), se suspenden retiros discrecionales y transferencias P2P |
| 5b | Desfase temporal explicable | Se documenta `explicacion`; el descuadre se resuelve cuando impacta el movimiento |
| 5c | Faltante no explicado | Escala como [[incidente_operativo]] (M9) y [[evento_riesgo_operativo]] (`R-BIL-11`) |
| — | Sobrante | También es descuadre: dinero sin dueño identificado va a `SUSPENSO_NO_IDENTIFICADO` y se investiga |
| 1a | Falta un cierre diario de saldos | La conciliación no corre; se alerta antes de que se acumulen días |

## Postcondiciones

- Existe una fila por día y por cuenta de custodia que responde "¿está la plata?".
- Ningún descuadre queda sin expediente ni sin plan de acción.

## Contrato · `openapi/nucleo-financiero.yaml`

```ts
export const EntradaCU50 = z.object({
  fecha: z.string().date(),
  cuentaCustodiaId: z.string().uuid(),
}).strict()

export const SalidaCU50 = z.object({
  conciliacionId: z.string().uuid(),
  saldoDineroElectronico: MontoSchema,
  saldoCustodia: MontoSchema,
  ratioCobertura: z.string(),
  cumpleEncaje: z.boolean(),
  descuadres: z.array(z.object({ tipo: z.string(), monto: MontoSchema, severidad: z.string() })),
}).strict()

export const ErroresCU50 = {
  SIN_CIERRES_DIARIOS: 'AP-CU50-01',
  SIN_EXTRACTO_BANCARIO: 'AP-CU50-02',
  CONCILIACION_DUPLICADA: 'AP-CU50-03',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SIN_CIERRES_DIARIOS` | Faltan saldos diarios sellados de la fecha |
| `SIN_EXTRACTO_BANCARIO` | No se cargó el movimiento de custodia |
| `CONCILIACION_DUPLICADA` | Ya existe para esa cuenta y fecha (R-BIL-11) |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `calcularCobertura` | Ratio con guarda de división por cero; puro |
| Átomo | `clasificarDescuadre` | Faltante, sobrante, desfase o error de registro |
| Molécula | `ConciliacionCustodiaRepositorio` | Conciliación diaria |
| Molécula | `SaldoDiarioRepositorio` | Suma de saldos de billetera |
| Molécula | `MovimientoCustodiaRepositorio` | Saldo real del banco |
| Organismo | `CU50ConciliarCustodia` | Trabajo diario; bloquea el cierre si no cuadra |
| Página | — | Sin endpoint: lo dispara el planificador o un evento |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `custodia.conciliada` | Habilita el cierre diario | Interno |
| `custodia.descuadrada` | Modo restringido, incidente y evento de riesgo | — |

## Interfaz

- **App:** Si hay modo restringido, la app explica por qué no se puede retirar.
- **Backoffice:** Tablero de encaje con el ratio por día y los descuadres abiertos.

## Restricciones aplicables

`R-BIL-11` · `R-BIL-12` · `R-AUD-01` · `R-AUD-07`

## Evidencia que deja

[[conciliacion_custodia]] · [[descuadre_custodia]] · [[movimiento_custodia]] ·
[[saldo_diario_billetera]] · [[incidente_operativo]] · [[evento_riesgo_operativo]]

## Criterios de aceptación

```gherkin
Dado un día con saldos de billetera por Bs 1.000.000 y custodia por Bs 1.000.000
Cuando corre la conciliación
Entonces ratio_cobertura es 1,000000 y cumple_encaje es true

Dada una conciliación DESCUADRADA del día
Cuando se intenta marcar el cierre_diario como cuadrado
Entonces la operación se rechaza

Dado un ratio_cobertura menor a 1
Cuando un usuario intenta retirar
Entonces la operación se rechaza por modo restringido
Y una recarga del mismo usuario sí se acepta
```

## Ver también

[[CU-10 Recargar saldo]] · [[CU-15 Emitir extracto y certificado de saldo]] · [[CU-24 Registrar el asiento contable de una operación]] · [[CU-28 Emitir la orden de desembolso y ejecutar el intento]] · [[CU-51 Ejecutar el cierre diario]] · [[CU-54 Registrar un evento de riesgo operativo]] · [[CU-57 Operar un punto de atención y arquear el efectivo]] · [[CU-99 Dar de alta un proveedor de pago y enrutar el cobro]]
