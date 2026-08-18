---
tags:
  - caso-uso
  - modulo/13-contabilidad-erp
codigo: CU-105
criticidad: media
actores: [Sistema, Contabilidad]
normas: [NIIF, Código de Comercio]
---

# CU-105 — Depreciar un activo fijo

> **Objetivo.** Que el gasto de comprar un bien de uso se reconozca a lo largo
> de su vida útil, no todo de golpe el mes de la compra.

## Actores y disparador

- **Actor principal:** el sistema, en la corrida mensual; Contabilidad da de
  alta el activo y lo da de baja.
- **Disparadores:** recepción de una [[factura_proveedor]] por un bien de uso;
  cierre de un [[periodo_contable]] (ver
  [[CU-100 Abrir y cerrar el período contable]]).

## Precondiciones

1. Existe la [[categoria_activo_fijo]] correspondiente, con sus tres cuentas
   contables (`cuenta_activo_id`, `cuenta_depreciacion_id`,
   `cuenta_gasto_depreciacion_id`) marcadas `es_cuenta_de_movimiento = true`.

## Flujo principal

1. Contabilidad da de alta el [[activo_fijo]] con `costo_adquisicion`,
   `fecha_adquisicion` y, si corresponde, `factura_proveedor_id`.
2. Al cerrar cada [[periodo_contable]], el sistema calcula la cuota mensual de
   depreciación (`costo_adquisicion - valor_residual` dividido entre
   `vida_util_meses` de la categoría, método línea recta).
3. **En la misma transacción**: se crea [[depreciacion_activo]] con el monto y
   el `periodo_contable_id`, se actualiza `depreciacion_acumulada` del activo
   (`valor_en_libros` se recalcula solo, es `GENERATED`), y se genera el
   [[asiento_contable]] (`DEBE` cuenta de gasto, `HABER` cuenta de
   depreciación acumulada).
4. Cuando `valor_en_libros` llega a `valor_residual`, el activo deja de generar
   depreciación nueva.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | El activo se dio de alta a mitad de mes | La primera cuota se prorratea por días; las siguientes son completas |
| — | El activo se da de baja antes de agotar su vida útil | `estado = 'DADO_DE_BAJA'` con motivo; deja de depreciarse desde ese período |
| — | El activo se vende | `estado = 'VENDIDO'`; la diferencia entre precio de venta y `valor_en_libros` se asienta como resultado, fuera del alcance de este caso |
| — | Ya existe una `depreciacion_activo` para ese activo y ese período | Se rechaza: `UQ(activo_fijo_id, periodo_contable_id)` impide duplicar la corrida |

## Postcondiciones

- `valor_en_libros` de cada activo refleja su valor contable real en todo
  momento, sin recalcularlo a mano.

## Contrato · `openapi/erp.yaml`

```ts
export const EntradaCU105 = z.object({
  activoFijoId: z.string().uuid(),
  periodoContableId: z.string().uuid(),
}).strict()

export const SalidaCU105 = z.object({
  depreciacionId: z.string().uuid(),
  monto: MontoSchema,
  valorEnLibros: MontoSchema,
}).strict()

export const ErroresCU105 = {
  DEPRECIACION_YA_CALCULADA: 'AP-CU105-01',
  ACTIVO_YA_AGOTADO: 'AP-CU105-02',
  ACTIVO_DADO_DE_BAJA: 'AP-CU105-03',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `DEPRECIACION_YA_CALCULADA` | Ya existe `depreciacion_activo` para ese activo y período |
| `ACTIVO_YA_AGOTADO` | `valor_en_libros` ya llegó a `valor_residual` |
| `ACTIVO_DADO_DE_BAJA` | El activo está `DADO_DE_BAJA` o `VENDIDO` |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `calcularCuotaDepreciacion` | Línea recta con prorrateo del primer mes; puro, con pruebas de propiedad |
| Molécula | `ActivoFijoRepositorio` | Alta, baja y actualización de depreciación acumulada |
| Molécula | `DepreciacionActivoRepositorio` | Alta append-only de corridas |
| Organismo | `CU105DepreciarPeriodo` | Se ejecuta como parte del cierre del período (worker, fase 9) |
| Página | `apps/backoffice` — inventario de activos fijos | Alta de activo, historial de depreciación por activo |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `activo_fijo.depreciado` | Actualiza el mayor con el gasto del período | Interno (worker de cierre) |
| `activo_fijo.dado_de_baja` | Detiene la depreciación futura | `CONTABILIDAD_ERP_ACTIVOS_FIJOS` |

## Interfaz

- **App:** No tiene pantalla: gestión administrativa interna.
- **Backoffice:** Inventario de activos con costo, depreciación acumulada y
  valor en libros; historial de corridas por activo.

## Restricciones aplicables

`R-AUD-01` · `R-AUD-05` · `R-AUD-06` · `R-CTB-07`

## Evidencia que deja

[[categoria_activo_fijo]] · [[activo_fijo]] · [[depreciacion_activo]] ·
[[asiento_contable]]

## Criterios de aceptación

```gherkin
Dado un activo fijo con costo 12000, sin valor residual y vida útil de 12 meses
Cuando se cierra un período completo
Entonces se crea depreciacion_activo por 1000 y valor_en_libros baja a 11000

Dado un activo fijo cuyo valor_en_libros ya llegó a su valor_residual
Cuando se intenta calcular una depreciación nueva
Entonces el sistema devuelve ACTIVO_YA_AGOTADO

Dado un activo fijo ya depreciado en el período vigente
Cuando se intenta calcular la depreciación de ese mismo período otra vez
Entonces el sistema devuelve DEPRECIACION_YA_CALCULADA
```

## Ver también

[[CU-100 Abrir y cerrar el período contable]]
