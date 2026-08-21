---
name: arrancar-carril
description: "El contrato operativo de una máquina que toma un carril de AportaYa: qué servicio posee, qué archivos no toca, en qué orden lee, qué comandos ejecuta y con qué evidencia cierra. Úsala como PRIMERA acción al abrir el chat de un carril, antes de leer ningún plan y antes de escribir el primer archivo. Reemplaza al prompt de arranque pegado a mano."
---

# Arrancar un carril

**Esta skill es lo primero que se carga en la máquina de un carril, y sustituye a
leer cuatro documentos de plan completos.** Contiene el contrato entero; lo demás se
lee **solo cuando esta skill lo manda**, y solo el tramo que manda.

> **Regla de esta skill: no ampliar el contexto sin necesidad.** Cada documento que
> se lee «por las dudas» son miles de tokens que no mejoran una sola línea de código.
> Lo que hace falta está acá o está enlazado con el tramo exacto.

---

## 1 · Los cinco datos del carril

Se completan **antes** de cualquier otra cosa. Si falta uno, se pregunta y se para.

| Dato | De dónde sale | Ejemplo |
| --- | --- | --- |
| `SERVICIO` | `planes/07` §3, tabla de la ola | `tarifas` |
| `ESQUEMA` · `ROL` | el servicio, con guion bajo | `tarifas` · `svc_tarifas` |
| `CU` | `planes/07` §3, columna CU | `30–36` |
| `OLA` · `CARRIL` | `planes/07` §3 | `2` · `B` |
| `RAMA` | fórmula fija | `<usuario>/feature/carril-2B-tarifas` |

---

## 2 · Orden de lectura — exacto, y nada más

**Se lee en este orden y se detiene acá.** No se leen los otros planes.

| # | Qué | Cuánto | Para qué |
| :-: | --- | --- | --- |
| 0 | `docs/Contrato de implementación para IA.md`, **entero** | ~2 min | Qué no se puede inventar, y los defaults ya elegidos |
| 1 | Esta skill, completa | — | El contrato |
| 2 | `docs/CasosDeUso/CU-<NN> *.md` — **todos los del carril, enteros** | todo | Es la especificación. **No se resume ni se saltea** |
| 3 | `docs/Restricciones.md` — **solo** los `R-XXX-nn` que citan esos CU | grep | Qué rechaza la base |
| 4 | Las skills de §4 | — | Cómo se escribe |
| 5 | `planes/<documento de tu fase>.md` — **solo tu sección** | 1 sección | Alcance y gate |

**Lo que NO se lee al arrancar:** el plan maestro entero, los otros documentos de
fase, los ADR completos, los CU de otros carriles. Si hace falta un ADR, esta skill
dice cuál y qué sección.

```bash
# el paso 3, sin leer las 2717 líneas de Restricciones
grep -ho 'R-[A-Z]\{3\}-[0-9]\{2\}' docs/CasosDeUso/CU-3*.md | sort -u
grep -A3 -E '^\| `(R-TAR-01|R-TAR-02)`' docs/Restricciones.md
```

---

## 3 · Qué poseés y qué no tocás

### Tuyo, en exclusiva

```
servicios/<SERVICIO>/**              TODO: build.gradle.kts, application.yml,
                                     openapi/, las 4 capas, trabajos/, pruebas,
                                     descriptor.yml, README.md
planes/informes/carril-<CARRIL>.md   tu informe
despliegue/compose/<SERVICIO>.yml    solo si necesitás un auxiliar
```

**No hay ningún archivo fuera de ahí que necesites tocar para entregar un caso de uso
completo.** Si creés que sí, la respuesta está en §7.

### Solo lectura — tocarlo es un rechazo automático

| Ruta | Quién la cambia |
| --- | --- |
| `sql/` `docs/` `scripts/` | Nadie durante los carriles |
| `plataforma/**` | Micro-PR (§7) |
| `gradle/libs.versions.toml` | Micro-PR. **Nunca** una dependencia en tu rama |
| `settings.gradle.kts` | Nadie: descubre por barrido |
| `despliegue/Dockerfile` `despliegue/k8s/` | Ola 0 y Ola 5 |
| `clientes/typescript/` | Generado |
| `.claude/skills/**` | Micro-PR |
| **El `openapi/` de otro servicio** | **Se lee** para generar su cliente. Nunca se edita |

---

## 4 · Qué skills cargás

**Las veinte de todo carril de backend**, en este orden:

| Grupo | Skills |
| --- | --- |
| Método (4) | `frontera-transaccional` · `implementar-desde-boveda` · `caso-de-uso` · `revision-codigo` |
| Forma (5) | `arquitectura-atomica` · `codigo-limpio` · `back-spring` · `servicios-y-sagas` · `glosario-dominio` |
| Datos y dinero (3) | `datos-jooq` · `dinero-decimal` · `contratos-api` |
| Corrección (5) | `errores-api` · `idempotencia-reintentos` · `seguridad-sesion-rls` · `seguridad-aplicacion` · `pruebas-cu` |
| Cierre (3) | `observabilidad` · `git-flujo` · `definicion-de-terminado` |

