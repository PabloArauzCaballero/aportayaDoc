---
tags:
  - moc
  - plan
  - carriles
titulo: "Carriles de trabajo concurrente — varias máquinas en paralelo"
fecha: 2026-08-16
---

# Carriles de trabajo concurrente

> **Para qué es este documento.** Para repartir el backend entre **varias máquinas
> trabajando a la vez**, cada una con su chat, su clon del repositorio, su rama y su
> base de datos, de modo que **dos carriles nunca editen el mismo archivo**. El
> conflicto de merge no se resuelve: se hace imposible por diseño.

> [!important] Este documento no sabe que existe el frontend
> Las olas de acá cuentan máquinas **solo para el backend**, y las de
> [[16 Carriles de frontend]] hacen lo mismo con el suyo: sumadas, el pico pide ocho
> máquinas y hay cinco. La secuencia real de ejecución sobre el parque concreto
> —qué puesto, en qué máquina, en qué tramo— está en
> [[17 Plan de acción secuencial · coordinación de cinco máquinas]], que **manda en
> el quién y el cuándo**. Este documento sigue mandando en **qué archivos posee cada
> carril**, que es lo que hace imposible el conflicto.

---

## 0 · Qué cambió el 2026-08-16, y por qué importa acá más que en ningún otro lado

Este documento existía para neutralizar **siete puntos de conflicto** con reglas que
alguien tenía que recordar. Con [[ADR-014 Arquitectura de servicios]], **cinco de los
siete dejan de existir**: no se neutralizan, no tienen dónde ocurrir.

| # | El conflicto de antes | Qué pasa ahora |
| :-: | --- | --- |
| 1 | `app.module.ts` lista cada módulo ⇒ **todos** lo editan | **No existe.** Son procesos separados; nadie los registra en ninguna lista. `settings.gradle.kts` descubre `servicios/` por barrido |
| 2 | `openapi.json` versionado ⇒ conflicto en **cada** PR | **No existe.** Una especificación por servicio, dentro del servicio |
| 3 | Barril de contratos compartido | **No existe.** Cada servicio publica su cliente generado |
| 4 | `planes/informe.md` lo escriben todos | Sigue resuelto: uno por carril en `planes/informes/` |
| 5 | `yarn.lock`: dos carriles agregan dependencias | **Se reduce.** Cada servicio tiene su `build.gradle.kts`; solo el catálogo de versiones es compartido, y una dependencia nueva sigue siendo micro-PR |
| 6 | `seeders/*/manifiesto.json` | Sigue siendo micro-PR |
| 7 | Dos carriles necesitan el mismo átomo | Sigue siendo micro-PR a `plataforma/` |
| 8 | `.env.example` que todos amplían | **No existe.** La configuración es el `application.yml` del servicio |
| 9 | `docker-compose.yml` base | Sigue compartido; solo Ola 0 y Ola 5 lo tocan |

**Lo que queda verdaderamente compartido son dos cosas**: el catálogo de versiones y
`plataforma/`. Ambas ya tenían mecanismo probado (micro-PR), y ambas se congelan en la
Ola 0.

> **A cambio aparece una obligación nueva, y hay que decirla clara:** las
> **especificaciones OpenAPI se escriben una ola antes** de que las necesite quien
> las consume. Es el único artefacto que cruza carriles. Si un contrato no está
> escrito a tiempo, el carril que depende de él **sí** queda bloqueado — y eso es un
> problema de planificación que antes no existía (§7).

---

## 1 · Por qué esto se puede paralelizar

Porque **el esquema ya está completo**. Las 306 tablas existen desde el primer día
(`sql/aplicar.sql`), con sus claves, índices, restricciones y RLS.

Eso cambia todo: un carril **no espera** a que otro implemente su caso de uso para
poder trabajar. Inserta sus propios fixtures directamente en las tablas de **su
esquema** y desarrolla contra ellas.

