---
tags:
  - arquitectura
  - adr
titulo: "ADR-002 — Acceso a datos: Kysely, no ORM"
estado: superada por ADR-016
fecha: 2026-08-12
---

# ADR-002 — Acceso a datos: Kysely, no ORM

> [!warning] Decisión superada el 2026-08-16
> La reemplaza [[ADR-016 Acceso a datos con jOOQ]], al pasar el backend a Spring Boot con
> arquitectura de microservicios ([[ADR-014 Arquitectura de servicios]]).
> Se conserva porque el motivo por el que se decidió lo que se decidió es
> parte del expediente: quien lea el ADR nuevo tiene que poder ver qué
> cambió y por qué.

## Contexto

El esquema **es generado**: `scripts/generar_ddl.py` produce `sql/` a partir de los
diagramas `docs/entidades/*.puml` y del catálogo de [[Restricciones]]. La base tiene
274 tablas, referencias circulares entre módulos, tablas *append-only* selladas por
`REVOKE`, restricciones `EXCLUDE` con `btree_gist`, políticas RLS y columnas
polimórficas validadas por trigger.

Un ORM moderno quiere ser dueño de tres cosas: el esquema, las migraciones y el SQL
que se ejecuta. Aquí las tres ya tienen dueño.

## Decisión

**Kysely**, con los tipos de las tablas **generados por introspección de la base
viva** (`kysely-codegen`), sobre el driver `pg`.

- El código de datos vive en `infraestructura/*Repositorio.ts`: **SQL, sin lógica**.
- Los tipos se regeneran como paso de build después de aplicar `sql/aplicar.sql`.
- Las migraciones **no** las administra la capa de datos: se aplican los artefactos
  de `sql/` con dbmate/Flyway ([[Entornos y despliegue]]).

## Motivo

**Porque la dirección de la verdad no se negocia.** Con un ORM declarativo, el
esquema existe dos veces: en `sql/` y en las entidades del ORM. Dos copias de 274
tablas divergen —no es una hipótesis, es lo que pasa siempre— y cuando divergen, la
bóveda deja de describir la base y todo este repositorio pierde valor. Con
introspección hay **una sola fuente** y la copia se regenera con un comando.

**Porque el SQL del dinero tiene que ser auditable.** Una consulta que calcula el
encaje de custodia (CU-50) o que cuadra un asiento (CU-24) es evidencia ante una
inspección. Tiene que poder leerse tal cual se ejecuta, no reconstruirse mentalmente
desde una cadena de métodos con *lazy loading*.

**Porque el modelo pelea con el ciclo de vida de un ORM.** Las tablas append-only
prohíben `UPDATE` a nivel de rol: cualquier mecanismo de *dirty checking* que
intente persistir un objeto modificado choca contra un `REVOKE`. El saldo, además,
no se escribe: se deriva de movimientos ([[transaccion_billetera]]). Un ORM invita
exactamente a lo contrario.

**Kysely y no Drizzle** —ambos servirían— porque Kysely es solo un constructor de
consultas tipado sin ambición de esquema, y su API se parece al SQL que ya está
escrito en `sql/`, lo que hace la revisión más directa.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Prisma** | Quiere administrar esquema y migraciones; el `SET LOCAL` de RLS es incómodo; no expresa `EXCLUDE`, índices parciales ni políticas. |
| **TypeORM / Sequelize** | Mismo problema de propiedad del esquema, más *dirty checking* incompatible con append-only. |
| **SQL crudo con `pg`** | Sin tipos, 274 tablas se vuelven ingobernables; un `rename` no rompe nada en compilación y rompe todo en producción. |
| **Drizzle** | Equivalente y aceptable; se elige uno solo para no tener dos estilos en el repo. |

## Consecuencias

**A favor**

- El esquema tiene un solo dueño y la bóveda sigue siendo verdad.
- El SQL es visible, revisable y perfilable.
- `yarn datos:tipos` después de aplicar `sql/` deja errores de compilación donde el
  modelo cambió: la desincronización se detecta al compilar, no en producción.

**En contra**

- Hay que escribir consultas a mano; no hay `findAll` mágico. Es deliberado.
- Los *joins* de módulos muy conectados ([[usuario]] tiene 195 FK entrantes) son
  verbosos: se encapsulan en vistas o en funciones de consulta con nombre.

## Reglas de uso

| Regla | Cómo se ve |
| --- | --- |
| El repositorio recibe la transacción, no la crea | `constructor(private readonly tx: Trx)` |
| Nada de lógica de negocio en el repositorio | Si hay un `if` sobre una regla, va al dominio |
| Nunca `UPDATE` sobre tabla append-only | Corrección = movimiento inverso |
| Nunca `SELECT *` en flujos de dinero | Las columnas se listan: un `ALTER` no debe cambiar el resultado en silencio |
| Toda escritura con clave de idempotencia la valida antes | Ver [[Flujo de una transacción]] |

## Cómo se verifica

- [ ] `kysely-codegen` corre en el CI **después** de aplicar `sql/`; si el diff no es
      vacío, el build falla.
- [ ] Ninguna dependencia de ORM en `package.json`.
- [ ] Ningún archivo de migración escrito a mano fuera de `sql/`.

## Ver también

[[ADR-001 Lenguaje y runtime]] · [[ADR-005 Dinero y decimales]] · [[ADR-007 Sesión, RLS y pooling]] · [[Restricciones]]
