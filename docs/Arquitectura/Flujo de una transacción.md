---
tags:
  - arquitectura
titulo: "Flujo de una transacción"
fecha_revision: 2026-08-16
---

# Flujo de una transacción

> Qué pasa, en orden, desde que llega una petición hasta que el efecto externo sale.
> Este orden **no es negociable**: cada paso existe porque una restricción, un caso
> de uso o una norma lo exige.

## El camino completo, dentro de un servicio

```
0  Gateway       enruta por prefijo reservado, limita tasa, asigna x-request-id
1  HTTP          controlador: implementa la interfaz generada del OpenAPI
2  Autenticación valida la FIRMA del JWT localmente; resuelve usuario, rol, dispositivo
3  Idempotencia  ¿ya existe esta clave? → devuelve la MISMA respuesta y termina
4  BEGIN         abre transacción (@Transactional en el organismo)
5  SET LOCAL     app.usuario_id, app.rol → recién ahora las políticas RLS aplican
6  Caso de uso   organismo: lee, decide con átomos puros, escribe por moléculas
7  Evento        inserta en evento_dominio del PROPIO esquema (misma transacción)
8  COMMIT        la base rechaza aquí lo que viole cualquier restricción
9  Respuesta     con la clave de idempotencia registrada
──────────────── frontera del COMMIT ────────────────
10 Relevo        lee el outbox con SKIP LOCKED y publica en Kafka
11 Consumidor    otro servicio recibe; inserta en evento_consumido (rechaza duplicado)
12 Adaptador     llama al proveedor (QR, WhatsApp, SIAT, UIF) y registra el intento
13 Reintento     con retroceso exponencial y variación aleatoria; cada intento es evidencia
```

## Por qué cada paso está donde está

| Paso | Por qué antes y no después |
| --- | --- |
| **2 · La firma se valida en el servicio, no en el gateway** | Un servicio que confía en una cabecera del gateway convierte la red interna en perímetro de confianza: quien la alcance suplanta a cualquiera ([[ADR-024 Autenticación y sesión distribuida]]) |
| **3 · Idempotencia antes del `BEGIN`** | La bóveda lo exige: *la clave se valida antes de cualquier escritura*. Un reintento del usuario en mala señal no puede duplicar un aporte |
| **5 · `SET LOCAL` después del `BEGIN`** | Solo dentro de la transacción el contexto muere en el `COMMIT`, que es lo que impide que la siguiente petición herede la identidad ([[ADR-021 Sesión, RLS y pooling]]) |
| **6 · Todo el caso de uso en una transacción** | *Una transacción por caso de uso*. Nada de «primero guardo y después ajusto el saldo»: ese «después» es donde se pierde el dinero |
| **7 · Encolar dentro de la transacción** | Si se revierte, el evento no existió. Si confirma, existe. No hay tercer resultado ([[ADR-018 Outbox transaccional y mensajería]]) |
| **8 · La base rechaza al final** | La aplicación valida para dar buen mensaje; la garantía es la restricción. Si la base rechaza algo que la aplicación dejó pasar, el defecto es de la aplicación |
| **10 · Kafka DESPUÉS del `COMMIT`** | Publicar dentro de la transacción anuncia un hecho que puede no haber ocurrido. Es el fallo que el outbox existe para impedir |
| **12 · El proveedor, fuera** | Una llamada externa dentro del `COMMIT` ata el dinero a la latencia y a la disponibilidad de un tercero |

## Reglas dentro del paso 6

| Regla | En una línea |
| --- | --- |
| **El saldo no se escribe: se deriva** | Se insertan movimientos con contrapartida; la caché de saldo se sincroniza en la misma transacción |
| **Nada se edita** | Corrección = movimiento inverso. Las tablas append-only rechazan el `UPDATE` a nivel de rol |
| **Los plazos se persisten al crear** | `vence_en`, `plazo_respuesta`, `fecha_limite` se calculan una vez, nunca en la consulta |
| **Denegar por omisión** | Sin límite, licencia, tarifario o política vigente ⇒ se rechaza |
| **Sin dinero en punto flotante** | Todo importe es `Dinero` sobre `BigDecimal` ([[ADR-019 Dinero con BigDecimal]]) |
| **El organismo orquesta, no consulta** | El SQL vive en moléculas; el cálculo, en átomos |
| **Ninguna llamada de red** | Ni a un proveedor ni a otro servicio. Lo que haga falta preguntar se pregunta **antes** del `BEGIN` |

---

## Cuando la operación cruza servicios

Este es el tramo que no existía con un solo despliegue. La regla que lo gobierna:

> **La transacción no cruza la red.** Cada servicio hace su parte en su propia
> transacción ACID, y el conjunto se coordina con una saga que sabe compensar
> ([[ADR-022 Comunicación entre servicios]]).

### CU-21 · cobrar el aporte, de punta a punta

```
aportes            BEGIN → marca la obligación EN_COBRO → COMMIT      ← reversible
   │
   ├─ consulta sincrónica ──► tarifas: ¿cuánto es la comisión?        ← sin efecto
   │
   ├─ llamada con Idempotency-Key ──► nucleo-financiero
   │        BEGIN → debita billetera + asiento contable → COMMIT      ← ACID, indivisible
   │
   ├─ llamada ──► tarifas: devengar la comisión                       ← compensable
   │
aportes            BEGIN → marca la obligación PAGADA → COMMIT
                   emite aportes.aporte_confirmado por outbox
   │
   └─► grupos, notificaciones, transparencia, garantia                ← consumo idempotente
```