| Dependencia | ¿Bloquea? |
| --- | --- |
| «Necesito la tabla `usuario`» | **No.** Existe. Se siembra un fixture |
| «Necesito `conContexto` y `Dinero`» | **Sí.** Son de la Ola 0 |
| «Necesito llamar a `nucleo-financiero`» | **No**, si su OpenAPI está escrito: se genera el cliente y se prueba con un doble |
| «Necesito que ese servicio esté implementado» | **No.** Se programa contra su contrato |
| «Necesito que su OpenAPI exista» | **Sí.** Punto de sincronización entre olas (§7) |
| «Necesito leer una tabla de otro servicio» | **Prohibido.** Se pide por su API (invariante 11) |

---

## 2 · La regla de oro

```
un carril = un servicio = un desplegable = una rama = una máquina = un chat
```

Un carril **posee en exclusiva** su directorio de servicio, entero: el build, la
configuración, el contrato, el código, las pruebas, el descriptor de despliegue y el
README. Todo lo demás es **de solo lectura** para él.

> **Si un carril necesita editar un archivo que no posee, no lo edita: abre un
> micro-PR al troncal** (§6). Sin excepciones — la excepción es exactamente el
> conflicto que este diseño evita.

**La diferencia con el diseño anterior es de grado y cambia el resultado.** Antes un
carril poseía un directorio *dentro* de una aplicación compartida, y la aplicación
compartida tenía piezas que todos tocaban. Ahora posee un proceso entero: no hay
pieza compartida que tocar.

---

## 3 · Mapa de olas

Seis olas. Dentro de una ola, **todos los carriles corren a la vez**. Entre olas hay
un punto de sincronización: todos fusionan a **`dev`** y rebasan antes de seguir.
`dev → main` solo cuando la ola cierra entera y en verde (`git-flujo`).

### Ola 0 · Troncal — **una sola máquina, nadie más trabaja**

| Carril | Fases | Alcance | Directorio propio |
| --- | --- | --- | --- |
| **T** | 0, 1, 2 | — | **todo el repositorio** |

Construye el monorepo Gradle, los esquemas y roles de base, `plataforma/*`
(`comun-dominio`, `comun-datos`, `comun-web`, `comun-mensajeria`, `comun-pruebas`),
el gateway, los generadores, el análisis estático, el CI y Docker.
**Bloquea las 20 fases restantes.** No se abre ningún otro carril hasta que su gate
esté ejecutado.

### Ola 1 · 4 carriles

| Carril | Fase | Servicio | Directorio propio | CU |
| --- | :-: | --- | --- | --- |
| **A** | 3 | `identidad` | `servicios/identidad/` | 01, 04, 05, 08, 09 |
| **B** | 5 | `nucleo-financiero` (contable) | `servicios/nucleo-financiero/` | 24 |
| **C** | 4 | `cumplimiento` (parcial) | `servicios/cumplimiento/` | 02, 03, 06, 40, 46 |
| **D** | 12 | `notificaciones` | `servicios/notificaciones/` | 80–83 |

> **D arranca ya**, aunque las notificaciones parezcan «para el final»: solo consume
> eventos de Kafka, que la Ola 0 ya dejó funcionando. Terminarlo temprano elimina los
> dobles de aviso de todos los carriles siguientes.

> **B y el carril 2A son el mismo servicio en olas distintas.** `nucleo-financiero`
> se construye en dos tramos: primero el libro contable (Fase 5), después billetera y
> custodia (Fase 6). **No pueden correr a la vez**, y esa es la única dependencia
> serializada que la fusión de módulos introduce. Está en el precio de mantener la
> partida doble en una sola transacción, y se paga acá.

### Ola 2 · 5 carriles — máxima concurrencia

| Carril | Fase | Servicio | Directorio propio | CU |
| --- | :-: | --- | --- | --- |
| **A** | 6 | `nucleo-financiero` (billetera) | `servicios/nucleo-financiero/` | 10–17, 50, 57 |
| **B** | 7 | `tarifas` | `servicios/tarifas/` | 30–36 |
| **C** | 8 | `grupos` | `servicios/grupos/` | 20, 59, 60, 62–65, 68, 69 |
| **D** | 15 | `auditoria` | `servicios/auditoria/` | 07, 54, 55, 58, 98 |
| **E** | 14 | `organizador` | `servicios/organizador/` | 90–93, 95, 96 |

