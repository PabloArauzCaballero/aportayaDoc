---
tags:
  - caso-uso
  - modulo/03-aportes-pagos-qr-y-conciliacion
  - modulo/10-billetera-custodia-y-dinero-electronico
codigo: CU-19
criticidad: alta
actores: [Usuario, Soporte, Supervisor, Proveedor de pago]
normas: [ASFI Consumidor Financiero, reglas de marca y contracargo, contabilidad]
---

# CU-19 — Reembolsar un pago y atender una disputa desde la pasarela

> **Objetivo.** Que un pago que no debió entrar vuelva por donde vino, con asiento y
> evidencia; y que un contracargo del emisor se responda dentro del plazo con la
> prueba que ya teníamos guardada, en vez de improvisarla.

## Actores y disparador

- **Actor principal:** soporte, con autorización de supervisor por encima del monto
  que fija la política.
- **Disparadores:** pago duplicado; cobro por monto equivocado; reclamo procedente
  ([[CU-52 Atender un reclamo en plazo]]); aviso de disputa del proveedor.

## Precondiciones

1. Existe [[pago]] acreditado y conciliado ([[conciliacion]]).
2. El [[proveedor_pago]] soporta reembolso, o existe procedimiento manual con
   evidencia.
3. Quien aprueba no es quien solicitó (`R-SEG-04`).

## Flujo principal

1. Se crea [[reembolso]] con `pago_id`, `monto`, `motivo` —`DUPLICADO`,
   `MONTO_ERRONEO`, `NO_RECONOCIDO`, `SERVICIO_NO_PRESTADO`—, `solicitado_por` y
   estado `SOLICITADO`. **El monto nunca supera lo pagado ni lo ya reembolsado**.
2. El supervisor aprueba (`aprobado_por`). Sin esa firma el reembolso no sale.
3. **En la misma transacción** que ejecuta:
   - se debita la cuenta que recibió el dinero, con `clave_idempotencia` derivada
     del `reembolso.id`;
   - si el pago saldaba una [[obligacion_aporte]], la obligación **vuelve a quedar
     pendiente** por ese importe: reembolsar no es condonar;
   - se registra el [[asiento_contable]] espejo;
   - se emite `evento_dominio` `pago.reembolsado`.
4. Se llama al proveedor con la referencia original. La respuesta queda en
   `referencia_proveedor` y `fecha_ejecucion`. El estado sigue el ciclo
   `SOLICITADO → APROBADO → ENVIADO → ACREDITADO`, y **el dinero no se da por
   devuelto hasta el acuse**.
5. Si la comisión ya se había devengado y facturado, se encadena
   [[CU-33 Devolver comisión y emitir nota de crédito]]: la nota de crédito y el
   reembolso son dos hechos distintos y ambos tienen que ocurrir.
6. **Disputa entrante.** Cuando el proveedor avisa un contracargo se crea
   [[disputa_pago]] con `tipo`, `monto_disputado`, `evidencias` y
   **`fecha_limite_respuesta` calculada y guardada al recibirla**, que es el plazo
   que manda y que casi siempre es corto.
7. Se arma el descargo con lo que el sistema ya tiene: [[orden_cobro]], [[qr_cobro]],
   [[intento_pago]], acuses de [[evento_entrega_mensaje]], sesión y aparato desde el
   que se operó. Se envía y se espera el veredicto.
8. Resuelta la disputa:
   - **a favor nuestro** → se cierra y el saldo se libera;
   - **en contra** → el importe se debita, se registra el asiento de pérdida y se
     abre [[evento_riesgo_operativo]] ([[CU-54 Registrar un evento de riesgo operativo]]).

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 1a | Se pide reembolsar más de lo pagado | Rechazo `MONTO_EXCEDE_PAGO`; el acumulado de reembolsos previos también cuenta |
| 3a | El beneficiario ya gastó el saldo | El reembolso deja la cuenta en deuda, no en saldo negativo: se crea [[deuda_participante]] y entra a cobranza (`R-BIL-02`) |
| 4a | El proveedor rechaza el reembolso | Queda `RECHAZADO` con el motivo; se resuelve por transferencia manual con evidencia y aprobación adicional |
| 4b | El proveedor no responde | Reintento con espera creciente; agotado, se abre [[excepcion_conciliacion]] y **bloquea el cierre diario** (`R-BIL-12`) |
| 6a | La disputa llega vencida por retraso del proveedor | Se responde igual y se deja constancia de la fecha de recepción real: el plazo se cuenta desde que lo supimos |
| 7a | No hay evidencia suficiente | Es un hallazgo en sí mismo: significa que el flujo de cobro no dejó rastro y se registra como [[hallazgo_auditoria]] |
| 8a | Disputa perdida sobre un aporte ya entregado al ganador del turno | La pérdida la absorbe la plataforma o el [[fondo_garantia]] según reglamento; **nunca se le quita el dinero al que ya cobró su turno** |
| — | Reintento del webhook de disputa | La clave del proveedor lo hace idempotente: una disputa por aviso |

## Postcondiciones

- Todo reembolso tiene solicitante, aprobador, asiento y acuse del proveedor.
- Toda disputa tiene plazo guardado, descargo enviado y veredicto registrado.

## Contrato · `openapi/aportes.yaml`

