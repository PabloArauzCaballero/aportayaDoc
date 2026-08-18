---
tags:
  - moc
  - plan
  - estandar
titulo: "Estándar de ejecución — código limpio, pruebas y calidad"
fecha: 2026-08-13
aplica_a: las 21 fases, sin excepción
---

# Estándar de ejecución

> **Este documento se aplica en las 21 fases, en cada archivo y en cada PR.** El
> [[00 Plan maestro]] dice *qué* construir y *en qué orden*; los documentos de fase
> dicen *qué piezas*; **este dice cómo se escribe**. Si un documento de fase y este
> se contradicen, gana este.

Es la unión de tres fuentes que ya existen y no se reescriben acá, se hacen
operativas: [[Prompt general de desarrollo]] · [[Prompt de backend]] · las skills
`codigo-limpio`, `arquitectura-atomica`, `revision-codigo`, `pruebas-cu`,
`ci-calidad`, `definicion-de-terminado`, `glosario-dominio`, `git-flujo`.

---

## 0 · Regla cero: no inventar

Trabajar con criterio de **temperatura 0**: máxima precisión, mínima especulación,
cero invención.

**No se inventan** requisitos, entidades, columnas, endpoints, estados, roles,
permisos, dependencias, variables de entorno ni reglas de negocio.

| Situación | Qué se hace |
| --- | --- |
| Falta info **crítica** (regla de negocio, estado válido, permiso, contrato, idempotencia, plazo legal) | **Se para y se pregunta** |
| Falta info **no crítica** | Se avanza **declarando el supuesto por escrito** |
| Hay contradicción entre lo pedido, el código y la bóveda | **No se elige en silencio**: se dice y se propone resolución |

> **En este proyecto casi nunca falta información.** La respuesta está en el caso de
> uso, en [[Restricciones]] o en [[Cumplimiento]]. Buscar ahí es más rápido que
> suponer — y es lo único que cuenta como fuente.

Un supuesto escrito se corrige. Uno silencioso se descubre en producción.

---

## 1 · Composición atómica

La estructura ya está en §3 del plan maestro. Las seis reglas que la sostienen:

1. **Nadie salta de nivel**: página → organismo → molécula → átomo. Nunca al revés ni
   en círculo.
2. **Un archivo, una pieza**, con el nombre de la pieza.
3. **Los átomos no conocen infraestructura.** Si necesita red, base o reloj: o no es
   átomo, o se inyecta.
4. **Las moléculas no orquestan** otras moléculas ni abren transacciones.
5. **Si una pieza no cabe en un nivel, hace de más**: se parte. Si cabe en dos,
   mezcla niveles: se parte.
6. **Antes de escribir, se declaran las piezas y su nivel.** Por escrito, en el PR.

Objetivos por nivel, más exigentes que el límite duro de líneas: **organismo ~200**,
molécula ~150, átomo ~80. Un archivo de 180 líneas que mezcla niveles **sigue estando
mal**: el conteo no sustituye al criterio.

---

## 2 · Simplicidad (KISS)

> La solución más simple que cumple el caso de uso **completo**, y ni una capa más.

| Regla | En la práctica |
| --- | --- |
| **Se abstrae al tercer uso** | Con dos ejemplos se adivina el patrón; con tres se ve |
| Sin patrones por moda | Un patrón entra si resuelve un problema **presente** |
| Sin generalización prematura | Nada de "por si mañana hay otra moneda" |
| Sin capa que solo reenvía | Si no transforma ni protege, sobra |
| Especializado antes que genérico | Un caso de uso claro vale más que un servicio genérico a medias |

Antes de introducir una abstracción: **¿qué problema real y presente resuelve, y qué
se rompería sin ella?** Si no hay respuesta, no entra.

> **Simple no es escaso.** Quitar una restricción, una prueba de rechazo o una traza
> **no simplifica: degrada.** Código profesional no es código sobre-ingenierizado,
> pero tampoco código al que le sacaron los controles.

---

## 3 · Nombres — el vocabulario es el de la bóveda

| Regla | Sí | No |
| --- | --- | --- |
| Específico y del dominio | `obligacionVencida`, `devengarComision` | `data`, `info`, `temp`, `item2` |
| Sin genéricos sin contexto | `ObligacionRepositorio` | `Manager`, `Helper`, `Processor`, `Utils` |
| Dice qué es, no cómo se hizo | `saldoDisponible` | `resultadoQuery3` |
| Booleanos afirmativos | `estaVigente`, `puedeCobrar` | `noEsInvalido` |
| **Español del dominio** | `participante`, `cuota`, `aporte` | mezclar `member`/`participante` |