### Ola 3 · 4 carriles

| Carril | Fase | Servicio | Directorio propio | CU |
| --- | :-: | --- | --- | --- |
| **A** | 9 | `aportes` | `servicios/aportes/` | 19, 21, 51, 99 |
| **B** | 13 | `transparencia` | `servicios/transparencia/` | 61, 70–76, 97 |
| **C** | 16 | `cumplimiento` | `servicios/cumplimiento/` | 41–45, 47–49, 52, 53, 56, 94 |
| **D** | 10a | `entregas` (parcial) | `servicios/entregas/` | 18 |

### Ola 4 · 2 carriles

| Carril | Fase | Servicio | Directorio propio | CU |
| --- | :-: | --- | --- | --- |
| **A** | 10b | `entregas` | `servicios/entregas/` | 22, 28 |
| **B** | 11 | `garantia` | `servicios/garantia/` | 23, 25–27, 29, 66, 67 |

### Ola 5 · 1 carril — convergencia

| Carril | Fase | Alcance |
| --- | :-: | --- |
| **T** | 17 | E2E, rendimiento, resiliencia, restauración, seguridad, despliegue de los quince procesos |

### Resumen

```
Ola 0 ──────────► 1 máquina    (troncal, bloqueante)
Ola 1 ──────────► 4 máquinas
Ola 2 ──────────► 5 máquinas   ← pico
Ola 3 ──────────► 4 máquinas
Ola 4 ──────────► 2 máquinas
Ola 5 ──────────► 1 máquina
```

Con **5 máquinas** se cubre el pico. Con menos, se corren menos carriles por ola en el
orden en que están listados (A primero).

---

## 4 · Propiedad de archivos

### Lo que un carril posee en exclusiva

| Ruta | Nota |
| --- | --- |
| `servicios/<su-servicio>/**` | **Todo**: `build.gradle.kts`, `application.yml`, `openapi/`, las cuatro capas, `trabajos/`, pruebas, `descriptor.yml`, `README.md` |
| `planes/informes/carril-<id>.md` | Su informe de progreso |
| `despliegue/compose/<su-servicio>.yml` | Su fragmento de compose. **Lo crea el generador `nuevoServicio`** y lo posee el carril ([[20 Saneamiento del plan · huecos de la migración a microservicios]] §3.1) |
| `plataforma/gateway/src/main/resources/rutas/<su-servicio>.yml` | **Excepción a `plataforma/**`**: su fragmento de la tabla de rutas del gateway. Lo crea el generador `nuevoServicio`; el gateway **compone** su tabla de rutas desde estos archivos, uno por servicio (§3.1 de [[20 Saneamiento del plan · huecos de la migración a microservicios]]) |

**Eso es todo lo que necesita.** No hay ningún archivo fuera de su directorio que
tenga que tocar para entregar un caso de uso completo — y esa frase es el objetivo
entero de este documento.

### Lo que ningún carril toca (solo lectura)

| Ruta | Quién la cambia |
| --- | --- |
| `sql/**`, `docs/**`, `scripts/**` | Nadie durante los carriles. Cambio de modelo = para todo y se hace en troncal |
| `plataforma/**` | Ola 0. Un átomo nuevo compartido = micro-PR. **Excepción:** `plataforma/gateway/src/main/resources/rutas/<servicio>.yml` lo posee el carril dueño del servicio (§3.1 de [[20 Saneamiento del plan · huecos de la migración a microservicios]]) |
| `gradle/libs.versions.toml` | **Micro-PR.** Una dependencia nueva nunca se agrega en rama de carril |
| `settings.gradle.kts` | Nadie: descubre `servicios/` por barrido |
| `despliegue/Dockerfile`, `despliegue/k8s/**` | Ola 0 y Ola 5. Los manifiestos son **generados** |
| `despliegue/compose/base.yml` | Ola 0 y Ola 5 |
| `clientes/typescript/**` | Nadie: es generado |
| `.github/**` | Ola 0 y Ola 5 |
| **`.claude/skills/**`** | **Micro-PR.** Las 65 skills son de todos: dos carriles ajustando la misma es el conflicto que este diseño evita |
| **El OpenAPI de otro servicio** | Se **lee** para generar su cliente. **Nunca se edita**: es del carril dueño |

