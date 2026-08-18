---
tags:
  - caso-uso
  - modulo/10-billetera-custodia-y-dinero-electronico
codigo: CU-11
criticidad: alta
actores: [Usuario, Proveedor de pago, Aprobador]
normas: [BCB, UIF art. 52 inc. i, antifraude]
---

# CU-11 — Retirar saldo (cash-out)

> **Objetivo.** Que el titular pueda sacar su dinero cuando quiera, y que nadie más
> pueda. Es la operación de mayor riesgo del sistema y por eso es **pesimista**:
> primero se reserva, después se paga.

## Actores y disparador

- **Actor principal:** titular de la cuenta.
- **Actores secundarios:** proveedor de pago; aprobador interno si el monto lo exige.
- **Disparador:** solicitud de retiro desde la app.

## Precondiciones

1. Cuenta `ACTIVA` o `LIMITADA` (retirar el propio saldo siempre debe ser posible,
   salvo bloqueo de autoridad).
2. [[instrumento_fondeo]] destino verificado, `titular_coincide=true` y
   `bloqueado_hasta` vencido (`R-BIL-09`).
3. Sin [[bloqueo_saldo]] vigente que afecte el importe.
4. `saldo_disponible >= monto_solicitado + costo_retiro`.

## Flujo principal

1. Se cotiza el costo con [[CU-30 Cotizar la comisión antes de operar]] y se muestra
   el neto final.
2. Se evalúan límites ([[CU-40 Evaluar límites antes de una operación]]) y
   antifraude ([[evaluacion_antifraude]]).
3. Se exige MFA ([[CU-04 Autenticar con MFA y registrar dispositivo]]);
   `orden_retiro.mfa_verificado=true`.
4. **En la misma transacción**: se crea [[orden_retiro]] con `clave_idempotencia` y
   se crea la [[retencion_saldo]] por el importe total
   (`motivo='ENTREGA_EN_CURSO'`), moviendo el importe de disponible a retenido.
5. Si el monto supera el tope de política, se exige segundo aprobador
   (`aprobada_por` ≠ solicitante, `R-SEG-04`) y/o se respeta
   `ventana_enfriamiento_hasta`.
6. Se envía la instrucción al proveedor con la misma clave de idempotencia.
7. Al confirmarse el pago, **en una transacción**:
   - se ejecuta la retención (`estado='EJECUTADA'`);
   - se crea [[transaccion_billetera]] `tipo='RETIRO'` con débito al usuario y
     crédito a `PUENTE_CUSTODIA`;
   - se registra el [[cargo_comision]] del costo de retiro si el tarifario lo
     define;
   - se genera el asiento contable y el `evento_dominio` `RETIRO_PAGADO`.
8. Se evalúan umbrales UIF (retiro de billetera acumulado) →
   [[CU-41 Detectar umbral y registrar formulario PCC-01]].

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | Antifraude decide `REVISAR` | La orden queda `EN_REVISION`; la retención se mantiene; se notifica plazo al usuario |
| 2b | Antifraude decide `RECHAZAR` | Se libera la retención y se registra el motivo; el usuario puede reclamar ([[CU-52 Atender un reclamo en plazo]]) |
| 6a | El proveedor falla o rechaza | Se libera la retención (`estado='LIBERADA'`) y el saldo vuelve a disponible. **Nunca queda dinero en el limbo** |
| 6b | Timeout sin respuesta | Se consulta estado por idempotencia; sin confirmación, la retención vence por `expira_en` y se libera |
| 3a | Instrumento agregado hoy | `bloqueado_hasta` impide el retiro: enfriamiento anti-toma de cuenta |
| — | Existe [[bloqueo_saldo]] parcial | Solo se puede retirar el excedente no bloqueado |

## Postcondiciones

- O el usuario recibió el dinero y su saldo bajó, o el saldo volvió íntegro a
  disponible. No hay tercer estado.

## Contrato · `openapi/nucleo-financiero.yaml`

