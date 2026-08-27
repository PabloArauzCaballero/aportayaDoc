---
tags:
  - plan
  - fase
titulo: "Fase 0 — Cimientos del repositorio"
fase: 0
depende_de: []
habilita: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17]
---

# Fase 0 — Cimientos del repositorio

> **Objetivo.** Que `git clone && docker compose --profile base up -d && ./gradlew
> bd:reset && ./gradlew :servicios:ejemplo:bootRun` deje corriendo un servicio Spring
> Boot que responde `/actuator/health`, contra una PostgreSQL 16 con las 305 tablas
> aplicadas, los catorce esquemas creados y los 20 catálogos mínimos sembrados. Sin un
> solo caso de uso todavía: esta fase construye el piso sobre el que se paran las
> otras 17.

> **Se ejecuta en:** Ola 0 · carril T (troncal, máquina única). **Ningún otro carril
> trabaja hasta que su gate esté ejecutado.** Ver [[07 Carriles de trabajo concurrente]].

> [!important] Antes de escribir la primera línea
> [[00b Estándar de ejecución · código limpio, pruebas y calidad]] aplica en esta fase
> entera: regla cero de no inventar, composición atómica, KISS, nombres del dominio,
> las siete pruebas obligatorias por caso de uso y el checklist de PR. **Se declara
> cada pieza por nivel antes de crearla.**

> **Receta exacta:** [[00c Recetario · implementar un caso de uso]] fija el orden de
> lectura, el orden de construcción, las firmas canónicas y los nombres de las piezas
> de `plataforma/`. **Se copian, no se reinventan.**

**Nada de lógica de negocio en esta fase.** Si aparece un `if` sobre una regla del
pasanaku, está mal ubicado.

## Gate de entrada

- [ ] `psql -v ON_ERROR_STOP=1 -f sql/aplicar.sql` corre sin error sobre una base vacía
- [ ] `python3 scripts/verificar_boveda.py` en verde
- [ ] **JDK 21**, Docker y `docker compose` disponibles en la máquina
- [ ] Node 22 disponible **solo** para `apps/movil` y `apps/backoffice`; el backend no
      lo usa

## Leer antes de empezar

| Archivo | Qué se saca de ahí |
| --- | --- |
| `docs/Arquitectura/Estructura del repositorio.md` | El árbol de carpetas, tal cual |
| `docs/Arquitectura/ADR-014 Arquitectura de servicios.md` | Cuáles son los catorce y dónde está el límite de cada uno |
| `docs/Arquitectura/ADR-017 Propiedad de datos por servicio.md` | Esquemas, roles y la excepción del libro contable |
| `docs/Arquitectura/ADR-025 Empaquetado y despliegue de los servicios.md` | Dockerfile, compose por perfiles, NGINX |
| `docs/Arquitectura/Entornos y despliegue.md` | Entornos y aplicación de `sql/` |
| `docs/Stack.md` | Las ocho exigencias que el stack tiene que sostener |
| `seeders/README.md` | Por qué los mínimos también van a producción |

---

## 0.1 · Monorepo Gradle multiproyecto

### Árbol a crear

