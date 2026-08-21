---
tags:
  - plan
  - fase
titulo: "Fases 12 a 16 — Plataforma, reputación y cumplimiento"
fases: [12, 13, 14, 15, 16]
depende_de: [11]
habilita: [17]
---

# Fases 12 a 16 — Plataforma, reputación y cumplimiento

> **Qué se construye acá.** Todo lo que el pasanaku necesita para ser un producto
> regulado y no solo un mecanismo que mueve plata: avisar (F12), medir y mostrar
> reputación (F13), habilitar y controlar organizadores (F14), auditar y reportar
> (F15) y cumplir con la UIF, ASFI y el consumidor financiero (F16).

> **Se ejecuta en:** Olas 1, 2 y 3 · carriles D (F12), D/E (F15, F14), B/C (F13, F16) — ver [[07 Carriles de trabajo concurrente]] para
> la propiedad de archivos y el prompt de arranque del carril.

> [!important] Antes de escribir la primera línea
> [[00b Estándar de ejecución · código limpio, pruebas y calidad]] aplica en
> esta fase entera: regla cero de no inventar, composición atómica, KISS,
> nombres del dominio, las siete pruebas obligatorias por caso de uso —la séptima
> es la compensación de saga— y el
> checklist de PR. **Se declara cada pieza por nivel antes de crearla.**

> **Receta exacta:** [[00c Recetario · implementar un caso de uso]] fija el orden de
> lectura, el orden de construcción en ocho pasos, las firmas canónicas y los
> nombres de las piezas de `comun/`. **Se copian, no se reinventan.**

## Estas cinco fases sí se paralelizan

Cerrada la Fase 11, las cinco son **independientes entre sí**. Se pueden repartir
entre personas o ejecutar en cualquier orden, con dos salvedades:

| Dependencia real | Motivo |
| --- | --- |
| F12 antes que las notificaciones "de verdad" de cualquier fase | Hasta F12, los avisos son *stubs* que registran el evento sin enviarlo |
| F15 (`definicion_reporte`, `ejecucion_reporte`) antes de F16 § reportes UIF | CU-43 usa la maquinaria de reportes de CU-58 |

**Regla de lectura obligatoria** (igual que en las fases anteriores): leer el CU
completo antes de implementarlo y responder por escrito las **seis preguntas de
frontera transaccional** — la sexta, restaurada por
[[20 Saneamiento del plan · huecos de la migración a microservicios]] §2: *¿esto
cruza a otro servicio y qué pasa si el otro falla?*

---

# FASE 12 — Notificaciones y comunicaciones

**Módulo:** `05_notificaciones` (15 tablas)
**Casos de uso:** CU-80, CU-81, CU-82, CU-83

> **Objetivo.** Que un hecho se convierta en un mensaje con acuse, que el
> recordatorio de aporte cobre avisando bien y no persiguiendo, y que un aviso
> obligatorio llegue aunque un proveedor esté caído.

## Gate de entrada

- [ ] Fase 11 cerrada
- [ ] Semilla `15-eventos-y-plantillas.json` aplicada

## Leer antes

`CU-80` … `CU-83` · `docs/Restricciones.md` § **R-NOT** ·
skills `notificaciones-consentimiento`, `proveedores-externos`, `trabajos-outbox`

## Capa por capa

| CU | Átomos | Moléculas | Organismo | Página |
| :-: | --- | --- | --- | --- |
| 80 | `renderizarPlantilla(version, variables)` — interpolación con escape, **falla si falta una variable** · `ventanaDeEnvio(preferencia, ahora)` | `NotificacionRepositorio` · `SupresionRepositorio` · `AdaptadorWhatsApp` · `AdaptadorPush` | `CU80DespacharNotificacion` — **consumidor idempotente del outbox** | — **solo por evento de dominio** |
| 81 | `escaleraDeRecordatorios(periodo, politica)` · `debeRecordar(obligacion, enviosPrevios, tope)` | `ProgramacionRepositorio` · `ObligacionRepositorio` | `CU81ProgramarRecordatorios` — trabajo diario, **idempotente por día** | — |
| 82 | `clasificarIntencion(texto)` — reglas explícitas; **si no está seguro devuelve `DESCONOCIDA`** · `verificarFirma(carga, firma, secreto)` | `RespuestaRepositorio` · `PagoRepositorio` · `SupresionRepositorio` | `CU82ProcesarRespuesta` | `POST /v1/webhooks/mensajeria/:proveedor` |
| 83 | `elegirProveedor(candidatos, canal, salud)` · `esperaConJitter(intento)` | `ColaEnvioRepositorio` (**toma con `SKIP LOCKED`**) · `RegistroDeSalud` · `AdaptadorMensajeria` | `CU83DespacharLote` | Trabajo `enviar-notificaciones` · `POST /v1/webhooks/:proveedor/acuses` |

