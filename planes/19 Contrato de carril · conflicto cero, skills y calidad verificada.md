---
tags:
  - moc
  - plan
  - carriles
  - calidad
titulo: "Contrato de carril — conflicto cero, skills y calidad verificada por máquina"
fecha: 2026-08-16
alcance: qué entrega todo carril, qué lo verifica, y qué hace imposible el conflicto
---

# Contrato de carril

> **Qué agrega este documento.** [[17 Plan de acción secuencial · coordinación de cinco máquinas]]
> dice **cuándo y dónde**. [[18 Fichas de carril · las 38 unidades de trabajo]] dice
> **qué es cada carril**. Este dice **qué entrega todo carril sin excepción, y qué
> máquina lo verifica**.

> [!important] La única idea de este documento
> **Nada se promete: todo se verifica.** Un carril no «tiene cuidado» con la
> observabilidad, con el endurecimiento o con los nombres: entrega piezas concretas, y
> una prueba escrita en la Ola 0 —que él no escribe y no puede desactivar— falla si no
> están. Con cinco carriles concurrentes y **un solo revisor**, todo control que
> dependa de que alguien se acuerde, no existe.

**Los tres mecanismos, en orden de fuerza:**

| # | Mecanismo | Por qué es más fuerte que el anterior |
| :-: | --- | --- |
| 1 | **Se genera** | Si la estructura la escribe un generador, no hay dos formas de escribirla |
| 2 | **Se barre** | Una prueba que **enumera** todas las rutas, trabajos y métricas cubre también las que todavía no existen |
| 3 | **Se deriva** | Un archivo generado desde la bóveda no puede divergir de la bóveda |

Lo que queda para la revisión humana es lo que ninguna máquina puede juzgar: si el
nombre dice lo que la cosa es, y si la frontera transaccional es la correcta.

---

## 1 · Arranque de máquina — las skills

Las **65 skills** del proyecto están versionadas en `.claude/skills/`: **viajan con el
clon**. Pero «viajan» no es «llegaron», y una máquina que arranca un carril sin las
skills de su dominio va a inventar exactamente lo que las skills existen para evitar.

### Verificación obligatoria, en cada máquina, antes de abrir cualquier carril

```bash
# 1 · llegaron todas
ls .claude/skills | grep -v README | wc -l          # tiene que dar 65

# 2 · el índice coincide con las carpetas
python3 scripts/verificar_boveda.py                 # "índice de skills completo (65 skills)"
                                                    # + "frontmatter de cada skill coincide con su carpeta"

# 3 · las ve la sesión
#    en el chat del puesto: /skills  → tienen que aparecer las 65 del proyecto
```

**Si falta alguna: no se copia a mano.** Se restaura desde el repositorio:

```bash
git checkout -- .claude/skills          # si se borraron localmente
git fetch && git rebase origin/dev      # si el clon quedó viejo
```

> [!warning] `.claude/` faltaba en la lista de solo lectura
> [[07 Carriles de trabajo concurrente]] §4 enumera lo que ningún carril toca y **no
> incluye `.claude/`**. Con cinco carriles, dos que ajusten la misma skill producen el
> conflicto que todo ese diseño evita. **Corregido: `.claude/skills/**` es de solo
> lectura para todos los carriles.** Una skill nueva o modificada es un **micro-PR al
> troncal**, igual que un átomo de `plataforma/dominio`.

### Skills de usuario — las que **no** viajan

Las skills instaladas fuera del repositorio (en `~/.claude/skills/`) **no están en el
clon**: si un puesto depende de una, hay que instalarla en esa máquina o no contar con
ella. La regla del proyecto es simple: **ninguna decisión de AportaYa puede depender de
una skill que no esté versionada en el repositorio.** Si una skill de usuario resulta
necesaria para el trabajo, se incorpora al repositorio por micro-PR y pasa a ser de
todos.

---

## 2 · Qué skills carga cada carril

Un carril **lee sus skills antes de escribir el primer archivo**, no cuando se traba.

### La que carga **todo** carril, sea del tipo que sea

| Skill | Cuándo | Por qué es aparte |
| --- | --- | --- |
| `arrancar-carril` | **Antes de leer ningún plan y antes del primer archivo** | Es el contrato operativo del puesto: qué posee, qué no toca, en qué orden lee. No está en la tabla de abajo porque no depende del carril: se carga siempre |

### Las veinte de todo carril de backend

Se cargan siempre, en este orden. Son el idioma común: sin ellas, cinco carriles
producen cinco estilos.

| Grupo | Skills |
| --- | --- |
| **Método** (4) | `frontera-transaccional` · `implementar-desde-boveda` · `caso-de-uso` · `revision-codigo` |
| **Forma del código** (5) | `arquitectura-atomica` · `codigo-limpio` · `back-spring` · `servicios-y-sagas` · `glosario-dominio` |
| **Datos y dinero** (3) | `datos-jooq` · `dinero-decimal` · `contratos-api` |
| **Corrección** (5) | `errores-api` · `idempotencia-reintentos` · `seguridad-sesion-rls` · `seguridad-aplicacion` · `pruebas-cu` |
| **Cierre** (3) | `observabilidad` · `git-flujo` · `definicion-de-terminado` |

### Las doce de todo carril de frontend

| Grupo | Skills |
| --- | --- |
| **Producto** (1) | `movil-expo` **o** `web-backoffice`, según el carril |
| **Diseño** (2) | `disenar-frontend` · `arquitectura-atomica` |
| **Forma** (3) | `codigo-limpio` · `glosario-dominio` · `contratos-api` *(de lectura: los contratos son del backend)* |
| **Dinero** (1) | `dinero-decimal` |
| **Seguridad** (1) | `seguridad-aplicacion` |
| **Cierre** (4) | `revision-codigo` · `git-flujo` · `definicion-de-terminado` · `observabilidad` |

