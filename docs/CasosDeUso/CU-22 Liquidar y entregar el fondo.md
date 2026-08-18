---
tags:
  - caso-uso
  - modulo/04-entregas-de-fondo
  - modulo/11-tarifas-comisiones-impuestos-y-facturacion
codigo: CU-22
criticidad: alta
actores: [Sistema, Organizador, Beneficiario]
normas: [ASFI transparencia, SIN facturación, contabilidad]
---

# CU-22 — Liquidar y entregar el fondo

> **Objetivo.** Que la bolsa llegue completa, a la persona correcta, **una sola
> vez**, y que cada boliviano descontado tenga una línea explicable —incluida la
> comisión de la plataforma.

## Actores y disparador

- **Actor principal:** el sistema (organizador digital) o el organizador humano.
- **Disparador:** llega la fecha programada del turno.

## Precondiciones

1. Existe [[turno]] del período sin entrega previa (`R-GRP-01`).
2. La bolsa está completa o el faltante está cubierto ([[CU-23 Cubrir un incumplimiento con el fondo]]).
3. El beneficiario tiene cuenta operativa y sin bloqueos que impidan acreditar.

## Flujo principal

1. Se crea [[entrega_fondo]] con `monto_bolsa_bruto` calculado desde las
   obligaciones acreditadas del período.
2. Se ejecutan las [[validacion_pre_entrega]] contra cada [[regla_entrega]]
   activa; una regla bloqueante en `RECHAZADA` impide autorizar.
3. Se calculan las [[deduccion_entrega]] línea por línea, en el orden definido:
   - `APORTE_PROPIO_DEL_PERIODO`, `DEUDA_VENCIDA_PROPIA`, `RECARGO_MORA_PROPIO`,
     `REPOSICION_FONDO_GARANTIA`;
   - **`COMISION_PLATAFORMA`**, cuyo importe viene de
     [[CU-31 Devengar y cobrar la comisión]] usando la [[tarifa_congelada_grupo]];
   - `RETENCION_IMPUESTO`, `COSTO_TRANSFERENCIA`.
4. `monto_neto_a_entregar = monto_bolsa_bruto - total_deducciones` (`R-GRP-02`).
5. Autoriza una persona/rol y **ejecuta otra** (`autorizada_por` ≠ `ejecutada_por`,
   `R-SEG-04`).
6. **En la misma transacción**:
   - se retiene la bolsa en la cuenta del grupo ([[CU-13 Retener y liberar saldo]]);
   - se crea la [[transaccion_billetera]] `tipo='ENTREGA_DE_FONDO'`: débito a la
     cuenta del grupo, crédito a la del beneficiario por el neto, crédito a
     `PLATAFORMA_INGRESOS` por la comisión y a `PLATAFORMA_IMPUESTOS_POR_PAGAR`
     por el impuesto;
   - se registra el [[asiento_contable]];
   - se marca el `cargo_comision` como cobrado.
7. Se emite la [[constancia_pago]] / comprobante con el detalle de deducciones y se
   solicita [[confirmacion_recepcion]].
8. Se dispara la facturación de la comisión ([[CU-32 Emitir factura electrónica]]).

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | Falta plata en la bolsa | `BLOQUEADA_POR_FONDO_INCOMPLETO`: problema del grupo, dispara módulo 8 |
| 2b | El beneficiario no cumple una validación | `BLOQUEADA_POR_VALIDACION`: problema individual, con causa concreta |
| 3a | El neto quedaría negativo | Se rechaza: `R-GRP-02` impide entregar menos que cero; el excedente de deuda queda como obligación |
| 5a | Intento de doble entrega del mismo turno | Rechazo por unicidad (`R-GRP-01`) |
| 7a | El beneficiario objeta las deducciones | `RECHAZADA_POR_BENEFICIARIO`; se abre reclamo ([[CU-52 Atender un reclamo en plazo]]) sin revertir aún |
| — | Entrega anulada después de acreditar | [[CU-14 Reversar una transacción]] + [[CU-33 Devolver comisión y emitir nota de crédito]] |

## Postcondiciones

- Un turno = una entrega. El beneficiario puede explicar cada deducción.
- El ingreso de la plataforma quedó devengado, cobrado y contabilizado.

## Contrato · `openapi/entregas.yaml`