```ts
export const EntradaCU19 = z.object({
  claveIdempotencia: z.string().uuid(),
  pagoId: z.string().uuid(),
  monto:  MontoSchema,
  motivo: z.enum(['DUPLICADO','MONTO_ERRONEO','NO_RECONOCIDO','SERVICIO_NO_PRESTADO']),
  observacion: z.string().max(300),
}).strict()

export const EntradaDisputaCU19 = z.object({
  pagoId: z.string().uuid(),
  tipo:   z.enum(['CONTRACARGO','DESCONOCIMIENTO','FRAUDE_DECLARADO','ERROR_TECNICO']),
  montoDisputado: MontoSchema,
  fechaLimiteRespuesta: z.string().datetime(),
  evidenciasProveedor: z.record(z.unknown()),
}).strict()

export const SalidaCU19 = z.object({
  reembolsoId: z.string().uuid().nullable(),
  disputaId:   z.string().uuid().nullable(),
  estado: z.enum(['SOLICITADO','APROBADO','ENVIADO','ACREDITADO','RECHAZADO',
                  'EN_DESCARGO','GANADA','PERDIDA']),
  obligacionReabierta: z.boolean(),
  notaCreditoRequerida: z.boolean(),
}).strict()

export const ErroresCU19 = {
  PAGO_NO_CONCILIADO:  'AP-CU19-01',
  MONTO_EXCEDE_PAGO:   'AP-CU19-02',
  APROBACION_REQUERIDA:'AP-CU19-03',
  MISMO_SOLICITANTE:   'AP-CU19-04',
  PROVEEDOR_SIN_SOPORTE:'AP-CU19-05',
  DISPUTA_DUPLICADA:   'AP-CU19-06',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `PAGO_NO_CONCILIADO` | El pago todavía no cruzó con el extracto: no se devuelve lo que no se sabe si entró |
| `MONTO_EXCEDE_PAGO` | El monto, sumado a reembolsos previos, supera lo pagado (`R-TAR-11` por analogía) |
| `APROBACION_REQUERIDA` | Supera el techo que la política deja aprobar a soporte |
| `MISMO_SOLICITANTE` | Quien aprueba es quien solicitó (`R-SEG-04`) |
| `PROVEEDOR_SIN_SOPORTE` | El proveedor no admite reembolso automático; hay que ir por el procedimiento manual |
| `DISPUTA_DUPLICADA` | Ya existe disputa abierta para ese pago y tipo |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `reembolsableRestante(pago, reembolsosPrevios)` | Cuánto queda por devolver; puro |
| Átomo | `armarDescargo(pago, intentos, acuses)` | Estructura el paquete de evidencia; puro |
| Molécula | `ReembolsoRepositorio` · `DisputaRepositorio` | Persistencia y estados |
| Molécula | `AdaptadorReembolsoProveedor` | Una implementación por pasarela, misma interfaz |
| Organismo | `CU19EjecutarReembolso` · `CU19AtenderDisputa` | Transacción: débito, obligación, asiento y evento |
| Página | `POST /pagos/:id/reembolsos` · `POST /webhooks/:proveedor/disputas` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `pago.reembolsado` | Aviso al usuario, nota de crédito y recálculo de la obligación | `PAGO_REEMBOLSAR` + aprobación |
| `disputa.abierta` | Trabajo con el plazo de respuesta y alerta al vencer la mitad | — |
| `disputa.resuelta` | Asiento de pérdida y evento de riesgo si fue en contra | `DISPUTA_GESTIONAR` |

## Interfaz

- **App:** *Movimientos → detalle del pago*: el reembolso aparece como movimiento
  propio, con su motivo y su fecha, nunca borrando el pago original.
- **Backoffice:** bandeja de reembolsos con doble firma y tablero de disputas
  ordenado por plazo restante, con el paquete de evidencia listo para adjuntar.

## Restricciones aplicables

`R-BIL-01` · `R-BIL-02` · `R-BIL-06` · `R-BIL-12` · `R-SEG-04` · `R-AUD-01` ·
`R-AUD-05` · `R-CON-01`

## Evidencia que deja

[[reembolso]] · [[disputa_pago]] · [[intento_pago]] · [[transaccion_billetera]] ·
[[asiento_contable]] · [[excepcion_conciliacion]] · `evento_dominio` ·
[[bitacora_evento]]

## Criterios de aceptación

```gherkin
Dado un pago de Bs 500 sin reembolsos previos
Cuando soporte solicita reembolsar Bs 600
Entonces se rechaza con MONTO_EXCEDE_PAGO

Dado un reembolso aprobado sobre un pago que saldaba una obligación
Cuando se ejecuta
Entonces la obligacion_aporte vuelve a estado PENDIENTE por ese importe
Y existe un asiento_contable cuadrado que lo respalda

Dada una disputa recibida del proveedor
Cuando se registra
Entonces fecha_limite_respuesta queda guardada
Y aparece en el tablero ordenada por plazo restante

Dado un reembolso ya ejecutado
Cuando el webhook del proveedor llega dos veces
Entonces el saldo se debita una sola vez
```

## Ver también

[[CU-14 Reversar una transacción]] · [[CU-33 Devolver comisión y emitir nota de crédito]] · [[CU-52 Atender un reclamo en plazo]] · [[CU-54 Registrar un evento de riesgo operativo]] · [[CU-99 Dar de alta un proveedor de pago y enrutar el cobro]]