## Las tres reglas que definen esta fase

1. **Ningún caso de uso envía un mensaje.** Emite un evento; F12 lo consume. Si un
   organismo importa un `AdaptadorWhatsApp`, la fase está mal implementada
   (invariante 6, regla de lint `sin-red-en-transaccion`).
2. **La supresión se respeta, salvo lo obligatorio.** Un usuario puede silenciar lo
   comercial; **no** puede silenciar un aviso que la norma exige (vencimiento de
   plazo, resolución de reclamo, cambio de tarifario). `SupresionRepositorio`
   distingue las dos categorías y la distinción está en el catálogo, no en el código.
3. **Tope de mensajes por usuario y ventana.** `debeRecordar` decide enviar,
   cancelar o **posponer**. Un recordatorio que se repite sin tope es acoso, y el
   consumidor financiero lo puede reclamar.

## Restricciones con prueba de rechazo

`R-NOT-01` `R-NOT-02` `R-NOT-03` · `R-GRP-03` · `R-SEG-01` `R-SEG-02` ·
`R-CON-01` · `R-RIS-03` · `R-AUD-01` `R-AUD-04`

## Gate de salida F12

- [ ] Gate común
- [ ] El mismo evento procesado dos veces produce **un** mensaje (`R-NOT-02`)
- [ ] Plantilla a la que le falta una variable ⇒ **falla al renderizar**, no envía un `{{nombre}}` literal
- [ ] Usuario suprimido no recibe lo comercial y **sí** recibe lo obligatorio (probado con ambos)
- [ ] Proveedor de mensajería caído ⇒ conmutación con evento; el aviso obligatorio llega igual
- [ ] Webhook entrante con firma inválida ⇒ rechazado sin procesar
- [ ] Los avisos *stub* de las fases 3 a 11 se reemplazan por envíos reales

---

# FASE 13 — Transparencia y reputación

**Módulo:** `06_transparencia_reputacion` (16 tablas)
**Casos de uso:** CU-61, CU-70, CU-71, CU-72, CU-73, CU-74, CU-75, CU-76, CU-97

> **Objetivo.** Que cada punto de reputación tenga un hecho detrás, que el número sea
> explicable y no una opinión, y que la historia del grupo se pueda auditar sin
> depender de nosotros.

## Gate de entrada

- [ ] Fase 11 cerrada
- [ ] CU-60 implementado en la Fase 8 (sus dos átomos se **importan**, no se reescriben)

## Leer antes

Los nueve CU · `docs/Restricciones.md` § **R-REP** ·
skills `reputacion-social`, `sorteo-transparencia`, `alertas-riesgo-temprano`

## 13.1 · La cadena de transparencia (CU-72, CU-73, CU-61)

| Nivel | Pieza | Nota |
| --- | --- | --- |
| Átomo | `serializarCanonico(contenido)` | Estructura estable y determinista. **Si esto no es puro, la cadena no sirve** |
| Átomo | `hashDeBloque(numero, hashAnterior, hashContenido)` | Puro |
| Átomo | `recorrerCadena(bloques)` | Devuelve **el primer fallo** |
| Átomo | `verificarCompromiso` · `barajarDeterminista` | **Importados de la Fase 8.** Una sola implementación para generar y verificar |
| Moléculas | `BloqueRepositorio` · `SelladoRepositorio` · `VerificacionRepositorio` · `SorteoRepositorio` | |
| Organismos | `CU72SellarBloque` · `CU73VerificarCadena` · `CU61VerificarSorteo` | |
| Páginas | `GET /v1/publico/grupos/:codigo/bloques` · `/verificacion` · `GET /v1/publico/sorteos/:id/verificacion` | **Rutas públicas, sin sesión** |

> **Las rutas públicas son las únicas del sistema declaradas públicas en
> `SecurityConfig` —y marcadas así en el contrato OpenAPI— fuera de registro y
> login.** No exponen datos personales: exponen hashes y órdenes. Eso hay
> que verificarlo con una prueba que inspeccione la respuesta, no confiar en el
> diseño.

## 13.2 · Reputación (CU-70, CU-71, CU-74, CU-75, CU-76)