---

## 5 · Los conflictos que quedan, y cómo se eliminan

De los siete originales quedan **dos**, y se les suma uno nuevo que trae la
arquitectura de servicios.

| # | Conflicto | Solución | Dónde |
| :-: | --- | --- | --- |
| 5 | Dos carriles agregan una dependencia | **Todas las dependencias comunes se declaran en la Ola 0**, en el catálogo de versiones. Una nueva = micro-PR. Nunca se agrega en rama de carril | Fase 0 |
| 7 | Dos carriles necesitan el mismo átomo compartido | El que lo necesita primero abre micro-PR a `plataforma/comun-dominio`; el segundo lo consume ya fusionado | §6 |
| **N** | **Dos carriles reclaman el mismo nombre global** (tema de Kafka, métrica, bloqueo, ruta) | **Prefijo de módulo obligatorio en todo identificador global**, y el CI falla si hay dos iguales | [[19 Contrato de carril · conflicto cero, skills y calidad verificada]] §3 |

> **El conflicto N es el único que la arquitectura de servicios empeora.** Con un
> proceso, dos rutas iguales chocaban al arrancar y alguien se enteraba. Con catorce
> procesos, dos servicios pueden registrar el mismo prefijo y **nadie ve nada** hasta
> que el gateway enruta mal en integración. Por eso el prefijo reservado deja de ser
> convención y pasa a ser una prueba de barrido.

---

## 6 · Micro-PR al troncal

Cuando un carril necesita algo compartido —un átomo en `plataforma/comun-dominio`,
una utilidad en `comun-web`, una dependencia en el catálogo, una línea en el
manifiesto de semillas:

```
1  rama:  <usuario>/chore/troncal-<carril>-<que-agrega>
2  UN solo cambio, en archivos compartidos. Nada de su servicio
3  con su prueba unitaria
4  PR marcado [MICRO] hacia dev → revisión prioritaria, merge el mismo día
5  todos los carriles rebasan sobre dev
```

**Reglas del micro-PR**

- **No espera.** Mientras se revisa, el carril sigue con lo que no depende de eso.
- **Nunca** mezcla cambios de servicio con cambios compartidos: son dos PR.
- Si dos carriles piden el mismo átomo el mismo día, gana el primero y el segundo
  consume el suyo. **No se duplica el átomo** — un `calcularPlazoHabil` duplicado es
  la forma en que dos servicios empiezan a calcular plazos distintos.
- **Un micro-PR a `plataforma/` reconstruye los catorce servicios en CI.** Es correcto
  y es lento: es la razón de que plataforma se congele temprano y de que un micro-PR
  sea la excepción, no el hábito.

---

## 7 · Los contratos se escriben una ola antes — la obligación nueva

Esta sección no existía y es la que hace o rompe la concurrencia.

**El OpenAPI de un servicio tiene que estar escrito antes de que arranque el carril
que lo consume.** No implementado: **escrito**. Con el contrato, el consumidor genera
el cliente, programa contra un doble y avanza.

| El contrato de… | Tiene que existir al abrir… | Porque lo consume |
| --- | --- | --- |
| `identidad` | Ola 1 | Todos: es quien emite el token |
| `nucleo-financiero` (libro) | Ola 1 | `2A` lo extiende; `2B`, `3A`, `4A` lo llaman |
| `nucleo-financiero` (billetera) | Ola 2 | `3A` aportes, `4A` entregas, `4B` garantía |
| `tarifas` | Ola 2 | `3A` aportes cotiza y devenga |
| `grupos` | Ola 2 | `3A`, `3B`, `4B` |
| `notificaciones` | Ola 1 | Todos emiten avisos |

