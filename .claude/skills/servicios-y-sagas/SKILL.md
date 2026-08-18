---
name: servicios-y-sagas
description: "Hacer que dos servicios de AportaYa se hablen sin romper el dinero: cuándo la llamada es sincrónica y cuándo es evento, timeout y cortacircuitos obligatorios, y la saga orquestada con compensación por reverso cuando una operación cruza servicios. Úsala al llamar a otro servicio, al consumir un evento, o cuando un caso de uso toque más de un servicio."
---

# Servicios y sagas

Con catorce servicios ([[ADR-014 Arquitectura de servicios]]), lo que era una llamada
a un método pasa a ser una llamada de red: puede tardar, puede fallar a la mitad y
puede ejecutarse dos veces. Esta skill dice qué hacer con eso.

## Primero: ¿de verdad tiene que cruzar?

Antes de escribir la llamada, revisá si la frontera está bien puesta. Una operación
que cruza tres servicios **en cada ejecución** suele ser una frontera mal dibujada, no
una saga necesaria.

| Señal | Qué significa |
| --- | --- |
| Necesitás datos de otro servicio en cada consulta | La frontera está mal, o falta una copia local por evento |
| Necesitás escribir en dos servicios atómicamente | O es una saga, o los dos agregados son el mismo y están mal separados |
| Necesitás leer su base directamente | **Nunca.** Invariante 11 |

## Sincrónico o evento

```
sincrónico   →  la respuesta al usuario depende del resultado
                (¿tiene saldo? ¿está habilitado? ¿cuánto es la comisión?)
evento       →  el otro tiene que enterarse, no responder
                (se confirmó un aporte, se declaró un incumplimiento)
```

**En la duda, evento.** Una llamada sincrónica acopla disponibilidad: si el otro está
caído, vos también.

## La llamada sincrónica

Siempre por el **cliente generado del OpenAPI ajeno**, nunca con un cliente HTTP
escrito a mano — un cliente a mano diverge del contrato en silencio.

```java
@Component
public class NucleoFinancieroCliente {

    @CircuitBreaker(name = "nucleo-financiero")
    @Retry(name = "nucleo-financiero")          // solo porque es idempotente
    @TimeLimiter(name = "nucleo-financiero")
    public Asiento debitar(SolicitudDebito s, ClaveIdempotencia clave) { … }
}
```

| Política | Valor por omisión | Regla |
| --- | :-: | --- |
| Timeout | 2 s | **Obligatorio, sin excepción.** Un cliente sin timeout es un rechazo de revisión |
| Reintento | 2, con retroceso exponencial y variación aleatoria | **Solo en operaciones idempotentes** |
| Cortacircuitos | 50 % de fallos en 20 llamadas | La conmutación **nunca es silenciosa**: deja evento y métrica |
| Mamparo | Pool propio por dependencia | Un servicio caído no consume los hilos de los demás |

**Dos reglas duras:**

- **Nunca dentro de una transacción abierta.** Mantiene una transacción de base
  esperando a otro proceso. Es el invariante 6 y con red se vuelve más grave.
- **Máximo dos saltos.** `A → B → C` está bien; `A → B → C → D` no: la latencia se
  acumula y el modo de fallo deja de poder razonarse. Si hace falta, es una saga.

## El evento

Se emite **dentro** de la transacción, a la tabla de outbox del propio esquema. Nunca
se publica a Kafka desde el código de negocio ([[ADR-018 Outbox transaccional y mensajería]]).

```java
outbox.emitir(dsl, "aportes.aporte_confirmado", carga);   // misma transacción
```

**Todo consumidor es idempotente, sin excepción.** El transporte es *al menos una vez*:

```java
@KafkaListener(topics = "aportaya.aportes.aporte_confirmado")
public void alConfirmarse(EventoAporte e) {
    if (!consumidos.registrar(e.id(), "grupos"))  return;   // clave única: ya procesado
    ...
}
```

