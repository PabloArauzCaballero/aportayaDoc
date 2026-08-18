---
tags:
  - caso-uso
  - modulo/05-notificaciones-y-comunicaciones
codigo: CU-83
criticidad: alta
actores: [Sistema, Operaciones, Proveedor de mensajería]
normas: [ASFI Consumidor Financiero, protección de datos, continuidad operativa]
---

# CU-83 — Enrutar el envío por proveedor de mensajería

> **Objetivo.** Que un aviso obligatorio llegue aunque el proveedor de siempre esté
> caído, sin duplicar mensajes al conmutar y sin gastar el presupuesto de envíos en
> reintentos ciegos.

## Actores y disparador

- **Actor principal:** el trabajador de envíos.
- **Disparadores:** [[envio_notificacion]] encolado por
  [[CU-80 Despachar una notificación]]; caída o degradación de un proveedor;
  cambio de prioridades por costo o cobertura.

## Precondiciones

1. Existe al menos un [[proveedor_mensajeria]] activo que soporte el canal, con
   `prioridad`, `costo_por_mensaje`, `limite_mensajes_por_segundo` y
   `salud_porcentaje`.
2. El envío ya pasó preferencias y supresión: **el enrutado no vuelve a decidir si
   se manda, solo por dónde** (`R-NOT-03`).

## Flujo principal

1. Cada envío entra a [[cola_envio]] con `particion`, `disponible_en` e `intentos`.
   La partición separa lo urgente y obligatorio de lo masivo: un recordatorio
   comercial nunca demora un aviso de vencimiento.
2. El trabajador toma lotes con `SELECT … FOR UPDATE SKIP LOCKED` y marca
   `bloqueada_hasta`: dos réplicas no toman el mismo envío.
3. Se elige proveedor por **canal soportado, prioridad, salud y costo**, respetando
   `limite_mensajes_por_segundo` con un regulador de caudal por proveedor. Exceder
   el ritmo del proveedor no acelera nada: hace que rechace.
4. Se envía con la `clave_idempotencia` del envío (`R-NOT-01`). **La clave es del
   envío, no del proveedor**: conmutar de proveedor no puede volver a mandar el
   mismo mensaje.
5. Se registran los [[evento_entrega_mensaje]] a medida que llegan los acuses. La
   `salud_porcentaje` del proveedor se recalcula con una ventana móvil de
   entregados sobre enviados.
6. **Conmutación.** Si la salud cae por debajo del umbral o hay errores seguidos, el
   proveedor se degrada y el tráfico pasa al siguiente por prioridad. Se avisa a
   operaciones; la conmutación es automática, pero **nunca silenciosa**.
7. Agotados los proveedores y los reintentos, el envío va a [[cola_muerta]] con el
   último error. Si `es_obligatorio`, se escala a operaciones: un aviso regulatorio
   que no salió es un incumplimiento, no una métrica.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 3a | Ningún proveedor soporta el canal | Se intenta el canal alternativo de la preferencia; si tampoco, va a cola muerta con motivo |
| 4a | El proveedor responde éxito y después reporta fallo | Manda el último acuse; se reintenta por otro proveedor con la **misma** clave |
| 4b | El proveedor duplica el mensaje por su cuenta | Se detecta por los acuses y se registra como incidencia del proveedor; entra a su evaluación |
| 5a | El proveedor nunca manda acuses | Se marca `ENVIADO` sin confirmación y se anota que ese proveedor no confirma; para mensajes obligatorios, se prefiere otro |
| 6a | Se conmuta a un proveedor más caro | Se registra el sobrecosto en [[costo_proveedor_operacion]]; la continuidad vale más que el ahorro, pero el número queda |
| 7a | Cola muerta creciendo | Alerta operativa: un canal que acumula fallos es un incidente, no un pendiente |
| — | Ventana de silencio nocturna | Los envíos no obligatorios se reprograman con `disponible_en`; los obligatorios salen igual |
| — | Alta de un proveedor nuevo | Entra con prioridad baja y una porción del tráfico; sube según su salud medida, no según lo que promete |

## Postcondiciones

- Todo envío tiene proveedor, intentos y desenlace registrados.
- Ningún mensaje se duplica al conmutar y ningún obligatorio muere en silencio.

## Contrato · `openapi/notificaciones.yaml`