### Y las propias de cada carril

Se suman a las anteriores. **Esta tabla es normativa**: un carril que no cargó las
suyas está trabajando de memoria.

| Carril | Skills propias |
| :-: | --- |
| `T0` `T1` `T2` troncal | `entorno-monorepo` · `decisiones-adr` · `ci-calidad` · `despliegue-contenedores` · `restriccion` · `boveda-modelo` · `semillas-catalogos` · `trabajos-outbox` · `resiliencia-rendimiento` |
| `1A` identidad | `autenticacion-jwt` · `kyc-onboarding` · `roles-y-accesos` |
| `1B` contable | `contabilidad-partida-doble` |
| `1C` habilitación | `kyc-onboarding` · `cumplimiento-uif` · `norma-nueva` · `motor-de-reglas` |
| `1D` notificaciones | `notificaciones-consentimiento` · `trabajos-outbox` · `proveedores-externos` |
| `2A` billetera | `contabilidad-partida-doble` · `cumplimiento-uif` · `motor-de-reglas` · `resiliencia-rendimiento` |
| `2B` tarifas | `facturacion-sin` · `semillas-catalogos` · `reembolsos-disputas` |
| `2C` grupos | `gobernanza-grupo` · `sorteo-transparencia` · `plazos-habiles` · `emparejamiento-ingreso` |
| `2D` auditoría | `extraccion-de-datos` · `indicadores-tablero` · `lecturas-proyecciones` |
| `2E` organizador | `organizador-habilitacion` · `automatizacion-tareas` · `motor-de-reglas` · `debido-proceso` |
| `3A` aportes | `qr-pagos` · `proveedores-externos` · `contabilidad-partida-doble` · `reembolsos-disputas` |
| `3B` transparencia | `sorteo-transparencia` · `reputacion-social` · `alertas-riesgo-temprano` |
| `3C` cumplimiento | `cumplimiento-uif` · `reportes-regulatorios` · `reclamos-consumidor` · `gobierno-comites` · `plazos-habiles` · `norma-nueva` · `debido-proceso` |
| `3D` cuenta bancaria | `desembolsos-payouts` |
| `4A` entregas | `desembolsos-payouts` · `contabilidad-partida-doble` |
| `4B` garantía | `garantia-mora-cobranza` · `debido-proceso` · `plazos-habiles` |
| `5T` convergencia | `resiliencia-rendimiento` · `respaldos-restauracion` · `despliegue-contenedores` · `ci-calidad` · `documentacion-entregables` · `observabilidad` |
| `5A` ERP ★ | `contabilidad-partida-doble` · `facturacion-sin` · `plan-por-fases` · `caso-de-uso` |
| `5B` publicidad ★ | `facturacion-sin` · `motor-de-reglas` · `proveedores-externos` |
| `F0-M` `F0-B` `F0-W` andamiajes | `decisiones-adr` · `ci-calidad` |
| `F1` sistema de diseño | `disenar-frontend` **completa**, con `docs/Views/Sistema-Diseno/` |
| `F2` shell móvil | `movil-expo` · `autenticacion-jwt` *(de lectura: el `ProveedorSesion`, refresh y `401`)* · `resiliencia-rendimiento` *(offline e intermitencia)* |
| `F3` móvil identidad | `kyc-onboarding` · `autenticacion-jwt` |
| `F4` móvil billetera | `qr-pagos` · `dinero-decimal` |
| `F5` móvil pasanaku | `gobernanza-grupo` · `alertas-riesgo-temprano` · `reclamos-consumidor` |
| `F6` shell backoffice | `web-backoffice` · `roles-y-accesos` *(navegación y tabla según rol)* |
| `F7` backoffice operación | `roles-y-accesos` · `extraccion-de-datos` |
| `F8` backoffice cumplimiento | `cumplimiento-uif` · `debido-proceso` · `gobierno-comites` |
| `F9` sitio público | `sorteo-transparencia` · `reputacion-social` · `reclamos-consumidor` |
| `F10` `F11` SEO y GEO | `documentacion-entregables` |
| `F12` publicación | `definicion-de-terminado` · `despliegue-contenedores` |
| `F13` `F14` backoffice nuevos ★ | `contabilidad-partida-doble` / `facturacion-sin` |

> **Los carriles `5A`, `5B`, `F13` y `F14` cargan además `plan-por-fases` y
> `caso-de-uso`**, porque sus documentos de fase todavía no existen: el carril empieza
> escribiendo el documento que le falta.

---

## 3 · Conflicto cero — el espacio de nombres del módulo

[[07 Carriles de trabajo concurrente]] §5 elimina siete conflictos y
[[16 Carriles de frontend]] §5 otros seis. Todos son **conflictos de archivo**. Faltan
los que no producen conflicto de merge pero rompen igual: **dos carriles eligiendo el
mismo nombre global.** Nadie ve nada en el `git diff`; el sistema se rompe al integrar.

> **La regla única que los elimina a todos:**
> **todo identificador global lleva el prefijo de su módulo, y el CI falla si hay dos
> iguales.** El módulo es la unidad de propiedad, así que también es la unidad de
> nombres.