```
Pasanaku/
├── settings.gradle.kts          descubre servicios/ por BARRIDO — no se edita al agregar uno
├── build.gradle.kts             convenciones comunes: toolchain 21, spotless, test
├── gradle/libs.versions.toml    catálogo de versiones — micro-PR
├── buildSrc/                    plugins de convención: servicio, jooq, openapi
├── .editorconfig · .gitignore · .dockerignore
├── plataforma/
│   ├── comun-dominio/           Dinero, Periodo, PlazoHabil — sin Spring
│   ├── comun-datos/             conContexto(), SET LOCAL, DataSource, jOOQ base
│   ├── comun-web/               manejador de errores, idempotencia, guardia, traza
│   ├── comun-mensajeria/        outbox, relevo a Kafka, consumidor idempotente
│   ├── comun-pruebas/           Testcontainers, fixtures, ArchUnit, barridos
│   └── gateway/                 Spring Cloud Gateway
├── servicios/                   ← vacío en la Fase 0, salvo el de ejemplo
├── clientes/typescript/         generado — no se edita
├── apps/movil · apps/backoffice
├── despliegue/
│   ├── Dockerfile               plantilla ÚNICA, parametrizada por servicio
│   ├── compose/base.yml         postgres, pgbouncer, kafka
│   ├── compose/<servicio>.yml   uno por servicio, propiedad del carril
│   └── k8s/                     GENERADO desde descriptor.yml
├── planes/                      este plan
│   └── informes/                un informe por carril
├── sql/ · seeders/ · scripts/ · docs/     (ya existen — no se tocan)
└── .github/workflows/ci.yml
```

> **`settings.gradle.kts` descubre por barrido.** Es la diferencia entre un archivo
> que catorce carriles editan y uno que nadie vuelve a tocar. Agregar un servicio es
> crear una carpeta.

### Tareas de Gradle obligatorias

| Tarea | Qué hace |
| --- | --- |
| `./gradlew bd:levantar` | `docker compose --profile base up -d --wait` |
| `./gradlew bd:aplicar` | `psql -v ON_ERROR_STOP=1 -f sql/aplicar.sql` |
| `./gradlew bd:semillas` | `python3 scripts/generar_semillas.py` + `sembrar.sql` (**20 catálogos**) |
| `./gradlew bd:dev` | `sembrar_dev.sql` (**15 archivos, nunca** en producción) |
| `./gradlew bd:reset` | volumen limpio → esquemas y roles → aplicar → semillas → prueba |
| `./gradlew generateJooq` | genera las clases desde la base viva, **por esquema** |
| `./gradlew generateOpenApiClients` | interfaces de servidor + cliente Java + cliente TypeScript |
| `./gradlew nuevoServicio -Pnombre=<x>` | crea el servicio entero: las 4 capas, build, `application.yml`, `openapi/`, `descriptor.yml`, README, fixtures |
| `./gradlew nuevoCu -Pcu=<NN>` | desde `docs/CasosDeUso/CU-<NN>*.md`: contrato, esqueleto, controlador y **las pruebas fallando** |
| `./gradlew test integrationTest contractTest sagaTest e2eTest` | los cinco corredores |
| `./gradlew erroresCatalogo` | genera `constraint_name → R-XXX-nn` desde `docs/Restricciones.md` |
| `./gradlew verificar` | spotless + check + los corredores + los diffs de generados |

> **Regla:** todo lo que el CI ejecuta tiene que poder ejecutarse igual en local con
> una sola tarea. Un paso de CI que no existe como tarea es un paso que nadie puede
> reproducir.

> [!warning] Todas las dependencias comunes se declaran **acá**
> Los carriles concurrentes **no agregan dependencias**: una versión nueva en una rama
> de carril produce conflicto en el catálogo con las otras cuatro máquinas. Se declara
> ahora todo lo que las 21 fases van a necesitar (Spring Boot, jOOQ, Kafka,
> ShedLock, Resilience4j, Micrometer, JUnit 5, Testcontainers, Spring Cloud Contract,
> ArchUnit, AssertJ, jqwik, Argon2). Lo que falte después entra por micro-PR
> ([[07 Carriles de trabajo concurrente]] §6).

### Convenciones de compilación — no negociables

`toolchain = 21`, `-Werror` con las advertencias de compilación activadas,
`spring.threads.virtual.enabled=true`, Spotless con formato único, y los plugins de
convención en `buildSrc/` para que un servicio nuevo herede todo sin copiar nada.

**Entregable 0.1:** `./gradlew build` en verde con `plataforma/` y un servicio de
ejemplo vacíos pero compilando.

---

## 0.2 · Esquemas, roles y permisos — **ya implementado**

