---
tags:
  - plan
  - informe
  - carril
titulo: "Puesto P3 · Legion — preparación de carril"
ola: 0
fase: T0
modulo: transparencia · garantia
rama: pablo/feature/carril-F0-M-andamiaje-movil
estado: en curso
---

# Puesto P3 · Legion — preparación de carril

**Entregable del tramo T0** de [[17 Plan de acción secuencial · coordinación de cinco máquinas]] §5:
leer los casos de uso del puesto y **declarar las piezas por nivel** antes de escribir
la primera línea. Este archivo lo escribe solo este puesto.

> **Qué es y qué no es.** Es el paso 0 de `frontera-transaccional` hecho por
> adelantado para los 16 casos de uso que le tocan a la Legion. No es código, no es
> diseño de tablas y no decide nada que la bóveda ya haya decidido. Donde encontré
> una contradicción, la anoté; no la resolví.

## 0 · Qué carriles tiene este puesto, y cuándo

| Carril | Tramo | Servicio | CU | Estado |
| :-: | :-: | --- | --- | :-: |
| `F0-M` andamiaje móvil | T1 | `apps/movil` | — | **hecho** · [[carril-F0-M]] |
| `F1` sistema de diseño | T2 | `packages/ui` | — | pendiente |
| `F2` shell móvil · `F6` shell backoffice | T3 | — | — | pendiente |
| **`3B` transparencia y reputación** | **T4** | `transparencia` | CU-61, 70–76, 97 | **declarado acá** |
| **`4B` garantía e incumplimiento** | **T5** | `garantia` | CU-23, 25, 26, 27, 29, 66, 67 | **declarado acá** |
| `F7` backoffice · operación | T6–T7 | `apps/backoffice` | — | pendiente |
| `F13` backoffice · ERP | T9 | `apps/backoffice` | — | pendiente |

> [!warning] Discrepancia entre dos documentos del plan, sin resolver
> [[17 Plan de acción secuencial · coordinación de cinco máquinas]] §5 T0 dice que P3
> prepara «los CU de **grupos** y transparencia». [[18 Fichas de carril · las 38 unidades de trabajo]]
> asigna `2C` grupos a **P1** (T3) y le da a P3 `3B` y `4B`. Declaré `3B` y `4B`, que
> es lo que dice la ficha —el documento que manda en el qué—, y dejo la discrepancia
> anotada porque **el que quede sin declarar es el que va a improvisar**.

---

## 1 · El mapa de propiedad, verificado contra `sql/`

No lo saqué de los planes: lo saqué de los `CREATE TABLE` del esquema aplicado. Es lo
que decide si un caso de uso es una transacción local o una saga, y equivocarse acá
es el error caro del paso 0.

**Lo que posee `transparencia`** — `bloque_transparencia` · `registro_sellado` ·
`verificacion_publica` · `evento_reputacion` · `puntaje_reputacion` ·
`componente_score` · `snapshot_reputacion` · `regla_impacto_evento` ·
`modelo_scoring` · `peso_factor` · `insignia_logro` · `insignia_otorgada` ·
`certificado_reputacion` · `resena_participante` · `metrica_grupo` · `alerta_riesgo`.

**Lo que posee `garantia`** — `registro_incumplimiento` · `evidencia_incumplimiento` ·
`descargo_participante` · `historial_estado_incumplimiento` · `politica_sancion` ·
`matriz_sancion` · `sancion` · `apelacion_sancion` · `fondo_garantia` ·
`movimiento_fondo` · `politica_cobertura` · `cobertura_incumplimiento` ·
`deuda_participante` · `subrogacion` · `aval_participante` · `ejecucion_aval` ·
`gestion_cobranza` · `estrategia_cobranza` · `abono_recuperacion` · `acuerdo_quita` ·
`castigo_deuda` · `promesa_pago` · `plan_contingencia` · `lista_restriccion_interna` ·
`devolucion_fondo` · `disolucion_anticipada` · `liquidacion_participante` ·
`reemplazo_participante` · `candidato_reemplazo` · `historial_incumplimiento_usuario` ·
`alerta_temprana` · `score_riesgo_incumplimiento`.

**Lo que estos dos carriles NO pueden leer con `SELECT`** (invariante 11), y de quién es:

| Tabla que los CU nombran | Dueño | Cómo se obtiene |
| --- | --- | --- |
| `sorteo_turnos` · `turno` · `participante` · `periodo` · `acuerdo` · `traspaso_cupo` · `reglamento_grupo` · `grupo` | `grupos` | API o proyección por evento |
| `obligacion_aporte` · `plan_regularizacion` · `comprobante_manual` | `aportes` | API |
| `transaccion_billetera` · `asiento_contable` · `cuenta_billetera` · `descuadre_custodia` | `nucleo_financiero` | **Solo él escribe el libro** (invariante 12) |
| `restriccion_usuario` · `reputacion_usuario` | `identidad` | API |
| `entrega_fondo` | `entregas` | API |
| `incidente_operativo` | `auditoria` | Evento |
| `evento_riesgo_operativo` | `cumplimiento` | Evento |

**Lo que sí se lee directo:** `catalogo` (por ejemplo `dia_no_habil`) y `comun`
(`bitacora_evento`, `registro_acceso_datos` también se escriben). Lo confirmé en los
`GRANT` de `sql/00_base/02_esquemas.sql`.

---

## 2 · Carril `3B` · transparencia — las seis preguntas por caso de uso

### CU-70 · Registrar un evento de reputación

1. **Todo junto o nada:** `evento_reputacion` (append-only) + la marca de puntaje
   sucio del usuario + `evento_consumido` del evento de dominio que lo disparó.
2. **Fuera del commit:** `reputacion.evento_registrado` al outbox. La notificación la
   decide `notificaciones` al consumirlo, no este servicio.
3. **Clave de idempotencia:** derivada del hecho —
   `(referencia_tipo, referencia_id, tipo_evento)`, que ya es `UNIQUE` por `R-REP-01`.
   Como consumidor, además `evento_consumido(id_evento, consumidor)`.
4. **Bloqueo:** ninguno adicional; decide la unicidad. Si el marcado de sucio toca la
   fila del puntaje, `forUpdate` sobre el puntaje vigente del usuario.
5. **Si muere tras el commit:** el relevo reenvía el evento y el trabajo que barre
   puntajes sucios toma el recálculo. No se pierde y no se duplica.
6. **Cruza servicios:** **entra** por evento de dominio de `aportes`, `garantia` y
   `grupos`; **no escribe** en ninguno. La regla de impacto es propia. → consumidor
   idempotente, sin saga.

> **No tiene endpoint, y eso es una decisión:** solo se llega por evento de dominio.
> Un endpoint para sumar puntos sería la puerta por la que la reputación deja de ser
> consecuencia de hechos.

### CU-71 · Recalcular el puntaje

1. **Todo junto o nada:** cerrar la vigencia del `puntaje_reputacion` anterior +
   insertar el nuevo + insertar sus `componente_score`. `R-REP-02` exige **uno solo
   vigente por usuario**: partirlo en dos transacciones es tener cero o dos.
2. **Fuera del commit:** `reputacion.recalculada` y `reputacion.nivel_cambiado`.
3. **Clave:** derivada — `puntaje:<usuarioId>:<modeloId>:<motivo>:<selloDeSuciedad>`.
   Para el cierre mensual, `puntaje:<usuarioId>:<periodo>`.
4. **Bloqueo:** `forUpdate` sobre el puntaje vigente del usuario. La restricción de
   exclusión de la base es la que realmente lo garantiza; el bloqueo evita el reintento.
5. **Si muere tras el commit:** el puntaje nuevo ya existe; el evento lo reenvía el
   relevo. Si muere **antes**, el usuario sigue marcado sucio y el trabajo lo retoma.
6. **Cruza servicios:** no escribe afuera. Lee su propio modelo y sus propios eventos.
   **Pero** `reputacion_usuario` es de `identidad` — ver hueco H-2.

### CU-72 · Sellar el bloque de transparencia

1. **Todo junto o nada:** `bloque_transparencia` + los N `registro_sellado` de las
   entidades incluidas.
2. **Fuera del commit:** `bloque.sellado`, y `bloque.cadena_rota` cuando corresponde.
3. **Clave:** derivada — `bloque:<grupoId>:<numeroBloque>`. La respalda
   `UNIQUE (grupo_id, numero_bloque)` (`R-REP-04`).