Si el modelo dice `obligacion_aporte`, el código **no** inventa `Payment`. La
trazabilidad especificación → código depende de que el nombre sea el mismo
(skill `glosario-dominio`).

Prohibido un archivo `Utils.java`: es el síntoma de una función sin dueño. Cada función
pertenece a un átomo con nombre.

---

## 4 · Funciones y condicionales

**Funciones**
- Una responsabilidad. Si hace falta un comentario para separar secciones dentro de
  una función, **son dos funciones**.
- **Sin banderas booleanas** que cambien el comportamiento: dos funciones con nombre.
- Pocos parámetros; si son muchos y relacionados, es un objeto de valor.
- **Retorno temprano** para casos borde; nada de pirámides de `else`.
- **El reloj, el azar y los identificadores se inyectan.** Un `new Date()` dentro de
  un cálculo es una prueba no determinista esperando fecha.

**Condicionales**
- Nada de números mágicos. Un umbral regulatorio dentro de un `if` es un **defecto de
  cumplimiento**, no de estilo: va a catálogo (invariante 10).
- Las condiciones complejas se nombran: `boolean superaUmbralUif = …` antes del `if`.
- Estados como **tipos** (`enum`), no como cadenas comparadas con `equals`.
- Sin `else` cuando el `if` retorna.

---

## 5 · Comentarios

El código dice **qué**; el comentario dice **por qué**, y solo cuando el porqué no es
evidente. En este proyecto los que valen la pena son casi siempre uno de estos tres:

```java
// R-BIL-04: el saldo se deriva; nunca se actualiza en su lugar.
// CU-31 paso 4: el tarifario se congela al crear el grupo, no al cobrar.
// UIF: el umbral llega del catálogo con vigencia; no se compara contra constante.
```

**Prohibidos:** comentarios que repiten el código · código comentado (para eso está
git) · `TODO` sin explicación ni dueño.

---

## 6 · Errores

| Regla | Cómo se ve |
| --- | --- |
| Fallar **temprano y ruidosamente** | Estado inválido ⇒ excepción; nunca continuar con datos dudosos |
| Nunca `catch` vacío | Ni tragar excepciones "para que no se caiga" |
| Error de negocio ≠ defecto | El primero se maneja con código (`AP-CU31-02`); el segundo se registra y **se propaga** |
| El mensaje al usuario no filtra | Sin SQL, sin nombres de tabla, sin trazas, sin datos personales |
| El rechazo de la base se traduce | Al código `R-XXX-nn` documentado, nunca el texto crudo de PostgreSQL |

El mapeo completo a HTTP está en §5 del plan maestro.

---

## 7 · Efectos secundarios y dependencias

- Lo que una pieza necesita **se le pasa**. Nada de dependencias ocultas ni estado
  global mutable.
- Todo borde externo entra por una **interfaz de dominio** con su adaptador:
  pasarela, SIAT, WhatsApp, KYC, almacenamiento de archivos.
- Sin `import` que crucen niveles hacia arriba.
- **Sin dependencias nuevas sin necesidad real y sin declararlo en el PR.** Cada una
  se justifica; nada de alpha/beta en producción sin ADR y plan de reversión;
  actualizaciones mayores por decisión consciente: toda versión vive **exacta** en el
  catálogo de Gradle (`gradle/libs.versions.toml`), nunca como rango.

---

## 8 · Las reglas del stack, en el día a día

Lo que hay que tener presente al escribir cada archivo, por tecnología.

### jOOQ y acceso a datos

| Sí | No |
| --- | --- |
| `conContexto(ctx, dsl -> …)` para toda consulta con RLS | Consultar fuera del contexto — la política no filtra |
| `set_config(…, true)` para el `SET LOCAL` | `SET` plano — sobrevive a la petición y contamina la siguiente |
| El `DSLContext` de la transacción en curso | Tomar otra conexión: pierde el contexto **en silencio** |
| `insertInto` explícito en append-only | Cualquier `update` sobre una tabla sellada |
| Columnas listadas | `select *` en flujos de dinero |
| Paginación y orden por **lista blanca** | Ordenar por un campo que llega del cliente |
| Regenerar tras cambiar el modelo | Editar a mano una clase generada |
| Un solo esquema: el propio | `SELECT` sobre el esquema de otro servicio |

**JPA está prohibido**, no desaconsejado: `spring-boot-starter-data-jpa` en un
`build.gradle.kts` es un rechazo automático.

