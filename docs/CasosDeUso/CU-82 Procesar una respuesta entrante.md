---
tags:
  - caso-uso
  - modulo/05-notificaciones-y-comunicaciones
codigo: CU-82
criticidad: media
actores: [Participante, Sistema, Soporte]
normas: [Consumidor financiero, protección de datos]
---

# CU-82 — Procesar una respuesta entrante

> **Objetivo.** Que contestar el mensaje sirva de algo. Si alguien responde "ya
> pagué", "no puedo este mes" o "no me escriban más", **el sistema tiene que
> entenderlo y actuar**, no dejarlo en un buzón que nadie lee.

## Actores y disparador

- **Actor principal:** el participante, respondiendo por WhatsApp o desde la app.
- **Actor secundario:** soporte, cuando hace falta una persona.
- **Disparador:** webhook entrante del proveedor de mensajería.

## Precondiciones

1. El webhook está firmado y la firma se verifica **antes de procesar nada**.
2. El remitente corresponde a un [[canal_vinculado]] conocido.

## Flujo principal

1. Se registra [[respuesta_entrante]] con el texto crudo, el canal, el remitente y
   la referencia al [[envio_notificacion]] que la originó, si la hay.
2. Se clasifica la intención con reglas simples y explícitas —no con adivinanza—:
   `YA_PAGUE`, `NO_PUEDO`, `BAJA`, `CONSULTA`, `NO_RECONOZCO`.
3. Según la intención:
   - **`YA_PAGUE`** → se cruza contra [[pago]] y [[conciliacion]]. Si está
     acreditado, se responde con el comprobante y se cancelan los recordatorios
     pendientes; si no aparece, se pide el comprobante y se abre
     [[comprobante_manual]] para revisión.
   - **`NO_PUEDO`** → se ofrece [[plan_regularizacion]] o registrar una
     [[promesa_pago]] con fecha, y se ajusta la escalera de recordatorios.
   - **`BAJA`** → se agrega a [[lista_supresion]] para la categoría comercial,
     **conservando los avisos obligatorios**, y se confirma qué va a seguir
     recibiendo y qué no.
   - **`NO_RECONOZCO`** → es un reclamo: se abre [[reclamo_cliente]] por
     [[CU-52 Atender un reclamo en plazo]] con el plazo corriendo desde ya.
   - **`CONSULTA`** → se deriva a [[ticket_soporte]] con el contexto del usuario.
4. Toda respuesta automática se envía por [[CU-80 Despachar una notificación]], con
   plantilla aprobada.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 1a | Firma del webhook inválida | Se descarta y se registra el intento; posible incidente de seguridad |
| 2a | La intención no se reconoce | Va a soporte como `CONSULTA`: **ante la duda, una persona**, no una respuesta automática equivocada |
| 3a | Dice "ya pagué" y el pago no aparece ni con comprobante | Se mantiene la deuda y se explica por qué; el comprobante queda en revisión, no se acredita por dicho |
| 3b | Pide la baja de avisos obligatorios | Se explica cuáles no se pueden suspender y por qué |
| — | Responde alguien que no es el titular | No se revela información: se responde de forma genérica y se registra |
| — | Mensaje con datos sensibles (foto de CI) | Se guarda cifrado, se registra el acceso y se avisa al usuario que no los mande por ese canal |

## Postcondiciones

- Cada respuesta tiene una acción o un ticket: ninguna queda sin destino.
- Las bajas se respetan de inmediato.

## Contrato · `openapi/notificaciones.yaml`

```ts
export const EntradaCU82 = z.object({
  proveedorId: z.string().uuid(),
  firma:       z.string(),
  cargaUtil:   z.record(z.unknown()),     // payload crudo del proveedor
  claveIdempotencia: z.string().max(120), // id del mensaje del proveedor
}).strict()

export const SalidaCU82 = z.object({
  respuestaId: z.string().uuid(),
  intencion: z.enum(['YA_PAGUE','NO_PUEDO','BAJA','CONSULTA','NO_RECONOZCO','DESCONOCIDA']),
  accion: z.enum(['PAGO_CONFIRMADO','COMPROBANTE_SOLICITADO','PROMESA_REGISTRADA',
                  'SUPRESION_APLICADA','RECLAMO_ABIERTO','TICKET_ABIERTO']),
  referenciaId: z.string().uuid().nullable(),
}).strict()

export const ErroresCU82 = {
  FIRMA_INVALIDA:      'AP-CU82-01',
  REMITENTE_DESCONOCIDO:'AP-CU82-02',
  MENSAJE_DUPLICADO:   'AP-CU82-03',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `FIRMA_INVALIDA` | La firma del webhook no valida. Se descarta y se registra el intento |
| `REMITENTE_DESCONOCIDO` | El número o la dirección no corresponde a ningún canal verificado; **no se revela información** |
| `MENSAJE_DUPLICADO` | Mismo `id_externo` del proveedor: ya fue procesado |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `clasificarIntencion(texto)` | Reglas explícitas y probadas; puro. Si no está seguro, devuelve `DESCONOCIDA` |
| Átomo | `verificarFirma(carga, firma, secreto)` | Puro |
| Molécula | `RespuestaRepositorio` · `PagoRepositorio` · `SupresionRepositorio` | |
| Organismo | `CU82ProcesarRespuesta` | Transacción y despacho de la acción |
| Página | `POST /webhooks/mensajeria/:proveedor` | Verifica firma y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `respuesta.recibida` | Clasificación y acción | Ninguno: webhook autenticado por firma |
| `supresion.aplicada` | Corte inmediato de envíos comerciales | — |
| `reclamo.abierto` | El reloj del plazo regulatorio empieza a correr | — |

## Interfaz

- **App:** la conversación se refleja en la [[bandeja_entrada]].
- **Backoffice:** bandeja de respuestas sin resolver, con la intención detectada y
  la acción tomada; las `DESCONOCIDA` se revisan primero.

## Restricciones aplicables

`R-NOT-01` · `R-NOT-03` · `R-SEG-01` · `R-SEG-02` · `R-CON-01`

## Evidencia que deja

[[respuesta_entrante]] · [[lista_supresion]] · [[promesa_pago]] ·
[[comprobante_manual]] · [[reclamo_cliente]] · [[ticket_soporte]]

## Criterios de aceptación

```gherkin
Dado un mensaje entrante con firma válida que dice "ya pagué"
Y un pago acreditado para esa obligación
Cuando se procesa
Entonces la acción es PAGO_CONFIRMADO y se cancelan los recordatorios

Dado un mensaje que dice "no me escriban más"
Cuando se procesa
Entonces el destinatario queda en lista_supresion para la categoría comercial
Y sigue recibiendo los avisos obligatorios

Dado el mismo mensaje reenviado por el proveedor
Cuando se procesa otra vez
Entonces no se duplica la respuesta ni la acción

Dado un mensaje con firma inválida
Cuando llega al webhook
Entonces se descarta y queda registrado el intento
```

## Ver también

[[CU-21 Cobrar el aporte del período]] · [[CU-52 Atender un reclamo en plazo]] · [[CU-80 Despachar una notificación]] · [[CU-81 Programar recordatorios de aporte]] · [[CU-83 Enrutar el envío por proveedor de mensajería]]
