---
tags:
  - plan
  - informe
  - carril
titulo: "Carril 0T — cimientos del repositorio"
ola: 0
fase: 0
modulo: troncal
rama: pablo/feature/carril-0T-cimientos
estado: en curso
---

# Carril 0T — cimientos del repositorio

**Fase** 0 · **Casos de uso** ninguno (esta fase construye el piso) · **Máquina** Mac M5

> Tramo **T0** de [[17 Plan de acción secuencial · coordinación de cinco máquinas]] §5.
> Es **bloqueante**: ningún otro carril trabaja hasta que su gate esté ejecutado.

## Qué está hecho, con la salida que lo prueba

| § | Entregable | Evidencia | Estado |
| :-: | --- | --- | :-: |
| 0.1 | Monorepo Gradle, `plataforma/` y los catorce servicios compilando | `./gradlew build` → BUILD SUCCESSFUL, 228 tareas | ✅ |
| 0.2 | Esquemas, roles y permisos | ya venía hecho · `AislamientoEsquemaTest` escrito, **sin correr** (ver Bloqueos) | 🟡 |
| 0.3 | Docker: base, pooler, cola y entrada | `bd:reset` → BUILD SUCCESSFUL · `prueba_humo.sql` **42 OK, 0 FALLA** | ✅ |
| 0.4 | Los tres generadores, envueltos en Gradle | `nuevoServicio`, `nuevoCu`, `verificarCriterios` · los catorce servicios regenerados sin edición manual | ✅ |
| 0.5 | Cinco reglas propias con su prueba | `./gradlew check` → SUCCESSFUL · 4 pruebas de las reglas + ArchUnit por servicio | ✅ |
| 0.6 | Los cinco corredores | `test` · `integrationTest` · `contractTest` · `sagaTest` · `e2eTest` + `testBarrido` | 🟡 |
| 0.7 | `plataforma/` y el gateway | `curl -f http://localhost/actuator/health` → **200** a través de NGINX, sin puerto publicado | ✅ |
| 0.8 | Los tres contratos que desbloquean la Ola 1 | `generateOpenApiClients` → `clientes/typescript/{identidad,nucleo-financiero,notificaciones}` | ✅ |
| 0.9 | CI con los 19 pasos | `.github/workflows/ci.yml` escrito; **no ejecutado** (corre al abrir el PR) | 🟡 |

**Además:** `./gradlew generateJooq compileJava` en verde sobre los catorce esquemas
—el invariante 1 funcionando— y `bd:reset` reproducible desde volumen limpio.

## Decisiones tomadas, y por qué

| Decisión | Por qué | Dónde |
| --- | --- | --- |
| Proyecto `bd/` en la raíz | `planes/01` §0.1 exige el comando `./gradlew bd:reset`; con esa sintaxis tiene que ser un subproyecto. No tiene código | `bd/build.gradle.kts` |
| `psql` corre **dentro** del contenedor, con el repo montado en `/repo:ro` | Tres de las cinco máquinas del parque no tienen `psql` instalado, y este Mac tampoco | `bd/build.gradle.kts` |
| La base se llama `pasanaku` | Es el nombre que ya usan todas las cabeceras generadas por `generar_semillas.py` y la guarda de `sembrar_dev.sql` | `despliegue/compose/base.yml` |
| PostgreSQL publica `127.0.0.1:5433` | `generateJooq` introspecciona la base **viva** desde el build, que corre fuera de Docker. Loopback y puerto no estándar. **Los servicios de aplicación siguen sin publicar puerto**: la única entrada pública es NGINX | `despliegue/compose/base.yml` |
| El `gateway` va en el perfil `base` | NGINX sin gateway no lleva a ningún lado, y un carril que prueba su servicio a través de la entrada pública necesita los dos | `despliegue/compose/base.yml` |
| `gradle/gradle-daemon-jvm.properties` con `toolchainVersion=21` | El daemon corre en 21 en las cinco máquinas tengan el `JAVA_HOME` que tengan. Este Mac tenía JDK 26 | `gradle/` |
| jOOQ generador y librería **misma versión** (3.20.5), sobrescribiendo la propiedad del BOM | El código generado por 3.20 llama a métodos que el runtime 3.19 del BOM no tiene: 100 errores de compilación en clases que nadie escribió | `aportaya.libreria` · `aportaya.servicio` |
| `deprecationOnUnknownTypes=false` en el generador de jOOQ | Una columna `inet` sale marcada obsoleta y `-Werror` rompe. **No baja el gate**: `-Werror` sigue vigente sobre el código que sí se escribe | `aportaya.jooq` |
| `archRule.failOnEmptyShould=false` | Las cuatro capas están vacías a propósito en la Fase 0. Sin esto la única salida práctica sería `@Disabled` en la prueba de arquitectura | `comun-pruebas/src/main/resources` |

## Correcciones al troncal encontradas al ejecutar

1. **`scripts/nuevo_servicio.py` no generaba la clase de arranque.** Un servicio Spring
   Boot sin su `@SpringBootApplication` no empaqueta: `bootJar` falla en los catorce.
   Ahora emite `Aplicacion.java`.