**Quién los escribe:** el borrador lo escribe la **Ola 0** para `identidad`,
`nucleo-financiero` y `notificaciones`, derivándolo de la sección **Contrato** de
cada `CU-NN`. Los demás los escribe el carril dueño **en su primer día**, antes que
cualquier código, y los publica en `dev` por micro-PR marcado `[CONTRATO]`.

> **Un contrato publicado se puede ampliar, no romper.** Agregar un campo opcional o
> una operación nueva es libre. Cambiar un tipo o quitar un campo rompe a quien ya
> generó el cliente: exige avisar y coordinar, y por eso el nivel de prueba de
> contrato falla en el CI **del que rompe**.

---

## 8 · Puntos de sincronización entre olas

Al cerrar una ola, **antes** de abrir la siguiente:

- [ ] Todos los carriles de la ola fusionaron a **`dev`**
- [ ] `dev` pasa el CI completo (los 19 pasos)
- [ ] `dev` → `main` solo cuando la ola cierra entera y verde
- [ ] Cada carril ejecutó su gate de fase y lo registró en su informe
- [ ] Los micro-PR pendientes están fusionados
- [ ] **Los contratos que la ola siguiente necesita están publicados en `dev`** (§7)
- [ ] **Las pruebas de contrato entre los pares que se llaman están en verde**
- [ ] Cada máquina hace `git pull` de `dev` y **no regenera el esquema**: está congelado
- [ ] Se actualiza `planes/informe.md` con el estado consolidado

**Prueba de integración entre olas.** Al cerrar cada ola, una máquina levanta el
perfil `todo` del compose y corre la suite E2E contra `main` fusionado. Un carril
verde en aislamiento y rojo integrado es información valiosa: casi siempre es un
átomo duplicado, un contrato mal asumido o una saga sin compensación.

---

## 9 · Montar una máquina nueva

Cada máquina es **independiente**: su clon, su rama, su Docker, su PostgreSQL. No hay
base compartida.

```bash
git clone <repo> && cd Pasanaku
git checkout -b <usuario>/feature/carril-<ola><id>-<servicio> origin/dev

docker compose --profile base up -d --wait   # postgres + pgbouncer + kafka
./gradlew bd:reset                            # esquema + roles + semillas + prueba de humo
./gradlew :servicios:<suyo>:generateJooq      # clases de SU esquema
./gradlew :servicios:<suyo>:build             # compila y corre sus pruebas

# las skills llegaron completas y la sesión las ve  (19 §1)
ls .claude/skills | grep -v README | wc -l     # 63
python3 scripts/verificar_boveda.py            # "índice de skills completo"

./gradlew :servicios:<suyo>:bootRun            # su servicio, solo el suyo
```

> **No se levantan los quince procesos.** Se levanta la infraestructura y **su**
> servicio. Contra los demás se programa por el contrato y se prueba con dobles. Si
> trabajar exigiera quince contenedores, esta arquitectura costaría más de lo que
> rinde ([[ADR-025 Empaquetado y despliegue de los servicios]]).

> **Antes de escribir el primer archivo**, el carril carga **sus** skills: las
> dieciocho transversales de backend más las propias de su servicio, según la tabla
> normativa de [[19 Contrato de carril · conflicto cero, skills y calidad verificada]] §2.
> Un carril que trabaja de memoria inventa exactamente lo que las skills existen para
> evitar.

Si algo de eso falla, **no es problema del carril**: es que `main` está roto y hay que
avisar antes de seguir.

Cada carril corre `test`, `integrationTest`, `contractTest` y, si su servicio
participa de una saga, `sagaTest`. **El `e2eTest` completo corre solo en `main`** y en
la Ola 5.

---

## 10 · Arranque de un carril — una sola línea

**El prompt de arranque dejó de pegarse a mano.** Está en la skill `arrancar-carril`,
que contiene el contrato entero: los cinco datos del carril, el orden de lectura
exacto, qué posee y qué no toca, los comandos de montaje, el ciclo de ocho pasos por
caso de uso, los doce invariantes condensados y el checklist de cierre.