```ts
export const EntradaCU11 = z.object({
  claveIdempotencia: z.string().uuid(),
  cuentaBilleteraId: z.string().uuid(),
  monto:             MontoSchema,
  instrumentoDestinoId: z.string().uuid(),
  factorMfa:         z.string().min(6).max(8),
}).strict()

export const SalidaCU11 = z.object({
  ordenRetiroId: z.string().uuid(),
  estado:        z.enum(['PENDIENTE','EN_REVISION','AUTORIZADA','PAGADA','RECHAZADA']),
  costoRetiro:   MontoSchema,
  montoNeto:     MontoSchema,
  retencionId:   z.string().uuid(),
}).strict()

export const ErroresCU11 = {
  SALDO_INSUFICIENTE: 'AP-CU11-01',
  MFA_REQUERIDO: 'AP-CU11-02',
  INSTRUMENTO_EN_ENFRIAMIENTO: 'AP-CU11-03',
  TITULAR_NO_COINCIDE: 'AP-CU11-04',
  BLOQUEO_DE_AUTORIDAD: 'AP-CU11-05',
  ENCAJE_INCUMPLIDO: 'AP-CU11-06',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SALDO_INSUFICIENTE` | El disponible no cubre monto más costo (R-BIL-02) |
| `MFA_REQUERIDO` | Falta el segundo factor o es inválido (R-BIL-09) |
| `INSTRUMENTO_EN_ENFRIAMIENTO` | El destino se agregó dentro de la ventana de enfriamiento |
| `TITULAR_NO_COINCIDE` | El instrumento no es del titular |
| `BLOQUEO_DE_AUTORIDAD` | Hay saldo inmovilizado por oficio |
| `ENCAJE_INCUMPLIDO` | El sistema está en modo restringido (R-BIL-11) |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `calcularNetoDeRetiro` | Monto menos costo, con redondeo declarado; puro |
| Átomo | `puedeRetirar` | Reúne las condiciones duras y devuelve el motivo del rechazo |
| Molécula | `OrdenRetiroRepositorio` | Alta y transiciones |
| Molécula | `RetencionSaldoRepositorio` | Reserva y liberación del importe |
| Molécula | `DesembolsoAdaptador` | Instrucción al proveedor con la misma clave de idempotencia |
| Organismo | `CU11RetirarSaldo` | Transacción: retención primero, pago después; nunca al revés |
| Página | `POST /billetera/retiros` | Traduce y delega, sin lógica |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `retiro.solicitado` | Retención del importe y evaluación antifraude | `BILLETERA_OPERAR` |
| `retiro.pagado` | Transacción de billetera, asiento y umbrales UIF | — |
| `retiro.rechazado` | Liberación de la retención y aviso | — |

## Interfaz

- **App:** *Retirar*: destino, monto, costo y neto a la vista antes de confirmar con biometría.
- **Backoffice:** Cola de retiros en revisión, con el puntaje antifraude y la decisión del motor.

## Restricciones aplicables

`R-BIL-01` · `R-BIL-02` · `R-BIL-06` · `R-BIL-07` · `R-BIL-08` · `R-BIL-09` ·
`R-BIL-11` · `R-BIL-19` · `R-BIL-20` · `R-SEG-04` · `R-LIM-01` · `R-AUD-01` ·
`R-AUD-03` · `R-UIF-02`

## Evidencia que deja

[[orden_retiro]] · [[retencion_saldo]] · [[evaluacion_antifraude]] ·
[[transaccion_billetera]] · [[movimiento_billetera]] · [[asiento_contable]] ·
[[respuesta_idempotente]] ·
[[registro_operacion_relevante]] (si aplica)

## Criterios de aceptación

```gherkin
Dado un usuario con saldo suficiente y MFA verificado
Cuando solicita un retiro
Entonces se crea una retencion_saldo VIGENTE por el importe total
Y el saldo_disponible disminuye y el saldo_retenido aumenta en el mismo monto

Dado un retiro cuyo proveedor responde error definitivo
Cuando se procesa la respuesta
Entonces la retención queda LIBERADA
Y el saldo_disponible vuelve a su valor original

Dado un instrumento de fondeo agregado hace una hora
Cuando el usuario intenta retirar hacia él
Entonces la operación se rechaza por período de enfriamiento
```

## Ver también

[[CU-04 Autenticar con MFA y registrar dispositivo]] · [[CU-10 Recargar saldo]] · [[CU-13 Retener y liberar saldo]] · [[CU-16 Cerrar billetera y devolver saldo]] · [[CU-17 Bloquear saldo por orden de autoridad]] · [[CU-18 Registrar y verificar una cuenta bancaria de destino]] · [[CU-28 Emitir la orden de desembolso y ejecutar el intento]] · [[CU-40 Evaluar límites antes de una operación]] · [[CU-57 Operar un punto de atención y arquear el efectivo]]
