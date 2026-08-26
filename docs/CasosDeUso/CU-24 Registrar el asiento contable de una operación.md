---
tags:
  - caso-uso
  - modulo/03-aportes-pagos-qr-y-conciliacion
codigo: CU-24
criticidad: alta
actores: [Sistema]
normas: [Ley 393 (libros y conservación), plan de cuentas, NIIF]
---

# CU-24 — Registrar el asiento contable de una operación

> **Objetivo.** Que toda operación con dinero tenga su espejo en el mayor por
> doble partida, en la misma transacción, y que **nada se edite jamás**.

## Actores y disparador

- **Actor principal:** el sistema.
- **Disparadores:** acreditación de pago, entrega de fondo, cobertura, devengo o
  cobro de comisión, recarga, retiro, reverso, ajuste.

## Precondiciones

1. Existe la [[cuenta_contable]] destino para cada concepto (el plan de cuentas
   está sembrado y mapeado: `concepto_tarifa.cuenta_ingreso_id`,
   `impuesto.cuenta_contable_id`, `cuenta_billetera.cuenta_contable_id`).

## Flujo principal

1. La operación de negocio arma su juego de partidas.
2. **En la misma transacción que el hecho económico** se crea
   [[asiento_contable]] con `origen_tipo` / `origen_id` apuntando al hecho, y sus
   [[movimiento_contable]].
3. Un trigger valida `SUM(debe) = SUM(haber)` al confirmar el asiento (`R-AUD-05`).
4. Se actualizan los saldos de cuenta y se emite `evento_dominio`.
5. El asiento entra al [[cierre_diario]] de su fecha ([[CU-51 Ejecutar el cierre diario]]).

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 3a | Las partidas no cuadran | La transacción entera falla: **no existe operación sin asiento cuadrado** |
| — | Corrección de un asiento | No se edita: se crea el asiento inverso y se enlaza con `asiento_reversa_id` (`R-AUD-06`) |
| 1a | Falta la cuenta contable del concepto | El devengo o la operación se rechazan en configuración, no en producción |
| — | Cierre del período contable | Los asientos del período cerrado no admiten inserciones retroactivas sin reapertura autorizada |

## Postcondiciones

- El mayor refleja exactamente la posición de dinero del sistema.
- La suma de saldos de billetera reconcilia contra las cuentas del mayor.

## Contrato · `openapi/nucleo-financiero.yaml`

```ts
export const EntradaCU24 = z.object({
  origenTipo: z.enum(['PAGO','ENTREGA','COBERTURA','COMISION','BILLETERA','AJUSTE']),
  origenId: z.string().uuid(),
  partidas: z.array(z.object({ cuentaCodigo: z.string(), debe: MontoSchema, haber: MontoSchema })).min(2),
  glosa: z.string().max(200),
}).strict()

export const SalidaCU24 = z.object({
  asientoId: z.string().uuid(),
  numero: z.number().int(),
  totalDebe: MontoSchema,
  totalHaber: MontoSchema,
}).strict()

export const ErroresCU24 = {
  ASIENTO_DESCUADRADO: 'AP-CU24-01',
  CUENTA_INEXISTENTE: 'AP-CU24-02',
  PERIODO_CERRADO: 'AP-CU24-03',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `ASIENTO_DESCUADRADO` | Debe y haber no coinciden (R-AUD-05) |
| `CUENTA_INEXISTENTE` | El código de cuenta no está en el plan |
| `PERIODO_CERRADO` | El período contable ya se cerró |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `cuadrarPartidas` | Verifica la igualdad y normaliza signos; puro con pruebas de propiedad |
| Molécula | `AsientoRepositorio` | Alta del asiento y sus movimientos, append-only |
| Molécula | `CuentaContableRepositorio` | Resuelve el código a la cuenta |
| Organismo | `CU24RegistrarAsiento` | Se ejecuta dentro de la transacción del hecho económico |
| Página | — | Sin endpoint: lo dispara el planificador o un evento |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `asiento.registrado` | Actualiza saldos de cuenta y alimenta el cierre diario | Interno |
| `asiento.reversado` | Asiento inverso enlazado al original | `CONTABILIDAD` |

## Interfaz

- **App:** No tiene pantalla: sostiene lo que el usuario ve en el panel de transparencia.
- **Backoffice:** Libro mayor con filtro por cuenta, período y origen.

## Restricciones aplicables

`R-AUD-01` · `R-AUD-05` · `R-AUD-06` · `R-CTB-02`

## Evidencia que deja

[[asiento_contable]] · [[movimiento_contable]] · [[cuenta_contable]] ·
[[cierre_diario]]

## Criterios de aceptación

```gherkin
Dada una entrega de fondo liquidada
Cuando se confirma el asiento
Entonces SUM(debe) = SUM(haber) para ese asiento

Dado un asiento confirmado
Cuando se intenta hacer UPDATE de un movimiento_contable
Entonces la base de datos lo rechaza

Dada una corrección contable
Cuando se ejecuta
Entonces existe un asiento nuevo con asiento_reversa_id apuntando al original
```

## Ver también

[[CU-35 Cerrar la liquidación mensual de ingresos]] · [[CU-50 Conciliar la custodia y verificar el encaje]] · [[CU-51 Ejecutar el cierre diario]] · [[CU-67 Disolver el grupo anticipadamente]] · [[CU-103 Registrar y pagar una factura de proveedor]]
