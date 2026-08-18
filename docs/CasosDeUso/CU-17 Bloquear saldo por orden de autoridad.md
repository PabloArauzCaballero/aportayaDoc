---
tags:
  - caso-uso
  - modulo/10-billetera-custodia-y-dinero-electronico
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
codigo: CU-17
criticidad: alta
actores: [Autoridad, Legal, Oficial de cumplimiento]
normas: [UIF, requerimientos judiciales y fiscales]
---

# CU-17 — Bloquear saldo por orden de autoridad

> **Objetivo.** Cumplir un oficio en minutos, **sin mentirle al titular ni perder
> el rastro del dinero**, y poder demostrar después que el bloqueo tuvo respaldo
> legal.

## Actores y disparador

- **Actor principal:** autoridad competente (juzgado, fiscalía, unidad de
  inteligencia financiera, supervisor).
- **Actor secundario:** área legal / oficial de cumplimiento.
- **Disparador:** recepción de un oficio.

## Precondiciones

1. El oficio está recibido, digitalizado y su autenticidad verificada.
2. Se identificó al titular afectado.

## Flujo principal

1. Se registra [[requerimiento_autoridad]] con `numero_oficio` único,
   `autoridad`, `alcance`, `plazo_respuesta`, `documento_url` y `hash_documento`.
2. Se crea [[bloqueo_saldo]] con `alcance` (`TOTAL` o `PARCIAL`),
   `monto_bloqueado`, `vence_en` si el oficio lo fija.
3. **En la misma transacción** se materializa como [[retencion_saldo]] con
   `motivo='ORDEN_AUTORIDAD'` y **sin** `expira_en` (`R-BIL-08`), moviendo el
   importe de disponible a retenido.
4. El saldo sigue figurando como del titular: la app muestra el monto no disponible
   con una explicación y una referencia de contacto.
5. Se responde a la autoridad dentro del plazo y se guarda `respuesta_url`.
6. Se emiten `evento_dominio` y entrada en [[bitacora_evento]]; toda consulta de
   datos del afectado queda en [[registro_acceso_datos]].

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | El saldo es menor al monto ordenado | Se bloquea lo disponible y se informa a la autoridad el faltante; los ingresos futuros se retienen hasta completar, si el oficio lo ordena |
| — | Levantamiento del bloqueo | Nuevo oficio → `bloqueo_saldo.estado='LEVANTADO'`, `levantada_por`, y la retención se libera en la misma transacción |
| — | Vencimiento del plazo del oficio | El bloqueo no se libera solo: requiere acto expreso registrado |
| 5a | El plazo de respuesta vence | Queda visible como vencido y escala a [[hallazgo_auditoria]] |
| — | El titular reclama | Se responde con número de oficio y autoridad; el reclamo se tramita por [[CU-52 Atender un reclamo en plazo]] |

## Postcondiciones

- El importe ordenado está indisponible y trazado a un oficio con hash.
- Ninguna orden se ejecuta sin documento de respaldo registrado.

## Contrato · `openapi/nucleo-financiero.yaml`

```ts
export const EntradaCU17 = z.object({
  cuentaBilleteraId: z.string().uuid(),
  autoridad: z.enum(['JUZGADO','UIF','ASFI','FISCALIA','INTERNO']),
  numeroOficio: z.string().max(60),
  alcance: z.enum(['TOTAL','PARCIAL']),
  montoBloqueado: MontoSchema.optional(),
  documentoUrl: z.string().url(),
  hashDocumento: z.string().length(64),
}).strict()

export const SalidaCU17 = z.object({
  bloqueoId: z.string().uuid(),
  retencionId: z.string().uuid(),
  montoEfectivamenteBloqueado: MontoSchema,
  faltante: MontoSchema,
}).strict()

export const ErroresCU17 = {
  OFICIO_DUPLICADO: 'AP-CU17-01',
  CUENTA_INEXISTENTE: 'AP-CU17-02',
  SIN_DOCUMENTO_DE_RESPALDO: 'AP-CU17-03',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `OFICIO_DUPLICADO` | Ya existe un bloqueo con ese número (R-BIL-14) |
| `CUENTA_INEXISTENTE` | No se identificó al titular |
| `SIN_DOCUMENTO_DE_RESPALDO` | No se ejecuta una orden sin el oficio y su hash |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `calcularAlcance` | Cuánto se puede bloquear y cuánto queda faltante; puro |
| Molécula | `BloqueoSaldoRepositorio` | Expediente del bloqueo |
| Molécula | `RetencionSaldoRepositorio` | Materializa la inmovilización |
| Organismo | `CU17BloquearSaldo` | Transacción: bloqueo, retención y trazabilidad del oficio |
| Página | `POST /cumplimiento/bloqueos` | Traduce y delega, sin lógica |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `saldo.bloqueado` | Aviso al titular con número de oficio y autoridad | `CUMPLIMIENTO_CASOS` |
| `saldo.desbloqueado` | Liberación de la retención al levantarse | — |

## Interfaz

- **App:** El titular ve el monto no disponible con el número de oficio y a quién consultar.
- **Backoffice:** Alta de oficios con carga del documento; sin archivo no se ejecuta.

## Restricciones aplicables

`R-BIL-08` · `R-BIL-14` · `R-BIL-13` · `R-SEG-02` · `R-AUD-04`

## Evidencia que deja

[[requerimiento_autoridad]] · [[bloqueo_saldo]] · [[retencion_saldo]] ·
[[registro_acceso_datos]] · [[bitacora_evento]]

## Criterios de aceptación

```gherkin
Dado un oficio judicial que ordena inmovilizar Bs 5.000
Cuando se registra
Entonces existe un bloqueo_saldo con numero_oficio único
Y una retencion_saldo VIGENTE con motivo ORDEN_AUTORIDAD y sin expira_en

Dado un bloqueo vigente
Cuando el titular intenta retirar el importe bloqueado
Entonces la operación se rechaza

Dado un intento de registrar dos bloqueos con el mismo numero_oficio
Cuando se inserta el segundo
Entonces la base de datos lo rechaza (R-BIL-14)
```

## Ver también

[[CU-11 Retirar saldo]] · [[CU-13 Retener y liberar saldo]] · [[CU-27 Restringir al deudor e incluirlo en la lista interna]] · [[CU-45 Atender un requerimiento de autoridad]]
