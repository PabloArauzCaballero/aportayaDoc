---
name: frontera-transaccional
description: "Decidir, antes de escribir código, qué va todo-junto-o-nada en un caso de uso de AportaYa: las seis preguntas obligatorias, el árbol que elige entre transacción local, llamada sincrónica, evento y saga, y dónde vive cada garantía. Úsala en el paso 0 de todo caso de uso, y cuando una operación toque más de un agregado o más de un servicio."
---

# Frontera transaccional

**Este es el paso 0 de todo caso de uso, y el que más cuesta cuando se saltea.**
Decidir mal acá no produce un error que las pruebas atrapen: produce un sistema que
funciona hasta el día que un proceso muere a mitad de camino.

> **Se responde por escrito, antes de la primera línea, y se espera el visto bueno.**
> Seis respuestas cortas cuestan menos que rehacer el caso de uso en el paso 6.

---

## 1 · Las seis preguntas

| # | Pregunta | Qué la respuesta determina |
| :-: | --- | --- |
| 1 | ¿Qué tiene que ocurrir **todo junto o nada**? | El cuerpo del `@Transactional` |
| 2 | ¿Qué queda **fuera** del commit? | Qué va al outbox |
| 3 | ¿Cuál es la **clave de idempotencia** y de dónde viene? | Cliente, proveedor o derivada del hecho |
| 4 | ¿Qué se **bloquea** si dos usuarios hacen esto a la vez, y a qué granularidad? | `forUpdate`, consultivo u optimista |
| 5 | ¿Qué pasa si el proceso **muere justo después del commit**? | Si el efecto se recupera solo o se pierde |
| 6 | ¿Esto **cruza a otro servicio**? ¿Qué pasa si el otro falla? | Transacción local, llamada, evento o saga |

**La sexta es la que agrega la arquitectura de servicios**, y es la que más se olvida
porque en un monolito no existía.

### Respuestas que no valen

| Respuesta | Por qué no vale |
| --- | --- |
| «Todo junto» sin enumerar qué | Si no podés listar las escrituras, no sabés dónde termina la transacción |
| «La clave la genera el backend» | Entonces el reintento del cliente crea un efecto nuevo. La clave viene **del hecho**, no de la ejecución |
| «No hace falta bloquear, es poco probable» | La concurrencia es el caso normal, no el raro. Un día de cobro son mil peticiones sobre las mismas obligaciones |
| «Si el proceso muere se reintenta» | ¿Quién lo reintenta? Si la respuesta no es «el relevo del outbox» o «el trabajo programado», nadie |
| «El otro servicio no va a fallar» | Va a fallar. La pregunta es qué hace el tuyo cuando pase |

---

## 2 · El árbol de decisión

**Se recorre de arriba hacia abajo. La primera coincidencia manda.**

```
¿La escritura toca UN solo agregado, en MI esquema?
├── SÍ  → TRANSACCIÓN LOCAL.  @Transactional + conContexto. Fin.
└── NO  ↓

¿Toca varios agregados de MI esquema?
├── SÍ  → TRANSACCIÓN LOCAL, con bloqueo por fila en orden fijo
│         (siempre el mismo orden ⇒ sin abrazo mortal). Fin.
└── NO  ↓

¿Necesito un DATO de otro servicio para decidir, sin escribir nada allá?
├── SÍ  → LLAMADA SINCRÓNICA, **antes** del BEGIN.
│         Timeout + cortacircuitos. El dato entra como parámetro. Fin.
└── NO  ↓

¿El otro servicio tiene que ESCRIBIR, y yo necesito su resultado para responder?
├── SÍ  → SAGA.  Cada paso local y ACID; compensación por reverso.
│         Va a §4. No hay atajo.
└── NO  ↓

¿El otro servicio solo tiene que ENTERARSE?
└── SÍ  → EVENTO por outbox, dentro de mi transacción.
          Consumidor idempotente del otro lado. Fin.
```

> **En la duda entre llamada y evento, elegí evento.** Una llamada sincrónica acopla
> disponibilidad: si el otro está caído, vos también.

---

## 3 · Dónde vive cada garantía

La pregunta que sigue a «¿qué va junto?» es «¿quién lo hace cumplir?». **Poner la
garantía en el lugar equivocado es el defecto más caro que hay.**

