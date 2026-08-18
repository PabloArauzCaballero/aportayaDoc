---
tags:
  - arquitectura
  - adr
titulo: "ADR-007 — Sesión, RLS y pooling"
estado: superada por ADR-021
fecha: 2026-08-12
---

# ADR-007 — Sesión, RLS y pooling

> [!warning] Decisión superada el 2026-08-16
> La reemplaza [[ADR-021 Sesión, RLS y pooling]], al pasar el backend a Spring Boot con
> arquitectura de microservicios ([[ADR-014 Arquitectura de servicios]]).
> Se conserva porque el motivo por el que se decidió lo que se decidió es
> parte del expediente: quien lea el ADR nuevo tiene que poder ver qué
> cambió y por qué.

## Contexto

El modelo protege los datos con **políticas de fila** en PostgreSQL: un participante
ve su grupo, un oficial de cumplimiento ve alertas, un auditor lee y no escribe. Esas
políticas leen el contexto de sesión `app.usuario_id` y `app.rol`. La bóveda lo dice
sin rodeos: *cada request setea el contexto; sin eso, las políticas no protegen nada*.

El detalle peligroso: en un servidor con pool de conexiones, la conexión que atendió
al usuario A la usa después el usuario B. Si el contexto se fija con `SET` normal,
**persiste en la conexión** y el usuario B hereda la identidad de A. Es la fuga de
datos más silenciosa que puede tener este sistema.

## Decisión

**El contexto se fija con `SET LOCAL` dentro de la misma transacción del caso de uso,
y nunca fuera de ella.**

- Un único punto de entrada abre la transacción, ejecuta
  `SET LOCAL app.usuario_id / app.rol` y recién entonces invoca el caso de uso.
- Fuera de transacción **no se consulta nada** de tablas con RLS.
- **PgBouncer en modo *transaction***, compatible con `SET LOCAL` porque el contexto
  muere en el `COMMIT`/`ROLLBACK`.
- Roles de base distintos por proceso, **los que ya define `sql/00_base/01_roles.sql`**:
  `rol_aplicacion`, `rol_backoffice`, `rol_cumplimiento`, `rol_auditor` (solo lectura)
  y `rol_migracion`. Ninguno es superusuario, y ninguno tiene `UPDATE`/`DELETE` sobre
  las tablas append-only. Si el worker necesita menos privilegios que la API, el rol
  se agrega al generador —no se improvisa en el despliegue.
- Los trabajos del worker fijan su propio contexto y actúan como sistema, no
  suplantando a un usuario, salvo que el evento indique el actor original.

## Motivo

**Porque `SET LOCAL` tiene el alcance correcto por construcción.** No depende de que
alguien recuerde limpiar la conexión al terminar: PostgreSQL lo revierte al cerrar la
transacción. La defensa está en el mecanismo, no en la disciplina.

**Porque el modo *transaction* de PgBouncer es lo único que escala aquí.** El modo
*session* ata una conexión por cliente y desperdicia el pool; el modo *statement*
prohíbe transacciones, que son el corazón de cada caso de uso. Queda uno solo, y
resulta ser el que convive con `SET LOCAL`.

**Porque RLS es evidencia, no comodidad.** Ante una inspección, "el backend filtra
por usuario" es una promesa; una política de fila es un control verificable con una
consulta. Lo mismo vale para la segregación de funciones que exige el modelo.

**Porque los roles separados limitan el daño.** Si el proceso de reportes es
comprometido, no puede escribir; si el worker lo es, no puede alterar el libro.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Filtrar por `WHERE usuario_id = ?` en la aplicación** | Un `WHERE` olvidado es una fuga; y no protege contra una consulta ad hoc mal hecha en soporte. |
| **`SET` sin `LOCAL`** | Filtra identidad entre requests al reutilizar la conexión. Prohibido. |
| **PgBouncer en modo *session*** | Desperdicia conexiones y no aporta nada sobre el modo transacción con `SET LOCAL`. |
| **Una conexión por usuario** | Imposible a escala de app móvil. |
| **Serverless con conexiones efímeras** | Latencia de establecimiento por request y pools sin estado; mala pareja para transacciones largas con contexto. |

## Consecuencias

**A favor**

- Aislamiento garantizado por la base, verificable con pruebas negativas.
- El pool escala sin sacrificar transacciones.

**En contra**

- **Nada de sentencias preparadas globales ni `LISTEN/NOTIFY` a través del pooler**:
  el modo transacción no los soporta. El worker se conecta directo a Postgres.
- Toda consulta necesita transacción, incluso una lectura simple. Se acepta: es una
  transacción de solo lectura y cuesta poco.
- El código no puede "reutilizar" una conexión entre pasos de un caso de uso sin
  pasarla explícitamente: la transacción se inyecta, no se busca.

## Reglas de uso

| Regla | Cómo se ve |
| --- | --- |
| Contexto y transacción nacen juntos | `conTransaccion(ctx, async (tx) => …)` |
| Ningún repositorio abre conexión propia | Recibe `tx` |
| Sin contexto no hay consulta | Si `ctx.usuarioId` es nulo, se rechaza antes del SQL |
| El worker declara su actor | `app.rol = 'sistema'` y el evento guarda el actor original |
| Roles con el mínimo privilegio | `GRANT` explícito; ninguno hereda de otro |

## Cómo se verifica

- [ ] Prueba negativa: con el contexto de otro usuario, la consulta devuelve cero
      filas —no un error de aplicación, cero filas por política.
- [ ] Prueba: dos requests seguidos sobre la misma conexión del pool no comparten
      contexto.
- [ ] Prueba: el rol `rol_auditor` falla al intentar escribir.
- [ ] Regla de lint: ninguna consulta fuera de `conTransaccion`.

## Ver también

[[ADR-002 Acceso a datos]] · [[Flujo de una transacción]] · [[Entornos y despliegue]] · [[Restricciones]]