Es lo que hace cumplir los invariantes 11 y 12. **Está hecho y verificado**: lo
genera `scripts/generar_ddl.py` desde `scripts/modelo.py`, y
`scripts/verificar_boveda.py` lo comprueba en cada corrida.

| Paso | Qué hace | Estado |
| --- | --- | :-: |
| 1 | La **asignación de esquema** sale del `.puml` del módulo, sin decisión humana | ✅ |
| 2 | **16 esquemas**: los catorce de servicio, más `catalogo` y `comun` | ✅ |
| 3 | Un rol `svc_<esquema>` por servicio, y su `search_path` propio | ✅ |
| 4 | `GRANT` solo sobre el esquema propio, `SELECT` sobre `catalogo`, `INSERT` sobre `comun` | ✅ |
| 5 | `REVOKE UPDATE, DELETE` append-only, **también al rol dueño** | ✅ |
| 6 | Las cuatro tablas del libro en `nucleo_financiero`, no en `aportes` | ✅ |
| 7 | El outbox y las bitácoras en `comun`: todos insertan, **nadie lee lo ajeno** | ✅ |

Lo que la corrida deja verificado hoy:

```
305 tablas con esquema asignado (modelo: 305)
el libro contable entero en nucleo_financiero
movimiento_billetera con el libro: partida doble en una transaccion
325 claves foraneas cruzan esquemas y las verifica el motor
14 roles de servicio creados · ningun servicio LEE el rastro ajeno
```

> **Las 325 FK cruzadas son el argumento entero a favor de un solo clúster.** Son
> exactamente las que se habrían perdido con una base por servicio, y las sigue
> verificando el motor.

> **La excepción del libro está enumerada a propósito.** Una excepción con nombre y
> motivo es preferible a una regla que se cumple a medias. Está en
> [[ADR-017 Propiedad de datos por servicio]].

**Entregable 0.2:** una prueba por par de servicios que comprueba que el `SELECT`
cruzado devuelve **permiso denegado**, y una que comprueba que solo
`svc_nucleo_financiero` escribe `asiento_contable`.

---

## 0.3 · Docker: la base, el pooler, la cola y la entrada

### `despliegue/compose/base.yml`

| Servicio | Imagen | Puerto expuesto | Notas |
| --- | --- | :-: | --- |
| `postgres` | `postgres:16` | **ninguno** (red interna) | `init.sql` crea `btree_gist`, `pgcrypto`; volumen `datos_pg` |
| `pgbouncer` | `edoburu/pgbouncer` | ninguno | `pool_mode = transaction` |
| `kafka` | `confluentinc/cp-kafka` | ninguno | modo KRaft, sin ZooKeeper |
| `nginx` | `nginx:alpine` | `80`, `443` | **única entrada pública** → gateway |

**Los perfiles son el punto de esta sección:** `base` (lo de arriba), `<servicio>`
(uno), `dinero` (los necesarios para un flujo de dinero completo) y `todo`. Una
máquina de carril levanta `base` y **su** servicio. Si trabajar exigiera quince
contenedores, esta arquitectura costaría más de lo que rinde.

### Dockerfile — uno solo, plantilla

```
FROM eclipse-temurin:21-jdk AS construccion  → ./gradlew :servicios:${SERVICIO}:bootJar
FROM eclipse-temurin:21-jre-alpine           → USER app (no root), capas de Spring Boot,
                                               HEALTHCHECK a /actuator/health/readiness
```

**Reglas de [[ADR-025 Empaquetado y despliegue de los servicios]]:** sin root, sin
`latest`, sin secretos en la imagen, **capas de Spring Boot** separadas para que un
cambio de código no empuje 200 MB, y `.dockerignore` que excluye `docs/`, `planes/`,
pruebas y `.git`.

> **Un `Dockerfile` y no catorce.** Es la única excepción a «el servicio posee todos
> sus archivos», y está justificada: no tiene contenido propio del servicio.

