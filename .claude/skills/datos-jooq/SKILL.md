---
name: datos-kysely
description: "Acceder a la base de AportaYa con Kysely: tipos introspectados, transacciones, SET LOCAL para RLS, bloqueos, numeric como string, append-only y consultas de cuadre. Úsala al escribir cualquier repositorio o consulta, al regenerar tipos tras cambiar el modelo, o cuando aparezca la tentación de meter un ORM."
---

# Acceso a datos con Kysely

El esquema **es generado** desde la bóveda y tiene un solo dueño. El código de datos
se **deriva** de la base viva por introspección, nunca al revés
([[ADR-002 Acceso a datos]]).

```bash
psql -f sql/aplicar.sql && yarn datos:tipos   # base primero, tipos después
```

Si `yarn datos:tipos` produce un diff, el modelo cambió y hay lugares del código que
revisar: el compilador te va a decir cuáles. Ese es todo el mecanismo anti-divergencia.

## Repositorio: recibe la transacción, no la crea

```ts
export class ObligacionRepositorio {
  async tomarParaActualizar(tx: Trx, id: string) {
    return tx.selectFrom('obligacion_aporte')
      .select(['id', 'participante_id', 'monto', 'moneda', 'estado', 'vence_en'])
      .where('id', '=', id)
      .forUpdate()                       // bloqueo de fila: dos pagos simultáneos
      .executeTakeFirstOrThrow()
  }
}
```

| Regla | Por qué |
| --- | --- |
| **Sin `SELECT *`** en flujos de dinero | Un `ALTER` no debe cambiar el resultado en silencio |
| **Sin lógica de negocio** | Si hay un `if` sobre una regla, va al átomo o al organismo |
| **`forUpdate()` cuando se lee para decidir y luego escribir** | Sin bloqueo, dos transacciones deciden sobre el mismo saldo |
| **Nada de `UPDATE` en tablas append-only** | La base lo rechaza; el código no debería intentarlo |
| **Orden y filtros por lista blanca** | Nunca ordenar por un campo que llega del cliente sin validar |

## Transacciones y contexto

Todo pasa por `conTransaccion`, que abre la transacción y fija el contexto de RLS con
`SET LOCAL` — la única forma segura con pool ([[ADR-007 Sesión, RLS y pooling]]):

```ts
await db.transaction().execute(async (tx) => {
  await sql`SET LOCAL app.usuario_id = ${ctx.usuarioId}`.execute(tx)
  await sql`SET LOCAL app.rol        = ${ctx.rol}`.execute(tx)
  return fn(tx)
})
```

Nunca `SET` sin `LOCAL`: la conexión vuelve al pool con la identidad del usuario
anterior. Es la fuga más silenciosa que puede tener este sistema.

**Ninguna consulta fuera de transacción**, ni siquiera de lectura: sin contexto, las
políticas de fila no protegen nada.

## Dinero

```ts
types.setTypeParser(1700, (v) => v)   // numeric → string, una sola vez, al crear el pool
```

`numeric` llega como *string* y se convierte a `Dinero` en el dominio. Nunca
`parseFloat`, nunca aritmética suelta (`dinero-decimal`).

Los agregados y cuadres se hacen **en SQL**, con `numeric`: es exacto y evita traer
diez mil filas al proceso.

```ts
const { cuadre } = await tx.selectFrom('transaccion_billetera')
  .select(({ fn }) => fn.sum<string>('monto').as('cuadre'))
  .where('transaccion_id', '=', id)
  .executeTakeFirstOrThrow()          // debe ser '0.00'
```

## Bloqueos

| Necesidad | Mecanismo |
| --- | --- |
| Dos pagos sobre la misma obligación o cuenta | `FOR UPDATE` sobre la fila del agregado |
| Proceso global único (cierre diario, conciliación, remisión) | Bloqueo consultivo `pg_advisory_xact_lock` |
| Edición de configuración que una persona debe resolver | Columna `version`, bloqueo optimista |
| Cola de trabajos | `FOR UPDATE SKIP LOCKED` (lo hace el worker) |

El aislamiento por defecto es `READ COMMITTED`; los flujos que leen para decidir y
después escriben usan bloqueo explícito, no `SERIALIZABLE` global.

## Consultas polimórficas

Las referencias polimórficas del modelo (bitácora, deducciones, alertas) **no tienen
FK física**: se validan por aplicación o trigger. Al consultarlas, el tipo de destino
se filtra siempre de forma explícita; nada de asumir que el identificador basta.

## Errores de la base

Se traducen al código documentado, nunca se propaga el texto crudo:

| Error de PostgreSQL | Se traduce a |
| --- | --- |
| `uq_pago_clave_idempotencia` | Respuesta original (no es error para el usuario) |
| `ck_movimiento_suma_cero` | `R-BIL-03` |
| `ex_turno_sin_solape` | `R-GRP-05` |
| Permiso denegado sobre append-only | Defecto de la aplicación: se registra y se corrige |

## Antipatrones

- Instalar un ORM "porque es más rápido de escribir".
- Escribir una migración a mano en vez de regenerar desde la bóveda.
- Repositorios que abren su propia conexión.
- `any` para saltar un tipo introspectado que no encaja: si no encaja, el modelo
  cambió y hay que mirarlo.
- Recalcular en el proceso lo que la base agrega mejor.

## Ver también

`seguridad-sesion-rls` · `contabilidad-partida-doble` · `back-nestjs` · `dinero-decimal` · `boveda-modelo` · `restriccion` · `pruebas-cu` ·
`docs/Arquitectura/ADR-002 Acceso a datos.md`