| # | Identificador global | Formato obligatorio | Ejemplo | Verificado por |
| :-: | --- | --- | --- | --- |
| 8 | Prefijo de ruta HTTP | el reservado del módulo (§3.1) | `/billetera/*` | barrido 2 |
| 9 | Nombre de trabajo programado (ShedLock) | `<modulo>.<trabajo>` | `nucleo_financiero.conciliar_custodia` | barrido 7 |
| 10 | Tema de Kafka | `aportaya.<modulo>.<evento>` | `aportaya.nucleo_financiero.saldo_retenido` | barrido 7 |
| 11 | Nombre de métrica | `aportaya_<modulo>_<nombre>_<unidad>` | `aportaya_billetera_recargas_total` | barrido 7 |
| 12 | Nombre de traza | `<servicio>.CU-<NN>` | `nucleo-financiero.CU-10` | barrido 1 |
| 13 | Clave de bloqueo consultivo | `hashtext('<modulo>.<recurso>')` — **nunca un entero literal** | — | barrido 7 |
| 14 | Plantilla de notificación | `<modulo>.<plantilla>` | `garantia.aviso_mora` | barrido 7 |
| 15 | Clave de configuración | `aportaya.<dominio>.<nombre>` en **el `application.yml` del servicio** | `aportaya.billetera.limite-diario` | arranque |
| 16 | Auxiliar de Docker | `despliegue/compose/<servicio>.yml` — **el base no se edita jamás** | — | §3.2 |
| 17 | Código de error | `AP-CU<NN>-<nn>` | `AP-CU21-03` | **derivado** de `docs/Restricciones.md` |
| 18 | Skill del proyecto | `.claude/skills/**` es **solo lectura**; cambio = micro-PR | — | §1 |
| 19 | Esquema y rol de base | `<modulo>` · `svc_<modulo>` | `tarifas` · `svc_tarifas` | `AislamientoEsquemaTest` |
| 20 | Nombre de servicio y paquete | `kebab-case` · `bo.aportaya.<servicio>` | `nucleo-financiero` | ArchUnit |

### 3.1 · Prefijos de ruta reservados

Se fijan en la Ola 0 y **no se negocian después**. Una ruta fuera del prefijo de su
módulo es un rechazo automático, no una discusión de diseño.

| Módulo | Prefijos |
| --- | --- |
| `01_identidad_usuarios` | `/identidad` · `/usuarios` · `/sesion` · `/roles` |
| `02_grupos_turnos` | `/grupos` · `/turnos` · `/acuerdos` |
| `03_contabilidad` (`1B`) | `/contabilidad` |
| `03_aportes_pagos_qr` (`3A`) | `/aportes` · `/pagos` · `/qr` · `/conciliacion` |
| `04_entregas_fondo` | `/entregas` · `/desembolsos` · `/cuentas-bancarias` |
| `05_notificaciones` | `/notificaciones` |
| `06_transparencia_reputacion` | `/reputacion` · **`/publico`** · **`/verificar`** |
| `07_organizador_automatizacion` | `/organizadores` · `/automatizacion` |
| `08_garantia_incumplimiento` | `/garantia` · `/incumplimientos` · `/cobranza` |
| `09_auditoria_reportes` | `/auditoria` · `/reportes` · `/indicadores` |
| `10_billetera_custodia` | `/billetera` · `/custodia` · `/puntos-atencion` |
| `11_tarifas_comisiones` | `/tarifas` · `/comisiones` · `/facturas` |
| `12_cumplimiento_asfi` | `/cumplimiento` · `/uif` · `/reclamos` · `/licencia` |
| `13_contabilidad_erp` ★ | `/erp` |
| `14_publicidad_campanas` ★ | `/publicidad` · `/campanas` · `/anunciantes` |

> **`/publico` y `/verificar` son de `06`, y son las únicas rutas sin sesión.** Que
> tengan un solo dueño es lo que permite que el barrido 1 (toda ruta exige sesión)
> tenga **exactamente una excepción declarada** en vez de una lista que crece sola.

### 3.2 · Los dos archivos compartidos que quedaban

| Archivo | El conflicto | La solución |
| --- | --- | --- |
| ~~**`.env.example`**~~ | *Ya no existe.* Con un servicio por carril, la configuración es el `application.yml` **dentro** del servicio | **El conflicto desapareció con la arquitectura**, no se neutralizó: no hay archivo de configuración compartido que ampliar ([[ADR-014 Arquitectura de servicios]]) |
| **`despliegue/compose/base.yml`** | Un carril necesita un auxiliar (un simulador de pasarela) | **No se edita.** Se agrega `despliegue/compose/<servicio>.yml` y se levanta con su perfil. El base es de la Ola 0 y de la Ola 5 |

---

## 4 · Lo que escribe la máquina, no el carril

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

**El código más limpio es el que nadie escribió dos veces de dos maneras.** Tres
generadores, construidos en la Ola 0, eliminan la variación estructural entre carriles.

### `./gradlew nuevoServicio -Pnombre=<x>` · hoy `python3 scripts/nuevo_servicio.py <x>`

Crea el **servicio entero**: las cuatro capas, el `build.gradle.kts` con los plugins de
convención, el `application.yml` con su configuración validada, el `openapi/<x>.yaml`
esqueleto, el `descriptor.yml` que genera el manifiesto de Kubernetes, el registro de
métricas con el prefijo correcto, el `README.md` con su tabla de CU vacía, el
directorio de fixtures y la clase de ArchUnit.

**Nadie escribe la estructura de un servicio a mano.** Es la diferencia entre catorce
servicios iguales y catorce servicios parecidos — y con desplegables separados, la
variación no se ve en ningún diff: se descubre en producción.

### `./gradlew nuevoCu -Pcu=<NN>` · hoy `python3 scripts/nuevo_cu.py <NN>`

Lee `docs/CasosDeUso/CU-<NN> *.md` y genera:

| Genera | Desde |
| --- | --- |
| `La operación en `openapi/<servicio>.yaml`` con entrada, salida y códigos de error | sección **Contrato** del CU |
| El esqueleto de `aplicacion/` con `@Transactional` y `conContexto` ya puestos | sección **Descomposición atómica** |
| El controlador que **implementa la interfaz generada**, con su permiso declarado | sección **Eventos, trabajos y permisos** |
| **Una prueba por cada bloque `gherkin`, con el mismo nombre, fallando** | sección **Criterios de aceptación** |
| **Una prueba de rechazo por cada `R-XXX-nn` citada, fallando** | sección **Restricciones aplicables** |

