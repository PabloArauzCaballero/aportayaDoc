---
tags:
  - arquitectura
  - adr
titulo: "ADR-022 — Comunicación entre servicios: gateway, llamadas y sagas"
estado: aceptada
fecha: 2026-08-16
---

# ADR-022 — Comunicación entre servicios

## Contexto

[[ADR-014 Arquitectura de servicios]] crea catorce procesos que antes eran módulos de
uno solo. Lo que era una llamada a un método pasa a ser una llamada de red: puede
tardar, puede fallar a la mitad y puede ejecutarse dos veces.

Y aparece el problema que no existía: **operaciones que cruzan servicios y tienen que
terminar bien o no terminar**. Cobrar un aporte toca `aportes` (la obligación),
`nucleo-financiero` (el débito y el asiento) y `tarifas` (la comisión). Antes era una
transacción; ahora son tres procesos.

## Decisión

**Una entrada pública por gateway; llamadas sincrónicas solo cuando la respuesta es
necesaria para responder; todo lo demás por evento; y una saga con compensación
cuando la operación cruza servicios y mueve dinero.**

### 1 · El gateway es la única puerta

**Spring Cloud Gateway** delante de los catorce. Ningún servicio publica puerto al
exterior.

| Responsabilidad del gateway | Responsabilidad del servicio |
| --- | --- |
| Enrutar por prefijo reservado | Validar el JWT él mismo |
| Terminar TLS | Autorizar por permiso |
| Límite de tasa por identidad y por IP | Límite de tasa por operación sensible |
| Asignar `x-request-id` si no viene | Propagarlo |
| Rechazar lo obviamente inválido | Validar el contrato completo |

El gateway **no** contiene reglas de negocio, no compone respuestas de varios
servicios y no traduce errores. Un gateway con lógica es el monolito volviendo por la
puerta de atrás, y además compartido por catorce carriles.

### 2 · Sincrónico solo cuando hace falta la respuesta

```
sincrónico   →  la respuesta al usuario depende del resultado
                (¿tiene saldo? ¿está habilitado? ¿cuánto es la comisión?)
asincrónico  →  el otro servicio tiene que enterarse, no responder
                (se confirmó un aporte, se declaró un incumplimiento)
```

Toda llamada sincrónica usa el **cliente generado del OpenAPI ajeno**
([[ADR-020 Contratos OpenAPI primero]]) y va envuelta en **Resilience4j**:

| Política | Valor por omisión | Regla |
| --- | --- | --- |
| Timeout | 2 s | **Obligatorio, sin excepción.** Un adaptador sin timeout es un rechazo de revisión |
| Reintento | 2 intentos, retroceso exponencial con variación aleatoria | Solo en operaciones idempotentes |
| Cortacircuitos | Abre al 50 % de fallos en 20 llamadas | La conmutación **nunca es silenciosa**: deja evento y métrica |
| Mamparo | Pool propio por dependencia | Un servicio caído no consume los hilos de los demás |

**Prohibido: una llamada sincrónica dentro de una transacción abierta.** Es el
invariante 6 y con red se vuelve más grave: mantiene una transacción de base abierta
mientras se espera a otro proceso.

**Prohibida la cadena de más de dos saltos.** `A → B → C` es aceptable; `A → B → C →
D` no: la latencia se acumula y el modo de fallo se vuelve imposible de razonar. Si
hace falta, la operación es una saga.

### 3 · Sagas para lo que cruza servicios y mueve dinero

**Saga orquestada, no coreografiada.** El servicio que inicia la operación es el
orquestador: guarda el estado de la saga en su propio esquema y decide el paso
siguiente. Con coreografía —cada servicio reaccionando a eventos— nadie sabe en qué
estado está la operación, y en un sistema con dinero eso no es aceptable.

```
CU-21 · cobrar el aporte
 1  aportes            marca la obligación EN_COBRO          ← local, reversible
 2  nucleo-financiero  debita y asienta                       ← ACID, idempotente
 3  tarifas            devenga la comisión                    ← compensable
 4  aportes            marca la obligación PAGADA             ← local
     falla en 3  ⇒  compensa 2 con un REVERSO, no con un UPDATE
```

Reglas de la saga, todas verificables:

- **Cada paso es idempotente**, con clave derivada del hecho disparador.
- **La compensación es un movimiento inverso**, nunca una edición: append-only vale
  igual entre servicios que dentro de uno.
- **El estado de la saga se persiste antes de cada paso.** Si el proceso muere, otro
  la retoma desde donde estaba.