| CU | Átomos | Organismo | Página |
| :-: | --- | --- | --- |
| 70 | `aplicarReglaDeImpacto(regla, contexto)` | `CU70RegistrarEventoReputacion` — **consumidor idempotente del outbox** | — **solo por evento de dominio** |
| 71 | `decaimiento(evento, hoy, ventana)` · `componerPuntaje(componentes, pesos)` (**propiedad: el total siempre cae dentro del rango del modelo**) | `CU71RecalcularPuntaje` | `GET /v1/usuarios/:id/reputacion` (solo lectura) |
| 74 | `cumpleCriterio(criterio, hechos)` · `insigniasAfectadasPor(evento)` | `CU74EvaluarInsignias` | Consumidor de evento · `GET /v1/perfil/insignias`. **Sin otorgamiento manual** |
| 75 | `contenidoCanonico(snapshot, seleccion)` · `hashYFirma(contenido, clave)` | `CU75EmitirCertificado` | `POST /v1/reputacion/certificados` · `GET /v1/verificar/:codigo` (público) |
| 76 | `compartieronPeriodo(autor, evaluado, grupo)` · `pesoDeResena(autor, contexto)` · `detectarDatosPersonales(texto)` | `CU76PublicarResena` + `ModeradorAutomatico` | `POST /v1/grupos/:id/resenas` · `POST /v1/resenas/:id/moderacion` |

**Tres reglas que no son negociables:**

- **No hay endpoint para otorgar una insignia a mano.** Se otorgan por criterio
  publicado, evaluado contra hechos. Un otorgamiento manual convierte el sistema de
  reputación en un favor.
- **`VerificadorPublico` resuelve el código sin filtrar existencia**: un código
  inválido y un código revocado tienen que responder igual, o se convierte en un
  oráculo para enumerar usuarios.
- **Sin convivencia comprobada no hay reseña.** `compartieronPeriodo` es la
  precondición, no una validación cosmética.

## 13.3 · Alertas tempranas (CU-97)

| Nivel | Pieza | Nota |
| --- | --- | --- |
| Átomo | `calcularMetricaGrupo(codigo, hechos)` | Una por métrica, **probada aparte** |
| Átomo | `evaluarScore(modelo, factores)` | Probabilidad **y factores principales** |
| Átomo | `mensajeEnHechos(alerta)` | Traduce la alerta a lenguaje de hechos, **sin exponer el modelo** |
| Molécula | `MetricaGrupoRepositorio` · `AlertaTempranaRepositorio` (unicidad de abierta) · `DisparadorDeAcompanamiento` |
| Organismo | `CU97EvaluarRiesgo` | |
| Página | Trabajo `evaluar-riesgo` · `GET /v1/grupos/:id/salud` | |