**Más las propias del carril**, según la tabla normativa de `planes/19` §2. Y esta
skill, `arrancar-carril`, que la carga **todo** carril antes que ninguna otra.

Si sos carril de backend, tu servicio tiene un `descriptor.yml` y vos lo poseés:
declarás su **nivel** de criticidad y **por qué**, y nunca menos de dos réplicas
([[ADR-037 Alta disponibilidad y balanceo]]).

> Un carril que no cargó las suyas está trabajando de memoria, y va a inventar
> exactamente lo que las skills existen para evitar.

---

## 5 · Montar la máquina — comandos exactos

```bash
git clone <repo> && cd Pasanaku
git checkout -b <RAMA> origin/dev

docker compose --profile base up -d --wait        # postgres + pgbouncer + kafka
./gradlew bd:reset                                 # esquemas + roles + semillas + humo
./gradlew :servicios:<SERVICIO>:generateJooq       # clases de TU esquema
./gradlew :servicios:<SERVICIO>:build

ls .claude/skills | grep -v README | wc -l         # 65
python3 scripts/verificar_boveda.py                # TODO OK
python3 scripts/verificar_carriles.py              # tu puesto dice lo mismo en los dos planes
python3 scripts/generar_k8s.py                     # tu descriptor cierra contra el pool

./gradlew :servicios:<SERVICIO>:bootRun            # tu servicio, solo el tuyo
```

**No se levantan los quince procesos.** Contra los otros trece se programa por su
OpenAPI y se prueba con dobles.

Si algo de esto falla, **no es problema tuyo**: `main` está roto. Avisá antes de
seguir.

---

## 6 · El ciclo por caso de uso — ocho pasos, sin variantes

Para cada `CU-<NN>`, en este orden. **Cada paso tiene una salida verificable.**

| # | Paso | Salida | Comando |
| :-: | --- | --- | --- |
| 0 | Declarar piezas por nivel + responder las **6 preguntas** de `frontera-transaccional` | Texto, esperando visto bueno | — |
| 1 | Generar el esqueleto | Archivos + pruebas **fallando** | `./gradlew nuevoCu -Pcu=<NN>` |
| 2 | Escribir el contrato en `openapi/<SERVICIO>.yaml` | Operación con entrada, salida y `AP-CU<NN>-<nn>` | `./gradlew generateOpenApiClients` |
| 2b | ¿Toca algo fuera del proceso? Puerto en `dominio/puertos/` + **adaptador local primero** | Interfaz + adaptador por omisión | `./gradlew test` |
| 3 | Átomos en `dominio/` | Cálculo puro, sin Spring ni jOOQ | `./gradlew test` |
| 4 | Moléculas en `infraestructura/` | Repositorios y clientes, sin lógica | `./gradlew integrationTest` |
| 5 | Organismo en `aplicacion/` | `@Transactional` + `conContexto` | `./gradlew integrationTest` |
| 6 | Página en `web/` | Implementa la interfaz generada | `./gradlew integrationTest` |
| 7 | Las **7 pruebas obligatorias** en verde | Ninguna `@Disabled` | `./gradlew test integrationTest contractTest sagaTest` |

**El paso 0 no se saltea.** Es donde se decide bien o mal, y cuesta cien veces menos
que descubrirlo en el paso 6.

**Los defaults no se eligen: ya están elegidos.** Archivos → adaptador local en disco.
Mensajería → bandeja interna y correo, con push como aviso; WhatsApp y SMS apagados.
Pagos y facturación → simulador. Móvil → Android, y iOS por pase de paridad. Semillas
→ `minimos/` para producción, `dev/` para todo lo demás, y el generador rechaza que se
crucen. Apartarse de cualquiera de esos es un ADR, no una decisión de implementación
(`docs/Contrato de implementación para IA.md` §7).

---

## 7 · Cuando necesitás algo que no poseés

| Necesitás | Hacé esto | **No** hagas esto |
| --- | --- | --- |
| Un átomo compartido (`Dinero`, `PlazoHabil`) | Micro-PR a `plataforma/comun-dominio` | Duplicarlo en tu servicio |
| Una dependencia nueva | Micro-PR al catálogo de versiones | Agregarla en tu `build.gradle.kts` |
| Un dato de otro servicio | Generar su cliente desde su OpenAPI | `SELECT` sobre su esquema |
| Que otro servicio escriba dinero | Llamar a `nucleo-financiero` con clave de idempotencia | Escribir el libro vos |
| Un endpoint de otro que no existe | Programar contra su OpenAPI + doble | Esperar a que lo implemente |
| **Su OpenAPI tampoco existe** | Escribirlo entre los dos, micro-PR `[CONTRATO]` **el mismo día** | Inventar la forma y ajustar después |
| Cambiar el modelo de datos | **PARÁS.** Es troncal, no de carril | Editar `sql/` o un `.puml` |