4. **Bloqueo:** consultivo `transparencia.bloque:<grupoId>` para serializar el sellado
   del grupo, más `forUpdate` sobre el último bloque para leer su `hash_bloque` sin
   que otro lo mueva. Orden fijo: **consultivo, después fila**.
5. **Si muere tras el commit:** el bloque existe y la cadena está bien; el evento lo
   reenvía el relevo. Si muere antes, el trabajo de cierre de período lo reintenta con
   la misma clave y la unicidad lo corta.
6. **Cruza servicios: sí, y es el punto caro del carril.** El contenido del bloque son
   aportes acreditados (`aportes`), entregas ejecutadas (`entregas`), coberturas
   (`garantia`), altas y bajas (`grupos`), acuerdos (`grupos`) y el sorteo (`grupos`).
   **Ninguno se puede consultar por SQL.** → ver hueco H-1: propongo proyección local
   alimentada por eventos, no cuatro llamadas sincrónicas dentro del cierre de período.

### CU-73 · Verificar la cadena

1. **Todo junto o nada:** un `verificacion_publica`. Nada más: es una lectura que deja
   constancia.
2. **Fuera del commit:** `cadena.verificada` o `cadena.rota`. El incidente y el evento
   de riesgo se piden **por evento**, porque son de `auditoria` y `cumplimiento`.
3. **Clave:** derivada — `verificacion:cadena:<grupoId>:<hashUltimoBloque>`. Es ruta
   pública: **no hay clave del cliente**, y por eso tiene que salir del hecho.
4. **Bloqueo:** ninguno para verificar. El congelamiento de sellados tras una cadena
   rota sí toma el consultivo de CU-72, y con el mismo nombre.
5. **Si muere tras el commit:** nada que recuperar; la verificación se repite y da lo
   mismo — es determinista o no sirve.
6. **Cruza servicios:** solo para avisar. Evento, nunca escritura directa.

### CU-61 · Verificar públicamente el sorteo

1. **Todo junto o nada:** un `verificacion_publica`.
2. **Fuera del commit:** `sorteo.verificado` / `sorteo.verificacion_fallida`.
3. **Clave:** derivada — `verificacion:sorteo:<sorteoId>:<hashPaquete>`.
4. **Bloqueo:** ninguno.
5. **Si muere tras el commit:** se repite sin consecuencia.
6. **Cruza servicios: sí, dos veces, y las dos son problema.** `sorteo_turnos` y
   `turno` son de `grupos`; y el CU dice que el átomo de verificación tiene que ser
   **el mismo** que usa CU-60 para sortear, que vive en `grupos`. → huecos H-3 y H-4.

### CU-74 · Otorgar y revocar una insignia

1. **Todo junto o nada:** `insignia_otorgada` (o el sellado de `revocada_en` y
   `motivo_revocacion`, que **no borra la fila**).
2. **Fuera del commit:** `insignia.otorgada` / `insignia.revocada`.
3. **Clave:** derivada — `insignia:<usuarioId>:<insigniaCodigo>`, respaldada por la
   unicidad de `R-REP-05`. El reintento del outbox es inocuo por construcción.
4. **Bloqueo:** decide la unicidad.
5. **Si muere tras el commit:** el relevo reenvía; la unicidad corta el duplicado.
6. **Cruza servicios:** el criterio se evalúa contra hechos del usuario. Los que sean
   de otros servicios entran por la misma proyección de H-1: **el criterio no se
   evalúa con una llamada de red por insignia del catálogo**.

### CU-75 · Emitir un certificado verificable

1. **Todo junto o nada:** `certificado_reputacion` nuevo + revocación del equivalente
   vigente. Los dos o ninguno: dos certificados vigentes con distinto contenido es
   exactamente lo que el CU quiere impedir.
2. **Fuera del commit:** `certificado.emitido`, `certificado.revocado`.
3. **Clave: del cliente.** Es la única de este carril que no se deriva del hecho,
   porque el hecho es «el titular lo pidió» y el titular puede pedirlo dos veces a
   propósito. Va la cabecera `Idempotency-Key`, más `UNIQUE(codigo_verificacion)`.