> **Las pruebas nacen fallando, y eso es el punto.** Un criterio de aceptación
> olvidado no es una prueba ausente que nadie nota: es **el build en rojo**. El carril
> no decide qué probar — decide cómo hacer pasar lo que ya está escrito.

### `python3 scripts/verificar_criterios.py`

Extiende `scripts/verificar_boveda.py`. Compara, para cada CU, **los bloques `gherkin`
de la bóveda contra las pruebas del archivo**, y falla si:

- hay un criterio sin su prueba — el CU está incompleto y el informe no puede decir que
  está implementado;
- hay una prueba que no corresponde a ningún criterio — o sobra, o el criterio se cambió
  en el código y no en la bóveda, que es peor;
- hay un `R-XXX-nn` citado sin prueba de rechazo.

**Esto convierte en mecánica la definición que hoy es prosa** en [[informe]]: «un CU
cuenta como implementado cuando todos sus criterios de aceptación tienen su prueba con
el mismo nombre». Deja de ser una afirmación de quien escribe el informe.

### Cifras citadas — ya está construido y ya encontró cuatro errores

`verificar_boveda.py` recalcula las cifras del modelo y **falla si algún plan cita
otra**. Corre en el paso 6 del CI, así que ya es bloqueante.

Es el mismo principio de «se deriva» aplicado al plan: un número escrito a mano en
prosa diverge del modelo en cuanto el modelo crece. **La primera corrida encontró
cuatro divergencias reales:**

| Los planes decían | Es | Dónde importaba |
| :-: | :-: | --- |
| `87 casos de uso` | **99** | el conteo del informe y el alcance de los carriles |
| `305 tablas` | **305** | **el gate de salida de la Fase 0**, que verifica ese número |
| `566 relaciones` | **633** | la descripción del modelo |
| `124 restricciones` | **138** | el gate de la fase 17: «cada restricción con prueba de rechazo» |

> Las cifras de esa primera columna van **en formato de código a propósito**: el
> verificador ignora lo que está entre acentos graves, porque ahí es una **cita**, no
> una afirmación. Un gate que no permite citar el error que corrige es un gate que
> obliga a mentir en la documentación.

> **Un gate con un número viejo es peor que no tener gate**: pasa en verde mientras
> afirma algo falso. Las 33 tablas de los módulos 13 y 14 no estaban en ninguna
> verificación.

Distingue el total del subconjunto declarado: `87 casos de uso **del núcleo**` es
válido y no falla; `87 casos de uso`, a secas, no.

---

## 5 · Las diecisiete pruebas de barrido

**Se escriben una vez, en la Ola 0, y cubren para siempre todo lo que venga después.**
No enumeran casos: enumeran **el registro vivo** de rutas, trabajos, eventos y métricas,
y afirman una propiedad sobre todos. Un carril nuevo queda cubierto sin que nadie
agregue nada.

Ningún carril las escribe, ninguno las modifica, **ninguno puede marcarlas como
`skip`**: el CI falla si el número de pruebas activas baja.

### Los que corren **dentro** de cada servicio

Viven en `plataforma/comun-pruebas` y cada servicio los hereda al construirse. Un
servicio nuevo queda cubierto sin que nadie agregue nada.

| # | Barrido | Qué afirma sobre **todas** | Preocupación |
| :-: | --- | --- | --- |
| 1 | **Sesión** | Toda ruta registrada responde `401` sin token y `403` con rol insuficiente. Excepción única declarada: los prefijos `/publico` y `/verificar` de `transparencia` | hardening |
| 2 | **Prefijo** | Toda ruta del servicio cae bajo **su** prefijo reservado de §3.1 | conflicto |
| 3 | **Idempotencia** | Toda ruta con efecto acepta `Idempotency-Key`, y repetirla con la misma clave devuelve **la misma respuesta**, no un segundo efecto | resiliencia |
| 4 | **Una transacción** | Instrumentando el pool: cada petición con efecto abre **exactamente un** `BEGIN…COMMIT`. Cero es un caso de uso sin transacción; dos es una frontera mal puesta | corrección |
| 5 | **Fuga** | Ninguna respuesta de error contiene nombre de tabla, de restricción, ruta de archivo ni traza de pila. Se prueba forzando cada restricción | hardening |
| 6 | **Dinero** | Todo campo monetario de toda respuesta se serializa como **cadena decimal**. Un número JSON en un importe es un fallo, no una advertencia | corrección |
| 7 | **Nombres** | Trabajos, temas, métricas, plantillas y claves de bloqueo: **con prefijo de módulo** | conflicto |
| 8 | **PII en registros** | Un fixture con datos personales marcados atraviesa cada caso de uso; **ningún registro, traza ni métrica los contiene** | hardening |
| 9 | **N+1** | Contando consultas por petición: ninguna crece con el tamaño del resultado. Se prueba con 1, 10 y 100 filas y se compara | eficiencia |
| 10 | **Corte** | Todo adaptador externo **y todo cliente de otro servicio** declara timeout y cortacircuitos. Con la dependencia caída, el endpoint responde **dentro del presupuesto** y no cuelga | resiliencia |
| 11 | **Contrato implementado** | Toda operación del `openapi/` del servicio tiene su controlador, y ningún controlador expone una ruta que no esté en el contrato | corrección |
| 12 | **Documentación viva** | El servicio tiene su `README.md` y su tabla de CU **coincide con los CU realmente registrados** en el código | documentación |

### Los cuatro que solo pueden correr **entre** servicios

Estos son nuevos, y son la contrapartida de haber partido el despliegue: **ninguno se
puede evaluar dentro de un servicio.** Corren en el CI de integración y en la Ola 5.