**Si falla el devengo de la comisión**, la saga compensa el débito con un **reverso**
—un movimiento inverso, nunca un `UPDATE`— y la obligación vuelve a PENDIENTE. El
libro queda cuadrado porque el reverso es un asiento más, no una corrección del
anterior.

### Las cinco reglas de la saga

| Regla | Por qué |
| --- | --- |
| **El estado se persiste antes de cada paso** | Si el proceso muere, otro la retoma desde donde estaba y no desde el principio |
| **Cada paso es idempotente**, con clave derivada del hecho | Reintentar un paso no puede duplicar su efecto |
| **La compensación es un movimiento inverso** | Append-only vale igual entre servicios que dentro de uno |
| **El libro contable nunca se parte** | El débito y su asiento son un solo paso, en un solo servicio ([[ADR-017 Propiedad de datos por servicio]]) |
| **Una saga que no puede compensar abre incidente y avisa a una persona** | No se reintenta para siempre en silencio |

### Qué garantiza cada tramo

| Tramo | Garantía | Qué NO garantiza |
| --- | --- | --- |
| Dentro de un servicio | **Atomicidad total**: o todo o nada, con las restricciones de la base | Nada sobre los otros servicios |
| Entre servicios, por saga | **Consistencia eventual con compensación**: termina bien o queda cuadrado | Que el usuario vea el resultado final de inmediato |
| Por evento de outbox | **Al menos una vez**, y un solo efecto por consumidor idempotente | Orden global; solo orden por clave de partición |

> **La interfaz tiene que saber decir «en proceso».** Un flujo que cruza servicios y
> se muestra como «listo» antes de que la saga termine está mintiendo, y en dinero
> eso se paga con un reclamo.

---

## Concurrencia

Dos participantes pagando al mismo tiempo la misma obligación, o un cierre diario
corriendo mientras entra un aporte, son el caso normal, no el raro.

- **Bloqueo por fila** sobre el agregado que se modifica (`SELECT … FOR UPDATE` sobre
  la obligación o la cuenta), no bloqueo optimista por reintento del usuario.
- **Bloqueo consultivo** para procesos globales: cierre diario, conciliación, remisión
  de reportes. Uno a la vez, por definición. La clave lleva prefijo de módulo y se
  calcula con `hashtext('<modulo>.<recurso>')`, nunca un entero literal.
- **ShedLock** para que un trabajo programado corra una sola vez entre réplicas del
  mismo servicio ([[ADR-018 Outbox transaccional y mensajería]]).
- **`version`** de bloqueo optimista para ediciones de configuración (tarifario,
  acuerdo, política), donde el conflicto debe avisarle a una persona.
- El nivel de aislamiento por defecto es `READ COMMITTED`; los flujos que leen para
  decidir y luego escriben usan bloqueo explícito, no `SERIALIZABLE` global.

## Errores y respuesta

| Situación | Qué devuelve | Qué queda registrado |
| --- | --- | --- |
| Entrada inválida por el contrato | `400` con lista de campos | Nada escrito |
| Regla de negocio de la aplicación | `422` con código `AP-CU<NN>-<nn>` | Intento en bitácora |
| Sin autenticar | `401` | `intento_autenticacion` |
| Sin permiso / fuera de política RLS | `403` o cero filas | Intento en bitácora |
| Restricción de la base rechaza | `409` con el código `R-XXX-nn` traducido | El rechazo, con la restricción que actuó |
| Clave de idempotencia repetida | `200` con la respuesta original, íntegra | Nada nuevo |
| Proveedor externo caído | `202`: aceptado, se completará | Trabajo en cola con sus intentos |
| **Otro servicio caído** | `503` si la respuesta era imprescindible; `202` si la operación puede seguir por saga | El cortacircuitos que actuó, con métrica y evento |
| Falla no prevista | `500` con **solo** el identificador de traza | Registro de error con la traza |

Un error nunca devuelve el mensaje crudo de PostgreSQL al cliente: se traduce al
código de la restricción, que es el que la bóveda documenta.

## Observabilidad

Cada petición lleva un `x-request-id` que **se propaga por toda la cadena**: a los
otros servicios en la llamada sincrónica, al outbox en la carga del evento, y de ahí
a los consumidores. Toda línea de registro incluye `cu`, `usuario_id`, `traza` y
`servicio`.

Con catorce servicios esto deja de ser una comodidad y pasa a ser la única forma de
responder la pregunta de soporte *«¿qué pasó con el aporte de Juan del martes?»*: una
consulta por `x-request-id` devuelve el recorrido completo, en orden, atravesando
todos los servicios que participaron.

> **La prueba de fuego:** después de una operación fallida en producción, ¿se puede
> reconstruir qué pasó **sin acceder a la base**? Si la respuesta es no, la
> observabilidad está incompleta aunque emita métricas.

## Ver también

[[ADR-021 Sesión, RLS y pooling]] · [[ADR-018 Outbox transaccional y mensajería]] · [[ADR-022 Comunicación entre servicios]] · [[ADR-017 Propiedad de datos por servicio]] · [[Estructura del repositorio]] · [[Restricciones]]