4. **Bloqueo:** `forUpdate` sobre los certificados vigentes del titular.
5. **Si muere tras el commit:** el certificado existe y su URL resuelve; el aviso al
   titular lo reenvía el relevo.
6. **Cruza servicios: sí.** La identidad verificada es de `identidad` → **llamada
   sincrónica antes del `BEGIN`**, con timeout y cortacircuitos, y el dato entra como
   parámetro. Nunca dentro de la transacción.

### CU-76 · Reseñar y moderar

1. **Todo junto o nada:** `resena_participante` con su `estado_moderacion`, y en la
   moderación el cambio de estado con `moderada_por` y motivo.
2. **Fuera del commit:** `resena.creada` / `publicada` / `rechazada`. El impacto en
   reputación **no se escribe acá**: sale por outbox y lo consume CU-70.
3. **Clave:** derivada — `resena:<autorId>:<evaluadoId>:<grupoId>:<dimension>`,
   respaldada por `R-REP-06`.
4. **Bloqueo:** decide la unicidad.
5. **Si muere tras el commit:** relevo. La moderación automática es un paso posterior
   y vuelve a correr sobre las pendientes.
6. **Cruza servicios: sí.** La convivencia —autor y evaluado en el mismo grupo y
   período— es de `grupos`: llamada sincrónica antes del `BEGIN`, o la proyección de
   H-1. La derivación a reclamo (CU-52) es de `cumplimiento`: evento.

### CU-97 · Anticipar el riesgo con alertas tempranas

1. **Todo junto o nada:** `metrica_grupo` del período + `alerta_riesgo` de ámbito
   plataforma.
2. **Fuera del commit:** `alerta_temprana.generada`, `alerta_riesgo.generada`, y el
   acompañamiento entero (plan, promesa, recordatorio reforzado).
3. **Clave:** derivada — `alerta:<ambito>:<referenciaId>:<codigo>:<periodoId>`.
4. **Bloqueo:** unicidad de alerta abierta por código y ámbito (`ALERTA_DUPLICADA`).
5. **Si muere tras el commit:** el trabajo de cierre de período reevalúa; idempotente
   por la clave.
6. **Cruza servicios: sí, y parte el caso de uso en dos.** `score_riesgo_incumplimiento`
   y `alerta_temprana` viven en **`garantia`**, no en `transparencia`. → hueco H-5,
   que es el más importante de los que encontré.

---

## 3 · Carril `4B` · garantía — las seis preguntas por caso de uso

> **El patrón del carril, dicho una vez:** cinco de los siete casos de uso mueven
> dinero, y **el libro contable no se parte** (invariante 12). Ninguno puede escribir
> `transaccion_billetera` ni `asiento_contable`: los pide a `nucleo-financiero`. Eso
> los vuelve **sagas**, no transacciones locales, y el estado de la saga se persiste
> en el esquema de `garantia` antes de cada paso.

### CU-25 · Declarar el incumplimiento con descargo

1. **Todo junto o nada:** `registro_incumplimiento` en `PRESUNTO` +
   `evidencia_incumplimiento` (con `es_inmutable`) + la primera fila de
   `historial_estado_incumplimiento` + **la fecha límite de descargo calculada y
   guardada**. El plazo se persiste al crear y no se recalcula al consultar
   (invariante 8): un plazo legal que se recalcula es un plazo que se mueve solo.
2. **Fuera del commit:** `incumplimiento.presunto`. La notificación con acuse la hace
   `notificaciones`.
3. **Clave:** derivada — `incumplimiento:<obligacionId>`.
4. **Bloqueo:** `forUpdate` sobre el registro de esa obligación; unicidad por
   obligación abierta.
5. **Si muere tras el commit:** el trabajo diario que vence descargos vuelve a pasar
   por el registro. Idempotente por la clave.
6. **Cruza servicios: sí, tres veces, todas de lectura y todas antes del `BEGIN`.**
   `obligacion_aporte` es de `aportes`; **los acuses de los recordatorios son de
   `notificaciones`** y sin ellos no se declara nada (`AP-CU25-02`); `dia_no_habil` es
   de `catalogo` y esa sí se lee directo. El evento de reputación va por outbox.

### CU-23 · Cubrir un incumplimiento con el fondo — **saga**