**Entregable 0.3:** `docker compose --profile base up -d` levanta la infraestructura;
`./gradlew bd:reset` deja la base cargada; `verificaciones.sql` y `prueba_humo.sql` en
verde.

---

## 0.4 · Los tres generadores

> [!note] Hoy son scripts de Python; la tarea de Gradle los envuelve
> Los tres generadores **ya existen y funcionan** como `scripts/nuevo_servicio.py`,
> `scripts/nuevo_cu.py` y `scripts/verificar_criterios.py` — no dependen de que el
> monorepo Gradle esté montado, que es justo lo que hace falta para arrancar la
> Fase 0. La tarea `./gradlew nuevoServicio` es un envoltorio de una línea sobre el
> script, y se agrega con las convenciones de `buildSrc/`.
>
> Se hicieron en Python a propósito: es donde ya viven `generar_ddl.py` y
> `verificar_boveda.py`, leen la misma `scripts/modelo.py` y por lo tanto **no
> pueden divergir del modelo**.

**El código más limpio es el que nadie escribió dos veces de dos maneras.** Con
catorce servicios esto pasa de conveniente a indispensable.

### `./gradlew nuevoServicio -Pnombre=<x>` · hoy `python3 scripts/nuevo_servicio.py <x>`

Crea el servicio entero: las cuatro capas, `build.gradle.kts` con los plugins de
convención, `application.yml` con la configuración validada, `openapi/<x>.yaml`
esqueleto, `descriptor.yml`, el registro de métricas con el prefijo correcto, el
`README.md` con su tabla de CU vacía, el directorio de fixtures y la clase de ArchUnit.

**Nadie escribe la estructura de un servicio a mano.** Es la diferencia entre catorce
servicios iguales y catorce servicios parecidos.

### `./gradlew nuevoCu -Pcu=<NN>` · hoy `python3 scripts/nuevo_cu.py <NN>`

Lee `docs/CasosDeUso/CU-<NN> *.md` y genera:

| Genera | Desde |
| --- | --- |
| La operación en el `openapi/<servicio>.yaml`, con entrada, salida y códigos de error | sección **Contrato** |
| El esqueleto de `aplicacion/` con `@Transactional` y `conContexto` ya puestos | sección **Descomposición atómica** |
| El controlador que implementa la interfaz generada, con su permiso declarado | sección **Eventos, trabajos y permisos** |
| **Una prueba por cada bloque `gherkin`, con el mismo nombre, fallando** | sección **Criterios de aceptación** |
| **Una prueba de rechazo por cada `R-XXX-nn` citada, fallando** | sección **Restricciones aplicables** |

> **Las pruebas nacen fallando, y eso es el punto.** Un criterio de aceptación
> olvidado no es una prueba ausente que nadie nota: es **el build en rojo**.

### `python3 scripts/verificar_criterios.py`

Extiende `verificar_boveda.py`. Compara, para cada CU, los bloques `gherkin` de la
bóveda contra las pruebas del archivo, y falla si hay un criterio sin prueba, una
prueba que no corresponde a ningún criterio, o un `R-XXX-nn` citado sin prueba de
rechazo.

**Entregable 0.4:** los tres generadores funcionando, y el servicio de ejemplo
producido por `nuevoServicio` sin edición manual.

---

## 0.5 · Análisis estático y las reglas propias

Las doce reglas de §6 del [[00 Plan maestro]], implementadas como reglas de análisis
estático y pruebas de ArchUnit en `plataforma/comun-pruebas`. **Ningún servicio puede
desactivarlas.**

En esta fase se implementan **cinco** (las que no dependen de código que aún no
existe); las siete restantes se activan en las Fases 1 y 2:

| Ahora (Fase 0) | Después |
| --- | --- |
| `capas` (ArchUnit) | `sin-punto-flotante-monetario` → Fase 1 |
| `sin-import-cruzado` (ArchUnit) | `sin-equals-bigdecimal` → Fase 1 |
| `tamano-archivo` | `sin-update-append-only` → Fase 1 |
| `sin-umbral-literal` | `consulta-en-contexto` → Fase 2 |
| `sin-jpa` | `transaccion-solo-en-organismo` → Fase 2 |
| | `sin-red-en-transaccion` → Fase 2 |
| | `sin-system-out` → Fase 2 |

> **`sin-jpa` se implementa ya, en la Fase 0.** Es la regla que protege el invariante
> 1, y la tentación de agregar `spring-boot-starter-data-jpa` aparece el primer día.

Cada regla propia lleva su propia prueba: una regla sin prueba se desactiva sola en el
primer refactor.

**Entregable 0.5:** `./gradlew check` en verde; las cinco reglas con su prueba pasando.

---

## 0.6 · Los cinco corredores de pruebas

| Corredor | Qué corre | Preparación | Timeout |
| --- | --- | --- | :-: |
| `test` | `*Test` de `dominio/` | ninguna | 5 s |
| `integrationTest` | `CU*Test`, `*RepositorioTest` | Testcontainers: PostgreSQL 16, `sql/aplicar.sql` + semillas mínimas | 120 s |
| `contractTest` | `*ContratoTest` | Spring Cloud Contract, por par de servicios | 60 s |
| `sagaTest` | `*SagaTest` | Testcontainers + dobles de los servicios participantes | 120 s |
| `e2eTest` | `*E2ETest` | `docker compose --profile todo up -d --wait` | 300 s |

**Detalle que ahorra horas:** el contenedor de PostgreSQL se levanta **una vez** por
corrida y se reutiliza por clase, no por archivo. Cada prueba corre dentro de una
transacción que se revierte al terminar, salvo las de concurrencia, las de trabajos
programados y las de consumidor, que necesitan confirmaciones reales.

**Entregable 0.6:** los cinco corredores configurados; una prueba de humo por corredor
pasando.

---

## 0.7 · `plataforma/` y el gateway

Solo el piso. Sin lógica de negocio.

```
plataforma/comun-datos/     conContexto() con SET LOCAL · DataSource · pool declarado
plataforma/comun-web/       manejador global de errores · idempotencia · guardia · traza
plataforma/comun-mensajeria/ outbox · relevo a Kafka · consumidor idempotente
plataforma/gateway/         enrutado por prefijo · TLS · límite de tasa · x-request-id
```

- `/actuator/health/liveness` responde sin tocar la base.
- `/actuator/health/readiness` verifica base **y** Kafka. Si alguno no responde, `503`.
- **Apagado controlado**: `SIGTERM` → deja de aceptar peticiones → termina la que está
  en curso → cierra el pool y el consumidor.
- Si falta una clave de configuración, el proceso **no levanta** y lo dice con el
  nombre de la clave.
- **El gateway no tiene lógica de negocio.** No compone respuestas, no traduce errores
  y no consulta la base. Un gateway con lógica es el monolito volviendo por la puerta
  de atrás, y además compartido por catorce carriles.

**Entregable 0.7:** `curl localhost/actuator/health` responde `200` a través de NGINX,
con el servicio sin puerto publicado.

---

## 0.8 · Los tres contratos que desbloquean la Ola 1

> **Reemplaza al «0.6b» del plan anterior**, que adelantaba `packages/contratos` con
> `CU-01` para desbloquear el frontend. La necesidad es la misma; el artefacto cambia.

Se escriben **los borradores de OpenAPI** de los tres servicios que todos consumen,
derivados de la sección **Contrato** de sus casos de uso:

| Contrato | Por qué acá | Quién lo consume |
| --- | --- | --- |
| `openapi/identidad.yaml` | Emite el token: sin él nadie autentica | Los trece, y los dos frontends |
| `openapi/nucleo-financiero.yaml` | Es el único que escribe dinero | `tarifas`, `aportes`, `entregas`, `garantia` |
| `openapi/notificaciones.yaml` | Todos emiten avisos | Todos |