2. **La regla `ningunImportCruzado` de `ArquitecturaTest` estaba mal escrita.** Los dos
   `should()` encadenados se evalúan como AND sobre dependencias distintas, así que
   `java.lang.Object` alcanzaba para que cualquier clase que dependiera de
   `plataforma/` violara la regla. Pasaba solo porque ninguna clase dependía de nada
   todavía. Ahora el predicado va compuesto: `resideInAPackage("bo.aportaya..")` **y**
   `resideOutsideOfPackages(propio, plataforma)`.
3. **El paso 12 del CI comparaba un diff sobre `clientes/typescript`**, que está en
   `.gitignore`: la comprobación era vacía. Ahora verifica que el cliente se genere y
   no salga vacío.

## Supuestos declarados

Regla cero: ninguno silencioso.

1. **Versiones del catálogo.** No están en la bóveda; se eligieron y se verificaron
   resolviendo: Spring Boot 3.5.6, jOOQ 3.20.5, Gradle 9.7.1, ShedLock 6.9.0,
   Resilience4j 2.3.0, ArchUnit 1.4.0, jqwik 1.9.2.
2. **`clientes/typescript` no se versiona.** Lo dice `Estructura del repositorio`
   («código generado versionado» está en «qué no va»), y ya estaba en `.gitignore`.
3. **El paquete de `plataforma/` es `bo.aportaya.plataforma.<módulo>`.** Lo impone la
   regla `ningunImportCruzado`, que solo admite el propio servicio y
   `bo.aportaya.plataforma..`.

## Huecos encontrados (no completados con una suposición)

| Hueco | Dónde | Por qué importa |
| --- | --- | --- |
| Los roles `svc_*` se crean **`NOLOGIN`** en `sql/00_base/02_esquemas.sql`, pero cada `application.yml` se conecta con `username: svc_<servicio>` | `sql/00_base/02_esquemas.sql` vs. `servicios/*/src/main/resources/application.yml` | O el despliegue les da `LOGIN`, o hay un rol de login que hace `SET ROLE`. Son dos arquitecturas de sesión distintas y afectan a RLS. **No lo resolví**: `sql/` es troncal y el cambio no es de carril. `AislamientoEsquemaTest` usa `SET ROLE`, que prueba los `GRANT` y no la conectividad |

## Bloqueos

| Qué | Desde | Detalle |
| --- | --- | --- |
| **Testcontainers no encuentra Docker en este Mac** | hoy | Docker Engine **29.6.2** (API 1.55). `docker-java`, el cliente que trae Testcontainers 1.21.3, recibe `400` de `/info` por las tres estrategias (env, socket unix, Docker Desktop). El socket responde bien por `curl` con `v1.41`. Probado sin efecto: `DOCKER_HOST` explícito, `~/.testcontainers.properties`, `DOCKER_API_VERSION=1.44`. **Consecuencia:** `integrationTest` no corre en el Mac, y con él `AislamientoEsquemaTest` (invariantes 11 y 12) queda escrito y sin ejecutar |

> Es exactamente lo que `planes/17` §5 previó para **P2 Ubuntu**: el gate se verifica
> en dos máquinas, no en una. Ubuntu tiene Docker nativo y una versión de motor más
> conservadora. Si allí pasa, el hallazgo es del entorno del Mac y no del código.

## Gate de salida de la Fase 0 — evidencia

- [x] `./gradlew spotlessCheck check` — BUILD SUCCESSFUL, 244 tareas
- [x] `docker compose --profile base up -d --wait` — postgres, pgbouncer, kafka, gateway y nginx **healthy**
- [x] `./gradlew bd:reset` — BUILD SUCCESSFUL
- [x] `prueba_humo.sql` — **42 líneas OK, 0 FALLA** sobre base recién creada
- [x] `./gradlew generateJooq compileJava` — BUILD SUCCESSFUL sobre los catorce esquemas
- [x] `./gradlew generateOpenApiClients` — los tres clientes TypeScript generados
- [x] `curl -f http://localhost/actuator/health` — **200** a través de NGINX
- [x] Ningún `build.gradle.kts` declara JPA · la tarea `sinJpa` está en `check`
- [ ] `./gradlew test integrationTest` — `test` en verde; `integrationTest` **bloqueado** (ver Bloqueos)
- [ ] `./gradlew e2eTest` — pendiente: no hay perfil `todo` todavía (Fase 17)
- [ ] `nuevoCu` probado punta a punta contra un CU real — pendiente
- [ ] `erroresCatalogo` — la tarea todavía no existe
- [ ] `python3 scripts/verificar_boveda.py` — pendiente de correr en esta rama
- [ ] `apps/backoffice` compila importando el cliente — **no es de este carril**: es la Ola F0 (P4 Dell A, Delta 2)

### Frases prohibidas sin evidencia

No se dice «está listo». Lo que hay es: **228 tareas de Gradle en verde, 42 líneas OK
y 0 FALLA en la prueba de humo, 200 por NGINX, tres clientes TypeScript generados, y
un corredor de integración bloqueado por el entorno del Mac.**

## Ver también

[[01 Fase 0 · Cimientos del repositorio]] · [[17 Plan de acción secuencial · coordinación de cinco máquinas]] · [[00 Plan maestro]] · [[Estructura del repositorio]]