1. **Todo junto o nada (paso local):** `cobertura_incumplimiento` + `movimiento_fondo`
   (append-only) + `deuda_participante` + `subrogacion` + el estado de la saga.
2. **Fuera del commit:** el movimiento de billetera y el asiento — **son de
   `nucleo-financiero`** —, más `incumplimiento.cubierto` y `fondo.consumido`.
3. **Clave:** derivada — `cobertura:<obligacionId>`. El paso financiero usa la misma
   raíz (`cobertura:<obligacionId>:mov`) para que el reintento no debite dos veces.
4. **Bloqueo:** `forUpdate` sobre el `fondo_garantia` del grupo. El saldo se deriva de
   movimientos, pero **la decisión de política necesita serializarse**: dos coberturas
   concurrentes contra el mismo fondo pueden pasar cada una su tope y juntas vaciarlo.
   Orden fijo: fondo → registro → deuda, siempre el mismo.
5. **Si muere tras el commit local y antes del paso financiero:** el estado de la saga
   está persistido y el trabajo la retoma desde ahí. Nunca «primero guardo y después
   ajusto» sin estado.
6. **Cruza servicios: SAGA.** Formulario en §4.

### CU-26 · Ejecutar el aval y subrogar — **saga**

1. **Todo junto o nada (paso local):** `ejecucion_aval` + imputación contra la
   `deuda_participante` + `subrogacion` a favor del avalista + el plazo de respuesta
   **calculado y guardado**.
2. **Fuera del commit:** el pago del avalista y su asiento (`nucleo-financiero`); los
   eventos `aval.notificado`, `aval.ejecutado`, `aval.anulado`; los dos eventos de
   reputación —negativo para el incumplido y **positivo para el avalista que
   respondió**— por outbox.
3. **Clave:** derivada — `ejecucion:<avalId>:<registroId>`.
4. **Bloqueo:** `forUpdate` sobre el aval, para que el tope firmado no se estire por
   dos ejecuciones concurrentes. Con varios avalistas, el orden de toma es el pactado
   y siempre el mismo.
5. **Si muere tras el commit:** el trabajo del plazo de respuesta retoma la ejecución
   en el estado en que quedó.
6. **Cruza servicios: SAGA** con `nucleo-financiero`. Además consulta el
   incumplimiento firme, que es propio.

### CU-27 · Restringir al deudor

1. **Todo junto o nada:** `lista_restriccion_interna` con su motivo, nivel, monto y
   vigencia. Al levantar: `retirado_en`, `retirado_por` y `motivo_retiro`, **en la
   misma transacción que acredita el último pago**.
2. **Fuera del commit:** `restriccion.aplicada` / `restriccion.levantada`.
3. **Clave:** derivada — `restriccion:<usuarioId>:<registroOrigenId>:<tipo>`.
4. **Bloqueo:** `forUpdate` sobre la lista vigente del usuario; no se acumulan dos
   restricciones idénticas.
5. **Si muere tras el commit:** el trabajo diario que revisa saldos en cero levanta lo
   que quedó. **Nadie tiene que acordarse de sacar a nadie**, que es el punto del CU.
6. **Cruza servicios: sí, y no es menor.** Las `restriccion_usuario` que el sistema
   **realmente consulta** viven en `identidad`, no acá. → hueco H-6.

### CU-29 · Devolver los aportes del fondo — **saga**

1. **Todo junto o nada (paso local):** una `devolucion_fondo` por participante + los
   `movimiento_fondo` de salida + el cierre del fondo en cero.
2. **Fuera del commit:** las acreditaciones en billetera y el asiento
   (`nucleo-financiero`); `fondo.devuelto`; `fondo.descuadrado` si no cuadra.
3. **Clave:** derivada — `devolucion:<fondoId>`; por participante,
   `devolucion:<fondoId>:<participanteId>`.
4. **Bloqueo:** `forUpdate` sobre el `fondo_garantia`. Nada de repartir con una
   cobertura viva.
5. **Si muere tras el commit local:** la saga se retoma; las acreditaciones ya hechas
   no se repiten porque cada una lleva su clave.