- **Una saga que no puede compensar abre un incidente** y avisa a una persona. No se
  reintenta para siempre en silencio.
- **La escritura del libro es un solo paso**, porque vive entera en
  `nucleo-financiero` ([[ADR-017 Propiedad de datos por servicio]]). Ninguna saga
  parte un asiento.

### 4 · Descubrimiento y configuración

Sin registro de servicios: los nombres se resuelven por DNS del orquestador de
contenedores (`http://nucleo-financiero:8080`). Catorce servicios estables no
justifican Eureka ni Consul, y un registro es una pieza más que puede caerse.

## Motivo

**La saga es el costo real de esta arquitectura, y conviene nombrarlo sin adornos.**
Lo que era un `@Transactional` pasa a ser una máquina de estados con compensación.
Se acepta porque el caso que más importa —el asiento contable— **no** se parte, y
porque los demás toleran consistencia eventual: que la comisión se devengue 200 ms
después del débito no rompe nada, siempre que termine devengándose o revirtiendo.

**La orquestación gana a la coreografía en un sistema con dinero** por una razón
operativa: cuando algo queda a medias, alguien tiene que poder preguntar «¿en qué
estado quedó esta operación?» y obtener una respuesta. Con coreografía, la respuesta
hay que reconstruirla de los registros de siete servicios.

**El gateway sin lógica** es lo que impide que catorce carriles vuelvan a compartir
un archivo. En cuanto el gateway compone respuestas, todos los carriles lo editan.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Transacciones distribuidas (XA / dos fases)** | PostgreSQL las soporta y son una trampa: bloqueos que sobreviven a la caída de un participante y un coordinador que es un fallo único. La industria las abandonó por buenas razones. |
| **Saga coreografiada** | Menos código y ningún lugar donde consultar el estado de una operación a medias. Inaceptable con dinero. |
| **Gateway que compone (agregación en el borde)** | Ahorra viajes al cliente y devuelve el archivo compartido por catorce carriles, además de meter lógica de negocio en la pieza que no debe tenerla. |
| **Malla de servicios (Istio, Linkerd)** | Resuelve bien timeout, reintento y mutua TLS, y agrega un plano de control entero a operar para catorce servicios. Se reevalúa si el parque crece; hoy Resilience4j en el cliente alcanza. |
| **Eureka o Consul** | Descubrimiento dinámico para un conjunto estático de catorce nombres. Una pieza más que puede fallar. |
| **Todo asincrónico, sin llamadas sincrónicas** | Puro y poco práctico: «¿tiene saldo?» necesita una respuesta antes de contestarle al usuario. |

## Consecuencias

**A favor**

- Los modos de fallo son explícitos y están acotados por política, no por suerte.
- Un servicio caído degrada a los que lo llaman de forma acotada, no los cuelga.
- El estado de toda operación distribuida se puede consultar.

**En contra, y hay que asumirlo**

- **Las sagas son trabajo nuevo y real**, y son la parte más difícil de probar del
  sistema. Cada una necesita su prueba de compensación.
- **Consistencia eventual visible al usuario** en los flujos que cruzan servicios: la
  interfaz tiene que saber mostrar «en proceso» y no mentir con «listo».
- **Depurar una operación cruza catorce registros.** Se paga con trazas
  correlacionadas por `x-request-id` de punta a punta, obligatorias.
- Latencia acumulada en las llamadas sincrónicas, acotada por el límite de dos saltos
  y por el presupuesto de respuesta.

## Cómo se verifica

- [ ] Ningún servicio publica puerto al exterior; solo el gateway.
- [ ] Todo cliente de otro servicio declara timeout, reintento y cortacircuitos.
- [ ] Ninguna llamada de red ocurre dentro de un método `@Transactional`.
- [ ] Ninguna cadena de llamadas supera dos saltos, medido en las trazas.
- [ ] Toda saga tiene prueba de compensación: se fuerza el fallo de cada paso y el
      sistema queda cuadrado.
- [ ] Con un servicio caído, el que lo llama responde dentro de su presupuesto y no
      cuelga.
- [ ] `x-request-id` aparece en el registro de todos los servicios que participaron
      de una operación.

## Ver también

[[ADR-014 Arquitectura de servicios]] · [[ADR-018 Outbox transaccional y mensajería]] · [[ADR-020 Contratos OpenAPI primero]] · [[ADR-021 Sesión, RLS y pooling]] · [[ADR-025 Empaquetado y despliegue de los servicios]] · [[Flujo de una transacción]] · [[_Arquitectura]]