### Contratos OpenAPI

- **El contrato se escribe antes que la implementación**, en
  `servicios/<servicio>/src/main/resources/openapi/<servicio>.yaml`.
- `additionalProperties: false` siempre: campo desconocido = error, no se ignora.
- El controlador **implementa la interfaz generada**. Si el contrato cambia y el
  controlador no, no compila.
- Los importes son **cadena** con dos decimales. **Nunca `type: number` para dinero.**
- Versión explícita en la ruta (`/v1/…`). Los cambios incompatibles no se hacen en
  silencio, y la prueba de contrato falla en el CI **del que rompe**.
- La documentación se **deriva** del contrato; no se escribe a mano.

### Registro estructurado

- **Toda línea de un caso de uso lleva `cu`, `usuario_id`, `traza` y `servicio`.**
- Redacción obligatoria: `authorization`, `cookie`, `password`, `pin`,
  `numero_documento`, `numero_cuenta`, `telefono`, `correo`, `token`, `clave_*`.
- La traza (`x-request-id`) se propaga **a los otros servicios y al consumidor de
  eventos**. Con catorce procesos, es la única forma de reconstruir una operación.
- **Prohibido `System.out`/`System.err`** en runtime (regla de análisis estático).
- Nivel `error` solo para lo que requiere que alguien actúe; lo demás, `warn`/`info`.

### Archivos y evidencia

- Siempre detrás del puerto `AlmacenArchivos`. **Ningún caso de uso conoce al
  proveedor de almacenamiento.**
- Tipo MIME y tamaño validados **por el contrato antes** de escribir.
- Rutas `AAAA/MM/<uuid>`, **nunca** derivadas del nombre del archivo del usuario.
- **SHA-256 guardado en la base**: la evidencia es el hash, no el archivo.
- Los archivos **no se borran**: se marca la baja (retención regulatoria).

### JUnit 5

- Cada prueba es un `@Test` con `@DisplayName` = el criterio de aceptación, citado
  tal cual está en el CU.
- **Sin `sleep`** para sincronizar: se coordina con promesas y bloqueos reales.
- Sin orden entre pruebas; cada una monta lo suyo.
- El reloj se **adelanta inyectado**, nunca se espera al real.
- **Una prueba que no puede fallar no es una prueba.** Si borrarla no rompe nada,
  sobra.

### Docker

- Multietapa sobre `eclipse-temurin`, `USER app` (**nunca root**), sin `latest`, sin
  secretos en la imagen.
- `HEALTHCHECK` a `/salud`; `dumb-init` como PID 1; apagado controlado con `SIGTERM`.
- La API **no publica puerto**: la única entrada pública es NGINX.
- `.dockerignore` excluye `docs/`, `planes/`, pruebas y `.git`.

---

## 9 · Pruebas — las siete obligatorias por caso de uso

Los seis niveles y las herramientas están en §7 del plan maestro. Lo que **cada CU**
tiene que tener, sin excepción (skill `pruebas-cu`):

| # | Prueba | Qué verifica |
| :-: | --- | --- |
| **1** | Un `@Test` **por criterio de aceptación**, con `@DisplayName` citando su texto | `@DisplayName("CU-21 · CA-3: un aporte por debajo del monto de la obligación se rechaza")` |
| **2** | **Rechazo de cada restricción citada** | Se provoca la violación y se espera **el error de la base**, no un `if` de la aplicación |
| **3** | **Reintento** | Misma clave de idempotencia ⇒ misma respuesta y **cero efectos nuevos**, contando filas antes y después |
| **4** | **Concurrencia** | Dos ejecuciones simultáneas: una gana, la otra falla claro, **el saldo queda correcto** |
| **5** | **Plazo**, si hay consecuencia legal | Se adelanta el reloj **inyectado** y se verifica vencimiento, alerta previa y estado |
| **6** | **Cuadre**, si mueve dinero | `SUM(monto) = 0.00` en la transacción, asiento equilibrado, y **prueba de propiedad** sobre el átomo de cálculo |
| **7** | **Compensación de saga**, si cruza a otro servicio | El `*SagaTest` fuerza el fallo de **cada paso remoto, uno por uno**, y verifica el reverso (movimiento inverso, nunca `UPDATE` del libro) y el estado final coherente — receta en [[00c Recetario · implementar un caso de uso]] §8b, mecánica en ADR-028 |

