---
tags:
  - caso-uso
  - modulo/02-grupos-cupos-turnos-y-gobernanza
  - modulo/01-identidad-usuarios-y-seguridad
codigo: CU-69
criticidad: media
actores: [Participante, Organizador, Invitado, Sistema]
normas: [Protección de datos, ASFI Consumidor Financiero, antifraude]
---

# CU-69 — Invitar a un contacto y registrar sus referencias

> **Objetivo.** Que el pasanaku siga creciendo como crece de verdad —por gente que
> se conoce— sin que la invitación se convierta ni en una filtración de datos ni en
> un canal de spam.

## Actores y disparador

- **Actor principal:** participante u organizador del grupo.
- **Disparadores:** cupo libre en un grupo; armado de un grupo nuevo; alta que
  requiere referencias personales; búsqueda de avalista.

## Precondiciones

1. Quien invita es participante activo del grupo, o su organizador.
2. El grupo tiene cupos libres o está en conformación.
3. Existe [[politica_token]] con vencimiento y usos para el token de invitación.

## Flujo principal

1. Se crea [[invitacion]] con `grupo_id`, `telefono_invitado`, `nombre_sugerido`,
   `emisor_id`, `canal`, `fecha_expiracion` y un [[token_verificacion]] **de un solo
   uso** ligado en `token_id`. Estado inicial `ENVIADA`.
2. Se despacha por [[CU-80 Despachar una notificación]] con plantilla aprobada. El
   mensaje dice **quién** invita y a **qué** grupo, con el monto y la periodicidad:
   nadie debería aceptar un compromiso de dinero sin saber cuál.
3. **El mensaje no revela datos de los integrantes**: ni nombres completos, ni
   teléfonos, ni montos individuales. Quien invita ya conoce al invitado; el sistema
   no tiene por qué presentar a nadie más.
4. `envios_realizados` cuenta los reenvíos, con tope. Superado, la invitación no se
   puede reenviar: insistir tres veces es recordar, insistir diez es acoso.
5. El invitado abre el enlace, se registra si hace falta
   ([[CU-01 Registro y apertura de billetera]]) y acepta. **En la misma
   transacción** se consume el token, la invitación pasa a `ACEPTADA` con
   `fecha_respuesta` y se encadena con
   [[CU-68 Postular a un grupo y ser emparejado]] para la asignación del cupo.
6. **Referencias personales.** El usuario registra [[referencia_personal]] con
   `nombre`, `telefono`, `relacion` y `acepta_ser_avalista`. Cada referencia se
   verifica por un mensaje que **la referencia misma responde**: sin esa
   confirmación queda `verificada = false` y no cuenta como respaldo.
7. Una referencia que aceptó ser avalista puede constituirse en
   [[aval_participante]] con su tope firmado ([[CU-26 Ejecutar el aval y subrogar la deuda]]).
   **Nadie queda de avalista por figurar en la agenda de otro.**

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 1a | El teléfono ya es usuario y participa del grupo | No se envía nada; se informa a quien invita, sin revelar más |
| 2a | El invitado está en lista de supresión | No se envía (`R-NOT-03`); a quien invita se le dice que no fue posible, sin explicar por qué |
| 4a | Se supera el tope de reenvíos | La invitación no se puede reenviar; queda `EXPIRADA` al vencer |
| 5a | El token vencido o ya usado | Se ofrece pedir una invitación nueva; **el token no se reactiva** |
| 5b | El invitado acepta y el cupo ya no está | Pasa a lista de espera del grupo, avisándole con claridad qué significa |
| 6a | La referencia no responde la verificación | Queda sin verificar; el usuario puede reemplazarla. No se insiste más de lo que fija el tope |
| 6b | La referencia pide no ser contactada | Se elimina el vínculo y su teléfono va a [[lista_supresion]] |
| — | Varias invitaciones al mismo teléfono desde grupos distintos | Se aplica el tope por destinatario y día (`R-NOT-02`), no por grupo |
| — | Invitación usada para captar clientes masivamente | El patrón se detecta y se limita: el canal de invitación es entre conocidos, no un canal comercial |

## Postcondiciones

- Toda incorporación por invitación tiene emisor identificado y token consumido una
  sola vez.
- Ninguna referencia figura como respaldo sin haberlo confirmado ella misma.

## Contrato · `openapi/grupos.yaml`

