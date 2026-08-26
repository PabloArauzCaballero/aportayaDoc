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
| 0.1 | Monorepo Gradle, `plataforma/` y los catorce servicios compilando | `./gradlew build` → BUILD SUCCESSFUL | ✅ |
| 0.2 | Esquemas, roles y permisos, **verificados por el motor** | `AislamientoEsquemaTest` en verde: 14 roles × 13 esquemas ajenos ⇒ `permission denied`, y solo `svc_nucleo_financiero` escribe `asiento_contable` | ✅ |
| 0.3 | Docker: base, pooler, cola y entrada | `bd:reset` → BUILD SUCCESSFUL · `prueba_humo.sql` **165 OK, 0 FALLA** | ✅ |
| 0.4 | Los tres generadores, envueltos en Gradle | `nuevoServicio` regenera los catorce sin edición manual · `nuevoCu -Pcu=01` produce 3 criterios y 9 rechazos **fallando** · `verificarCriterios` → «sin divergencias» | ✅ |
| 0.5 | Cinco reglas propias con su prueba | `capas`, `sin-import-cruzado`, `sin-jpa` (ArchUnit × 14) · `tamano-archivo` y `sin-umbral-literal` (barrido, con 4 pruebas propias) | ✅ |
| 0.6 | Los cinco corredores | `test` · `integrationTest` · `contractTest` · `sagaTest` · `e2eTest` + `testBarrido`. Con prueba de humo: los tres primeros y el barrido | 🟡 |
| 0.7 | `plataforma/` y el gateway | `curl -f http://localhost/actuator/health` → **200** a través de NGINX, sin puerto publicado | ✅ |
| 0.8 | Los tres contratos que desbloquean la Ola 1 | `generateOpenApiClients` → tres clientes, y **`tsc -p clientes/tsconfig.json` los compila** · `RutaEnPrefijoContratoTest` verifica que ninguna ruta cae fuera de `PREFIJOS` | ✅ |
| 0.9 | CI con los 19 pasos | `.github/workflows/ci.yml`; **no ejecutado** (corre al abrir el PR) | 🟡 |
| — | `erroresCatalogo` | 189 restricciones → 112 reglas, generado desde `sql/` · `CatalogoDeErroresTest` en verde | ✅ |
| — | Verificadores del repositorio | `verificar_seguridad` TODO OK (2 avisos) · `verificar_carriles` TODO OK · `verificar_criterios` sin divergencias · `generar_k8s` 34 archivos, **690 conexiones de 1000** que acepta PgBouncer | ✅ |

**Además:** `./gradlew generateJooq compileJava` en verde sobre los catorce esquemas
—el invariante 1 funcionando— y `bd:reset` reproducible desde volumen limpio.

### Lo que queda abierto, y de quién es

| Qué | De quién | Cuándo |
| --- | --- | --- |
| `e2eTest` y el perfil `todo` de compose | Ola 5 | Fase 17. Hoy no hay servicio con código que levantar |
| Una prueba de humo de `sagaTest` | este carril, más adelante | Fase 2, cuando exista la primera saga. Escribir una saga falsa para marcar una casilla es bajar el gate |
| `apps/backoffice` compilando el cliente TypeScript | **P4 Dell A** | Ola F0 · Delta 2. No es de este carril |
| `verificar_boveda.py` en verde | **no es de este carril** | Falla por un wikilink roto en `docs/Views/AportaYa-Maqueta.md` (cambio de maqueta sin commitear): apunta a `planes/20`, y `planes/` no es parte de la bóveda |

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
   todavía. Ahora el predicado va compuesto.
3. **`verificar_criterios.py` daba por ausente la prueba que `nuevo_cu.py` acababa de
   generar.** El formateador parte `@DisplayName("texto largo")` en dos líneas y el
   verificador exigía `@DisplayName("` pegado. Los dos gates se contradecían: **le
   habría pasado a los cinco carriles el primer día, en su primer caso de uso.**
   Ahora tolera el salto de línea y los literales concatenados.
4. **La plantilla de `descriptor.yml` era anterior a [[ADR-037 Alta disponibilidad y balanceo]]**
   y no conocía `nivel`. Regenerar los catorce servicios borró la decisión de
   criticidad de los catorce descriptores (244 líneas), y `generar_k8s.py` empezó a
   rechazar con `nivel 'None' no existe`. Restaurados, y el nivel de cada servicio
   vive ahora en una tabla del generador: regenerar ya no lo borra.
5. **El paso 12 del CI comparaba un diff sobre `clientes/typescript`**, que está en
   `.gitignore`: la comprobación era vacía. Ahora genera el cliente **y lo compila**
   con `tsc`, que es lo que el gate pide de verdad.
7. **El cliente TypeScript generado no compilaba.** Los trece modelos importaban
   `mapValues` de un `runtime.ts` que el generador no lo exporta. Se habría entregado
   roto a los tres carriles de frontend, y ninguno de ellos habría podido arreglarlo:
   `clientes/typescript/` es generado. Corregido con `withoutRuntimeChecks`, y el
   `tsc` quedó como paso 12b del CI para que no vuelva a pasar en silencio.
8. **`--forzar` del generador pisaba archivos curados.** Me borró dos veces trabajo
   ya hecho: el nivel de criticidad de los catorce descriptores, y los tres contratos
   de §0.8 (que volvieron a `paths: {}`, con lo cual `generateOpenApiClients` pasó a
   saltarse en silencio y el gate seguía diciendo BUILD SUCCESSFUL). Ahora
   `descriptor.yml`, `README.md` y `openapi/<servicio>.yaml` solo se escriben si
   faltan.
