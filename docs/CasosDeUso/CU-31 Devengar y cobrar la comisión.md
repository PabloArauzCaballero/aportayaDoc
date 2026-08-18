---
tags:
  - caso-uso
  - modulo/11-tarifas-comisiones-impuestos-y-facturacion
codigo: CU-31
criticidad: alta
actores: [Sistema]
normas: [Contabilidad (devengado vs percibido), SIN, ASFI]
---

# CU-31 — Devengar y cobrar la comisión

> **Objetivo.** Separar **ganar** de **cobrar**. El ingreso se reconoce cuando
> ocurre el hecho generador; el cobro es otra cosa y puede fallar, exonerarse o
> devolverse sin que el ingreso desaparezca del registro.

## Actores y disparador

- **Actor principal:** el sistema, al consumir un `evento_dominio`.
- **Disparador:** ocurre un hecho listado en [[catalogo_hecho_generador]] y activo
  en el tarifario aplicable (por defecto: entrega de fondo acreditada).

## Precondiciones

1. Existe [[cotizacion_comision]] vigente para la operación, o se recalcula.
2. El concepto tiene `cuenta_ingreso_id` mapeada.

## Flujo principal

1. Se valida idempotencia: `UNIQUE (referencia_tipo, referencia_id,
   concepto_tarifa_id)` impide devengar dos veces el mismo hecho (`R-TAR-04`).
2. **En la misma transacción del hecho económico**:
   - se crea [[devengo_comision]] (*append-only*) con `monto_base`,
     `monto_comision`, `monto_descuento`, `monto_impuesto`, `monto_total`,
     `periodo_contable` y `estado='DEVENGADO'`;
   - se crean los [[calculo_impuesto]] por cada impuesto aplicable;
   - se registra el [[asiento_contable]] del ingreso y del impuesto por pagar.
3. Se ejecuta el cobro según `concepto_tarifa.forma_cobro`:
   - **`DEDUCCION_DE_ENTREGA`** → se crea la [[deduccion_entrega]] de tipo
     `COMISION_PLATAFORMA` y el [[cargo_comision]] que la referencia (1:1,
     `R-TAR-06`);
   - **`DEBITO_DE_BILLETERA`** → [[transaccion_billetera]] `tipo='COBRO_COMISION'`;
   - **`OBLIGACION_DE_APORTE`** → [[obligacion_aporte]] de tipo
     `COMISION_PLATAFORMA` para el caso de prorrateo.
4. Cobrado el importe, `devengo_comision.estado='COBRADO'`.
5. Se dispara la facturación ([[CU-32 Emitir factura electrónica]]).

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 3a | Sin saldo para debitar | `cargo_comision.estado='FALLIDO'`, se reintenta según política |
| 3b | Tres intentos fallidos | Se crea [[cuenta_por_cobrar_comision]] y entra al circuito de cobranza (M8); el devengo queda `COBRADO_PARCIAL` o `INCOBRABLE` |
| 2a | Existe [[exencion_comision]] vigente que cubre el 100% | Se devenga en cero con `estado='EXONERADO'` y queda registrado quién lo autorizó |
| — | Se anula la operación de origen | [[CU-33 Devolver comisión y emitir nota de crédito]] |
| 1a | Evento duplicado | No se devenga dos veces; se responde idempotente |

## Postcondiciones

- Todo hecho generador tiene exactamente un devengo por concepto.
- El estado del devengo dice la verdad sobre si el dinero entró.

## Contrato · `openapi/tarifas.yaml`

```ts
export const EntradaCU31 = z.object({
  claveIdempotencia: z.string().uuid(),
  cotizacionId: z.string().uuid(),
  referencia: z.object({ tipo: z.string(), id: z.string().uuid() }),
  formaCobro: z.enum(['DEDUCCION_DE_ENTREGA','DEBITO_DE_BILLETERA','OBLIGACION_DE_APORTE']),
}).strict()

export const SalidaCU31 = z.object({
  devengoId: z.string().uuid(),
  cargoId: z.string().uuid().nullable(),
  estado: z.enum(['DEVENGADO','COBRADO','COBRADO_PARCIAL','EXONERADO','INCOBRABLE']),
  montoTotal: MontoSchema,
}).strict()

export const ErroresCU31 = {
  DEVENGO_DUPLICADO: 'AP-CU31-01',
  COTIZACION_VENCIDA: 'AP-CU31-02',
  COBRO_FALLIDO: 'AP-CU31-03',
  SIN_CUENTA_DE_INGRESO: 'AP-CU31-04',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `DEVENGO_DUPLICADO` | Ese hecho ya devengó esa comisión (R-TAR-04) |
| `COTIZACION_VENCIDA` | Hay que recalcular antes de devengar |
| `COBRO_FALLIDO` | Tras los reintentos pasa a cuenta por cobrar |
| `SIN_CUENTA_DE_INGRESO` | El concepto no tiene cuenta contable mapeada |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `componerDevengo` | Base, comisión, descuentos e impuestos; puro |
| Átomo | `elegirViaDeCobro` | Traduce forma de cobro a la operación concreta |
| Molécula | `DevengoRepositorio` | Append-only, con su clave de idempotencia |
| Molécula | `CargoRepositorio` | Ejecuta el cobro por la vía elegida |
| Molécula | `ImpuestoRepositorio` | Cálculo por impuesto vigente |
| Organismo | `CU31DevengarComision` | Se ejecuta dentro de la transacción del hecho económico |
| Página | — | Sin endpoint: lo dispara el planificador o un evento |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `comision.devengada` | Emisión de la factura electrónica | Interno |
| `comision.incobrable` | Alta en cuenta por cobrar y gestión de cobranza | — |

## Interfaz

- **App:** El usuario ve la comisión como una línea del detalle, nunca como un descuento sin nombre.
- **Backoffice:** Reporte de devengado contra cobrado por concepto y período.

## Restricciones aplicables

`R-TAR-04` · `R-TAR-05` · `R-TAR-06` · `R-TAR-11` · `R-AUD-01` · `R-AUD-05`

## Evidencia que deja

[[devengo_comision]] · [[cargo_comision]] · [[calculo_impuesto]] ·
[[deduccion_entrega]] o [[transaccion_billetera]] · [[asiento_contable]]

## Criterios de aceptación

```gherkin
Dada una entrega de fondo acreditada
Cuando se procesa el evento
Entonces existe un devengo_comision con estado DEVENGADO o COBRADO

Dado el mismo evento reprocesado
Cuando se intenta devengar otra vez
Entonces la base de datos lo rechaza por unicidad (R-TAR-04)

Dado un cobro fallido tres veces
Cuando se agota el reintento
Entonces existe una cuenta_por_cobrar_comision para ese devengo
```

## Ver también

[[CU-22 Liquidar y entregar el fondo]] · [[CU-30 Cotizar la comisión antes de operar]] · [[CU-32 Emitir factura electrónica]] · [[CU-35 Cerrar la liquidación mensual de ingresos]] · [[CU-36 Segmentar comercialmente y aplicar precio diferenciado]]