| # | Barrido | Qué afirma | Por qué no puede ser local |
| :-: | --- | --- | --- |
| 13 | **Prefijo sin dos dueños** | Juntando los catorce `openapi/`, ningún prefijo de ruta aparece en dos servicios | Un servicio no ve las rutas de los otros. Con un solo proceso chocaban al arrancar; ahora **nadie se entera** hasta que el gateway enruta mal |
| 14 | **Outbox sin huérfanos** | Todo tema emitido por alguien tiene al menos un consumidor registrado, y todo consumidor escucha un tema que alguien emite | El emisor y el consumidor son procesos distintos |
| 15 | **Aislamiento de esquema** | Por cada par de servicios, el rol de uno **no puede leer** el esquema del otro | Es una propiedad de los `GRANT`, no del código |
| 16 | **Rutas del gateway** | Juntando los archivos `plataforma/gateway/**/rutas/<servicio>.yml`, ningún prefijo se repite y **todo** prefijo está en la lista reservada de §3.1 | La tabla de rutas del gateway se compone desde un archivo por servicio ([[20 Saneamiento del plan · huecos de la migración a microservicios]] §3.1); el choque solo se ve al juntarlos |

### El que corre sobre el **frontend**

Vive en el CI de frontend (`test:front`), no en el de servicios, y cubre el único
recurso compartido que quedó entre carriles de interfaz: los mocks de MSW.

| # | Barrido | Qué afirma | Preocupación |
| :-: | --- | --- | --- |
| 17 | **Mocks sin duplicar** | En `pruebas/mocks/`, **ningún CU tiene dos handlers**: el primer carril que necesita un CU crea su handler y el segundo lo importa ([[20 Saneamiento del plan · huecos de la migración a microservicios]] §6.3) | conflicto |

> **El barrido 13 es el que reemplaza a una garantía que se perdió.** Con un solo
> proceso, dos rutas iguales rompían el arranque y alguien lo veía en el acto. Con
> catorce, dos servicios pueden reclamar el mismo prefijo y el `git diff` no muestra
> nada. Es el precio de la partición, y se paga con esta prueba.

> **Por qué el barrido es más fuerte que la revisión.** Con cinco carriles y un solo
> revisor, revisar cada endpoint nuevo no escala. Enumerar todos los endpoints, sí — y
> además cubre el endpoint que se agregue dentro de seis semanas, cuando ya nadie se
> acuerde de esta lista.

---

## 6 · Los presupuestos

Un objetivo sin número es una intención. Estos se fijan **provisorios ahora** y se
**recalibran con la medición real de la fase 17**; lo que no se puede es no tener
ninguno, porque entonces «va lento» es una opinión.

| Presupuesto | Valor provisorio | Se mide en | Bloquea |
| --- | :-: | --- | :-: |
| p95 de lectura sobre la semilla de prueba | ≤ 200 ms | `test:api` en P2 | sí |
| p95 de operación de dinero | ≤ 400 ms | `test:api` en P2 | sí |
| Consultas por request | ≤ 10 | barrido 9 | sí |
| Respuesta con el proveedor externo caído | ≤ 2 s | barrido 10 | sí |
| Arranque del proceso hasta `/salud/listo` | ≤ 5 s | compose | sí |
| Tamaño de la imagen de runtime | ≤ 300 MB | `docker build` | advierte |
| JS inicial del sitio público | ≤ 150 KB | Lighthouse CI | sí |
| Arranque de la app en Android de gama baja | ≤ 3 s | `F12` | sí |
| Tamaño de archivo | 220 advierte · 260 revisión · **300 bloquea** | lint | sí |
| Cobertura | los pisos de §6 del [[00 Plan maestro]] | CI | sí |
| Dependencias nuevas en rama de carril | **0** | `gradle/libs.versions.toml` sin diff | sí |

> **El presupuesto se mide en la máquina de medición.** Los números de **latencia de
> backend** salen de **P2 · Ubuntu**, con Docker nativo. Un p95 medido bajo la VM de
> macOS o bajo WSL2 mide la virtualización, no el sistema
> ([[17 Plan de acción secuencial · coordinación de cinco máquinas]] §7). Los
> **presupuestos de frontend** —JS del sitio, Lighthouse y CWV— se miden en **P5**, la
> máquina que trabaja el sitio; **P2 no toca frontend en ningún tramo**, solo publica
> el número en el informe.

---

## 7 · Las cinco preocupaciones transversales

Para cada una: **qué entrega el carril** y **qué lo verifica**. La columna de la
derecha es la que importa: si está vacía, la de la izquierda es una intención.

### 7.1 · Documentación

| Entrega del carril | Verificación |
| --- | --- |
| `README.md` del módulo: qué resuelve, sus CU, sus eventos, sus trabajos, sus variables de entorno | **barrido 12** — la tabla de CU coincide con lo registrado |
| Contrato OpenAPI por CU, con ejemplos que **son** los del CU | paso 13 del CI: OpenAPI genera y **valida contra los ejemplos de la bóveda** |
| Los supuestos declarados, en `informes/carril-P<N>.md` | revisión de cierre de tramo |
| Un ADR por cada decisión cara de revertir | `verificar_boveda.py`: seis secciones obligatorias, estado válido, sin números repetidos |
| **Nada duplicado** | La bóveda es la fuente; el README del módulo **enlaza**, no repite. Un dato en dos lugares diverge |

> **Lo que no se documenta:** lo que el código ya dice. Un comentario que repite el
> nombre de la función es ruido; uno que explica **por qué** esa regla existe, es la
> única documentación que sobrevive (`codigo-limpio`).

### 7.2 · Observabilidad