> **La alerta temprana dispara acompañamiento, no castigo.** El mensaje habla de
> hechos ("tenés dos aportes vencidos"), nunca de probabilidades ("tenés 73 % de
> riesgo de incumplir"). Y los factores del score se **guardan**: un score sin
> factores guardados no se puede discutir, y por lo tanto no se puede usar.

## Restricciones con prueba de rechazo

`R-REP-01` … `R-REP-06` · `R-GAR-07` · `R-GRP-06` · `R-SEG-02` `R-SEG-03` ·
`R-RIS-01` · `R-BIL-12` · `R-AUD-01` `R-AUD-02` `R-AUD-04` **`R-AUD-09`** **`R-AUD-10`**

## Gate de salida F13

- [ ] Gate común
- [ ] Alterar un bloque de la cadena ⇒ `recorrerCadena` señala **ese** bloque como el primer fallo
- [ ] **`R-AUD-09`: los hashes de la bitácora los calcula la base, no la aplicación** (probado: la app no firma su propia huella)
- [ ] **`R-AUD-10`: las cadenas se verifican en el control diario**, no solo al auditar — el trabajo diario existe y falla ruidosamente
- [ ] Las rutas públicas no devuelven ningún dato personal (probado inspeccionando la respuesta)
- [ ] Código de certificado inválido y revocado responden **igual** (sin oráculo de existencia)
- [ ] `componerPuntaje`: propiedad del rango en verde para entradas aleatorias
- [ ] Reseña sin convivencia comprobada ⇒ rechazada
- [ ] Toda alerta temprana tiene sus factores guardados y su mensaje en hechos

---

# FASE 14 — Organizador y automatización

**Módulo:** `07_organizador_automatizacion` (12 tablas)
**Casos de uso:** CU-90, CU-91, CU-92, CU-93, CU-95, CU-96

> **Objetivo.** Que administrar plata ajena se gane con requisitos medibles, se
> sostenga con desempeño evaluado y se pierda con debido proceso — y que lo repetitivo
> se automatice sin que lo sensible se automatice nunca.

## Gate de entrada

- [ ] Fase 11 cerrada · F12 recomendada (las sanciones notifican)

## Leer antes

Los seis CU · `docs/Restricciones.md` § **R-ORG** ·
skills `organizador-habilitacion`, `automatizacion-tareas`, `motor-de-reglas`,
`debido-proceso`

## 14.1 · Habilitación y contrato (CU-90, CU-91)

| CU | Átomos | Moléculas | Organismo | Página |
| :-: | --- | --- | --- | --- |
| 90 | `evaluarRequisitos(requisitos, hechos)` — cumple/no **por requisito, con valores** · `nivelAlcanzado(requisitosCumplidos)` | `SolicitudOrganizadorRepositorio` (unicidad de pendiente) · `CapacitacionOrganizadorRepositorio` (módulos, **vigencia** y aprobación) | `CU90ResolverSolicitud` | `POST /v1/organizadores/solicitudes` · `/:id/resolucion` |
| 91 | `hashContenido(texto)` · `versionAplicable(contratos, fecha)` | `ContratoOrganizadorRepositorio` (unicidad de vigente) · `ReasignadorDeGrupos` | `CU91FirmarContrato` · `CU91RescindirContrato` | `POST /v1/organizadores/contratos/:id/firma` · `/rescision` |

> **`ReasignadorDeGrupos` es la pieza que evita grupos huérfanos.** Rescindir un
> contrato sin trasladar la administración deja grupos con dinero y sin
> responsable. La reasignación va **en la misma transacción** que la rescisión, con
> constancia.

## 14.2 · Desempeño y sanción (CU-92, CU-93)

| CU | Átomos | Nota |
| :-: | --- | --- |
| 92 | `calcularMetrica(codigo, hechos, meta)` (una por métrica) · `puntajePonderado(metricas)` (**invariante: total = suma**) · `sugerirAccion(puntaje, tendencia)` | `LectorDeHechos` consulta agregados **desde la réplica de lectura** |
| 93 | `efectosDeSancion(tipo)` · `plazosDeSancion(politica, desde, calendario)` — descargo, apelación y **prescripción** | `ReasignadorDeGrupos` **compartido con CU-91** |

Organismos: `CU92EvaluarDesempeno` (trabajo mensual) · `CU93AplicarSancion` ·
`CU93ResolverApelacion`.
Páginas: `GET /v1/organizadores/:id/evaluaciones` ·
`POST /v1/organizadores/:id/sanciones` · `POST /v1/sanciones-org/:id/apelacion`.

Aplica el mismo circuito de debido proceso de la Fase 11.A: causal, notificación,
plazo guardado, descargo, decisión motivada, **apelación resuelta por otro**,
prescripción.

## 14.3 · Motor de automatización (CU-95, CU-96)

Es la fase donde es más fácil abrir un agujero. Las cinco reglas del motor:

| Regla | Pieza que la implementa |
| --- | --- |
| La condición se **compila a un AST validado**, nunca se evalúa como código | `compilarExpresion(texto)` — la **misma** implementación que usa Cumplimiento (F16), en `plataforma/comun-reglas` ([[20 Saneamiento del plan · huecos de la migración a microservicios]] §3) |
| Los umbrales **apuntan al catálogo**, no llevan números | `ReglaAutomatizacionRepositorio` con referencia a `umbral_operativo` |
| **Simulación obligatoria** contra el histórico antes de activar | `SimuladorDeReglas`, en **solo lectura** |
| **Catálogo cerrado de acciones**: un ejecutor por acción, misma interfaz | `EjecutorDeAccion` |
| Lo sensible **exige confirmación humana**, que **caduca** | `exigeConfirmacion(accion)` con tabla explícita |

Más: `claveDeTarea(regla, ambito, hecho)` es **determinista** — la idempotencia sale
del hecho disparador, no de un UUID generado en el consumidor. `TareaRepositorio` toma
con `SKIP LOCKED`. `describirAccion` da la vista previa en lenguaje llano para la
confirmación.

Organismos: `CU95ActivarRegla` · `CU96EjecutarTarea`.
Páginas: `POST /v1/automatizacion/reglas` · `/:id/simulacion` ·
`POST /v1/tareas/:id/confirmacion`. Trabajo: `motor-automatizacion`.

> **La condición se reevalúa al ejecutar, no solo al programar.** Entre que la regla
> decidió y la tarea corre puede haber pasado cualquier cosa. Si la condición ya no
> se cumple, la tarea se cancela y lo registra.

## Restricciones con prueba de rechazo

`R-ORG-01` … `R-ORG-07` · `R-UIF-09` · `R-SEG-03` `R-SEG-04` · `R-REP-03` ·
`R-CON-01` `R-CON-06` · `R-BIL-06` · `R-RIS-01` · `R-AUD-01` `R-AUD-04` `R-AUD-08`

## Gate de salida F14

- [ ] Gate común
- [ ] Rescindir un contrato **no** deja grupos huérfanos (probado con grupos activos)
- [ ] Regla sin simulación registrada ⇒ **no se puede activar**
- [ ] Una condición con código arbitrario ⇒ falla al compilar, no se ejecuta
- [ ] Acción sensible sin confirmación humana ⇒ no se ejecuta; la confirmación **caduca**
- [ ] La misma tarea disparada dos veces ⇒ un solo efecto (clave determinista)
- [ ] Condición que dejó de cumplirse al ejecutar ⇒ tarea cancelada **con registro**
- [ ] Apelación de sanción resuelta por alguien distinto del que sancionó

---

# FASE 15 — Auditoría, reportes, datos personales e indicadores

**Módulo:** `09_auditoria_reportes` (19 tablas; tres ya están de la Fase 2)
**Casos de uso:** CU-07, CU-54, CU-55, CU-58, CU-98

> **Objetivo.** Sacar datos con permiso, huella y vencimiento; medir sin que dos
> áreas reporten cifras distintas de lo mismo; y convertir un incidente en una pérdida
> cuantificada.

## Gate de entrada

- [ ] Fase 11 cerrada
- [ ] Réplica de lectura configurada (`aportaya.datasource.lectura.url`, ADR-031 — supera a ADR-011)

## Leer antes

`CU-07`, `CU-54`, `CU-55`, `CU-58`, `CU-98` · `docs/Arquitectura/ADR-011 Lecturas y réplica.md`
· skills `extraccion-de-datos`, `indicadores-tablero`, `observabilidad`,
`lecturas-proyecciones`

## 15.1 · Motor de reportes y exportación (CU-58) — primero

**La Fase 16 depende de esto.** CU-43 (remisión mensual a la UIF) usa esta maquinaria.

| Nivel | Pieza | Nota |
| --- | --- | --- |
| Átomo | `validarParametros(esperados, recibidos)` | Nombre, tipo y rango |
| Átomo | `hashResultado(filas, columnas)` | Hash **canónico y estable** del resultado |
| Molécula | `DefinicionReporteRepositorio` · `EjecucionRepositorio` (versionado) |
| Molécula | `EjecutorDeConsulta` | **Réplica de lectura, tiempo máximo y sesión del solicitante** |
| Molécula | `ExportadorCifrado` | Genera, **cifra** y le pone **caducidad** |
| Organismo | `CU58EjecutarReporte` | |
| Páginas | `POST /v1/reportes/:id/ejecuciones` · `GET /v1/exportaciones/:id` | |

> **`EjecutorDeConsulta` corre con la sesión del solicitante y la RLS vigente.** Un
> reporte no es una puerta trasera: si el solicitante no puede ver una fila
> consultando, tampoco puede verla exportando. Esto se prueba explícitamente.

La exportación **caduca** y tiene **tope de descargas**. El archivo va por el puerto
`AlmacenArchivos` de `plataforma/comun-dominio`
([[20 Saneamiento del plan · huecos de la migración a microservicios]] §3) cifrado,
con su SHA-256 en base.

## 15.2 · Derechos sobre datos personales (CU-07)

| Nivel | Pieza |
| --- | --- |
| Átomo | `calcularPlazoLegal` — **se guarda, no se recalcula** |
| Átomo | `clasificarDatosRetenibles` — qué se conserva por ley y qué se **seudonimiza** |
| Molécula | `SolicitudDatosRepositorio` · `AnonimizacionRepositorio` |
| Organismo | `CU07AtenderDerechosDeDatos` |
| Página | `POST /v1/usuarios/:id/solicitudes-datos` |

> **La supresión no borra lo que la norma obliga a conservar: lo seudonimiza.** Un
> `DELETE` sobre el libro contable rompe el cuadre y viola la retención regulatoria.
> `clasificarDatosRetenibles` es la pieza que resuelve esa tensión, y su tabla es
> explícita.

## 15.3 · Riesgo operativo e incidentes (CU-54, CU-55)

| CU | Átomos | Moléculas | Organismo | Página |
| :-: | --- | --- | --- | --- |
| 54 | `clasificarEvento` — valida la taxonomía y calcula la **pérdida neta** | `EventoRiesgoRepositorio` (base de pérdidas, **append-only**) · `PlanAccionRepositorio` (responsable y plazo) | `CU54RegistrarRiesgoOperativo` | `POST /v1/riesgos/eventos` |
| 55 | `calcularPlazosDelIncidente` — contención, reporte y notificación; **se guardan** | `IncidenteSeguridadRepositorio` · `ActivoInformacionRepositorio` | `CU55GestionarIncidente` | `POST /v1/seguridad/incidentes` |

Los eventos de riesgo ya vienen alimentados desde fases anteriores: un faltante de
arqueo (F6, CU-57), un reverso (F6, CU-14) y una cadena de transparencia rota (F13,
CU-73) **abren** un evento de riesgo. Esta fase cierra el circuito.

## 15.4 · Tablero de indicadores (CU-98)

| Nivel | Pieza | Nota |
| --- | --- | --- |
| Átomo | `variacion(actual, anterior)` | **Manejo de cero y de ausencia** |
| Átomo | `cumpleMeta(valor, meta, sentido)` | Semáforo según si más es mejor o peor |
| Átomo | `suprimirPorMinimo(valor, casos, minimo)` | **Privacidad por agregación** |
| Molécula | `IndicadorRepositorio` (unicidad por código, dimensión y período) · `CalculadorDeIndicador` (uno por indicador, **contra la réplica**) |
| Organismo | `CU98PublicarTablero` — calcula la familia completa y publica el período |
| Página | Trabajo `publicar-indicadores` · `GET /v1/tablero` |

Cuatro reglas: la **definición del indicador es versionada** · la **meta se fija antes
del período** · lo **provisorio se marca como tal** · cada familia tiene **dueño**.
Son las que evitan que dos áreas reporten cifras distintas de lo mismo.

## Restricciones con prueba de rechazo

`R-RIS-01` `R-RIS-02` · `R-SEG-02` `R-SEG-03` `R-SEG-05` `R-SEG-06` ·
`R-UIF-06` · `R-CON-05` · `R-LIC-03` · `R-BIL-12` ·
`R-AUD-01` `R-AUD-07` `R-AUD-08`

## Gate de salida F15

- [ ] Gate común
- [ ] **Un reporte no devuelve filas que el solicitante no podría ver consultando** (RLS en la ejecución, probado)
- [ ] La exportación caduca y respeta el tope de descargas
- [ ] Supresión de datos ⇒ seudonimización, **el libro contable sigue cuadrando**
- [ ] Indicador con menos casos que el mínimo ⇒ suprimido, no publicado
- [ ] Reejecutar un reporte con los mismos parámetros da el **mismo hash**
- [ ] Un evento de riesgo abierto por un arqueo faltante llega hasta su plan de acción

---

# FASE 16 — Cumplimiento UIF/ASFI, reclamos y continuidad

**Módulo:** `12_cumplimiento_asfi` (**47 tablas — el módulo más grande del modelo**)
**Casos de uso:** CU-41, CU-42, CU-43, CU-44, CU-45, CU-47, CU-48, CU-49, CU-52, CU-53, CU-56, CU-94

> **Objetivo.** Que el sistema detecte, investigue, reporte y responda: umbrales UIF,
> alertas, casos, ROS, oficios de autoridad, reclamos en plazo, gobierno por comités y
> continuidad probada.

Por tamaño (47 tablas, 12 CU) se parte en **cuatro sub-fases con gate propio**. Es el
riesgo 10 del plan maestro.

## Gate de entrada

- [ ] Fase 11 cerrada · **Fase 15 § 15.1 cerrada** (CU-43 depende de CU-58)
- [ ] Semillas `06-umbrales-uif.json`, `07-reportes-regulatorios.json`, `08-gobierno-y-licencia.json` aplicadas
- [ ] Fase 4 ya dejó implementados CU-02, CU-03, CU-06 y CU-46 de este módulo

## Leer antes

Los doce CU · **`docs/Cumplimiento.md` completo** · `docs/Restricciones.md` § **R-UIF**,
§ **R-CON**, § **R-LIC**, § **R-RIS** · skills `cumplimiento-uif`,
`reportes-regulatorios`, `reclamos-consumidor`, `gobierno-comites`,
`respaldos-restauracion`, `motor-de-reglas`

## Sub-fase 16.A — Umbrales y operaciones relevantes (CU-41, CU-42)

| Nivel | Pieza | Nota |
| --- | --- | --- |
| Átomo | `acumularEnVentana` | Suma desde el reinicio de la ventana. **Puro y reproducible** |
| Átomo | `convertirAUsd` | Aplica el tipo de cambio del día **y lo devuelve para guardarlo** |
| Átomo | `clasificarConceptoRog` | Tipo de transacción → concepto del art. 53 |
| Moléculas | `UmbralUifRepositorio` · `OperacionRelevanteRepositorio` · `TipoCambioRepositorio` |
| Organismos | `CU41RegistrarPcc01` · `CU42RegistrarRog` — **consumidores idempotentes del evento post-commit** (S9), nunca dentro de la transacción de la operación |
| Páginas | — sin endpoint |

> **`convertirAUsd` devuelve el tipo de cambio para guardarlo, no solo el monto
> convertido.** Un reporte a la UIF tiene que poder reconstruirse tal cual se generó,
> años después, con la cotización que se usó ese día.

Estos dos organismos **consumen el evento post-commit** que las fases 6 y 9 emiten al
mover dinero (S9 de [[20 Saneamiento del plan · huecos de la migración a microservicios]] §2).
El umbral se evalúa contra el `catalogo` local del servicio de dinero **antes** del
commit; el registro de la operación relevante lo escribe `cumplimiento` **al consumir
el evento**, append-only e idempotente por `id_evento`. **Nunca** se registra dentro de
la transacción de la operación: eso cruzaría a otro servicio dentro del commit, que es
justo lo que S9 evita.

**Gate 16.A:** una operación que cruza el umbral emite el evento post-commit y
`cumplimiento` registra la operación relevante **al consumirlo** (S9); el mismo evento
duplicado ⇒ un solo registro (acumulado idempotente); la ventana acumulada es
reproducible; el tipo de cambio queda guardado.

## Sub-fase 16.B — Monitoreo, casos y ROS (CU-44, CU-48, CU-45)

| CU | Átomos | Moléculas | Organismo |
| :-: | --- | --- | --- |
| 44 | `agruparAlertas` (mismo cliente → un caso) · `calcularPlazoInvestigacion` | `AlertaRepositorio` · `CasoRepositorio` · `ReporteSospechosoRepositorio` | `CU44InvestigarYReportar` |
| 48 | `compilarExpresion(texto)` (AST validado contra el esquema de campos) · `evaluarRegla(ast, operacion, umbrales)` · `accionPermitida(severidad, accion)` | `ReglaRepositorio` · `AlertaCumplimientoRepositorio` · `SimuladorDeReglas` (**histórico, solo lectura**) | `CU48ActivarRegla` · `CU48TriarAlerta` |
| 45 | `delimitarAlcance` — traduce el pedido a un conjunto **acotado** de datos | `RequerimientoRepositorio` · `AccesoDatosRepositorio` (**cada consulta con su justificación**) | `CU45AtenderRequerimiento` |

Páginas: `POST /v1/cumplimiento/casos` · `POST /v1/reglas` · `/:id/simulacion` ·
`POST /v1/alertas/:id/triaje` · `POST /v1/cumplimiento/requerimientos`.

Mismo motor de reglas de la Fase 14.3: **una sola implementación** de
`compilarExpresion` y `SimuladorDeReglas`, compartida entre automatización y
cumplimiento. Si se duplican, se calibran distinto y divergen.

> **Toda alerta termina con una conclusión escrita.** Una alerta cerrada sin motivo
> es peor que una alerta no generada: demuestra que el monitoreo existe y que nadie
> lo mira.

**Gate 16.B:** una regla sin simulación no se activa · toda alerta tiene conclusión ·
un requerimiento de autoridad deja registro de cada consulta con su justificación ·
el ROS tiene su radicado.

## Sub-fase 16.C — Reportes regulatorios (CU-43)

| Nivel | Pieza | Nota |
| --- | --- | --- |
| Átomo | `armarArchivo` | Genera el formato del catálogo **y su hash** |
| Átomo | `calcularFechaLimite` | Plazo desde el catálogo. **Se guarda, no se recalcula** |
| Moléculas | `ReporteRegulatorioRepositorio` · `EnvioRegulatorioRepositorio` (constancia y reintentos) · `OrganismoAdaptador` |
| Organismo | `CU43RemitirReportes` — trabajo mensual **con doble control antes de enviar** |
| Página | `POST /v1/cumplimiento/reportes/:periodo/envio` |

Tres reglas del calendario regulatorio (`07-reportes-regulatorios.json`, 10 reportes):
**se remite hasta el día 15, incluso en cero** · el vencimiento **alerta antes**, no
después · una observación del organismo abre un **reenvío** con su propio plazo.

Usa la maquinaria de CU-58 (Fase 15) para generar, y el cron con bloqueo por
identificador de la Fase 2.

**Gate 16.C:** un mes sin operaciones **igual remite** (reporte en cero) · el archivo
es reproducible (mismo hash al regenerar) · un reporte por vencer alerta con
antelación · una observación abre reenvío con plazo propio.

## Sub-fase 16.D — Consumidor, gobierno y continuidad (CU-52, CU-53, CU-56, CU-47, CU-49, CU-94)

| CU | Piezas clave |
| :-: | --- |
| 52 | `calcularPlazoHabil` (**5 días hábiles administrativos desde el ingreso**) · `evaluarProrroga` (límite y comunicación exigida) · `ReclamoRepositorio` · `DevolucionRepositorio` (reparación cuando el resultado es favorable) · `CU52AtenderReclamo` |
| 53 | `armarExpediente` (reclamo + respuesta + evidencia técnica) · `InstanciaReclamoRepositorio` · `EvidenciaRepositorio` · `CU53ElevarReclamo` |
| 56 | `compararObjetivos` (RTO y RPO **obtenidos contra comprometidos**) · `PruebaContinuidadRepositorio` · `ActaComiteRepositorio` · `CU56ProbarContinuidad` |
| 47 | `riesgoInherente(probabilidad, impacto)` · `riesgoResidual(inherente, controles)` · `VerificadorDeAlcance` (cruza contra la licencia vigente) · `CU47AprobarEvaluacion` |
| 49 | `tieneIncompatibilidad(rolesActuales)` · `coberturaDePeriodo(personal, capacitaciones, periodo)` (**lista nominal de pendientes**) · `CU49Designar` · `CU49RegistrarCapacitacion` |
| 94 | `verificarQuorum(asistentes, comite)` · `computarVotos(votos, quorum)` (**abstenciones que no ponderan**) · `esParteInteresada(miembro, asunto)` · `ActaRepositorio` (**append-only**) · `EjecutorDeDecision` · `CU94CerrarActa` |

Páginas: `POST /v1/reclamos` · `/:id/instancias` · `POST /v1/continuidad/pruebas` ·
`POST /v1/evaluaciones-producto` · `/:id/aprobacion` ·
`POST /v1/cumplimiento/oficiales` · `/capacitaciones` · `POST /v1/comites/:tipo/sesiones`.

> **Los efectos del acta se aplican en la misma transacción que la cierra.** Un acta
> cerrada cuyos efectos se aplican "después" es un acta que dice una cosa mientras el
> sistema hace otra. `EjecutorDeDecision` despacha según `referenciaTipo`, con
> catálogo cerrado.

**Gate 16.D:** el plazo de 5 días hábiles se guarda al ingresar el reclamo y una
prórroga sin comunicación se rechaza · un producto sin no objeción **no se lanza** ·
un oficial con rol incompatible no se designa · un acta sin quórum no se cierra · los
efectos del acta se aplican en su misma transacción.

## Restricciones con prueba de rechazo

`R-UIF-01` … `R-UIF-12` (las 12) · `R-CON-01` … `R-CON-05` ·
`R-LIC-01` `R-LIC-03` `R-LIC-04` · `R-RIS-01` `R-RIS-03` ·
`R-LIM-01` · `R-BIL-14` · `R-SEG-02` `R-SEG-04` ·
`R-AUD-01` `R-AUD-04` `R-AUD-08`

## Gate de salida F16

- [ ] Los cuatro gates de sub-fase (16.A a 16.D) cerrados
- [ ] Gate común
- [ ] **Las 12 restricciones R-UIF con prueba de rechazo**
- [ ] Reporte mensual en cero se remite igual
- [ ] Un reclamo vencido dispara alerta **antes** del vencimiento, con el plazo guardado
- [ ] `compilarExpresion` es una sola implementación compartida con la Fase 14
- [ ] Toda alerta de monitoreo termina con conclusión escrita
- [ ] Los 87 casos de uso **del núcleo** están implementados. **Ninguno queda pendiente**
      (los 12 de contabilidad ERP y publicidad son de las fases 18 y 19)

---

## 🏁 Hito: el sistema está completo

Al cerrar la Fase 16, los **87 casos de uso del núcleo** están implementados, las
**304 tablas** tienen código que las escribe y las **140 restricciones** tienen prueba
de rechazo. Falta endurecerlo y desplegarlo: [[06 Fase 17 · Endurecimiento, E2E y despliegue]].

## Ver también

[[00c Recetario · implementar un caso de uso]] · [[07 Carriles de trabajo concurrente]] · [[00b Estándar de ejecución · código limpio, pruebas y calidad]] · [[00 Plan maestro]] · [[04 Fases 8 a 11 · Circuito del pasanaku]] · [[06 Fase 17 · Endurecimiento, E2E y despliegue]] · [[Cumplimiento]] · [[_CasosDeUso]]