Y todo consumidor tolera **fuera de orden**: el orden solo está garantizado por clave
de partición. Un consumidor que asume orden global está mal escrito.

## La saga

**Orquestada, no coreografiada.** El servicio que inicia la operación guarda el estado
en su propio esquema y decide el paso siguiente. Con coreografía nadie sabe en qué
estado quedó una operación a medias, y con dinero eso no es aceptable.

```
CU-21 · cobrar el aporte
 1  aportes            marca la obligación EN_COBRO          ← local, reversible
 2  nucleo-financiero  debita y asienta                       ← ACID, idempotente
 3  tarifas            devenga la comisión                    ← compensable
 4  aportes            marca la obligación PAGADA             ← local
     falla en 3  ⇒  compensa 2 con un REVERSO, no con un UPDATE
```

### Las cinco reglas

| Regla | Por qué |
| --- | --- |
| **El estado se persiste antes de cada paso** | Si el proceso muere, otro la retoma desde donde estaba |
| **Cada paso es idempotente**, con clave derivada del hecho | Reintentar no puede duplicar el efecto |
| **La compensación es un movimiento inverso** | Append-only vale igual entre servicios que dentro de uno. Nunca un `UPDATE` |
| **El libro contable nunca se parte** | Débito y asiento son un solo paso, en `nucleo-financiero` |
| **Una saga que no puede compensar abre incidente y avisa a una persona** | No se reintenta para siempre en silencio |

### Qué garantiza cada tramo

| Tramo | Garantía | Qué NO garantiza |
| --- | --- | --- |
| Dentro de un servicio | Atomicidad total, con las restricciones de la base | Nada sobre los otros |
| Entre servicios, por saga | Consistencia eventual **con compensación** | Que el usuario vea el resultado de inmediato |
| Por evento | Al menos una vez, un solo efecto por consumidor idempotente | Orden global |

> **La interfaz tiene que saber decir «en proceso».** Un flujo que cruza servicios y
> se muestra como «listo» antes de que la saga termine está mintiendo, y en dinero eso
> se paga con un reclamo.

## Identidad y traza

- Se propaga **el JWT del usuario original**, no un usuario técnico. Si `nucleo-financiero`
  ejecutara todo como «servicio de billetera», la RLS dejaría de proteger nada en la
  mitad más sensible del sistema.
- El receptor **valida la firma él mismo**. No confía en una cabecera que diga quién
  es el usuario ([[ADR-024 Autenticación y sesión distribuida]]).
- El `x-request-id` viaja en la llamada, en la carga del evento y de ahí al consumidor.
  Es lo único que permite reconstruir una operación sin leer catorce registros.

## Las pruebas que esto obliga

| Prueba | Qué fuerza |
| --- | --- |
| **De contrato** | Que lo que publicás es lo que el consumidor espera. Falla en **tu** CI, no en el suyo |
| **Evento duplicado** | El mismo evento dos veces ⇒ un efecto |
| **Evento fuera de orden** | Llega el segundo antes que el primero ⇒ no rompe |
| **Compensación** | Se fuerza el fallo de **cada** paso y el sistema queda cuadrado |
| **Proveedor caído** | Con el otro servicio abajo, respondés dentro del presupuesto y no colgás |

## Antes de dar por terminado

- [ ] Cada cliente declara timeout, reintento y cortacircuitos
- [ ] Ninguna llamada de red dentro de un método `@Transactional`
- [ ] Ninguna cadena supera dos saltos
- [ ] Todo consumidor inserta en `evento_consumido` antes de actuar
- [ ] La saga persiste su estado antes de cada paso
- [ ] Hay una prueba de compensación por cada paso que puede fallar
- [ ] La compensación es un reverso, no una edición

## Ver también

`back-spring` · `trabajos-outbox` · `idempotencia-reintentos` · `contratos-api` ·
`resiliencia-rendimiento` · `contabilidad-partida-doble` · `observabilidad`