| Entrega del carril | Verificación |
| --- | --- |
| Una **traza** por caso de uso, nombrada `<modulo>.CU-<NN>`, con `x-request-id` propagado | barrido 1 y 12 |
| **Registro estructurado** con Logback: sin `console.*`, sin PII, con `traza` | lint `sin-console-log` + **barrido 8** |
| **Métricas** con prefijo de módulo: contador de operaciones, histograma de latencia, contador de rechazos por código de error | barrido 7 |
| Un **evento de dominio en el outbox** por cada hecho relevante | barrido 11 |
| **Bitácora** de toda operación que cambia dinero, estado legal o permisos | prueba por CU: la operación deja su rastro |
| Un **evento de riesgo operativo** cuando algo que debía cuadrar no cuadra | prueba de `2A` (custodia) y `3A` (conciliación) |

> **La prueba de fuego de este bloque:** después de una operación fallida en
> producción, ¿se puede reconstruir qué pasó **sin acceder a la base**? Si la respuesta
> es no, la observabilidad del carril está incompleta aunque emita métricas.

### 7.3 · Endurecimiento

| Entrega del carril | Verificación |
| --- | --- |
| **Denegación por defecto**: guardia global, permiso declarado por ruta | **barrido 1** |
| Entrada validada **por el contrato**, no a mano | lint: sin `interface` que duplique un esquema |
| **Contexto de RLS** puesto por `SET LOCAL` dentro de la misma transacción | paso 11 del CI + `seguridad-sesion-rls` |
| Errores traducidos: el cliente ve `AP-CU<NN>-<nn>`, nunca el error de PostgreSQL | **barrido 5** |
| **Límite de tasa** en autenticación y en operaciones de dinero | prueba de API por endpoint sensible |
| Secretos **fuera** de la base, del código y de la imagen | paso 19 del CI: escaneo de secretos |
| Datos sensibles **cifrados** en reposo (cuenta bancaria, documentos) | prueba de `3D`: el valor en la base no es legible |
| Contenedor **sin root**, sin `latest`, sin puerto publicado salvo NGINX | `despliegue-contenedores` + gate de la fase 0 |
| **CORS configurado en el gateway** para los orígenes del backoffice y del sitio —los dos clientes de navegador—; ningún servicio ni cliente lo gestiona por su cuenta. Los tres frontends apuntan a **una sola base URL: el gateway**, y el prefijo enruta (§3.1) | revisión del gateway (troncal) — ver [[10 Plan maestro del frontend]] §3 |

### 7.4 · Resistencia y resiliencia

| Entrega del carril | Verificación |
| --- | --- |
| **Idempotencia** en todo endpoint con efecto y en todo consumidor de webhook | **barrido 3** |
| **Timeout** en todo adaptador externo, sin excepción | **barrido 10** |
| **Reintento con retroceso y variación aleatoria** — nunca reintento inmediato en bucle | prueba con proveedor simulado que falla dos veces |
| **Cortacircuitos** por proveedor, con conmutación **nunca silenciosa** | barrido 10 + `proveedores-externos` |
| **Exactamente una vez entre réplicas** en todo trabajo programado | prueba con dos réplicas del servicio levantadas |
| **Webhook duplicado y fuera de orden** absorbidos | prueba obligatoria en `3A` y en todo carril con proveedor |
| **Apagado controlado**: `SIGTERM` termina el request en curso y cierra el pool | prueba de integración del proceso |
| **Reverso, nunca `UPDATE`**, para corregir dinero | lint `sin-update-append-only` + prueba de rechazo |
| **`descriptor.yml` con `nivel` y `nivel_porque`** — nunca una réplica | `python3 scripts/generar_k8s.py` (falla si no cierra) |
| **`replicas.max` que cabe en el pool**: `Σ (max × pool) ≤ pgbouncer` | el mismo generador, regla 3 de [[ADR-037 Alta disponibilidad y balanceo]] |
| **Degradación declarada**: qué se apaga primero si el servicio presiona | revisión de cierre de carril |

> **La regla que resume el bloque:** la red duplica, reordena y se cae. El diseño lo
> absorbe; no lo denuncia. Un carril que responde «eso no debería pasar» a un webhook
> repetido no está terminado.

### 7.5 · Eficiencia

| Entrega del carril | Verificación |
| --- | --- |
| **Sin N+1**: ninguna consulta dentro de un bucle | **barrido 9** |
| **Paginación obligatoria** en todo listado; sin `findAll` sobre tablas de crecimiento libre | revisión + presupuesto de consultas |
| Lecturas pesadas contra la **réplica de solo lectura** | `lecturas-proyecciones` |
| **Streaming** en exportaciones, nunca el resultado entero en memoria | prueba de exportación con 100.000 filas |
| Índices **existentes** usados: `EXPLAIN` en las consultas del camino caliente | revisión de cierre de carril |
| **No se optimiza sin medir**: toda optimización trae su número antes y después | revisión — una optimización sin medición se revierte |

---

## 8 · Qué se agrega al pipeline

Los 19 pasos de §6 del [[00 Plan maestro]] siguen igual y en el mismo orden. Se agregan
seis pasos de **backend**, **todos bloqueantes**:

```
 0b  python3 scripts/verificar_carriles.py     65 skills asignadas · puestos alineados 17↔18 · balance
 0c  python3 scripts/generar_k8s.py            ningún servicio con 1 réplica · el escalado cabe en el pool
 6b  python3 scripts/verificar_criterios.py    criterios gherkin ↔ prueba  ·  R-XXX ↔ prueba de rechazo
12b  ./gradlew testAislamiento                 barrido 15: ningún rol lee un esquema ajeno
16b  ./gradlew testBarrido                     los 12 barridos locales de §5
16d  ./gradlew testBarridoEntreServicios       los 4 de §5 que solo existen integrados (13, 14, 15, 16)
16c  ./gradlew presupuestos                    los números de §6, medidos, no declarados
```

Y una guarda sobre las guardas:

```
     ./gradlew testActivas                     el número de pruebas activas NUNCA baja
```

