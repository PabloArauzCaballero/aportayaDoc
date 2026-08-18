---
tags:
  - caso-uso
  - modulo/11-tarifas-comisiones-impuestos-y-facturacion
codigo: CU-33
criticidad: alta
actores: [Soporte, Supervisor]
normas: [SIN, ASFI atención de reclamos]
---

# CU-33 — Devolver comisión y emitir nota de crédito

> **Objetivo.** Reparar un cobro indebido devolviendo el dinero **y** corrigiendo
> el documento fiscal, de modo que la plata y el papel queden atados.

## Actores y disparador

- **Actor principal:** soporte, con autorización de supervisor.
- **Disparadores:** entrega anulada; error de tarifa; reclamo procedente
  ([[CU-52 Atender un reclamo en plazo]]); falla de servicio.

## Precondiciones

1. Existe [[devengo_comision]] en estado `COBRADO` o `COBRADO_PARCIAL`.
2. El monto a devolver no supera lo efectivamente cobrado (`R-TAR-11`).

## Flujo principal

1. Se crea [[devolucion_comision]] con `motivo` tipificado
   (`ENTREGA_ANULADA`, `ERROR_DE_TARIFA`, `RECLAMO_PROCEDENTE`,
   `FALLA_DE_SERVICIO`), `monto_devuelto`, `forma` y `autorizada_por`.
2. Si nace de un reclamo, se enlaza `reclamo_id` (y el reclamo guarda
   `devolucion_comision_id`): un reclamo favorable con monto **exige** devolución
   asociada (`R-CON-04`).
3. **En la misma transacción**:
   - se ejecuta la forma elegida: `ABONO_BILLETERA` crea
     [[transaccion_billetera]] `tipo='DEVOLUCION'`; `COMPENSACION` reduce un
     devengo futuro;
   - `devengo_comision.estado='DEVUELTO'`;
   - se registra el [[asiento_contable]] de reversa del ingreso.
4. Se emite [[nota_credito_debito]] de tipo `CREDITO` sobre la
   [[factura_electronica]] original, con su propio `cuf`, y se enlaza
   `devolucion_comision_id`.
5. Se notifica al usuario con el motivo en lenguaje llano.
6. Si el motivo es `ERROR_DE_TARIFA` o `FALLA_DE_SERVICIO`, se registra
   [[CU-54 Registrar un evento de riesgo operativo]].

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | Se intenta devolver más de lo cobrado | Rechazo por `R-TAR-11` |
| 4a | La factura estaba `EMITIDA_OFFLINE` sin enviar | Se envía primero y luego se emite la nota de crédito |
| 3a | El usuario cerró su billetera | Se devuelve por transferencia bancaria y se documenta |
| — | Error masivo de tarifa | Se procesa por lote, se abre hallazgo y se comunica proactivamente a los afectados |

## Postcondiciones

- El dinero volvió y el documento fiscal quedó corregido, ambos trazables entre sí.

## Contrato · `openapi/tarifas.yaml`

```ts
export const EntradaCU33 = z.object({
  claveIdempotencia: z.string().uuid(),
  devengoId: z.string().uuid(),
  motivo: z.enum(['ENTREGA_ANULADA','ERROR_DE_TARIFA','RECLAMO_PROCEDENTE','FALLA_DE_SERVICIO']),
  montoDevuelto: MontoSchema,
  forma: z.enum(['ABONO_BILLETERA','NOTA_CREDITO','COMPENSACION']),
  reclamoId: z.string().uuid().optional(),
  autorizadaPor: z.string().uuid(),
}).strict()

export const SalidaCU33 = z.object({
  devolucionId: z.string().uuid(),
  notaCreditoId: z.string().uuid().nullable(),
  transaccionId: z.string().uuid().nullable(),
  cuf: z.string().nullable(),
}).strict()

export const ErroresCU33 = {
  EXCEDE_LO_COBRADO: 'AP-CU33-01',
  DEVENGO_NO_COBRADO: 'AP-CU33-02',
  FACTURA_SIN_ENVIAR: 'AP-CU33-03',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `EXCEDE_LO_COBRADO` | La devolución supera lo efectivamente cobrado (R-TAR-11) |
| `DEVENGO_NO_COBRADO` | No hay nada que devolver |
| `FACTURA_SIN_ENVIAR` | Primero se envía la factura offline y luego la nota |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `calcularMaximoDevolvible` | Cobrado menos ya devuelto; puro |
| Molécula | `DevolucionRepositorio` | Expediente de la devolución |
| Molécula | `NotaCreditoRepositorio` | Documento fiscal de corrección |
| Organismo | `CU33DevolverComision` | Transacción: devolución, asiento de reversa y nota de crédito |
| Página | `POST /comisiones/:devengoId/devoluciones` | Traduce y delega, sin lógica |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `comision.devuelta` | Nota de crédito y aviso con el motivo | `SOPORTE` con autorización |
| `riesgo.evento_registrado` | Pérdida operativa si el motivo es error o falla | — |

## Interfaz

- **App:** El abono aparece en el extracto con el motivo escrito.
- **Backoffice:** Formulario de devolución enlazado al reclamo que la origina.

## Restricciones aplicables

`R-TAR-10` · `R-TAR-11` · `R-CON-04` · `R-AUD-01` · `R-AUD-06` · `R-SEG-04`

## Evidencia que deja

[[devolucion_comision]] · [[nota_credito_debito]] · [[transaccion_billetera]] ·
[[asiento_contable]] · [[reclamo_cliente]] (si aplica) · [[evento_riesgo_operativo]]

## Criterios de aceptación

```gherkin
Dado un devengo cobrado por Bs 18
Cuando se devuelven Bs 18 por reclamo procedente
Entonces el devengo queda DEVUELTO
Y existe una nota de crédito con cuf único enlazada a la devolución

Dado un intento de devolver Bs 25 sobre un cobro de Bs 18
Cuando se ejecuta
Entonces la operación se rechaza

Dado un reclamo con resultado FAVORABLE y monto reclamado
Cuando se intenta cerrarlo sin devolución asociada
Entonces el cierre se rechaza (R-CON-04)
```

## Ver también

[[CU-14 Reversar una transacción]] · [[CU-19 Reembolsar un pago y atender una disputa]] · [[CU-32 Emitir factura electrónica]] · [[CU-52 Atender un reclamo en plazo]]