- **Son borradores, no implementaciones.** El carril dueño los amplía; **no puede
  romperlos** sin avisar, y la prueba de contrato falla en el CI del que rompe.
- Con estos tres publicados, la Ola 1 arranca sin esperar a nadie, y el frontend
  genera su cliente TypeScript.

**Entregable 0.8:** `./gradlew generateOpenApiClients` produce el cliente TypeScript, y
`apps/backoffice` compila importándolo. Es lo que desbloquea la Fase F0 del frontend.

---

## 0.9 · CI

`.github/workflows/ci.yml` con los 19 pasos de §6 del [[00 Plan maestro]], en ese orden
y todos bloqueantes. En la Fase 0 varios corren sobre lo que existe (poco) pero
**tienen que existir en el archivo desde ahora**: un paso que se agrega "después" nunca
se agrega.

**El CI construye solo los servicios afectados** por el cambio, más los que dependen de
`plataforma/`. Un cambio en plataforma construye los catorce: es correcto, es lento, y
es la razón de que plataforma se congele acá.

Además: escaneo de secretos en cada push, y verificación de que el catálogo de
versiones no cambió en una rama de carril.

**Entregable 0.9:** CI en verde sobre la rama de la fase.

---

## Gate de salida de la Fase 0

Ejecutar, no suponer:

```bash
./gradlew spotlessCheck check
docker compose --profile base up -d --wait
./gradlew bd:reset                          # esquemas + roles + aplicar + semillas + prueba
psql -f sql/50_verificacion/verificaciones.sql
psql -f sql/50_verificacion/prueba_humo.sql        # todo OK, cero FALLA
./gradlew generateJooq compileJava
./gradlew generateOpenApiClients
./gradlew test integrationTest
./gradlew nuevoServicio -Pnombre=ejemplo && ./gradlew :servicios:ejemplo:build
curl -f http://localhost/actuator/health
./gradlew e2eTest
python3 scripts/verificar_boveda.py
```

- [ ] Los trece puntos del gate común (§9 del [[00 Plan maestro]])
- [ ] Las 305 tablas existen y las verificaciones de `sql/50_verificacion/` pasan
- [ ] **Los catorce esquemas y los catorce roles existen**, y el `SELECT` cruzado entre
      cualquier par devuelve permiso denegado ← invariante 11
- [ ] **Solo `svc_nucleo_financiero` escribe `asiento_contable`** ← invariante 12
- [ ] Los **20** catálogos mínimos están sembrados y la licencia figura `EN_TRAMITE`
- [ ] `prueba_humo.sql` da **todo OK, cero FALLA** sobre base recién creada
- [ ] Los tres generadores producen un servicio y un caso de uso sin edición manual
- [ ] **Los tres contratos de 0.8 están publicados** y el cliente TypeScript compila:
      es lo que desbloquea la Ola 1 y la Fase F0 del frontend
- [ ] Ningún servicio publica puerto: solo NGINX
- [ ] Ningún `build.gradle.kts` declara JPA y ningún `application.yml` tiene `spring.jpa`
- [ ] El proceso no levanta si falta una clave de configuración (probado a propósito)
- [ ] La suma de los pools declarados es menor que `max_connections`

## Ver también

[[17 Plan de acción secuencial · coordinación de cinco máquinas]] · [[00c Recetario · implementar un caso de uso]] · [[00b Estándar de ejecución · código limpio, pruebas y calidad]] · [[00 Plan maestro]] · [[02 Fases 1 y 2 · Capa de datos y núcleo transversal]] · [[Estructura del repositorio]] · [[ADR-025 Empaquetado y despliegue de los servicios]] · [[ADR-017 Propiedad de datos por servicio]]