Y los pasos de **frontend**, que corren en los carriles `F*`, todos **bloqueantes**:

```
 f1  yarn lint && yarn typecheck               capas, tokens, tipos del contrato
 f2  yarn test:front                           unitarias, componente (MSW) y contrato · incluye el barrido 17 (mocks sin duplicar)
 f3  yarn test:a11y                            jest-axe / axe-core: cero violaciones serias
 f4  npx lighthouse-ci autorun                 CWV y presupuesto de JS del sitio (§6: ≤ 150 KB) — corre en P5
 f5  yarn seo:validar                          metadatos, canonical, hreflang, JSON-LD (solo `apps/web`)
```

> **Lighthouse y la medición de frontend corren en P5**, la máquina que trabaja el
> sitio; **P2 solo publica el número en el informe** —no toca frontend en ningún
> tramo—. La latencia de backend, en cambio, se mide en P2 (§6).

> **`13b` desapareció y `12b` ocupó su lugar.** El paso que verificaba los fragmentos
> `.env.d/` ya no tiene objeto: con un servicio por carril, la configuración vive
> dentro del servicio y no hay archivo compartido que conciliar. Lo reemplaza el que
> comprueba la frontera que sí es nueva: que ningún rol lea el esquema de otro.

> **Por qué esa última.** Es la única defensa contra el atajo que efectivamente se toma
> bajo presión: marcar una prueba como `skip` para que el build pase. Con cinco
> carriles y un revisor, un `skip` puesto un martes no lo ve nadie.

---

## 9 · Lo que esto le cuesta a la Ola 0 — dicho sin adorno

Todo lo de este documento **se construye en la Ola 0**, en `T0` y `T2`, por el mismo
puesto (P1 · Mac) y antes de que exista un solo caso de uso.

| Lo que hay que construir | Dónde |
| --- | --- |
| Los tres generadores de §4 | `T0` |
| Las diecisiete pruebas de barrido de §5 (la 17, de mocks, en el andamiaje de frontend F0) | `T2` — necesitan el registro de rutas y el outbox |
| `verificar_criterios.py` | `T0` |
| El generador de manifiestos desde `descriptor.yml` | `T0` |
| La tabla de prefijos reservados y su barrido | `T0` |
| Los presupuestos y su corredor | `T2`, recalibrados en la fase 17 |

**Eso alarga la Ola 0.** Y la Ola 0 es la que bloquea a las otras cuatro máquinas: es
el peor lugar del proyecto para agregar trabajo.

**Se hace igual, y por una razón concreta:** cada una de estas piezas se paga una vez y
se cobra **treinta y ocho veces**, una por carril. La alternativa —confiar en que cada
carril se acuerde de la observabilidad, del prefijo de la métrica y del timeout— no es
más barata: es más cara y se descubre tarde, cuando cinco módulos ya divergieron y
unificarlos significa tocar los cinco.

> **El único caso en que algo de esto se posterga:** si `verificar_boveda.py` y los
> gates existentes no están en verde. Primero funciona lo que hay; después se le agrega
> encima.

---

## 10 · Checklist de cierre de carril

Se pega en `planes/informes/carril-P<N>.md` al cerrar. **Cada casilla se marca con la
salida del comando pegada abajo, no con una afirmación.** Hay **dos variantes**: la de
carril de **backend** (abajo) y la de carril de **frontend** (al final de la sección);
cada carril pega la que le corresponde.

### Variante de carril de backend

```markdown
### Cierre del carril <ID> — tramo T<N>

- [ ] Las skills obligatorias del carril (§2) estaban cargadas antes del primer archivo
- [ ] Piezas declaradas por nivel antes de escribir, con visto bueno
- [ ] Las 5 preguntas de frontera transaccional, respondidas por escrito, por CU

**Generado, no escrito a mano**
- [ ] El servicio salió de `./gradlew nuevoServicio`
- [ ] Cada CU salió de `./gradlew nuevoCu`

**Verificado por máquina**
- [ ] `./gradlew verificar` en verde          → salida pegada
- [ ] `./gradlew testBarrido` en verde   → los 12 locales
- [ ] `./gradlew presupuestos` en verde       → números pegados
- [ ] `python3 scripts/verificar_criterios.py` en verde
- [ ] `./gradlew testActivas` — ninguna prueba desactivada
- [ ] Cobertura sobre el piso de su ámbito

**Transversales (§7)**
- [ ] README del módulo al día, sin duplicar la bóveda
- [ ] Traza, métricas y evento de outbox por caso de uso
- [ ] Permiso declarado por ruta · rate limit donde corresponde
- [ ] Timeout y cortacircuitos en cada adaptador externo
- [ ] Sin N+1 · listados paginados

**Lo que no verifica ninguna máquina** — se responde por escrito:
- [ ] ¿Los nombres dicen lo que las cosas son? (`glosario-dominio`)
- [ ] ¿La frontera transaccional es la correcta, o solo pasa las pruebas?
- [ ] ¿Qué supuse que no estaba en la bóveda?
- [ ] ¿Qué dejé peor de como lo encontré?
```

### Variante de carril de frontend

