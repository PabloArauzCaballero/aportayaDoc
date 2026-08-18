---
tags:
  - caso-uso
  - modulo/05-notificaciones-y-comunicaciones
  - modulo/03-aportes-pagos-qr-y-conciliacion
codigo: CU-81
criticidad: media
actores: [Sistema, Participante]
normas: [Consumidor financiero, buenas prácticas de cobranza]
---

# CU-81 — Programar y enviar recordatorios de aporte

> **Objetivo.** Que la gente pague a tiempo porque **se le avisó bien**, no porque
> se la persiguió. La cobranza temprana y amable recupera más que la insistencia, y
> el exceso de mensajes es la vía más rápida a que bloqueen el canal.

## Actores y disparador

- **Actor principal:** el planificador.
- **Disparador:** apertura del [[periodo]]; el calendario de la
  [[programacion_recordatorio]].

## Precondiciones

1. Existen [[obligacion_aporte]] `PENDIENTE` del período.
2. Hay [[programacion_recordatorio]] activa para el grupo o la global por defecto.

## Flujo principal

1. Al abrir el período se programan los recordatorios según la escalera definida:
   típicamente **tres días antes**, el **día del vencimiento**, y luego dentro del
   plazo de gracia. Cada escalón tiene su plantilla y su tono.
2. Un trabajo diario toma los recordatorios que vencen hoy y, por cada uno:
   - **verifica si sigue haciendo falta**: si la obligación ya está `PAGADO`, se
     cancela. Nada peor que cobrarle a quien ya pagó;
   - respeta el tope de mensajes por persona y por día (`R-NOT-02`);
   - genera el enlace de pago con [[orden_cobro]] y su [[qr_cobro]], para que el
     aviso sea accionable y no solo informativo;
   - despacha por [[CU-80 Despachar una notificación]].
3. Tras el vencimiento y la gracia, el tono cambia: deja de ser recordatorio y pasa
   a [[gestion_cobranza]], con su propia escalera y registro de
   [[accion_cobranza]].
4. Cada envío queda enlazado a la obligación, para poder responder *"¿cuántas veces
   se le avisó?"* con un número y sus fechas.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | La obligación ya fue pagada | El recordatorio se cancela con motivo `YA_PAGADO` |
| 2b | Se alcanzó el tope diario de mensajes | Se pospone al día siguiente; el tope protege el canal y al usuario |
| 2c | El participante ya tiene [[promesa_pago]] vigente | Se cambia la plantilla por una que reconoce el compromiso, en vez de repetir el genérico |
| — | El grupo desactiva los recordatorios | Solo se envían los obligatorios: vencimiento y consecuencias |
| — | El participante responde "ya pagué" | Va a [[CU-82 Procesar una respuesta entrante]], que lo cruza con la conciliación |

## Postcondiciones

- Nadie recibe más mensajes de los que el tope permite.
- A nadie se le reclama un aporte ya acreditado.

## Contrato · `openapi/notificaciones.yaml`

```ts
export const EntradaCU81 = z.object({
  periodoId: z.string().uuid(),
  escalon:   z.enum(['PREVIO','VENCIMIENTO','GRACIA','POST_GRACIA']),
}).strict()

export const SalidaCU81 = z.object({
  programados: z.number().int(),
  enviados:    z.number().int(),
  cancelados:  z.number().int(),
  pospuestos:  z.number().int(),
  detalle: z.array(z.object({
    obligacionId: z.string().uuid(),
    resultado: z.enum(['ENVIADO','CANCELADO_YA_PAGADO','POSPUESTO_TOPE','SUPRIMIDO']),
  })),
}).strict()

export const ErroresCU81 = {
  PERIODO_NO_ABIERTO:   'AP-CU81-01',
  SIN_PROGRAMACION:     'AP-CU81-02',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `PERIODO_NO_ABIERTO` | El período no está abierto o ya se liquidó: no hay qué recordar |
| `SIN_PROGRAMACION` | El grupo no definió escalera de recordatorios; se aplican solo los obligatorios |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `escaleraDeRecordatorios(periodo, politica)` | Devuelve las fechas de cada escalón; puro |
| Átomo | `debeRecordar(obligacion, enviosPrevios, tope)` | Decide enviar, cancelar o posponer; puro |
| Molécula | `ProgramacionRepositorio` · `ObligacionRepositorio` | |
| Organismo | `CU81ProgramarRecordatorios` | Trabajo diario, idempotente por día |
| Página | — | Sin endpoint: lo dispara el planificador |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `recordatorio.programado` | Nada hasta su fecha | Interno |
| `recordatorio.debido` | [[CU-80 Despachar una notificación]] con enlace de pago | — |
| — | Trabajo cron diario con bloqueo entre réplicas | — |

## Interfaz

- **App:** el aviso llega con el monto, la fecha límite y el botón **Pagar ahora**.
- **Backoffice:** por grupo, cuántos avisos salieron y cuántos derivaron en pago —
  la métrica que dice si la escalera funciona.

## Restricciones aplicables

`R-NOT-01` · `R-NOT-02` · `R-GRP-03`

## Evidencia que deja

[[programacion_recordatorio]] · [[notificacion]] y [[envio_notificacion]] ·
[[orden_cobro]] y [[qr_cobro]] generados · [[accion_cobranza]] tras la gracia

## Criterios de aceptación

```gherkin
Dado un período recién abierto con seis obligaciones pendientes
Cuando corre la programación
Entonces existen recordatorios para los escalones previo, vencimiento y gracia

Dada una obligación pagada antes del recordatorio
Cuando corre el trabajo diario
Entonces el recordatorio se cancela con motivo YA_PAGADO y no se envía

Dado un usuario que ya alcanzó el tope diario
Cuando le corresponde otro recordatorio
Entonces queda pospuesto para el día siguiente

Dado el mismo trabajo diario ejecutado dos veces
Cuando corre la segunda vez
Entonces no se duplica ningún envío
```

## Ver también

[[CU-21 Cobrar el aporte del período]] · [[CU-80 Despachar una notificación]] · [[CU-82 Procesar una respuesta entrante]] · [[CU-83 Enrutar el envío por proveedor de mensajería]] · [[CU-95 Definir una regla de automatización]] · [[CU-96 Programar y ejecutar una tarea automatizada]] · [[CU-97 Anticipar el riesgo con alertas tempranas]]