> **La prueba 2 es la que más se hace mal.** Si pasa porque la aplicación validó
> antes, **no probó la restricción**: hay que ejercerla saltándose la capa de
> aplicación, y esperar el nombre de la restricción de PostgreSQL.

**Sin semillas, todo falla — y es correcto.** `R-LIM-01` y `R-LIC-01` rechazan
cualquier operación sin límite, tarifario o licencia vigente. Ese es el invariante 9
funcionando, no un problema del entorno.

**Dobles de proveedores externos**: implementan la interfaz de dominio y reproducen
las fallas reales — timeout, respuesta duplicada, respuesta fuera de orden, error no
clasificado. Nunca se prueba contra el proveedor real en CI.

---

## 10 · Seguridad, en cada archivo

- Todo lo que viene de afuera es **hostil** hasta validarse.
- Consultas **parametrizadas** siempre; jamás concatenación de entradas.
- Autorización verificada en el servidor **en cada operación**, contra el recurso
  concreto — no solo contra el rol.
- **Mínimo privilegio** en credenciales, roles y permisos; procesos distintos, roles
  distintos.
- Límite de tasa en bordes públicos y operaciones sensibles.
- Datos personales: los mínimos, enmascarados en logs, eliminados cuando corresponde.
- **Ningún secreto** en el código, el repositorio ni los logs. Un secreto detectado
  en un diff se **rota**, no solo se revierte.
- Registro de auditoría de toda operación que cambie estado relevante: quién, qué,
  cuándo, desde dónde, con qué resultado.

---

## 11 · Qué nunca se versiona

```gitignore
*.log
logs/
backups/
*.dump
*.sql.gz
artifacts/
**/resultados-humo*.json
**/resultados-carga*.json
```

La evidencia (pruebas, carga, restauración) se publica como **artefacto del CI** —
`artifacts/pruebas/`, `artifacts/carga/`, `artifacts/restauracion/` — no se commitea.

La configuración vive en `application.yml` por servicio, con los secretos inyectados
por el entorno de despliegue: no hay archivos `.env` que ignorar ni `.env.example`
que mantener.

---

## 12 · Antes de abrir el PR

Checklist de la skill `codigo-limpio`, ampliado con lo del stack. **Se ejecuta, no se
supone.**

### Composición y código
- [ ] Las piezas nuevas están declaradas **por nivel** en la descripción del PR
- [ ] Ningún nombre genérico sin contexto (`data`, `Manager`, `utils`, `helper`)
- [ ] Ninguna función con dos responsabilidades
- [ ] Ningún número regulatorio en el código
- [ ] Sin `TODO` huérfanos, sin código comentado, sin `System.out` ni logs fuera de Logback
- [ ] Sin `@SuppressWarnings` sin justificación escrita, sin casts crudos

### Frontera transaccional (respondida **por escrito** en el PR)
- [ ] ¿Qué ocurre todo junto o nada?
- [ ] ¿Qué queda fuera del commit?
- [ ] ¿Cuál es la clave de idempotencia y de dónde viene: cliente o proveedor?
- [ ] ¿Qué se bloquea si dos usuarios hacen esto a la vez, y a qué granularidad?
- [ ] ¿Qué pasa si el proceso muere justo después del commit?
- [ ] ¿Esto cruza a otro servicio y qué pasa si el otro falla? ¿Cuál es la compensación?

### Pruebas
- [ ] Cada criterio de aceptación tiene su `@Test` con `@DisplayName` idéntico
- [ ] Cada restricción citada tiene su prueba de **rechazo**
- [ ] Hay prueba de reintento, de concurrencia y de fallo del proveedor externo
- [ ] Si mueve dinero: prueba de cuadre al centavo

### Verde local
- [ ] `./gradlew spotlessCheck check test integrationTest contractTest`
- [ ] `./gradlew generateJooq compileJava generateOpenApiClients` — compila y el cliente no produce diff

Y si el PR toca `docs/entidades/*.puml`, `docs/Restricciones.md` o `sql/`:
`sql/` **regenerado** (no editado a mano) · prueba de humo en verde ·
`python3 scripts/verificar_boveda.py` OK · restricción nueva con código, norma citada
y prueba de rechazo.

---

## 13 · Revisión de código — orden por riesgo

Una revisión no es una lectura de arriba abajo: es una **búsqueda ordenada por
riesgo** (skill `revision-codigo`).

