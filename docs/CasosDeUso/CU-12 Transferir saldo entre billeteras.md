---
tags:
  - caso-uso
  - modulo/10-billetera-custodia-y-dinero-electronico
codigo: CU-12
criticidad: alta
actores: [Usuario, Sistema]
normas: [UIF art. 53 inc. g, límites BCB]
---

# CU-12 — Transferir saldo entre billeteras

> **Objetivo.** Mover saldo de una cuenta a otra de forma instantánea y sin costo
> de red bancaria. Es el mecanismo por el que **el aporte al pasanaku se vuelve un
> toque**, y también el vector de lavado más obvio: por eso se monitorea.

## Actores y disparador

- **Actor principal:** usuario ordenante.
- **Disparadores:** aporte a un grupo; envío a otra persona; movimiento interno
  del sistema (cobertura, comisión, devolución).

## Precondiciones

1. Ambas [[cuenta_billetera]] existen, están `ACTIVA` y comparten `moneda`.
2. `politica_billetera.permite_transferencia_p2p = true` para el ordenante.
3. Saldo disponible suficiente; sin bloqueos que afecten el importe.

## Flujo principal

1. Se resuelve la cuenta destino (alias público o grupo) y se muestra al ordenante
   **a quién le está enviando** antes de confirmar.
2. Se evalúan límites ([[CU-40 Evaluar límites antes de una operación]]) y
   antifraude.
3. **En una sola transacción**:
   - se crea [[transaccion_billetera]] `tipo='TRANSFERENCIA_P2P'` o
     `'APORTE_A_GRUPO'`, con `clave_idempotencia`;
   - se crean dos [[movimiento_billetera]]: débito al ordenante, crédito al
     destino (suma cero);
   - se crea [[transferencia_p2p]] con `concepto` y, si es aporte,
     `obligacion_id` apuntando a la [[obligacion_aporte]] que salda;
   - si es aporte, se actualiza la obligación y se registra el
     [[asiento_contable]] correspondiente;
   - se emite `evento_dominio` `TRANSFERENCIA_EJECUTADA`.
4. El motor de umbrales evalúa acumulados de transferencia desde billetera →
   [[CU-42 Detectar umbral y registrar ROG]].
5. Se notifica a ambas partes.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 1a | Alias inexistente o cuenta cerrada | Se rechaza antes de mover nada |
| 2a | Antifraude detecta circularidad (A→B→A en minutos) | Se retiene la operación y se genera [[alerta_monitoreo_lft]] |
| 3a | Saldo insuficiente | Rechazo por `R-BIL-02`; no se crea transacción parcial |
| 3b | El aporte excede el saldo pendiente de la obligación | Se acredita el excedente como saldo a favor o se rechaza según política del grupo |
| 4a | Se supera el umbral acumulado | Se registra la operación relevante; la transferencia **no se bloquea** por ese solo hecho |

## Postcondiciones

- La suma de saldos del sistema no cambió: el dinero cambió de cuenta.
- Si era aporte, la obligación quedó saldada o parcialmente pagada, con traza.

## Contrato · `openapi/nucleo-financiero.yaml`

```ts
export const EntradaCU12 = z.object({
  claveIdempotencia: z.string().uuid(),
  cuentaOrigenId:    z.string().uuid(),
  destino:           z.object({ tipo: z.enum(['ALIAS','GRUPO']), valor: z.string() }),
  monto:             MontoSchema,
  concepto:          z.string().max(140),
  obligacionId:      z.string().uuid().optional(),
}).strict()

export const SalidaCU12 = z.object({
  transaccionId: z.string().uuid(),
  saldoDespues:  MontoSchema,
  destinatario:  z.object({ nombre: z.string(), tipo: z.string() }),
  obligacionSaldada: z.boolean(),
}).strict()

export const ErroresCU12 = {
  SALDO_INSUFICIENTE: 'AP-CU12-01',
  DESTINO_INEXISTENTE: 'AP-CU12-02',
  CUENTA_DESTINO_NO_OPERATIVA: 'AP-CU12-03',
  LIMITE_EXCEDIDO: 'AP-CU12-04',
  P2P_NO_HABILITADO: 'AP-CU12-05',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SALDO_INSUFICIENTE` | No alcanza el disponible (R-BIL-02) |
| `DESTINO_INEXISTENTE` | El alias o el grupo no existe |
| `CUENTA_DESTINO_NO_OPERATIVA` | Está cerrada o congelada |
| `LIMITE_EXCEDIDO` | Supera el techo de transferencia del nivel |
| `P2P_NO_HABILITADO` | La política de la cuenta no permite transferencias |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `componerParDeMovimientos` | Débito y crédito que suman cero; puro |
| Átomo | `aplicarAObligacion` | Cuánto del monto salda la obligación y cuánto queda a favor |
| Molécula | `TransferenciaRepositorio` | Alta de la transferencia |
| Molécula | `ObligacionRepositorio` | Actualiza el aporte si la transferencia lo salda |
| Organismo | `CU12TransferirSaldo` | Transacción: par de movimientos, obligación y asiento |
| Página | `POST /billetera/transferencias` | Traduce y delega, sin lógica |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `transferencia.ejecutada` | Umbrales UIF, asiento y aviso a ambas partes | `BILLETERA_OPERAR` |
| `aporte.confirmado` | Actualiza el tablero del grupo | — |

## Interfaz

- **App:** Enviar con confirmación de a quién: nombre y foto antes de aceptar.
- **Backoffice:** Trazabilidad de transferencias entre cuentas para investigación.

## Restricciones aplicables

`R-BIL-01` · `R-BIL-02` · `R-BIL-06` · `R-BIL-19` · `R-BIL-20` · `R-GRP-03` ·
`R-LIM-01` · `R-AUD-01` · `R-AUD-03` · `R-AUD-05` · `R-UIF-02`

## Evidencia que deja

[[transaccion_billetera]] · [[movimiento_billetera]] · [[transferencia_p2p]] ·
[[obligacion_aporte]] · [[asiento_contable]] · [[registro_operacion_relevante]]

## Criterios de aceptación

```gherkin
Dada una transferencia de Bs 500 entre dos cuentas activas
Cuando se ejecuta
Entonces existen dos movimiento_billetera que suman cero
Y el saldo total del sistema permanece constante

Dado un aporte con obligacion_id
Cuando se acredita
Entonces obligacion_aporte.monto_pagado aumenta en el importe
Y existe un asiento_contable con SUM(debe) = SUM(haber)

Dado un usuario que acumula USD 1.000 en transferencias desde billetera en 3 días
Cuando ejecuta la que alcanza el umbral
Entonces existe un registro_operacion_relevante con formulario ROG-03
```

## Ver también

[[CU-21 Cobrar el aporte del período]] · [[CU-42 Detectar umbral y registrar ROG]] · [[CU-44 De alerta de monitoreo a reporte de operación sospechosa]]