```markdown
### Cierre del carril <ID> — tramo T<N>

- [ ] Las skills obligatorias del carril de frontend (§2) estaban cargadas antes del primer archivo
- [ ] Piezas declaradas por nivel (átomo/molécula/organismo/pantalla) antes de escribir, con visto bueno

**Verificado por máquina** — salida pegada abajo
- [ ] `yarn lint && yarn typecheck` en verde   → capas, tokens, tipos del contrato
- [ ] `yarn test:front` en verde                → unitarias, componente (MSW) y contrato · incluye el barrido 17 (mocks sin duplicar)
- [ ] `yarn test:a11y` en verde                 → cero violaciones serias
- [ ] `npx lighthouse-ci autorun` en verde (solo `apps/web`) → CWV y **JS ≤ 150 KB** (§6) · corrido en P5
- [ ] `yarn seo:validar` en verde (solo `apps/web`)  → metadatos, canonical, hreflang, JSON-LD

**Presupuestos (§6)**
- [ ] JS inicial del sitio ≤ 150 KB (gate) · objetivo < 50 KB en páginas de contenido
- [ ] Tamaño de archivo bajo el límite · arranque de la app ≤ 3 s en Android de gama baja (F12)

**Invariantes del frontend**
- [ ] Los cuatro estados en toda pantalla con datos: cargando, vacío, error, éxito
- [ ] Cero literales de diseño fuera de tokens (lint) · ningún importe formateado fuera de `Monto`
- [ ] Ningún `fetch` en un componente · ningún tipo reescrito a mano (viene de `clientes/typescript`)
- [ ] Doble envío bloqueado en operaciones de dinero, con la misma clave de idempotencia
- [ ] Contraste AA, foco visible, navegación por teclado · claro y oscuro probados

**Lo que no verifica ninguna máquina** — se responde por escrito:
- [ ] ¿Los nombres dicen lo que las cosas son? (`glosario-dominio`)
- [ ] ¿La pantalla sale de la sección Interfaz del CU, sin inventar? (regla cero)
- [ ] ¿Qué supuse que no estaba en la bóveda?
- [ ] ¿Qué dejé peor de como lo encontré?
```

---

## 11 · Balance de carga entre los cinco puestos

El cuello de botella del proyecto es la atención, no el cómputo. Un puesto que carga
mucho más que otro **en el mismo tramo** no va "un poco más lento": es el que retrasa
la ola siguiente, mientras otra máquina espera.

### La unidad y el número que importa

La unidad es la **superficie** del carril —la escala de ● a ●●●●● de
[[18 Fichas de carril · las 38 unidades de trabajo]]—. No son horas: es cuánto hay que
sostener en la cabeza a la vez.

> **Se mide por tramo, no por proyecto.** El acumulado del proyecto no es
> accionable: las máquinas trabajan en paralelo dentro de un tramo, no compiten por
> un total. Lo que atrasa una ola es que en **ese** tramo alguien cargue el cuádruple
> que su vecino.

`python3 scripts/verificar_carriles.py` imprime las dos vistas y **avisa a partir de
4x** dentro de un tramo:

```
  tramo    P1   P2   P3   P4   P5   max/min  ocupados
     T3      4    4    4    2    3   2.0x     5/5
  !! T5      4    1    3    3    3   4.0x     5/5
```

### Las tres reglas

1. **Un carril por puesto y por tramo.** Dos a la vez solo valen si el plan de
   coordinación los declara **en serie** con `X → Y` (`T1 → T2` en la Ola 0,
   `F2 → F6` en T3, `4A → F4` si alguna vez hiciera falta). Lo verifica el script:
   dos carriles simultáneos sin declarar es **falla**, no aviso.
2. **Un tramo desparejo se corrige moviendo una deuda declarada**, que es trabajo ya
   identificado y sin dueño de tramo — nunca partiendo un carril.
3. **El que cierra temprano toma la deuda declarada de su tramo.** Es el único
   mecanismo de balanceo que funciona con la ocupación llena: `planes/17` ya lleva la
   lista de deudas por tramo, y esa lista es la cola de trabajo del que se libera.

### Por qué el acumulado NO se rebalancea

```
acumulado: 107 unidades · media 21.4
!! P1: 31  +45%      P2: 12  -44%      P3: 23  +7%   P4: 24  +12%   P5: 17  -21%
```

La primera lectura de esta tabla lleva a una conclusión equivocada —"moverle un carril
de P1 a P2"— y hay que decir por qué no se hace:

- **La ocupación ya está llena.** Salvo en los extremos (T0, T9, T10), cada máquina
  tiene exactamente un carril por tramo. Sacarle `F4` a P1 no lo alivia: **lo deja
  parado en T5**, y como `F5` depende de `F4`, tampoco puede adelantarlo. Se cambia un
  desbalance por una máquina ociosa y una dependencia cruzada nueva.
- **La diferencia es de tamaño de carril, no de reparto.** `identidad`, `grupos` y la
  app móvil son intrínsecamente más grandes que `contabilidad` o `entregas`. Igualar
  el total exigiría partir un carril, que es lo que la regla 1 prohíbe por ser la
  única fuente real de conflicto.
- **La Ola 0 no es reasignable.** Las 9 unidades de `T0` `T1` `T2` bloquean a todos y
  las tiene que hacer una sola máquina, en serie.

**Entonces el acumulado se reporta como contexto y no como falla.** El riesgo real que
señala no es "P1 trabaja de más": es que **los tramos de P1 duran más que los de los
demás**. Eso se administra con la regla 3 y con prioridad de revisión para P1, no
moviendo carriles.

### Qué NO cuenta como rebalanceo

- **Partir un carril entre dos máquinas.** Rompe la propiedad exclusiva del
  desplegable, que es lo único que hace imposible el conflicto (§3).
- **Mover un carril a mitad de tramo.** El puesto que lo recibe empieza sin contexto:
  lo que se ahorra en superficie se paga en lectura.
- **Bajar la escala de tamaño de una ficha** para que cierre el promedio.

---

## Ver también

[[17 Plan de acción secuencial · coordinación de cinco máquinas]] · [[18 Fichas de carril · las 38 unidades de trabajo]] ·
[[07 Carriles de trabajo concurrente]] · [[16 Carriles de frontend]] · [[00 Plan maestro]] ·
[[00b Estándar de ejecución · código limpio, pruebas y calidad]] · [[00c Recetario · implementar un caso de uso]] ·
[[06 Fase 17 · Endurecimiento, E2E y despliegue]] · [[informe]]
