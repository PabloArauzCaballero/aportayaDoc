---
tags:
  - caso-uso
  - modulo/10-billetera-custodia-y-dinero-electronico
  - modulo/09-auditoria-reportes-y-cumplimiento
codigo: CU-14
criticidad: alta
actores: [Operador, Supervisor]
normas: [Ley 393 (conservación e integridad), ASFI riesgo operativo]
---

# CU-14 — Reversar una transacción

> **Objetivo.** Corregir un error **sin borrar ni editar nada**: el error y su
> corrección conviven en el extracto, que es lo que exige cualquier contabilidad
> seria y lo que espera un auditor.

## Actores y disparador

- **Actor principal:** operador de soporte u operaciones.
- **Actor secundario:** supervisor que autoriza.
- **Disparadores:** error operativo; contracargo del proveedor; anulación de una
  entrega; orden de autoridad.

## Precondiciones

1. Existe la [[transaccion_billetera]] original en estado `APLICADA`.
2. No existe ya un reverso ejecutado sobre ella (`R-BIL-15`).
3. La cuenta afectada tiene saldo suficiente si el reverso implica debitar.

## Flujo principal

1. Se crea [[reverso_transaccion]] con `tipo` (`ANULACION`, `CONTRACARGO`,
   `ERROR_OPERATIVO`, `ORDEN_AUTORIDAD`), `motivo` y `monto_reversado`.
2. Un supervisor autoriza (`autorizada_por` ≠ quien lo solicitó, `R-SEG-04`).
3. **En una sola transacción**:
   - se crea una nueva [[transaccion_billetera]] `tipo='REVERSO'` con los
     [[movimiento_billetera]] espejados (débito donde hubo crédito y viceversa);
   - la original **no se modifica**: solo se enlaza por `transaccion_original_id`;
   - se genera el [[asiento_contable]] de reversa con `asiento_reversa_id`;
   - si el reverso afecta un devengo, se dispara
     [[CU-33 Devolver comisión y emitir nota de crédito]];
   - se emite `evento_dominio` `TRANSACCION_REVERSADA`.
4. Si el motivo es `ERROR_OPERATIVO`, se registra además
   [[CU-54 Registrar un evento de riesgo operativo]] con la pérdida asociada.
5. Se notifica al titular con la explicación en lenguaje llano.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 3a | El saldo ya se gastó y quedaría negativo | El reverso genera una obligación de restitución en lugar de saldo negativo: **nunca se permite descubierto** (`R-BIL-02`) |
| 2a | No hay segundo aprobador | La reversa no se ejecuta |
| 1a | Se intenta reversar dos veces | Rechazo por unicidad (`R-BIL-15`) |
| — | Contracargo del proveedor | Además del reverso, se abre disputa y se evalúa cargo al usuario según contrato |

## Postcondiciones

- La transacción original sigue existiendo, íntegra y visible.
- El extracto del usuario muestra el movimiento y su corrección.

## Contrato · `openapi/nucleo-financiero.yaml`

```ts
export const EntradaCU14 = z.object({
  claveIdempotencia: z.string().uuid(),
  transaccionOriginalId: z.string().uuid(),
  tipo:   z.enum(['ANULACION','CONTRACARGO','ERROR_OPERATIVO','ORDEN_AUTORIDAD']),
  motivo: z.string().min(20).max(300),
  autorizadaPor: z.string().uuid(),
}).strict()

export const SalidaCU14 = z.object({
  reversoId: z.string().uuid(),
  transaccionReversoId: z.string().uuid(),
  generaObligacionDeRestitucion: z.boolean(),
}).strict()

export const ErroresCU14 = {
  TRANSACCION_NO_REVERSABLE: 'AP-CU14-01',
  FALTA_AUTORIZACION: 'AP-CU14-02',
  SALDO_INSUFICIENTE_PARA_REVERSO: 'AP-CU14-03',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `TRANSACCION_NO_REVERSABLE` | No está aplicada o ya fue reversada (R-BIL-15) |
| `FALTA_AUTORIZACION` | Quien autoriza no puede ser quien solicita (R-SEG-04) |
| `SALDO_INSUFICIENTE_PARA_REVERSO` | Se generó obligación de restitución en su lugar |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `espejarMovimientos` | Invierte débitos y créditos; puro |
| Molécula | `ReversoRepositorio` | Expediente del reverso |
| Molécula | `TransaccionBilleteraRepositorio` | Alta de la transacción compensatoria |
| Organismo | `CU14ReversarTransaccion` | Transacción: compensación, asiento de reversa y riesgo operativo |
| Página | `POST /billetera/transacciones/:id/reverso` | Traduce y delega, sin lógica |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `transaccion.reversada` | Asiento de reversa, devolución de comisión y aviso | `REVERSO_AUTORIZAR` |
| `riesgo.evento_registrado` | Base de pérdidas si el motivo es error operativo | — |

## Interfaz

- **App:** El extracto muestra el movimiento y su corrección, nunca solo el resultado.
- **Backoffice:** Formulario de reverso con doble aprobación y motivo obligatorio.

## Restricciones aplicables

`R-AUD-01` · `R-AUD-03` · `R-AUD-06` · `R-BIL-02` · `R-BIL-15` · `R-SEG-04`

## Evidencia que deja

[[reverso_transaccion]] · dos [[transaccion_billetera]] enlazadas ·
[[asiento_contable]] de reversa · [[evento_riesgo_operativo]] (si aplica)

## Criterios de aceptación

```gherkin
Dada una transacción aplicada por Bs 300
Cuando se reversa
Entonces existe una nueva transaccion_billetera de tipo REVERSO por Bs 300
Y la transacción original conserva sus movimientos sin cambios

Dado un intento de UPDATE sobre movimiento_billetera
Cuando lo ejecuta el rol de aplicación
Entonces la base de datos lo rechaza (R-AUD-01)

Dado un reverso que dejaría el saldo negativo
Cuando se ejecuta
Entonces se genera una obligación de restitución
Y el saldo_disponible no baja de cero
```

## Ver también

[[CU-19 Reembolsar un pago y atender una disputa]] · [[CU-33 Devolver comisión y emitir nota de crédito]] · [[CU-54 Registrar un evento de riesgo operativo]] · [[Restricciones]]
