---
tags:
  - caso-uso
  - modulo/13-contabilidad-erp
codigo: CU-102
criticidad: media
actores: [Contabilidad, Operaciones]
normas: [Código de Comercio, control interno]
---

# CU-102 — Dar de alta un tercero comercial y su orden de compra

> **Objetivo.** Que la empresa pueda comprarle a un proveedor externo (que no es
> un usuario de la plataforma) con una orden autorizada previa a cualquier factura.

## Actores y disparador

- **Actor principal:** Operaciones (solicita), Contabilidad (autoriza).
- **Disparadores:** necesidad de comprar un bien o servicio a un
  [[tercero_comercial]] nuevo o existente.

## Precondiciones

1. Si el tercero no existe, se cuenta con su `numero_documento` (NIT) para el alta.
2. El [[centro_costo]] contra el que se compra, si aplica, existe y está activo.

## Flujo principal

1. Se da de alta el [[tercero_comercial]] con `tipo = 'PROVEEDOR'` (o `'AMBOS'`),
   `numero_documento` único, y estado `ACTIVO`.
2. Se crea la [[orden_compra]] en estado `BORRADOR`, con `monto_total`, `moneda`
   y el `centro_costo_id` que va a absorber el gasto.
3. Contabilidad aprueba: `orden_compra.estado = 'APROBADA'`, con `aprobada_por`.
4. Al recibir el bien o servicio, la orden pasa a `RECIBIDA_PARCIAL` o
   `RECIBIDA_TOTAL` según corresponda, habilitando la [[factura_proveedor]]
   (ver [[CU-103 Registrar y pagar una factura de proveedor]]).

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 1a | El `numero_documento` ya existe | Se reutiliza el tercero existente, no se duplica |
| 3a | Se intenta aprobar una orden ya `CANCELADA` | Se rechaza |
| — | El tercero se bloquea (`tercero_comercial.estado = 'BLOQUEADO'`) | Ninguna orden de compra nueva puede crearse contra él; las abiertas se resuelven caso por caso |
| — | Se recibe menos de lo pedido | La orden queda `RECIBIDA_PARCIAL`; puede generar más de una `factura_proveedor` |

## Postcondiciones

- Toda compra a un tercero externo tiene una orden autorizada antes de que exista
  la obligación de pagarla.

## Contrato · `openapi/erp.yaml`

```ts
export const EntradaCU102 = z.object({
  terceroComercialId: z.string().uuid().optional(),
  terceroNuevo: z.object({
    razonSocial: z.string().max(150),
    numeroDocumento: z.string().max(30),
    email: z.string().email().optional(),
  }).optional(),
  centroCostoId: z.string().uuid().optional(),
  descripcion: z.string().max(300),
  montoTotal: MontoSchema,
}).strict()

export const SalidaCU102 = z.object({
  terceroComercialId: z.string().uuid(),
  ordenCompraId: z.string().uuid(),
  estado: z.string(),
}).strict()

export const ErroresCU102 = {
  TERCERO_BLOQUEADO: 'AP-CU102-01',
  DOCUMENTO_YA_REGISTRADO_A_OTRO_TERCERO: 'AP-CU102-02',
  ORDEN_SIN_APROBACION: 'AP-CU102-03',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `TERCERO_BLOQUEADO` | El tercero existe pero está `BLOQUEADO` |
| `DOCUMENTO_YA_REGISTRADO_A_OTRO_TERCERO` | El NIT coincide con un tercero de otra razón social |
| `ORDEN_SIN_APROBACION` | Se intenta recibir o facturar una orden todavía en `BORRADOR` |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `validarDocumentoTercero` | Formato de NIT; puro |
| Molécula | `TerceroComercialRepositorio` | Alta y bloqueo de terceros |
| Molécula | `OrdenCompraRepositorio` | Alta, aprobación y recepción de órdenes |
| Organismo | `CU102AltaTerceroYOrden` | Transacción de alta de tercero + orden |
| Página | `apps/backoffice` — pantalla de proveedores y órdenes de compra | Alta de tercero, listado de órdenes por estado |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `orden_compra.aprobada` | Habilita registrar la factura del proveedor | `CONTABILIDAD_ERP_COMPRAS` |
| `tercero_comercial.bloqueado` | Impide nuevas órdenes contra ese tercero | `CONTABILIDAD_ERP_COMPRAS` |

## Interfaz

- **App:** No tiene pantalla: gestión administrativa interna.
- **Backoffice:** Alta de proveedor, listado de órdenes de compra con filtro por
  estado y centro de costo.

## Restricciones aplicables

`R-CTB-04`

Es compra administrativa de la empresa a un tercero, fuera del circuito de
custodia del pasanaku: no aplican las restricciones de billetera.

## Evidencia que deja

[[tercero_comercial]] · [[orden_compra]]

## Criterios de aceptación

```gherkin
Dado un NIT que no existe en tercero_comercial
Cuando se da de alta un proveedor nuevo con ese NIT
Entonces se crea tercero_comercial en estado ACTIVO

Dada una orden_compra en estado BORRADOR
Cuando Contabilidad la aprueba
Entonces pasa a estado APROBADA con aprobada_por registrado

Dado un tercero_comercial en estado BLOQUEADO
Cuando se intenta crear una orden_compra nueva contra él
Entonces el sistema rechaza la operación
```

## Ver también

[[CU-103 Registrar y pagar una factura de proveedor]]