```ts
export const EntradaCU83 = z.object({
  envioId: z.string().uuid(),
  particion: z.enum(['OBLIGATORIO','TRANSACCIONAL','COMERCIAL','MASIVO']),
  disponibleEn: z.string().datetime().nullable(),
}).strict()

export const SalidaCU83 = z.object({
  envioId: z.string().uuid(),
  proveedorCodigo: z.string(),
  estado: z.enum(['EN_COLA','ENVIADO','ENTREGADO','FALLIDO','COLA_MUERTA']),
  intentos: z.number().int(),
  proveedoresIntentados: z.array(z.string()),
  costo: MontoSchema,
  proximoIntento: z.string().datetime().nullable(),
}).strict()

export const ErroresCU83 = {
  SIN_PROVEEDOR_PARA_CANAL: 'AP-CU83-01',
  ENVIO_YA_PROCESADO:       'AP-CU83-02',
  RITMO_EXCEDIDO:           'AP-CU83-03',
  PROVEEDOR_DEGRADADO:      'AP-CU83-04',
  AGOTADOS_LOS_REINTENTOS:  'AP-CU83-05',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SIN_PROVEEDOR_PARA_CANAL` | Ningún proveedor activo soporta ese canal |
| `ENVIO_YA_PROCESADO` | La clave de idempotencia ya se usó (`R-NOT-01`); no es falla |
| `RITMO_EXCEDIDO` | Se alcanzó el límite por segundo; se reprograma, no se descarta |
| `PROVEEDOR_DEGRADADO` | El elegido está por debajo del umbral de salud; se conmuta |
| `AGOTADOS_LOS_REINTENTOS` | Todos los proveedores fallaron; va a cola muerta |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `elegirProveedor(candidatos, canal, salud)` | Prioridad, salud y costo; puro |
| Átomo | `esperaConJitter(intento)` | Retroceso exponencial con dispersión; puro |
| Molécula | `ColaEnvioRepositorio` | Toma con `SKIP LOCKED`, bloqueo y reprogramación |
| Molécula | `RegistroDeSalud` | Ventana móvil de entregados sobre enviados por proveedor |
| Molécula | `AdaptadorMensajeria` | Uno por proveedor, misma interfaz |
| Organismo | `CU83DespacharLote` | Toma, envía, registra acuses y conmuta |
| Página | Trabajo `enviar-notificaciones` · `POST /webhooks/:proveedor/acuses` | Sin endpoint público |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `envio.despachado` | Registro de costo y espera de acuse | — |
| `proveedor.degradado` | Conmutación y aviso a operaciones | — |
| `envio.a_cola_muerta` | Escalamiento si el mensaje era obligatorio | — |
| — | Trabajo continuo de despacho por partición y control de cola muerta | — |

## Interfaz

- **App:** sin pantalla.
- **Backoffice:** *Mensajería*: salud y costo por proveedor, cola por partición,
  cola muerta con el último error, y el interruptor manual para degradar un
  proveedor sin esperar a la métrica.

## Restricciones aplicables

`R-NOT-01` · `R-NOT-02` · `R-NOT-03` · `R-AUD-01` · `R-AUD-04` · `R-RIS-03`

## Evidencia que deja

[[cola_envio]] · [[envio_notificacion]] · [[proveedor_mensajeria]] ·
[[evento_entrega_mensaje]] · [[cola_muerta]] · [[costo_proveedor_operacion]] ·
`evento_dominio`

## Criterios de aceptación

```gherkin
Dado un envío obligatorio y el proveedor de mayor prioridad caído
Cuando se despacha
Entonces se conmuta al siguiente proveedor
Y el mensaje se envía una sola vez con la misma clave de idempotencia

Dadas dos réplicas del trabajador
Cuando ambas toman lotes a la vez
Entonces ningún envío es procesado por las dos

Dado un proveedor con salud por debajo del umbral
Cuando se elige proveedor para un envío nuevo
Entonces no se lo selecciona y queda registrada la degradación

Dado un envío obligatorio que agota todos los proveedores
Cuando cae a cola muerta
Entonces se escala a operaciones y no se cierra en silencio
```

## Ver también

[[CU-56 Ejecutar una prueba de continuidad]] · [[CU-80 Despachar una notificación]] · [[CU-81 Programar recordatorios de aporte]] · [[CU-82 Procesar una respuesta entrante]] · [[CU-96 Programar y ejecutar una tarea automatizada]]
