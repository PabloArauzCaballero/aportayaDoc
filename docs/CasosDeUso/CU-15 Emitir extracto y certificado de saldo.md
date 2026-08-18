---
tags:
  - caso-uso
  - modulo/10-billetera-custodia-y-dinero-electronico
codigo: CU-15
criticidad: media
actores: [Usuario, Sistema]
normas: [ASFI Consumidor Financiero]
---

# CU-15 — Emitir extracto y certificado de saldo

> **Objetivo.** Poner a disposición del titular la información de su cuenta, con
> integridad verificable, y **dejar constancia de que se puso a disposición**.

## Actores y disparador

- **Actor principal:** titular.
- **Disparadores:** cierre de período (mensual); solicitud del titular; trámite
  ante un tercero que exige certificado.

## Precondiciones

1. La cuenta existe y el solicitante es el titular o está autorizado.
2. Existen [[saldo_diario_billetera]] cerrados para el período pedido.

## Flujo principal — extracto

1. Se arma el extracto desde [[movimiento_billetera]] del período, con
   `saldo_disponible_posterior` como columna de saldo corrido.
2. Se calculan totales y se contrasta con `saldo_diario_billetera` de la fecha de
   inicio y fin: si no coinciden, **no se emite** y se abre incidente.
3. Se crea [[estado_cuenta_billetera]] con `saldo_inicial`, `total_creditos`,
   `total_debitos`, `saldo_final`, `url_archivo` y `hash_archivo`.
4. Se registra `entregado_en` cuando el titular lo descarga o se le envía.
5. Se registra el acceso en [[registro_acceso_datos]] si lo genera un operador y no
   el propio titular.

## Flujo principal — certificado de saldo

1. Se toma el saldo a `fecha_corte` desde `saldo_diario_billetera` (no recalculado
   al vuelo).
2. Se crea [[certificado_saldo]] con `folio` único, `hash_documento` y
   `url_documento`.
3. El tercero receptor puede verificar folio + hash contra un endpoint público de
   verificación.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | El extracto no cuadra con el saldo diario | Se bloquea la emisión, se abre [[descuadre_custodia]] o [[incidente_operativo]] |
| 1a | Período sin cierres diarios completos | Se emite hasta la última fecha cerrada y se informa el corte |
| — | Cuenta cerrada | El extracto histórico sigue disponible durante el período de conservación |
| 3a | El período pedido tiene reversas posteriores a su cierre | Se emite tal como quedó el período y la reversa figura en su fecha real: **un extracto no se reescribe hacia atrás** |
| — | Se pide el certificado para un trámite con tercero | Se emite a nombre del solicitante con folio y hash verificables en línea, sin exponer movimientos |

## Postcondiciones

- Existe el documento archivado con su hash: se puede probar qué se entregó.

## Contrato · `openapi/nucleo-financiero.yaml`

```ts
export const EntradaCU15 = z.object({
  cuentaBilleteraId: z.string().uuid(),
  tipo:   z.enum(['EXTRACTO','CERTIFICADO']),
  desde:  z.string().date().optional(),
  hasta:  z.string().date().optional(),
  fechaCorte: z.string().date().optional(),
}).strict()

export const SalidaCU15 = z.object({
  documentoId: z.string().uuid(),
  folio:  z.string().nullable(),
  urlArchivo: z.string().url(),
  hashArchivo: z.string().length(64),
  saldoFinal:  MontoSchema,
}).strict()

export const ErroresCU15 = {
  PERIODO_SIN_CIERRES: 'AP-CU15-01',
  EXTRACTO_NO_CUADRA: 'AP-CU15-02',
  SIN_PERMISO_SOBRE_LA_CUENTA: 'AP-CU15-03',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `PERIODO_SIN_CIERRES` | Faltan cierres diarios en el rango pedido |
| `EXTRACTO_NO_CUADRA` | El calculado difiere del saldo diario sellado |
| `SIN_PERMISO_SOBRE_LA_CUENTA` | El solicitante no es el titular ni backoffice |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `componerExtracto` | Arma saldo corrido y totales desde los movimientos; puro |
| Átomo | `verificarContraSaldoDiario` | Contrasta el calculado con el sellado |
| Molécula | `SaldoDiarioRepositorio` | Cierres diarios encadenados |
| Molécula | `DocumentoAdaptador` | Genera el PDF y lo guarda con object lock |
| Organismo | `CU15EmitirExtracto` | Transacción: emisión, hash y registro de entrega |
| Página | `GET /billetera/extractos` | Traduce y delega, sin lógica |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `extracto.emitido` | Entrega por el canal preferido y archivo con hash | `BILLETERA_VER` |
| `certificado.emitido` | Publicación del folio verificable | — |

## Interfaz

- **App:** *Movimientos → Descargar*: extracto por mes y certificado de saldo con folio.
- **Backoffice:** Emisión de certificados a pedido, con registro de acceso a datos.

## Restricciones aplicables

`R-CON-08` · `R-AUD-07` · `R-SEG-02`

## Evidencia que deja

[[estado_cuenta_billetera]] · [[certificado_saldo]] · [[registro_acceso_datos]]

## Criterios de aceptación

```gherkin
Dado un período con cierres diarios completos
Cuando se emite el extracto
Entonces saldo_final coincide con el saldo_diario_billetera de la fecha final
Y existe hash_archivo no nulo

Dado un certificado emitido
Cuando un tercero verifica folio y hash
Entonces el sistema confirma su autenticidad

Dada una diferencia entre el extracto calculado y el saldo diario
Cuando se intenta emitir
Entonces la emisión se bloquea y se registra el descuadre
```

## Ver también

[[CU-50 Conciliar la custodia y verificar el encaje]] · [[CU-52 Atender un reclamo en plazo]] · [[CU-58 Definir, programar y exportar un reporte]]