```ts
export const EntradaCU69 = z.object({
  grupoId: z.string().uuid(),
  telefonoInvitado: z.string().regex(/^\+591\d{8}$/),
  nombreSugerido: z.string().max(80).nullable(),
  canal: z.enum(['SMS','WHATSAPP','ENLACE']),
}).strict()

export const EntradaReferenciaCU69 = z.object({
  nombre:   z.string().min(3).max(120),
  telefono: z.string().regex(/^\+591\d{8}$/),
  relacion: z.enum(['FAMILIAR','AMIGO','LABORAL','VECINO']),
  aceptaSerAvalista: z.boolean(),
}).strict()

export const SalidaCU69 = z.object({
  invitacionId: z.string().uuid().nullable(),
  referenciaId: z.string().uuid().nullable(),
  estado: z.enum(['ENVIADA','ACEPTADA','RECHAZADA','EXPIRADA','NO_ENVIABLE']),
  enviosRealizados: z.number().int(),
  expiraEn: z.string().datetime().nullable(),
  verificada: z.boolean().nullable(),
}).strict()

export const ErroresCU69 = {
  SIN_CUPOS_LIBRES:      'AP-CU69-01',
  DESTINATARIO_SUPRIMIDO:'AP-CU69-02',
  YA_ES_PARTICIPANTE:    'AP-CU69-03',
  TOPE_REENVIOS:         'AP-CU69-04',
  TOKEN_INVALIDO:        'AP-CU69-05',
  REFERENCIA_DUPLICADA:  'AP-CU69-06',
  EMISOR_NO_HABILITADO:  'AP-CU69-07',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SIN_CUPOS_LIBRES` | El grupo está completo y no admite lista de espera |
| `DESTINATARIO_SUPRIMIDO` | El teléfono está en [[lista_supresion]]; se responde sin revelar el motivo |
| `YA_ES_PARTICIPANTE` | Ya participa del grupo |
| `TOPE_REENVIOS` | Se agotaron los reenvíos permitidos |
| `TOKEN_INVALIDO` | Vencido o ya consumido; se ofrece pedir otro |
| `REFERENCIA_DUPLICADA` | El mismo teléfono ya figura como referencia del usuario |
| `EMISOR_NO_HABILITADO` | Quien invita no es participante activo ni organizador del grupo |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `normalizarTelefono(texto)` | Formato E.164 boliviano; puro |
| Átomo | `puedeInvitar(emisor, grupo)` | Habilitación del emisor; puro |
| Molécula | `InvitacionRepositorio` · `ReferenciaRepositorio` | Persistencia, unicidad y conteo de reenvíos |
| Molécula | `EmisorDeTokens` | Token de un solo uso con vencimiento y registro de intentos |
| Organismo | `CU69Invitar` · `CU69AceptarInvitacion` | Transacción: consumo de token, estado y encadenamiento con el ingreso |
| Página | `POST /grupos/:id/invitaciones` · `POST /invitaciones/:token/aceptar` · `POST /referencias` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `invitacion.enviada` | Notificación con plantilla aprobada y tope diario | `PARTICIPANTE` |
| `invitacion.aceptada` | Postulación al cupo y aviso al emisor | — |
| `referencia.verificada` | Habilita constituirla en aval | — |
| — | Trabajo que expira invitaciones vencidas y limpia tokens | — |

## Interfaz

- **App:** *Grupo → Invitar*: elegir un contacto, ver el mensaje exacto que se va a
  enviar antes de mandarlo, y el estado de cada invitación. En *Mis referencias*, el
  estado de verificación de cada una y si aceptó ser avalista.
- **Backoffice:** invitaciones por grupo con tasa de aceptación, y control de abuso
  del canal.

## Restricciones aplicables

`R-NOT-01` · `R-NOT-02` · `R-NOT-03` · `R-GRP-15` · `R-SEG-03` · `R-AUD-04`

## Evidencia que deja

[[invitacion]] · [[referencia_personal]] · [[token_verificacion]] ·
[[intento_validacion_token]] · [[envio_notificacion]] · [[aval_participante]] ·
`evento_dominio`

## Criterios de aceptación

```gherkin
Dado un participante activo y un grupo con cupos libres
Cuando invita a un teléfono nuevo
Entonces existe una invitacion ENVIADA con token de un solo uso
Y el mensaje no contiene datos de los otros integrantes

Dado un token de invitación ya consumido
Cuando se intenta aceptar otra vez
Entonces se rechaza con TOKEN_INVALIDO

Dado un teléfono en lista de supresión
Cuando se lo intenta invitar
Entonces no se envía nada y se responde sin revelar el motivo

Dada una referencia personal registrada
Cuando la referencia no responde la verificación
Entonces queda con verificada en false y no puede constituirse en aval
```

## Ver también

[[CU-01 Registro y apertura de billetera]] · [[CU-26 Ejecutar el aval y subrogar la deuda]] · [[CU-68 Postular a un grupo y ser emparejado]] · [[CU-80 Despachar una notificación]]
