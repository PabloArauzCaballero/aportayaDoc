---
name: datos-jooq
description: "Acceder a la base de AportaYa con jOOQ: clases generadas por introspección, esquema y rol por servicio, transacciones, SET LOCAL para RLS, bloqueos, BigDecimal, append-only y consultas de cuadre. Úsala al escribir cualquier repositorio o consulta, al regenerar tras cambiar el modelo, o cuando aparezca la tentación de meter JPA."
---

# Acceso a datos con jOOQ

El esquema **es generado** desde la bóveda y tiene un solo dueño. El código de datos
se **deriva** de la base viva por introspección, nunca al revés
([[ADR-016 Acceso a datos con jOOQ]]).

```bash
./gradlew bd:reset                        # base con el esquema de sql/ y semillas
./gradlew :servicios:<x>:generateJooq     # clases DEL ESQUEMA DE <x>, y solo de ese
./gradlew :servicios:<x>:compileJava      # ← este es el gate
```

> **El gate no es un diff: es la compilación.** El código generado **no se versiona**.
> Si el esquema cambió y el código no, el build falla. Con catorce servicios,
> versionar el generado serían catorce diffs ilegibles y catorce puntos de conflicto.

## Un esquema y un rol por servicio

Cada servicio genera **solo las clases de su esquema** y se conecta con **su rol**.
No puede consultar las tablas de otro aunque quiera: no tiene `GRANT`, y las clases
ni siquiera existen en su código ([[ADR-017 Propiedad de datos por servicio]]).

| Necesidad | Cómo se resuelve | Cómo **no** |
| --- | --- | --- |
| Un dato de otro servicio | Llamada a su API, por el cliente generado | `SELECT` cruzado de esquema |
| Muchas veces el mismo dato | Copia local por evento del outbox, marcada como réplica y nunca autoritativa | Vista sobre el esquema ajeno |
| Un informe que cruza todo | `auditoria` sobre la **réplica**, con `rol_auditor` | Que cada servicio exporte su parte |
| Mover dinero | Pedírselo a `nucleo-financiero` con clave de idempotencia | Escribir el libro por tu cuenta |

## La transacción y el contexto de RLS

```java
@Transactional                                  // ← SOLO en aplicacion/
public ResultadoX ejecutar(EntradaX e, ContextoSesion ctx) {
    return datos.conContexto(ctx, dsl -> {      // ← SET LOCAL dentro de la transacción
        ...
    });
}
```

| Regla | Por qué |
| --- | --- |
| `set_config(…, true)` — **nunca `SET` plano** | Con pool, un `SET` plano sobrevive a la petición y contamina la siguiente. Es una fuga de datos entre usuarios |
| El `DSLContext` es el de la transacción en curso | Tomar otra conexión pierde el contexto **en silencio**: el error más caro de esta skill |
| Sin contexto no hay consulta | La ausencia de `usuarioId` es un defecto, no un caso |
| Ninguna consulta con RLS fuera de `conContexto` | Regla de análisis estático |

**La prueba negativa es obligatoria en cada servicio:** una consulta con el contexto de
otro usuario devuelve **cero filas**, no un error. Si devuelve error, la política está
mal escrita; si devuelve filas, hay una fuga.

## El repositorio: SQL, sin lógica

```java
@Repository
public class ObligacionRepositorio {

    public Optional<Obligacion> tomarParaActualizar(DSLContext dsl, UUID id) {
        return dsl.selectFrom(OBLIGACION_APORTE)
                  .where(OBLIGACION_APORTE.ID.eq(id))
                  .forUpdate()                     // bloqueo por fila sobre el agregado
                  .fetchOptional()
                  .map(this::aDominio);
    }
}
```

- **No abre transacciones.** Recibe el `DSLContext` de la que ya está abierta.
- **No contiene un `if` de negocio.** Si hay una decisión, pertenece a un átomo.
- Devuelve tipos del dominio, no registros de jOOQ: el organismo no debería saber que
  existe una tabla.

## Dinero

jOOQ mapea `numeric(14,2)` a `BigDecimal` de forma nativa. Lo que hay que sostener:

| Regla | Verificación |
| --- | --- |
| Ningún `double` ni `float` para dinero, en ninguna capa | Análisis estático |
| `compareTo`, nunca `equals`, para comparar importes | `1.10` y `1.1` son iguales en valor y distintos en `equals` |
| `RoundingMode` explícito siempre | `divide` sin él lanza excepción, y eso es una funcionalidad |
| En JSON, **cadena decimal, nunca número** | El cliente es JavaScript: un `number` de JSON es un doble del otro lado |

## Append-only

Las tablas de `sql/35_append_only/` tienen `REVOKE UPDATE, DELETE` para el rol dueño.
**La base rechaza**; el análisis estático solo adelanta el fallo. Corrección = insertar
el movimiento inverso, nunca editar el original.

## Bloqueos

| Situación | Qué se usa |
| --- | --- |
| Un agregado que se modifica | `forUpdate()` sobre la fila (obligación, cuenta) |
| Proceso global (cierre diario, conciliación) | Bloqueo consultivo con `hashtext('<modulo>.<recurso>')` — **nunca un entero literal** |
| Trabajo programado entre réplicas | ShedLock, con nombre `<modulo>.<trabajo>` |
| Edición de configuración (tarifario, acuerdo) | Bloqueo optimista por `version`: el conflicto le avisa a una persona |

Aislamiento por omisión `READ COMMITTED`. Los flujos que leen para decidir y después
escriben usan bloqueo explícito, no `SERIALIZABLE` global.

## Consultas de cuadre

Las que responden «¿esto cierra?». Van en el servicio dueño del libro y se prueban con
datos reales:

- Suma de débitos igual a suma de créditos, por asiento y por período.
- Saldo derivado de movimientos igual al saldo en caché.
- Custodia igual a la suma de saldos de billetera.

**El saldo no se escribe: se deriva.** Se insertan movimientos con contrapartida.

## Lo que está prohibido

| Prohibido | Por qué |
| --- | --- |
| **JPA / Hibernate**, en cualquier servicio | *Dirty checking* contra append-only y `hbm2ddl` contra `sql/`. Prohibido por ADR, no desaconsejado |
| `spring-boot-starter-data-jpa` en un `build.gradle.kts` | Lo mismo, en la puerta de entrada |
| Abrir transacción dentro de un repositorio | Rompe «una transacción por caso de uso» |
| Migraciones escritas a mano fuera de `sql/` | Dos fuentes de esquema |
| Migrar al arrancar (`spring.flyway.enabled: true`) | Catorce procesos compitiendo por el mismo esquema |
| Versionar el código generado | El gate es la compilación |
| `SELECT` sobre el esquema de otro servicio | Invariante 11 |

## Cuando cambia el modelo

**Para todo.** Un cambio de esquema se hace en troncal, se regenera, se verifica la
bóveda y recién ahí los carriles rebasan. La partición en servicios **no** compra
independencia de modelo: un cambio rompe la compilación de los catorce a la vez, y eso
es correcto pero hay que hacerlo ordenado.

## Ver también

`back-spring` · `dinero-decimal` · `seguridad-sesion-rls` · `restriccion` ·
`boveda-modelo` · `lecturas-proyecciones` · `pruebas-cu`
