---
tags:
  - caso-uso
  - modulo/10-billetera-custodia-y-dinero-electronico
codigo: CU-13
criticidad: media
actores: [Sistema, Operador]
normas: [Integridad de saldo, ASFI Consumidor Financiero]
---

# CU-13 — Retener y liberar saldo

> **Objetivo.** Apartar dinero sin moverlo de dueño, de forma visible y explicable
> para el titular, y garantizar que **toda retención termina**: se ejecuta, se
> libera o vence.

## Actores y disparador

- **Actor principal:** el sistema.
- **Disparadores:** aporte programado; entrega en curso; retiro en trámite;
  disputa; alerta antifraude; comisión pendiente; orden de autoridad.

## Precondiciones

1. La cuenta tiene `saldo_disponible >= monto` a retener.

## Flujo principal

1. Se crea [[retencion_saldo]] con `motivo`, `monto`, `referencia_tipo`/`referencia_id`
   y **`expira_en` obligatorio** salvo motivo `ORDEN_AUTORIDAD` (`R-BIL-08`).
2. **En la misma transacción** se ajusta la cuenta: baja `saldo_disponible`, sube
   `saldo_retenido`. El `saldo_total` no cambia.
3. La app muestra la retención con texto en lenguaje llano: *"Bs 500 reservados
   para tu aporte del 10 de marzo"*.
4. Al concretarse el hecho, la retención se **ejecuta**: se crea la
   [[transaccion_billetera]] correspondiente y la retención pasa a `EJECUTADA`
   dentro de la misma transacción.
5. Si el hecho no ocurre, se **libera**: `estado='LIBERADA'`, el importe vuelve a
   disponible y se registra `liberada_por` y el motivo.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 1a | Saldo insuficiente | No se crea la retención; el hecho que la requería no puede avanzar |
| 5a | La retención vence sin resolverse | Un proceso diario la libera por `expira_en` y deja constancia. Ninguna retención queda viva indefinidamente |
| — | Retención por orden de autoridad | No expira: solo se levanta con [[CU-17 Bloquear saldo por orden de autoridad]] |
| — | El titular reclama por saldo retenido | Se responde con la fila: motivo, monto, referencia y vencimiento ([[CU-52 Atender un reclamo en plazo]]) |

## Postcondiciones

- `saldo_retenido` es siempre igual a la suma de retenciones vigentes (`R-BIL-07`).
- Toda retención tiene un final registrado.

## Contrato · `openapi/nucleo-financiero.yaml`

```ts
export const EntradaCU13 = z.object({
  cuentaBilleteraId: z.string().uuid(),
  monto:  MontoSchema,
  motivo: z.enum(['APORTE_PROGRAMADO','ENTREGA_EN_CURSO','DISPUTA','ORDEN_AUTORIDAD','ANTIFRAUDE','COMISION_PENDIENTE']),
  referencia: z.object({ tipo: z.string(), id: z.string().uuid() }).optional(),
  expiraEn: z.string().datetime().optional(),
}).strict()

export const SalidaCU13 = z.object({
  retencionId: z.string().uuid(),
  saldoDisponible: MontoSchema,
  saldoRetenido:   MontoSchema,
  expiraEn:        z.string().datetime().nullable(),
}).strict()

export const ErroresCU13 = {
  SALDO_INSUFICIENTE: 'AP-CU13-01',
  VENCIMIENTO_REQUERIDO: 'AP-CU13-02',
  RETENCION_YA_EJECUTADA: 'AP-CU13-03',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SALDO_INSUFICIENTE` | No hay disponible para retener |
| `VENCIMIENTO_REQUERIDO` | Falta expiración y el motivo no es orden de autoridad (R-BIL-08) |
| `RETENCION_YA_EJECUTADA` | Se intentó operar sobre una retención cerrada |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `vigenciaDeRetencion` | Calcula el vencimiento según motivo y política; puro |
| Molécula | `RetencionSaldoRepositorio` | Alta, ejecución y liberación |
| Organismo | `CU13RetenerSaldo` | Transacción: retención y recálculo de saldos por trigger |
| Página | `POST /billetera/retenciones` | Traduce y delega, sin lógica |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `saldo.retenido` | Aviso al titular con el motivo en lenguaje llano | Interno o `BACKOFFICE` |
| `saldo.liberado` | Devolución del importe a disponible | — |

## Interfaz

- **App:** El saldo retenido se muestra separado, con el motivo y hasta cuándo.
- **Backoffice:** Listado de retenciones vigentes por cuenta, con su origen.

## Restricciones aplicables

`R-BIL-02` · `R-BIL-03` · `R-BIL-07` · `R-BIL-08` · `R-BIL-16` · `R-AUD-04`

## Evidencia que deja

[[retencion_saldo]] · [[cuenta_billetera]] · [[transaccion_billetera]] (si se ejecuta)

## Criterios de aceptación

```gherkin
Dada una cuenta con Bs 1.000 disponibles
Cuando se retienen Bs 400
Entonces saldo_disponible es 600 y saldo_retenido es 400
Y saldo_total sigue siendo 1.000

Dada una retención vencida y no ejecutada
Cuando corre el proceso diario
Entonces queda LIBERADA y el saldo vuelve a disponible

Dado un intento de crear una retención sin expira_en y motivo distinto de ORDEN_AUTORIDAD
Cuando se inserta
Entonces la base de datos la rechaza (R-BIL-08)
```

## Ver también

[[CU-11 Retirar saldo]] · [[CU-22 Liquidar y entregar el fondo]] · [[CU-17 Bloquear saldo por orden de autoridad]]