6. **Cruza servicios: SAGA.** Y trae la regla más dura del carril: **si la suma de
   devoluciones no coincide con el saldo del fondo, la transacción no confirma**
   (`R-GAR-06`). El cuadre es de la base, no de la aplicación.

### CU-66 · Reemplazar a un participante moroso — **saga**

1. **Todo junto o nada (paso local):** `reemplazo_participante` +
   `candidato_reemplazo` elegido + la deuda que **se queda con el saliente** + la
   continuidad de `gestion_cobranza` + `historial_incumplimiento_usuario`.
2. **Fuera del commit:** el traspaso de cupo y el cambio de estado a `EXPULSADO`, que
   son de `grupos`; el evento de reputación, que es de `transparencia`;
   `participante.reemplazado`.
3. **Clave:** derivada — `reemplazo:<registroIncumplimientoId>`.
4. **Bloqueo:** `forUpdate` sobre el reemplazo abierto del registro.
5. **Si muere tras el commit:** el trabajo que vence la búsqueda retoma; si el
   traspaso ya se pidió a `grupos`, la clave impide duplicarlo.
6. **Cruza servicios: SAGA** con `grupos`. La compensación no es un `UPDATE`: si el
   traspaso falla, el reemplazo vuelve a `BUSCANDO_CANDIDATO` con su fila de historial.

### CU-67 · Disolver el grupo anticipadamente — **saga, la más grande**

1. **Todo junto o nada (paso local):** `disolucion_anticipada` + una
   `liquidacion_participante` por cupo con su desglose.
2. **Fuera del commit:** las devoluciones en billetera y los asientos
   (`nucleo-financiero`); el cambio de estado del grupo a `DISUELTO_ANTICIPADAMENTE`
   (`grupos`); la resolución de la entrega en curso (`entregas`); `grupo.disuelto`.
3. **Clave:** derivada — `disolucion:<grupoId>`.
4. **Bloqueo:** `forUpdate` sobre el grupo en liquidación, más el consultivo del fondo.
   Orden fijo: grupo → fondo → deudas.
5. **Si muere tras el commit:** la saga se retoma desde el paso persistido. Ninguna
   liquidación se recalcula: se congeló la posición al empezar.
6. **Cruza servicios: SAGA con tres.** Es la operación más acoplada de los dos
   carriles, y por eso el átomo de prelación **se prueba con propiedades**: para
   cualquier combinación de aportes, cobros y deudas, lo repartido tiene que ser igual
   a lo disponible, ni un centavo más.

---

## 4 · Formulario de saga · CU-23, el patrón de los otros cuatro

| Campo | Valor |
| --- | --- |
| **Orquestador** | `garantia` — el que inicia guarda el estado en **su** esquema |
| **Pasos** | 1 evaluar política y abrir cobertura · 2 debitar el fondo y acreditar al grupo **en `nucleo-financiero`, con su asiento** · 3 crear deuda y subrogación · 4 abrir la gestión de cobranza |
| **Cuál es ACID e indivisible** | El 2, entero, dentro de `nucleo-financiero`. **El libro no se parte** |
| **Clave de cada paso** | Derivada del hecho: `cobertura:<obligacionId>` como raíz |
| **Compensación** | 2 ⇒ **movimiento inverso**, nunca un `UPDATE`. 1, 3 y 4 ⇒ estado local con su fila de historial |
| **Si no puede compensar** | Incidente y aviso a una persona. **Nunca reintento infinito silencioso** |
| **Qué ve el usuario** | «En proceso». Nunca «cubierto» antes de que el asiento exista |

---

## 5 · Piezas declaradas por nivel

**Átomos puros** — sin Spring, sin jOOQ, probados solos:

| Átomo | CU | Por qué es átomo |
| --- | :-: | --- |
| `serializarCanonico` · `hashDeBloque` | 72, 73 | Si no son puros y deterministas, **la cadena no sirve**: dos implementaciones tienen que dar el mismo hash |
| `recorrerCadena` | 73 | Devuelve el primer fallo |
| `verificarCompromiso` · `barajarDeterminista` | 61 | **Compartidos con CU-60, que es de otro servicio** — ver H-4 |
| `aplicarReglaDeImpacto` | 70 | Puntos y peso |
| `decaimiento` · `componerPuntaje` | 71 | El total siempre cae dentro del rango del modelo — propiedad |
| `cumpleCriterio` · `insigniasAfectadasPor` | 74 | Evalúa contra datos, no contra pedidos |
| `contenidoCanonico` · `hashYFirma` | 75 | Reproducible desde el snapshot |
| `compartieronPeriodo` · `pesoDeResena` · `detectarDatosPersonales` | 76 | |
| `calcularMetricaGrupo` · `evaluarScore` · `mensajeEnHechos` | 97 | El mensaje se dice **en hechos, nunca en probabilidades** |
| `calcularPlazoHabil` · `armarEvidenciaAutomatica` | 25 | El plazo se calcula una vez y se guarda |
| `evaluarPoliticaCobertura` | 23 | Topes |
| `topeDisponible` · `cubreElHecho` | 26 | El tope firmado no se estira |
| `nivelSegunPolitica` · `restriccionesDe` | 27 | Por regla escrita, no por criterio del operador |
| `calcularDevolucion` · `repartirRemanente` | 29 | **Propiedad:** ni un centavo perdido |
| `evaluarCandidato` · `deudaQueNoSeTraspasa` | 66 | |
| `calcularPrelacion` · `factorProrrata` | 67 | **Propiedad:** lo repartido = lo disponible |

**Moléculas** (repositorios y clientes, sin lógica): `BloqueRepositorio` ·
`SelladoRepositorio` · `VerificacionRepositorio` · `EventoReputacionRepositorio` ·
`ReglaImpactoRepositorio` · `PuntajeRepositorio` · `ModeloScoringRepositorio` ·
`InsigniaRepositorio` · `CertificadoRepositorio` · `VerificadorPublico` ·
`ResenaRepositorio` · `ModeradorAutomatico` · `MetricaGrupoRepositorio` ·
`RegistroIncumplimientoRepositorio` · `HistorialEstadoRepositorio` ·
`FondoGarantiaRepositorio` · `DeudaRepositorio` · `AvalRepositorio` ·
`EjecucionAvalRepositorio` · `SubrogacionRepositorio` · `ListaRestriccionRepositorio` ·
`EvaluadorDeRestricciones` · `DevolucionFondoRepositorio` · `LiquidacionRepositorio` ·
`ReemplazoRepositorio` · `CobranzaRepositorio` · `AlertaTempranaRepositorio` ·
`DisparadorDeAcompanamiento`, más **un cliente generado por cada servicio del que
dependen**: `grupos`, `aportes`, `identidad`, `notificaciones`, `nucleo-financiero`,
`entregas`.

**Organismos** (uno por caso de uso, con `@Transactional` y `conContexto`):
`CU61VerificarSorteo` · `CU70RegistrarEventoReputacion` · `CU71RecalcularPuntaje` ·
`CU72SellarBloque` · `CU73VerificarCadena` · `CU74EvaluarInsignias` ·
`CU75EmitirCertificado` · `CU76PublicarResena` · `CU97EvaluarRiesgo` ·
`CU23CubrirIncumplimiento` · `CU25DeclararIncumplimiento` · `CU25ResolverDescargo` ·
`CU26EjecutarAval` · `CU27AplicarRestriccion` · `CU27LevantarRestriccion` ·
`CU29DevolverFondo` · `CU66ReemplazarParticipante` · `CU67DisolverGrupo`.

**Páginas** — las cuatro públicas son la razón de existir de `apps/web`:
`GET /publico/sorteos/:id/verificacion` · `GET /publico/grupos/:codigo/bloques` ·
`GET /publico/grupos/:codigo/verificacion` · `GET /verificar/:codigo`. **Sin sesión y
con `noindex, nofollow`**: llevan datos de personas.

---

## 6 · Huecos y contradicciones, encontrados al leer y **no resueltos**

Regla cero: se declaran, no se completan con una suposición.