En el chat de la máquina que toma el carril:

```text
/arrancar-carril

Carril <ID>, ola <N>. Servicio: <nombre>. CU: <lista>.
```

Y nada más. La skill pide lo que falte.

### Por qué esto y no el prompt pegado

| Prompt pegado a mano | Skill |
| --- | --- |
| Se copia mal, se recorta, envejece en cada máquina | Una sola fuente, versionada, que viaja con el clon |
| Manda leer cuatro documentos de plan enteros | Manda leer **solo** los CU del carril y el tramo exacto de lo demás |
| Cada máquina arranca con una variante distinta | Cinco máquinas arrancan idénticas |
| El contexto se llena antes de escribir la primera línea | §9 de la skill fija la economía de tokens |

> **Es la misma regla que gobierna todo este proyecto aplicada al prompt: lo que se
> genera o se versiona no tiene cinco variantes; lo que se copia a mano, sí.**

---

## 11 · Cuando dos carriles se pisan igual

Va a pasar. Qué hacer:

| Síntoma | Causa habitual | Qué se hace |
| --- | --- | --- |
| Conflicto en un archivo compartido | Alguien editó fuera de su propiedad | Se revierte, se abre micro-PR |
| Dos átomos con el mismo cálculo y nombres distintos | Ningún carril abrió micro-PR y ambos improvisaron | Se unifica en `plataforma/comun-dominio` y se borran los dos; **prioridad alta** |
| Un carril necesita un endpoint de otro que no existe | Dependencia no prevista | Se programa contra su **OpenAPI** y se prueba con un doble. El contrato existe antes que la implementación |
| **El contrato del otro carril tampoco existe** | Fallo de planificación de §7 | **Se escribe el contrato entre los dos, se publica por micro-PR `[CONTRATO]` el mismo día**, y recién ahí se programa |
| La prueba de contrato falla tras fusionar | Alguien cambió su OpenAPI de forma incompatible | Falla el CI **del que rompió**. Se revierte o se versiona la ruta |
| `dev` rojo tras fusionar una ola | Integración, no aislamiento | Se para la ola siguiente hasta arreglarlo |
| Un carril termina mucho antes | Estimación desigual | Toma un carril de la ola siguiente **solo si los contratos que consume ya están en `dev`** |

---

## 12 · Lo que **no** se paraleliza, nunca

- **Un cambio de modelo** (`docs/entidades/*.puml`, `docs/Restricciones.md`, `sql/`).
  Para todo, se hace en troncal, se regenera, se verifica la bóveda, se fusiona, y
  recién ahí los carriles rebasan. **La partición en servicios no compra
  independencia de modelo**: un cambio de esquema rompe la compilación de los catorce
  a la vez, y eso es correcto pero hay que hacerlo ordenado.
- **La Ola 0.** Es el piso; si se parte, cada carril inventa su propio piso.
- **Los dos tramos de un mismo servicio.** Cuando un servicio se construye o se
  retoma en más de un tramo, **sus dos tramos nunca corren a la vez**: son el mismo
  esquema, y dos carriles escribiéndolo en paralelo es el conflicto que este diseño
  evita. Vale para los tres que tienen esa forma
  ([[20 Saneamiento del plan · huecos de la migración a microservicios]] §7.2):
  `nucleo-financiero` (Fase 5 libro contable + Fase 6 billetera), `cumplimiento`
  (Fase 4 habilitación parcial + Fase 16 UIF/ASFI) y `auditoria`.
- **La Ola 5.** Rendimiento, resiliencia y despliegue se miden sobre el sistema
  entero, no por partes.

## Ver también

[[19 Contrato de carril · conflicto cero, skills y calidad verificada]] · [[18 Fichas de carril · las 38 unidades de trabajo]] · [[17 Plan de acción secuencial · coordinación de cinco máquinas]] · [[00 Plan maestro]] · [[00b Estándar de ejecución · código limpio, pruebas y calidad]] · [[00c Recetario · implementar un caso de uso]] · [[01 Fase 0 · Cimientos del repositorio]] · [[informe]]
