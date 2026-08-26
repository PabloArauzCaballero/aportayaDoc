---
tags:
  - plan
  - informe
  - carril
titulo: "Carril 0T — cimientos, capa de datos y núcleo transversal"
ola: 0
fase: 0-2
modulo: troncal
rama: pablo/feature/carril-0T-cimientos
estado: en curso
---

# Carril 0T — cimientos, capa de datos y núcleo transversal

**Fases** 0, 1 y 2 · **Casos de uso** ninguno (este carril construye el piso) · **Máquina** Mac M5

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

## Fase 1 · `comun-dominio` — los diez átomos

| Átomo | Estado | Nota |
| --- | :-: | --- |
| `Dinero` · `Moneda` | ✅ | Escala 2, moneda pegada al importe, `dividir` exige regla de redondeo |
| `Periodo` | ✅ | Solape detectado aunque toque por un solo día |
| `PlazoHabil` · `CalendarioHabil` | ✅ | Calendario **inyectado**; corre a favor del cliente, nunca hacia atrás |
| `ContextoSesion` · `SinContextoDeSesion` | ✅ | Sin rol no hay contexto; `sistema` es un rol, no una excepción |
| `ClaveIdempotencia` | ✅ | Derivada del hecho: `aporte:<id>` |
| `CodigoError` | ✅ | `AP-CU<NN>-<nn>` validado al construir |
| `Prorrateo` | ✅ | Reparto por total acumulado |
| `Reloj` · `Ids` | ✅ | Los dos se inyectan; el azar es criptográfico |
| `Traza` | ✅ | Atraviesa los catorce servicios |

**Evidencia:** `41 pruebas · 0 saltadas · 0 falladas` · cobertura de **ramas 100 %** y
**líneas 99,4 %** sobre un piso declarado de 95 % · ninguna dependencia de Spring ni
de jOOQ (lo verifica ArchUnit).

### Un defecto que encontró la prueba de ejemplo, no la de propiedad

`Prorrateo` redondeaba cada parte por separado y volcaba el residuo en la primera:
repartir 90,00 entre tres daba **30,02 · 29,99 · 29,99**. La suma cuadraba —por eso
las mil pruebas de propiedad pasaban— y aun así estaba mal: dos participantes ponían
un centavo de menos y uno tres de más. Ahora se reparte por total acumulado y ninguna
parte se aleja más de un centavo de su proporción justa.

**Es el argumento entero a favor de tener las dos.** La propiedad prueba que no se
pierde plata; el ejemplo prueba que el reparto es justo.

### Dos trampas del entorno, desactivadas en el troncal

1. **jqwik reportaba sus propiedades como SALTADAS** al convivir con `@Test` en la
   misma clase: tres mil casos de cuadre que nunca corrieron, con el build en verde.
   Las propiedades viven ahora en su propia clase, y **el build falla si cualquier
   corredor deja una prueba saltada** — que era la única forma de que esto no
   volviera a pasar en silencio.
2. **El daemon de Gradle no arrancaba en frío.** `gradle-daemon-jvm.properties` pedía
   Java 21 sin decir de dónde bajarlo: funcionaba mientras el daemon siguiera vivo y
   fallaba con `No defined toolchain download url` en el primer arranque limpio — es
   decir, en las otras cuatro máquinas. Regenerado con las URLs de las seis
   plataformas.

## Fase 1 · `comun-datos` y el invariante 3

`conContexto(ctx, fn)` fija `app.usuario_id`, `app.rol` y `app.traza` con
`set_config(..., true)` —que es `SET LOCAL`— dentro de la transacción en curso.

Tres decisiones, y las tres tienen su prueba:

| Decisión | Por qué | Prueba |
| --- | --- | --- |
| **No lleva `@Transactional`** | La transacción la abre el caso de uso, y una sola vez. Si esta clase la abriera por su cuenta, «una transacción por caso de uso» sería una frase | `DatosTest` · sin transacción lanza `SinTransaccion` |
| **Falla si no hay transacción abierta** | `SET LOCAL` suelto **no fija nada** y PostgreSQL solo emite un WARNING: la consulta correría sin política de fila y devolvería filas de todos, sin error y sin rastro | idem |
| **`TransactionAwareDataSourceProxy`** | Sin él, jOOQ pide una conexión nueva al pool y la consulta corre fuera de la transacción que acaba de hacer `SET LOCAL` | `ContextoDeFilaRepositorioTest` |

**Evidencia:** `5 pruebas · 0 saltadas · 0 falladas`, tres de ellas contra PostgreSQL
real: fila propia ⇒ 1, fila ajena ⇒ **0 filas y ningún error**, rol privilegiado ⇒
todas, y el contexto muere en el `COMMIT`.

### El hallazgo más caro del carril: RLS no estaba en vigor

Al escribir esa prueba apareció que `svc_identidad` **veía cero filas de su propia
tabla**. Tirando del hilo:

| # | Qué | Consecuencia |
| :-: | --- | --- |
| 1 | `fn_seg_aplicar_rls()` recorría `WHERE n.nspname = 'public'` | Se escribió antes de que [[ADR-017 Propiedad de datos por servicio]] partiera el modelo en catorce esquemas. Desde entonces **no encontraba ni una tabla**: de las **86** con `usuario_id` o `cuenta_billetera_id`, ninguna tenía política de fila |
| 2 | La verificación de `R-SEG-03` —«tablas con datos de titular sin RLS forzada»— filtraba por el **mismo** `public` | Devolvía cero filas siempre. **La comprobación que debía denunciar el agujero lo estaba tapando** |
| 3 | Las políticas se escriben `FOR ALL TO rol_aplicacion`, y `rol_aplicacion` **no tenía un solo miembro** | Los servicios se conectan como `svc_<esquema>`. Una política que no le aplica a nadie no protege: la tabla queda abierta o cerrada por accidente, nunca por diseño |

Los tres se arreglaron en la fuente de verdad —`docs/Restricciones.md` y
`scripts/generar_ddl.py`—, no en el SQL generado.

**Antes:** 6 tablas con RLS activa. **Después:** **92 tablas, 92 políticas**, y
`bd:reset` sigue en 165 OK / 0 FALLA.

> `rol_aplicacion` tiene cero privilegios de tabla, no puede iniciar sesión y no
> tiene `BYPASSRLS`: concederlo no abre nada. Es la marca que hace que las políticas
> apliquen, y se verificó contra la base antes de tocarla.

## Fase 1 · las cuatro pruebas que la cierran

| Prueba | Invariante | Estado | Evidencia |
| --- | :-: | :-: | --- |
| `AislamientoEsquemaTest` | 11 y 12 | ✅ | 14 roles × 13 esquemas ajenos ⇒ permiso denegado; solo `svc_nucleo_financiero` escribe `asiento_contable` |
| `ContextoDeFilaRepositorioTest` | 3 | ✅ | Fila ajena ⇒ **0 filas, sin error**; el contexto muere en el `COMMIT` |
| `AppendOnlyRepositorioTest` | 5 | ✅ | **79 tablas selladas**; el `UPDATE` y el `DELETE` los rechaza la **base** con `R-AUD-01` |
| `DineroCuadreTest` + propiedades | 4 | ✅ | 3.000 casos generados; el prorrateo no pierde ni inventa un centavo |
| `EsquemaAlDiaTest` | 1 | 🟡 | Necesita clases generadas de jOOQ: va en el primer servicio (carril 1A), no en `plataforma/` |

**Total de `plataforma/`: 73 pruebas · 0 saltadas · 0 falladas.**

## Carril 1A · `identidad` — CU-04 terminado

**`POST /sesiones` · Autenticar con MFA y registrar dispositivo.** Recorre el
pipeline entero: contrato OpenAPI → interfaz generada → controlador → organismo con
`@Transactional` → `conContexto` → escritura → outbox → `COMMIT`.

**Evidencia:** `27 pruebas · 0 saltadas · 0 falladas` en `identidad`, de las cuales
**los 7 criterios de aceptación de la bóveda** y **una prueba de rechazo por cada uno
de los 9 `R-XXX-nn` citados**. `verificar_criterios.py` → «sin divergencias».

### Dos decisiones que valen más que el código

1. **El caso de uso no lanza para rechazar.** El caso de uso pide que el intento
   fallido quede escrito; si el organismo lanzara, la transacción revertiría y se
   llevaría consigo el `intento_autenticacion` que acaba de escribir. Devuelve un
   `ResultadoDeAutenticacion` y la página lo traduce a `422` **después del `COMMIT`**.
2. **CU-04 no lleva clave de idempotencia, y es deliberado.** Cada intento *es* un
   hecho distinto. Colapsar dos en uno borraría justo lo que hay que poder contar.

### Lo que el motor encontró y el código no

Escribiendo las pruebas, la base rechazó cuatro cosas que yo había dado por buenas:
columnas inventadas en cinco tablas, `ip_origen` que es `inet` y no cadena, y
`ck_asignacion_no_autoasignada` — nadie se asigna un rol a sí mismo (`R-SEG-07`).
**Ninguna la habría visto una revisión de código.**

### Lo que queda de 1A, y por qué

| CU | Estado | Por qué |
| :-: | :-: | --- |
| CU-04 | ✅ | — |
| CU-01 | bloqueado | Escribe en `cumplimiento` (debida diligencia, calificación de riesgo, expediente) y en `nucleo_financiero` (cuenta de billetera y su espejo contable). Es una **saga**, no una transacción local, y necesita los contratos de los carriles 1B y 1C |
| CU-05 | bloqueado | `aceptacion_contrato` y `contrato_adhesion` viven en `cumplimiento`. El caso de uso dice `openapi/identidad.yaml` pero sus tablas son de otro servicio: **hueco declarado**, hay que decidir de quién es antes de escribirlo |
| CU-08 · CU-09 | pendiente | Self-contained en `identidad`; siguen a continuación |

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
