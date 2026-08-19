---
tags:
  - arquitectura
  - adr
titulo: "ADR-016 — Acceso a datos: jOOQ generado desde la base viva"
estado: aceptada
fecha: 2026-08-16
---

# ADR-016 — Acceso a datos con jOOQ

> Supera a [[ADR-002 Acceso a datos]], que eligió Kysely con tipos introspectados.
> La forma de la decisión no cambia; cambia el lenguaje que la implementa.

## Contexto

El esquema es **generado y es dueño**: `scripts/generar_ddl.py` produce 307 tablas
y 633 claves foráneas desde los `.puml` de la bóveda. El acceso a datos tiene que
leer ese esquema, no proponerlo. Además hay que sostener cuatro cosas que la mayoría
de los ORM entorpecen: `SET LOCAL` en la misma conexión de la transacción,
`numeric(14,2)` sin pasar por punto flotante, tablas append-only donde `UPDATE` está
revocado, y `EXCLUDE` con `btree_gist` que ningún generador de esquema entiende.

Con catorce servicios aparece un requisito nuevo: **cada servicio genera solo el
código de su propio esquema** ([[ADR-017 Propiedad de datos por servicio]]). Un
artefacto único con las 307 tablas volvería a acoplar los catorce proyectos al mismo
archivo generado, que es exactamente lo que la partición evita.

## Decisión

**jOOQ, con el código generado por introspección de la base viva, un artefacto de
generación por servicio limitado a su esquema. JPA/Hibernate queda prohibido.**

- La generación corre contra una PostgreSQL efímera a la que se le aplicó
  `sql/aplicar.sql`, con `includes` restringido al esquema del servicio.
- Salida a `build/generated/jooq/` — **artefacto de compilación, no versionado**.
  Con catorce servicios, versionar el generado multiplicaría por catorce el diff
  que hoy protege el invariante 1; en su lugar el CI regenera y compila, y **la
  compilación es el gate**: si el esquema cambió y el código no, no compila.
- `DSLContext` inyectado; el repositorio recibe el `Configuration` de la
  transacción en curso, nunca abre la suya.
- Migraciones aplicando los artefactos de `sql/` en orden, sin herramienta de
  migración: `psql -v ON_ERROR_STOP=1 -f sql/aplicar.sql` como `Job` de despliegue.

> [!warning] Corregido por [[ADR-032 Aplicación del esquema]]
> Este ADR decía **Flyway**. ADR-032 lo descartó: `sql/` es **generado**, y Flyway
> asume archivos inmutables con checksum, así que cada regeneración rompería el
> historial. Vale ADR-032, no esta línea.

### Lo que queda explícitamente prohibido

| Prohibido | Por qué |
| --- | --- |
| **JPA / Hibernate**, en cualquier servicio | El *dirty checking* escribe por su cuenta sobre entidades leídas: es incompatible con append-only. Y `hbm2ddl` compite con `sql/` por la propiedad del esquema |
| `Flyway.migrate()` con SQL escrito a mano fuera de `sql/` | Dos fuentes de esquema |
| `spring.jpa.*` en cualquier `application.yml` | No hay JPA que configurar; su presencia es el síntoma de que alguien lo agregó |
| Abrir transacción dentro de un repositorio | Rompe el invariante 2 |
| Leer `numeric` como `double` o `float` | Invariante 4 |

## Motivo

**jOOQ es a Java lo que Kysely era a TypeScript**, y por las mismas razones: es un
constructor de consultas tipado que se genera **desde** el esquema, deja el SQL a la
vista —importante cuando una consulta tiene que ser auditable— y no pretende
administrar la base. La decisión de [[ADR-002 Acceso a datos]] se conserva en su
sustancia; solo cambia la herramienta que la encarna.

**Además sabe cosas que Kysely no sabía.** jOOQ modela `EXCLUDE`, tipos compuestos,
funciones de ventana y CTE recursivas de PostgreSQL de primera mano, que es lo que
las consultas de cuadre y de conciliación necesitan.

**No versionar el generado es un cambio consciente.** El invariante 1 se verificaba
con `git diff --exit-code` sobre los tipos generados. Con catorce servicios ese
archivo se vuelve un punto de conflicto entre carriles y un diff ilegible. La
propiedad que hay que preservar no es «el generado está en git» sino «el código no
puede divergir del esquema», y eso lo garantiza mejor una compilación que falla que
un diff que alguien regenera para que pase.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **JPA / Hibernate** | La opción por omisión de Spring, y la peor acá. Dirty checking contra append-only, `hbm2ddl` contra `sql/`, y consultas generadas que nadie puede auditar. Prohibido explícitamente, no solo descartado. |
| **Spring Data JDBC** | Más liviano que JPA y sin dirty checking, pero su modelo de agregados asume que él define el esquema y no tipa las consultas complejas. Sirve para un CRUD, no para consultas de cuadre. |
| **JdbcTemplate a secas** | Sin tipos: cada nombre de columna es un string, y con 307 tablas eso es una fuente permanente de error que el compilador podría atrapar. |
| **MyBatis** | SQL en XML, tipado parcial y una capa de configuración que no compra nada frente a jOOQ. |
| **Versionar el código generado** | Preserva el gate por diff y crea un conflicto de merge por cada carril que toque el esquema. El gate por compilación es igual de fuerte y no tiene ese costo. |

## Consecuencias

**A favor**

- Consultas tipadas contra el esquema real, y SQL legible en la revisión.
- `EXCLUDE`, CTE y ventanas sin bajar a strings.
- La generación por esquema mantiene a cada servicio ignorante de las tablas
  ajenas: no puede consultarlas ni por accidente, porque no existen en su código.

**En contra, y hay que asumirlo**

- **La compilación depende de una base.** El primer `./gradlew build` en una
  máquina nueva levanta PostgreSQL en Docker y aplica el esquema. Se documenta en
  el arranque de máquina y se cachea el generado entre corridas.
- jOOQ es de pago para bases comerciales; con PostgreSQL la edición libre alcanza
  y es la que se usa.
- Un cambio de esquema obliga a recompilar los catorce servicios, y algunos van a
  romper. Eso es correcto: es información que un diff silencioso escondería.

## Cómo se verifica

- [ ] `./gradlew :servicios:<x>:generateJooq` no produce clases de tablas que no
      pertenezcan al esquema de `<x>`.
- [ ] Ningún `build.gradle.kts` declara una dependencia `spring-boot-starter-data-jpa`.
- [ ] Ningún `application.yml` contiene una clave `spring.jpa`.
- [ ] Cambiar una columna en la bóveda y no regenerar **rompe la compilación** del
      servicio dueño.
- [ ] El código generado no está versionado: `git status` limpio tras generar.

## Ver también

[[ADR-002 Acceso a datos]] · [[ADR-015 Lenguaje, runtime y framework]] · [[ADR-017 Propiedad de datos por servicio]] · [[ADR-019 Dinero con BigDecimal]] · [[ADR-021 Sesión, RLS y pooling]] · [[Restricciones]] · [[_Arquitectura]]
