---
tags:
  - caso-uso
  - modulo/13-contabilidad-erp
codigo: CU-104
criticidad: alta
actores: [Sistema, Tesorería]
normas: [Ley 393 (libros y conservación), NIIF]
---

# CU-104 — Cobrar una cuenta por cobrar

> **Objetivo.** Que todo lo que un tercero le debe a la empresa —sin importar
> si es un aporte del pasanaku o la liquidación de publicidad de un
> anunciante— se cobre y se contabilice por el mismo camino disciplinado.

## Actores y disparador

- **Actor principal:** el sistema (genera la cuenta por cobrar), Tesorería
  (registra el cobro).
- **Disparadores:** cualquier hecho que genere una obligación de cobro fuera del
  circuito de billetera del pasanaku — hoy, el cierre de una
  [[factura_publicidad]] (ver [[CU-114 Liquidar y facturar el gasto publicitario]]).

## Precondiciones

1. Existe el hecho de origen (`origen_tipo`/`origen_id`) que justifica el cobro.

## Flujo principal

1. El sistema crea [[cuenta_por_cobrar]] con `origen_tipo`, `origen_id`, `monto`,
   `moneda` y `estado = 'PENDIENTE'`.
2. Tesorería registra el [[cobro_cuenta_por_cobrar]] cuando el tercero paga,
   total o parcialmente.
3. **En la misma transacción**: se actualiza `monto_cobrado`/`saldo_pendiente`
   de la cuenta por cobrar, se genera el [[asiento_contable]] del ingreso
   (`DEBE` banco/caja, `HABER` cuentas por cobrar), y si `saldo_pendiente = 0`,
   `estado = 'COBRADA'`.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | Cobro parcial | `estado = 'COBRADA_PARCIAL'`; admite más de un `cobro_cuenta_por_cobrar` |
| — | El tercero no paga después de vencido el plazo razonable (política comercial) | Contabilidad puede marcar `estado = 'INCOBRABLE'` con motivo, sin borrar el registro |
| — | Se registra un cobro mayor al `saldo_pendiente` | Se rechaza: el excedente no se acredita automáticamente a otra cuenta |
| — | El período contable vigente está cerrado al momento del cobro | El asiento del cobro se registra en el período abierto actual, nunca retroactivo |

## Postcondiciones

- Toda plata que un tercero externo le debe a la empresa queda con estado
  verificable: pendiente, cobrada parcial, cobrada, o declarada incobrable con
  motivo — nunca "se perdió de vista".

## Contrato · `openapi/erp.yaml`

```ts
export const EntradaCU104 = z.object({
  cuentaPorCobrarId: z.string().uuid(),
  monto: MontoSchema,
  formaCobro: z.enum(['TRANSFERENCIA', 'QR', 'TARJETA', 'EFECTIVO']),
}).strict()

export const SalidaCU104 = z.object({
  cobroId: z.string().uuid(),
  saldoPendiente: MontoSchema,
  estado: z.enum(['PENDIENTE', 'COBRADA_PARCIAL', 'COBRADA', 'INCOBRABLE']),
}).strict()

export const ErroresCU104 = {
  CUENTA_INEXISTENTE: 'AP-CU104-01',
  MONTO_MAYOR_AL_SALDO: 'AP-CU104-02',
  CUENTA_YA_INCOBRABLE: 'AP-CU104-03',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `CUENTA_INEXISTENTE` | El `cuentaPorCobrarId` no existe |
| `MONTO_MAYOR_AL_SALDO` | El cobro excede `saldo_pendiente` |
| `CUENTA_YA_INCOBRABLE` | Se intenta cobrar una cuenta ya marcada `INCOBRABLE` |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `calcularSaldoPendiente` | `monto - monto_cobrado`; puro |
| Molécula | `CuentaPorCobrarRepositorio` | Alta y actualización de saldo |
| Molécula | `CobroCuentaPorCobrarRepositorio` | Alta append-only del cobro |
| Organismo | `CU104RegistrarCobro` | Transacción de cobro + asiento |
| Página | `apps/backoffice` — cuentas por cobrar | Listado con origen, saldo y acción de registrar cobro |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `cuenta_por_cobrar.creada` | Aparece en el panel de cuentas por cobrar | Interno |
| `cuenta_por_cobrar.cobrada` | Genera el asiento del ingreso | `CONTABILIDAD_ERP_COBRAR` |

## Interfaz

- **App:** No tiene pantalla: el anunciante ve su factura por su propio canal
  (módulo 14), no el detalle contable interno.
- **Backoffice:** Cuentas por cobrar con origen, antigüedad de saldo y acción de
  registrar cobro.

## Restricciones aplicables

`R-AUD-01` · `R-AUD-05` · `R-AUD-06` · `R-CTB-06`

## Evidencia que deja

[[cuenta_por_cobrar]] · [[cobro_cuenta_por_cobrar]] · [[asiento_contable]]

## Criterios de aceptación

```gherkin
Dada una cuenta_por_cobrar pendiente con origen_tipo FACTURA_PUBLICIDAD
Cuando Tesorería registra un cobro por el monto total
Entonces la cuenta pasa a estado COBRADA y queda su asiento contable enlazado

Dada una cuenta_por_cobrar con saldo_pendiente de 500
Cuando se intenta registrar un cobro de 800
Entonces el sistema devuelve MONTO_MAYOR_AL_SALDO

Dada una cuenta_por_cobrar marcada INCOBRABLE
Cuando se intenta registrar un cobro sobre ella
Entonces el sistema devuelve CUENTA_YA_INCOBRABLE
```

## Ver también

[[CU-114 Liquidar y facturar el gasto publicitario]]