```
1. ¿Qué caso de uso implementa? ¿Coincide con lo que dice la bóveda?
2. Frontera transaccional e idempotencia
3. ¿Dónde quedó cada garantía: base o aplicación?
4. Dinero: tipos, redondeo, cuadre
5. Pruebas: criterios, rechazos, reintento
6. Composición: niveles y dirección de dependencia
7. Nombres, legibilidad, comentarios
8. Estilo → lo resolvió la herramienta; si aparece acá, falta configuración
```

### Se rechaza sin discusión

| Hallazgo | Por qué |
| --- | --- |
| Un importe tipado `double` o `float`, un `parseDouble` o aritmética suelta sobre dinero | Exactitud es cumplimiento |
| `UPDATE` sobre tabla append-only, o "ajustar" un saldo en vez de insertar movimiento | Corrección = movimiento inverso |
| Llamada a proveedor externo **dentro** de la transacción | Va por outbox |
| Escritura **antes** de validar la clave de idempotencia | La bóveda lo exige explícitamente |
| Consulta sin `SET LOCAL`, o `SET` sin `LOCAL` | **Fuga de identidad entre requests** |
| Un umbral, límite o tarifa como constante en el código | Va a catálogo con vigencia |
| Regla que protege dinero "validada solo en el backend", sin su `R-XXX-nn` | La garantía está en el lugar equivocado |
| Criterio de aceptación sin prueba, o restricción citada sin prueba de rechazo | **No está terminado** |
| Migración escrita a mano fuera de `sql/` | El esquema se genera desde la bóveda |
| `@SuppressWarnings` sin justificación escrita, o un cast crudo | Silencia justo donde hay dudas |

### Se comenta, no se bloquea

Nombres mejorables que no inducen a error · una molécula que quizá suba a
`plataforma/comun-dominio` (recordar: **al tercer uso**) · consultas optimizables sin
evidencia de que sean un problema · preferencias dentro del mismo nivel.

### Las siete preguntas que sacan los defectos reales

1. **¿Qué pasa si esto se ejecuta dos veces?** El reintento en mala señal es el caso
   normal, no el raro.
2. **¿Qué pasa si el proceso muere justo después del `COMMIT`?**
3. **¿Qué pasa si dos personas hacen esto a la vez?** ¿Qué fila se bloquea?
4. **¿Qué ve un usuario que no debería ver esto?** ¿Lo impide la política de fila o
   solo un `WHERE`?
5. **¿Dónde queda la evidencia?** Si mañana hay un reclamo o una inspección, ¿con qué
   consulta se responde?
6. **Si borro esta prueba, ¿algo falla?**
7. **¿Esto contradice un ADR vigente?** Si sí, **el ADR gana** hasta que se escriba
   uno nuevo que lo supere.

---

## 14 · Git

- Rama por trabajo, con nombre que diga qué hace.
- **Mensajes de commit en español**, describiendo el porqué, no el qué del diff.
- Un PR = un alcance. Si toca a la vez la bóveda y el código, el PR lo dice y
  regenera los derivados.
- **Nunca** se commitean secretos, `.env`, dumps ni artefactos.

---

## 15 · Definición de terminado

De la skill `definicion-de-terminado`, y es la regla más incómoda del proyecto:

> **Está prohibido afirmar "listo", "compila", "pasa las pruebas" o "es seguro" sin
> haberlo ejecutado.** Una casilla marcada sin comando corrido es una afirmación
> falsa, no un atajo.

Cuando se entrega trabajo, se incluye siempre:

1. **Qué se construyó**, con las piezas listadas por nivel.
2. **Los supuestos declarados**, si los hubo.
3. **Qué queda sin cubrir** y por qué.
4. **Cómo verificarlo**: comandos concretos y qué debería verse.

No se entrega código incompleto disfrazado de terminado, ni `TODO` sin explicación,
ni funciones vacías, ni ejemplos ficticios mezclados con la implementación real. Si
algo quedó fuera, **se dice explícitamente**.

---

## 16 · Restricciones finales

- No se cambian decisiones de arquitectura ya tomadas **sin decirlo y justificarlo**
  con un ADR.
- No se agregan dependencias sin necesidad real ni sin declararlas.
- No se reformatea ni se "mejora" código ajeno fuera del alcance pedido.
- **No se optimiza sin medir**: primero claridad, después rendimiento con evidencia.
- Ante duda razonable, **se pregunta**. Ante duda menor, **se decide y se documenta**.

## Ver también

[[00 Plan maestro]] · [[07 Carriles de trabajo concurrente]] · [[Prompt general de desarrollo]] · [[Prompt de backend]] ·
[[Método de arquitectura]] · [[ADR-023 Composición atómica en Java]] · [[Restricciones]]