| Si la regla… | Vive en | La aplicación… |
| --- | --- | --- |
| Protege dinero o cualquier valor contable | **Base** (`CHECK`, `EXCLUDE`, trigger) | valida igual, solo para dar buen mensaje |
| Impide duplicados o doble efecto | **Base** (`UNIQUE` de idempotencia) | valida la clave **antes** de escribir |
| Guarda o limita un plazo con consecuencia legal | **Base**, con el plazo persistido | lo calcula al crear, no al consultar |
| Impide editar algo inmutable | **Base** (`REVOKE` + trigger) | ni lo intenta |
| Aísla los datos de un usuario | **Base** (RLS) | fija el contexto; no filtra a mano |
| Aísla los datos de un servicio | **Base** (`GRANT` por esquema) | no puede consultarlo aunque quiera |
| Es umbral, límite o tarifa que puede cambiar | **Catálogo**, con vigencia | lo lee; jamás lo escribe en el código |
| Es preferencia o mensaje | **Aplicación** | es la única dueña |

**La prueba de que está en el lugar correcto:** si borrás la validación de la
aplicación, ¿la base sigue rechazando? Si la respuesta es no y hay dinero de por
medio, la garantía está mal puesta.

---

## 4 · Cuando la respuesta es saga

Solo si el árbol de §2 llegó hasta ahí. Antes de escribirla, **revisá si la frontera
está bien puesta**: una operación que cruza tres servicios en cada ejecución suele ser
un límite mal dibujado, no una saga necesaria.

### El formulario de la saga

| Campo | Ejemplo (CU-21) |
| --- | --- |
| **Orquestador** | `aportes` — el que inicia guarda el estado en **su** esquema |
| **Pasos** | 1 marcar EN_COBRO · 2 debitar+asentar · 3 devengar comisión · 4 marcar PAGADA |
| **Cuál es ACID e indivisible** | El 2, entero, dentro de `nucleo-financiero` |
| **Clave de idempotencia de cada paso** | Derivada del hecho: `aporte:<obligacionId>:<periodo>` |
| **Compensación de cada paso** | 2 ⇒ **reverso** (movimiento inverso). 1 y 4 ⇒ estado local |
| **Qué pasa si no puede compensar** | Incidente + aviso a una persona. **Nunca** reintento infinito silencioso |
| **Qué ve el usuario mientras tanto** | «En proceso». **Nunca «listo»** antes de que termine |

### Las cinco reglas

1. **El estado se persiste antes de cada paso.** Si el proceso muere, otro la retoma
   desde donde estaba, no desde el principio.
2. **Cada paso es idempotente**, con clave derivada del hecho.
3. **La compensación es un movimiento inverso**, nunca un `UPDATE`.
4. **El libro contable nunca se parte.** Débito y asiento son un solo paso.
5. **Una saga que no puede compensar abre incidente.**

---

## 5 · Errores que ya conocemos

| Error | Cómo se ve | Qué produce |
| --- | --- | --- |
| Transacción abierta esperando a la red | `cliente.debitar()` dentro de `@Transactional` | Conexión de base bloqueada por la latencia de un tercero; el pool se agota |
| «Primero guardo y después ajusto» | Dos `@Transactional` seguidos | Ese «después» es donde se pierde el dinero |
| Clave de idempotencia generada al ejecutar | `UUID.randomUUID()` en el organismo | El reintento crea un efecto nuevo |
| Bloqueo en orden distinto según el camino | A→B en un método, B→A en otro | Abrazo mortal bajo carga |
| Publicar a Kafka antes del `COMMIT` | `kafkaTemplate.send()` en el organismo | Se anuncia un hecho que puede no haber ocurrido |
| Transacción que abarca dos servicios | Cualquier intento de XA | No existe. Es una saga o está mal partido |
| Compensar con `UPDATE` | «Corrijo el asiento anterior» | Rompe append-only y la auditoría |

---

## 6 · La plantilla para el paso 0

Se pega en el chat, se completa y **se espera el visto bueno antes de escribir nada**.

```markdown
### CU-<NN> · frontera transaccional

1. Todo junto o nada: <lista exacta de escrituras>
2. Fuera del commit: <efectos al outbox>
3. Clave de idempotencia: <valor> — viene de <cliente | proveedor | derivada del hecho>
4. Bloqueo: <forUpdate sobre X | consultivo <modulo>.<recurso> | optimista por version>
   Orden de toma si son varios: <A, luego B — siempre el mismo>
5. Si muere tras el commit: <quién lo recupera y cómo>
6. Cruza servicios: <no | sí: llamada a X | evento a Y | SAGA (formulario §4)>

### Piezas por nivel
Átomos:     <...>
Moléculas:  <...>
Organismo:  CU<NN><VerboObjeto>.java
Página:     <X>Controller.java
```

## Ver también

`arrancar-carril` · `back-spring` · `servicios-y-sagas` · `idempotencia-reintentos` ·
`datos-jooq` · `contabilidad-partida-doble` · `restriccion` · `pruebas-cu`