```ts
export const EntradaCU22 = z.object({
  claveIdempotencia: z.string().uuid(),
  turnoId: z.string().uuid(),
  autorizadaPor: z.string().uuid(),
  ejecutadaPor: z.string().uuid(),
}).strict()

export const SalidaCU22 = z.object({
  entregaId: z.string().uuid(),
  montoBolsaBruto: MontoSchema,
  deducciones: z.array(z.object({ tipo: z.string(), monto: MontoSchema, referencia: z.string() })),
  montoNeto: MontoSchema,
  constanciaUrl: z.string().url(),
}).strict()

export const ErroresCU22 = {
  BOLSA_INCOMPLETA: 'AP-CU22-01',
  VALIDACION_BLOQUEANTE: 'AP-CU22-02',
  ENTREGA_DUPLICADA: 'AP-CU22-03',
  SEGREGACION_INCUMPLIDA: 'AP-CU22-04',
  NETO_NEGATIVO: 'AP-CU22-05',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `BOLSA_INCOMPLETA` | Falta plata del período y el fondo no cubre |
| `VALIDACION_BLOQUEANTE` | Una regla de entrega bloqueante quedó en rechazada |
| `ENTREGA_DUPLICADA` | Ese turno ya fue entregado (R-GRP-01) |
| `SEGREGACION_INCUMPLIDA` | Quien autoriza no puede ejecutar (R-SEG-04) |
| `NETO_NEGATIVO` | Las deducciones superan la bolsa (R-GRP-02) |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `calcularDeducciones` | Arma las líneas en orden y devuelve el neto; puro y con pruebas de propiedad |
| Átomo | `componerAsientoDeEntrega` | Partidas de bolsa, beneficiario, ingreso e impuesto |
| Molécula | `EntregaRepositorio` | Alta y transiciones de la entrega |
| Molécula | `ValidacionPreEntregaRepositorio` | Ejecuta y registra cada regla |
| Molécula | `DevengoComisionRepositorio` | Devenga la comisión del tarifario congelado |
| Organismo | `CU22LiquidarEntrega` | Transacción: liquidación completa, sin estados intermedios |
| Página | `POST /entregas/:turnoId` | Traduce y delega, sin lógica |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `entrega.autorizada` | Retención de la bolsa y validaciones previas | `ENTREGA_AUTORIZAR` |
| `entrega.ejecutada` | Comisión, factura, asiento, bloque de transparencia y aviso | `ENTREGA_EJECUTAR` |

## Interfaz

- **App:** *Cobrar mi turno*: bolsa, cada deducción con su motivo, y el neto en grande.
- **Backoffice:** Tablero de entregas del día con sus validaciones y el doble control.

## Restricciones aplicables

`R-GRP-01` · `R-GRP-02` · `R-TAR-04` · `R-TAR-06` · `R-BIL-01` · `R-SEG-04` ·
`R-AUD-05`

## Evidencia que deja

[[entrega_fondo]] · [[deduccion_entrega]] · [[validacion_pre_entrega]] ·
[[transaccion_billetera]] · [[devengo_comision]] · [[cargo_comision]] ·
[[asiento_contable]] · [[constancia_pago]] · [[confirmacion_recepcion]]

## Criterios de aceptación

```gherkin
Dada una bolsa bruta de Bs 6.000 y deducciones por Bs 518
Cuando se liquida la entrega
Entonces monto_neto_a_entregar es 5.482
Y existe una deduccion_entrega de tipo COMISION_PLATAFORMA con referencia a cargo_comision

Dado un turno con entrega ya ejecutada
Cuando se intenta crear otra entrega para el mismo turno
Entonces la base de datos lo rechaza (R-GRP-01)

Dada una entrega autorizada
Cuando la ejecuta el mismo usuario que la autorizó
Entonces la operación se rechaza por segregación de funciones
```

## Ver también

[[CU-13 Retener y liberar saldo]] · [[CU-18 Registrar y verificar una cuenta bancaria de destino]] · [[CU-20 Crear grupo y congelar tarifario]] · [[CU-21 Cobrar el aporte del período]] · [[CU-23 Cubrir un incumplimiento con el fondo]] · [[CU-28 Emitir la orden de desembolso y ejecutar el intento]] · [[CU-31 Devengar y cobrar la comisión]] · [[CU-32 Emitir factura electrónica]] · [[CU-62 Permutar turnos entre participantes]]