| # | Hueco | Por qué importa | De quién es |
| :-: | --- | --- | --- |
| **H-1** | **El contenido del bloque de CU-72 vive en cuatro servicios ajenos.** Aportes, entregas, coberturas, altas y bajas, acuerdos y sorteo: ninguno se puede leer con `SELECT` (invariante 11) | Es el caso de uso que define si la transparencia es un mecanismo o una promesa. Cuatro llamadas sincrónicas dentro del cierre de período acoplan la disponibilidad de los cuatro. **Mi propuesta, sujeta a visto bueno: proyección local en `transparencia` alimentada por los eventos de dominio que esos servicios ya emiten** | Decisión de arquitectura · P1 |
| **H-2** | `reputacion_usuario` está en el esquema **`identidad`**, mientras `puntaje_reputacion` y todo el resto están en `transparencia` | CU-75 la nombra en su evidencia. O es una proyección de identidad y hay que decirlo, o el modelo tiene la reputación partida entre dos servicios | Bóveda · troncal |
| **H-3** | CU-61 verifica el sorteo, pero `sorteo_turnos` y `turno` son de **`grupos`** | Una ruta **pública** que depende de una llamada sincrónica a otro servicio se cae cuando ese servicio se cae. El paquete público debería viajar por evento al sellarse el sorteo | Decisión de arquitectura · P1 |
| **H-4** | CU-61 exige que el átomo de verificación sea **el mismo** que usa CU-60 para sortear — y CU-60 es de `grupos` | El CU lo dice explícito: con dos implementaciones se comprueba que dos códigos coinciden, no que el sorteo es correcto. → `verificarCompromiso` y `barajarDeterminista` tienen que vivir en **`plataforma/comun-dominio`**, por micro-PR, antes de que cualquiera de los dos carriles arranque | Micro-PR a `plataforma` |
| **H-5** | **CU-97 está asignado a `3B` (transparencia) pero dos de sus tres tablas principales —`alerta_temprana` y `score_riesgo_incumplimiento`— viven en `garantia`** | Tal como está declarado, el caso de uso **no se puede implementar dentro de un servicio**. O se parte (métricas en transparencia, score y alertas en garantía), o las tablas cambian de esquema. Las dos opciones son cambio de modelo, y eso es troncal: **paro y pregunto** | Bóveda · troncal |
| **H-6** | CU-27 escribe `lista_restriccion_interna` (garantía) pero **las `restriccion_usuario` que el sistema consulta viven en `identidad`** | CU-68 y CU-90 consultan las restricciones al decidir un ingreso. Si la lista y la restricción efectiva están en servicios distintos, hay una ventana en la que la persona está en la lista y todavía puede entrar a un grupo | Decisión de arquitectura · P1 |
| **H-7** | CU-97 dice que la alerta **nunca** produce por sí sola una restricción, y CU-27 exige causa consumada | No es un conflicto: es la regla que evita castigar por pronóstico. La anoto para que no se «simplifique» al implementar | — |
| **H-8** | La discrepancia de §0 sobre quién prepara `grupos` | Un carril sin declaración es un carril que improvisa | P1 |

---

## 7 · Qué necesito en `dev` antes de arrancar cada carril

| Carril | Necesita | Estado hoy |
| :-: | --- | :-: |
| `3B` | `2C` grupos cerrado (sorteo y turnos) · contratos de `3A` aportes · H-1, H-3, H-4 y H-5 resueltos | ninguno |
| `4B` | `2C` grupos · `3A` aportes · `2A` billetera · `1B` asiento contable · calendario de CU-59 · H-6 resuelto | ninguno |

Los dos están detrás de la Ola 1 y la Ola 2 completas. **La preparación es lo único
que este puesto puede adelantar sobre ellos hoy, y es lo que hace este archivo.**

## 8 · Lo que este archivo no dice

- No declara los carriles de frontend `F1`, `F2`, `F6`, `F7` ni `F13`. Se declaran
  cuando llegue su tramo, con el mismo formato.
- No propone tablas nuevas ni cambios de esquema. Los cinco huecos que tocarían el
  modelo están anotados como huecos, no como propuestas.
- **Ninguna de las seis preguntas está respondida «se verá al implementar».** Donde no
  supe, escribí el hueco con su dueño.

## Ver también

[[17 Plan de acción secuencial · coordinación de cinco máquinas]] ·
[[18 Fichas de carril · las 38 unidades de trabajo]] ·
[[05 Fases 12 a 16 · Plataforma, reputación y cumplimiento]] ·
[[04 Fases 8 a 11 · Circuito del pasanaku]] · [[carril-F0-M]]