6. **`prueba_humo.sql` no es idempotente.** Dice funcionar «igual con la base recién
   creada o ya sembrada», pero correrlo dos veces seguidas da 27 FALLA por clave
   duplicada de sus propios datos de prueba. No afecta al gate —`bd:reset` recrea el
   volumen— pero un carril que corra `bd:humo` dos veces va a creer que rompió algo.
   **No lo toqué:** es el único archivo escrito a mano de `sql/`, y `sql/` es troncal.

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

**Ninguno abierto.**

### Resuelto · Testcontainers no encontraba Docker en el Mac

El síntoma era `Could not find a valid Docker environment`, con las tres estrategias
fallando con un `400` cuyo cuerpo era un `Info` vacío. El proxy de Docker Desktop
oculta el mensaje real; el socket crudo
(`~/Library/Containers/com.docker.docker/Data/docker.raw.sock`) lo dice entero:

```
client version 1.32 is too old. Minimum supported API version is 1.40
```

`docker-java` —el cliente que trae Testcontainers 1.21.3, que es la última— pide por
omisión la **API 1.32**, y Docker Engine 29 exige **1.40**. No es del Mac: le va a
pasar a cualquier máquina del parque con un motor reciente, WSL2 incluido.

**Arreglado en el troncal, no en la máquina:** `aportaya.base` fija `api.version=1.41`
en todas las tareas de prueba y propaga `DOCKER_HOST`, `DOCKER_CONTEXT` y
`DOCKER_API_VERSION` si el entorno los trae. 1.41 la soporta cualquier motor desde
2020 y satisface el mínimo de los actuales. Ninguna ruta local quedó en el repositorio.

## Gate de salida de la Fase 0 — evidencia

Comandos **ejecutados**, con su resultado.

```
./gradlew spotlessCheck check                BUILD SUCCESSFUL
./gradlew bd:reset                           BUILD SUCCESSFUL
  prueba_humo.sql                            165 OK · 0 FALLA  (base recién creada)
  verificaciones.sql                         sin filas
./gradlew generateJooq compileJava           BUILD SUCCESSFUL  (14 esquemas)
./gradlew generateOpenApiClients             BUILD SUCCESSFUL  (3 clientes, 28 .ts)
tsc -p clientes/tsconfig.json                sin errores
./gradlew test integrationTest contractTest sagaTest testBarrido
                                             BUILD SUCCESSFUL
curl -f http://localhost/actuator/health     200 a través de NGINX
python3 scripts/verificar_seguridad.py       TODO OK · 2 avisos
python3 scripts/verificar_carriles.py        TODO OK
python3 scripts/verificar_criterios.py       sin divergencias
python3 scripts/generar_k8s.py               34 archivos · 690/1000 conexiones
```

**Pruebas que corrieron, por corredor:**

| Corredor | Pruebas | Falladas |
| --- | :-: | :-: |
| `test` | 77 | 0 |
| `integrationTest` | 15 | 0 |
| `contractTest` | 1 | 0 |
| `testBarrido` | 30 | 0 |
| `sagaTest` | 0 | — |
| `e2eTest` | 0 | — |

Ninguna `@Disabled`.

### Los trece puntos del gate

- [x] Las 304 tablas existen y `sql/50_verificacion/` pasa
- [x] Los catorce esquemas y los catorce roles existen, y el `SELECT` cruzado entre
      cualquier par devuelve permiso denegado ← **invariante 11**, verificado por
      `AislamientoEsquemaTest` (14 × 13 = 182 comprobaciones)
- [x] Solo `svc_nucleo_financiero` escribe `asiento_contable` ← **invariante 12**
- [x] Los 20 catálogos mínimos sembrados
- [x] `prueba_humo.sql` da todo OK, cero FALLA sobre base recién creada
- [x] Los tres generadores producen un servicio y un caso de uso sin edición manual
- [x] Los tres contratos de 0.8 publicados y el cliente TypeScript generado
- [x] Ningún servicio de aplicación publica puerto: solo NGINX
      (PostgreSQL expone `127.0.0.1:5433` para `generateJooq`; ver Decisiones)
- [x] Ningún `build.gradle.kts` declara JPA y ningún `application.yml` tiene `spring.jpa`
- [x] La suma de los pools declarados (690) es menor que lo que acepta PgBouncer (1000)
- [ ] `./gradlew e2eTest` — sin perfil `todo` todavía (Fase 17)
- [ ] El proceso no levanta si falta una clave de configuración — **probado a medias**:
      los `${BD_URL}` sin valor por omisión lo garantizan, pero no lo ejecuté a propósito
- [ ] `python3 scripts/verificar_boveda.py` — falla por un wikilink de la maqueta que
      no es de este carril (ver «Lo que queda abierto»)

### Frases prohibidas sin evidencia

No se dice «está listo». Lo que hay es: **123 pruebas en verde repartidas en cuatro
corredores, 165 líneas OK y 0 FALLA en la prueba de humo desde volumen limpio, 182
comprobaciones de permiso cruzado, 200 por NGINX, tres clientes TypeScript generados
y 690 de 1000 conexiones en el pico.**

## Ver también

[[01 Fase 0 · Cimientos del repositorio]] · [[17 Plan de acción secuencial · coordinación de cinco máquinas]] · [[00 Plan maestro]] · [[Estructura del repositorio]]
