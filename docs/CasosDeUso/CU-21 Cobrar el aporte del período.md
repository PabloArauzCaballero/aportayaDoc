---
tags:
  - caso-uso
  - modulo/03-aportes-pagos-qr-y-conciliacion
  - modulo/10-billetera-custodia-y-dinero-electronico
codigo: CU-21
criticidad: alta
actores: [Participante, Sistema]
normas: [ASFI conciliación y contabilidad, UIF]
---

# CU-21 — Cobrar el aporte del período

> **Objetivo.** Que "pagué" signifique "el dinero está en la cuenta del grupo",
> nunca una declaración del usuario, y que un reintento no cobre dos veces.

## Actores y disparador

- **Actor principal:** participante.
- **Disparadores:** apertura del período; recordatorio; débito programado
  autorizado.

## Precondiciones

1. Existe [[periodo]] abierto y [[obligacion_aporte]] generada por cupo
   (`R-GRP-03`: una obligación de tipo `APORTE_PERIODICO` por período y cupo).
2. La cuenta del participante está operativa.

## Flujo principal

1. Al abrir el período se generan las [[obligacion_aporte]] con `monto_esperado`,
   `fecha_vencimiento` y `fecha_fin_gracia` según [[politica_mora]].
2. El participante elige medio de pago:
   - **saldo** → [[CU-12 Transferir saldo entre billeteras]] hacia la cuenta del grupo;
   - **QR / pasarela** → se emite [[orden_cobro]] con `clave_idempotencia` y
     [[qr_cobro]]; el pago llega como [[pago]] y se concilia ([[conciliacion]]).
3. **En la misma transacción** que acredita:
   - se actualiza `obligacion_aporte.monto_pagado` y su `estado`;
   - se crea la [[transaccion_billetera]] con crédito a la cuenta del grupo;
   - se registra el [[asiento_contable]].
4. Si hay débito programado, la [[retencion_saldo]] creada el día de la
   autorización se ejecuta en la fecha ([[CU-13 Retener y liberar saldo]]).
5. Se evalúan umbrales UIF sobre la operación.
6. Si vence sin pagar, se aplica recargo como **obligación nueva** que apunta a la
   original (`obligacion_origen_id`), nunca modificando el monto original.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | Webhook duplicado | Idempotencia: un solo pago acreditado (`R-BIL-06`) |
| 2b | Pago por monto distinto | Se acredita como parcial (`PAGADO_PARCIAL`) o genera excepción de conciliación |
| 3a | El pago no concilia con el extracto bancario | [[excepcion_conciliacion]] abierta; **bloquea el cierre diario** (`R-BIL-12`) |
| 6a | Vence el plazo de gracia | Se genera recargo y, superado el umbral, [[registro_incumplimiento]] |
| — | El participante paga de más | El excedente queda como saldo a favor, no se "pierde" |

## Postcondiciones

- La obligación refleja exactamente lo pagado, lo condonado y lo cubierto.
- La bolsa del grupo creció en el importe acreditado, con asiento espejo.

## Contrato · `openapi/aportes.yaml`

```ts
export const EntradaCU21 = z.object({
  claveIdempotencia: z.string().uuid(),
  obligacionId: z.string().uuid(),
  monto: MontoSchema,
  medio: z.enum(['SALDO','QR','TRANSFERENCIA']),
}).strict()

export const SalidaCU21 = z.object({
  pagoId: z.string().uuid().nullable(),
  ordenCobroId: z.string().uuid().nullable(),
  estado: z.enum(['PENDIENTE','PAGADO_PARCIAL','PAGADO','EN_VERIFICACION']),
  saldoPendiente: MontoSchema,
  qr: z.object({ payloadEmv: z.string() }).nullable(),
}).strict()

export const ErroresCU21 = {
  OBLIGACION_NO_VIGENTE: 'AP-CU21-01',
  MONTO_NO_COINCIDE: 'AP-CU21-02',
  SALDO_INSUFICIENTE: 'AP-CU21-03',
  PERIODO_CERRADO: 'AP-CU21-04',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `OBLIGACION_NO_VIGENTE` | Está anulada, condonada o ya saldada |
| `MONTO_NO_COINCIDE` | No coincide con lo esperado y el grupo no acepta parcial |
| `SALDO_INSUFICIENTE` | Paga con saldo y no le alcanza |
| `PERIODO_CERRADO` | El período ya se liquidó |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `aplicarPagoAObligacion` | Distribuye el pago entre capital y recargo; puro |
| Átomo | `clasificarMora` | Días de atraso y su severidad según política |
| Molécula | `ObligacionRepositorio` | Toma la obligación para actualizar, con bloqueo |
| Molécula | `OrdenCobroRepositorio` | Emite la orden y su QR conciliable |
| Molécula | `ConciliacionRepositorio` | Cruce contra el extracto bancario |
| Organismo | `CU21CobrarAporte` | Transacción: pago, obligación, movimientos y asiento |
| Página | `POST /aportes` | Traduce y delega, sin lógica |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `aporte.confirmado` | Reputación, tablero del grupo y cancelación de recordatorios | `BILLETERA_OPERAR` |
| `aporte.vencido` | Recargo y, superado el umbral, incumplimiento | — |

## Interfaz

- **App:** *Mi aporte*: monto, fecha límite y un botón; con saldo es instantáneo.
- **Backoffice:** Estado de cobranza por grupo y período, con la brecha para completar la bolsa.

## Restricciones aplicables

`R-GRP-03` · `R-BIL-01` · `R-BIL-06` · `R-AUD-01` · `R-AUD-05` · `R-UIF-02`

## Evidencia que deja

[[obligacion_aporte]] · [[orden_cobro]] · [[pago]] · [[conciliacion]] ·
[[transaccion_billetera]] · [[asiento_contable]]

## Criterios de aceptación

```gherkin
Dada una obligación de Bs 500 pendiente
Cuando el participante paga con saldo
Entonces monto_pagado es 500 y estado es PAGADO
Y la cuenta del grupo aumentó Bs 500

Dado un pago no conciliado con el extracto
Cuando se intenta cerrar el día
Entonces el cierre_diario no puede marcarse cuadrado

Dado un aporte vencido con política de mora
Cuando corre el proceso diario
Entonces se crea una obligación de tipo RECARGO_MORA con obligacion_origen_id
```

## Ver también

[[CU-12 Transferir saldo entre billeteras]] · [[CU-22 Liquidar y entregar el fondo]] · [[CU-51 Ejecutar el cierre diario]] · [[CU-59 Mantener el calendario de días no hábiles]] · [[CU-81 Programar recordatorios de aporte]] · [[CU-82 Procesar una respuesta entrante]] · [[CU-99 Dar de alta un proveedor de pago y enrutar el cobro]]