**Micro-PR:** rama `<usuario>/chore/troncal-<CARRIL>-<qué>`, un solo cambio, con su
prueba, marcado `[MICRO]`, hacia `dev`. **No esperás**: seguís con lo que no depende.

---

## 8 · Las reglas que no se negocian

**Regla cero — no inventar.** La respuesta está en el CU, en `Restricciones` o en
`Cumplimiento`. Si falta algo **crítico**, PARÁS Y PREGUNTÁS. Si no es crítico,
declarás el supuesto por escrito en tu informe. **Un supuesto silencioso es el
defecto más caro de este proyecto.**

Los doce invariantes de `planes/00` §1, condensados — si tu código viola uno, está mal
aunque pase las pruebas:

| # | En una línea |
| :-: | --- |
| 1 | El esquema es de `sql/`. Las clases se generan; si el esquema cambió y no regeneraste, no compila |
| 2 | Una transacción por caso de uso. `@Transactional` **solo** en `aplicacion/` |
| 3 | `SET LOCAL` dentro de la transacción. Nunca `SET` plano |
| 4 | Ningún importe en `double`/`float`. `compareTo`, nunca `equals` |
| 5 | Append-only: corrección = movimiento inverso, jamás `UPDATE` |
| 6 | Ninguna llamada de red dentro de la transacción. Ni proveedor ni **otro servicio** |
| 7 | La clave de idempotencia se valida **antes** de escribir |
| 8 | Los plazos se persisten al crear, nunca se recalculan al consultar |
| 9 | Denegar por omisión: sin límite, licencia o tarifario vigente ⇒ rechazo |
| 10 | Umbrales y tarifas son catálogo, no constantes |
| 11 | **No leés la base de otro servicio.** Con ninguna excusa |
| 12 | **El libro contable no se parte.** Solo `nucleo-financiero` lo escribe |

**Commits** en español, con prefijo y citando el CU:
`feat: CU-21 cobrar aporte con QR`. Nunca «arreglos varios». Nunca a `main` ni `dev`.

**Tu PR trae el cambio completo:** contrato + código + pruebas. Un PR que deja la
bóveda vieja crea dos verdades.

---

## 9 · Economía de tokens — cómo no desperdiciar la sesión

| Hacé | En vez de |
| --- | --- |
| `grep` la restricción concreta | Leer `docs/Restricciones.md` entero (2.717 líneas) |
| Leer **tus** CU completos | Leer el índice de los 99 |
| Abrir el ADR que esta skill nombra, en su sección | Leer los 26 ADR |
| Leer **tu** sección del documento de fase | Leer el documento de fase entero |
| Pedir el archivo que vas a editar | Pedir el árbol del repositorio |
| Reusar lo que ya leíste en la sesión | Volver a leerlo «para confirmar» |

> **Un carril bien arrancado no necesita releer nada.** Si estás releyendo, es que el
> paso 0 quedó incompleto.

---

## 10 · Terminás cuando

**Cada casilla se marca con la salida del comando pegada abajo, no con una
afirmación** (`definicion-de-terminado`).

```markdown
### Cierre del carril <CARRIL> — servicio <SERVICIO>

- [ ] Las 20 skills + las propias estaban cargadas antes del primer archivo
- [ ] Piezas declaradas por nivel y las 6 preguntas respondidas, por CU

**Generado, no escrito a mano**
- [ ] El servicio salió de `./gradlew nuevoServicio`
- [ ] Cada CU salió de `./gradlew nuevoCu`

**Verificado por máquina** — salida pegada
- [ ] `./gradlew spotlessCheck check`
- [ ] `./gradlew generateJooq compileJava`
- [ ] `./gradlew test integrationTest contractTest sagaTest`
- [ ] `./gradlew testBarrido` — los 12 locales (los 3 entre servicios corren en integración)
- [ ] `python3 scripts/verificar_criterios.py`
- [ ] Cobertura sobre el piso de su ámbito
- [ ] `AislamientoEsquemaTest` — no leo ningún esquema ajeno

**Transversales**
- [ ] README del servicio al día, sin duplicar la bóveda
- [ ] Traza, métricas y evento de outbox por caso de uso
- [ ] Permiso declarado por endpoint · límite de tasa donde corresponde
- [ ] Timeout y cortacircuitos en cada cliente y adaptador
- [ ] Sin N+1 · listados paginados

**Lo que no verifica ninguna máquina** — se responde por escrito:
- [ ] ¿Los nombres dicen lo que las cosas son?
- [ ] ¿La frontera transaccional es la correcta, o solo pasa las pruebas?
- [ ] ¿Qué supuse que no estaba en la bóveda?
- [ ] ¿Qué dejé peor de como lo encontré?
```

## Ver también

`frontera-transaccional` · `implementar-desde-boveda` · `back-spring` ·
`servicios-y-sagas` · `pruebas-cu` · `definicion-de-terminado` · `git-flujo`
