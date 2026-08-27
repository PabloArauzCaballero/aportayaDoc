# Módulo 9 — Auditoría, Reportes, Cumplimiento y Datos Personales

> **Pregunta de negocio que responde este módulo:**
> *Todo lo anterior está muy bien. Ahora: ¿cómo lo pruebo? ¿Cómo sé quién hizo qué?
> ¿Cómo detecto que alguien está usando la plataforma para lavar plata? ¿Y qué hago
> cuando un usuario me pide que borre sus datos?*

Es el módulo transversal: **observa a los ocho anteriores sin que ellos dependan de
él.** Se alimenta por eventos, no por acoplamiento — un principio de diseño que
importa porque una auditoría que obliga a los módulos a conocerla es una auditoría
que se desactiva en cuanto molesta.

---

## La distinción que define el módulo: tres registros, no uno

Es el error clásico confundirlos. Son tres preguntas distintas y hacen falta las
tres:

| Entidad | Responde | Cuándo se pide |
| --- | --- | --- |
| `BitacoraEvento` | **Quién hizo qué sobre qué dato.** Auditoría técnica de escritura. | Cuando algo cambió y hay que saber quién lo cambió. |
| `EventoDominio` | **Qué pasó en el negocio.** Integración entre módulos. | Continuamente: es como M5, M6 y cumplimiento se enteran de las cosas. |
| `RegistroAccesoDatos` | **Quién MIRÓ datos sensibles.** Auditoría de lectura. | **Cuando hay una filtración.** |

El tercero es el que casi siempre falta y el único que sirve el día que se filtran
las cédulas de los usuarios. Una bitácora de escritura no dice nada de quién
consultó.

---

## Paquete: Bitácora y Trazabilidad

### `BitacoraEvento` / `bitacora_evento` — **inmutable, encadenada por hash**

**Qué es.** El registro insert-only de cada cambio sobre cualquier dato del
sistema, con valor anterior, valor nuevo y autor.

**Para qué sirve (negocio).** Implementa RN-17 y responde la pregunta que aparece
en cada conflicto serio: ***¿quién cambió esto y cuándo?*** Casos concretos que
solo esta tabla resuelve:

- El monto del aporte del grupo cambió de Bs 500 a Bs 600 y nadie sabe quién lo
  cambió.
- Una obligación aparece como exonerada y el participante jura que no pidió nada.
- La cuenta bancaria de un beneficiario se modificó tres días antes de su turno.

Dos campos merecen atención especial:

**`hashAnterior` — encadenamiento.** Cada registro incluye el hash del anterior.
Alterar un registro viejo rompe la cadena y `verificarCadena()` lo detecta.
Esto convierte la bitácora de "un log que un administrador podría editar" a "un
libro que, si se toca, se nota". Sin encadenamiento, la auditoría solo protege
contra errores, no contra alguien con acceso a la base de datos — que es
precisamente contra quien hay que protegerse.

**`suplantandoAUsuarioId` — actuación de soporte.** Cuando un agente de soporte
opera "como" un usuario para resolverle un problema, este campo deja constancia.
**Sin él, nadie distingue lo que hizo el titular de lo que hizo un empleado.** Es la
diferencia entre poder afirmar "el usuario autorizó esa entrega" y no poder
afirmarlo.

`reconstruirEstadoA(entidad, id, fecha)` permite responder "¿cómo estaba esto el 15
de marzo?", que es lo que se necesita cuando el reclamo llega meses después.

**Por qué debe existir.** Sin bitácora, cualquier disputa se resuelve por la
palabra de quien tiene acceso al sistema. Con bitácora encadenada, se resuelve con
evidencia verificable.

**A nivel de sistema.**
`hash_registro = SHA256(secuencia || entidad || entidad_id || accion ||
valor_nuevo || fecha_hora || hash_anterior)`.
`REVOKE UPDATE, DELETE ON bitacora_evento FROM rol_aplicacion` — solo `INSERT`.
`entidad_id` es referencia polimórfica a cualquier PK de M1–M8, validada en
aplicación y **no por FK**, deliberadamente: acoplar la auditoría al esquema que
audita significaría que cada migración de esquema rompe la auditoría.
Particionada por mes.

---

### `EventoDominio` / `evento_dominio` — patrón outbox

**Qué es.** Un hecho de negocio publicado para que otros módulos reaccionen:
`pagos.acreditado`, `incumplimiento.detectado`, `entrega.confirmada`.

**Para qué sirve (negocio).** Es **el sistema nervioso de la plataforma**, y
resuelve un problema muy concreto de consistencia.

El problema: cuando se acredita un pago hay que actualizar la obligación, notificar
al usuario (M5), registrar el evento de reputación (M6) y alimentar métricas. Si
esas cuatro cosas se hacen con llamadas directas, cualquier falla parcial deja el
sistema inconsistente: **el pago se acreditó pero la reputación no lo registró**, y
nadie se entera hasta que alguien nota que su score no subió.

La solución (outbox transaccional): los módulos escriben **su cambio y el evento en
la misma transacción**. Un publicador los envía después. Si la cola está caída, los
eventos esperan; nada se pierde y nada se desincroniza del dato real.

`causationId` permite reconstruir la cadena causal completa:
`aporte vencido → incumplimiento detectado → cobertura aplicada → sanción
notificada`. Es lo que permite explicar por qué pasó algo, no solo que pasó.

**Por qué debe existir.** Sin outbox, la reputación, las notificaciones y la
auditoría se desincronizan del dato real ante cualquier fallo — y esos fallos
ocurren.

**A nivel de sistema.**
`CREATE INDEX ON evento_dominio (ocurrido_en) WHERE estado = 'PENDIENTE'` para el
despacho. Consumidores: M6 (reputación), M5 (notificaciones), métricas y
cumplimiento.

---

### `RegistroAccesoDatos` / `registro_acceso_datos`

**Qué es.** El registro de quién **consultó** datos sensibles de quién, con qué
justificación.

**Para qué sirve (negocio).** **Es el registro que se pide cuando hay una
filtración**, y el que casi nunca existe.

El escenario: se filtran los documentos de identidad de 200 usuarios. La bitácora
de escritura no sirve — nadie los modificó, alguien los *miró*. Sin esta tabla, no
hay forma de saber si fue un empleado, cuál, ni cuántos registros consultó.

`justificacion` + `ticketSoporteId` imponen una disciplina saludable: **para ver la
cédula o la cuenta bancaria de alguien hay que decir por qué**, y ese porqué debe
estar atado a un caso real. La sola existencia de ese requisito reduce
drásticamente las consultas por curiosidad.

`esAccesoInusual()` detecta el patrón peligroso: un consultor que en una hora
accede a 300 registros de usuarios sin relación entre sí no está resolviendo un
ticket, está extrayendo una base de datos.

**Por qué debe existir.** Es requisito de cualquier normativa seria de protección de
datos, y es la única defensa —y la única prueba— ante un incidente de privacidad.

**A nivel de sistema.** Consultas masivas por un mismo consultor disparan
`alerta_cumplimiento` de categoría `RED_SOSPECHOSA`.

---

### `ServicioAuditoria` — Servicio de dominio

**Qué es.** El punto único de escritura de auditoría y de verificación de la
cadena.

**Para qué sirve (negocio).** `trazarOperacion(correlationId)` es la función que
usa el soporte: dado un identificador de correlación, devuelve **todo** lo que
ocurrió en ese flujo, a través de todos los módulos. Es la diferencia entre
diagnosticar un problema en diez minutos o en dos días.

---

### `PoliticaRetencion` / `politica_retencion` — Política configurable

**Qué es.** Cuánto tiempo se conserva cada tipo de dato y qué se hace al vencer:
archivar, anonimizar o eliminar.

**Para qué sirve (negocio).** Concilia dos obligaciones que se contradicen:
**conservar** (la normativa financiera exige guardar registros contables por años)
y **borrar** (la normativa de datos personales exige no guardar más de lo
necesario).

`baseLegal` documenta bajo qué norma se retiene cada cosa. Cuando un usuario ejerce
su derecho de cancelación y no se le puede borrar todo, este campo es la
justificación.

`ARCHIVAR | ANONIMIZAR | ELIMINAR` son tres respuestas distintas: los asientos
contables se archivan (se necesitan íntegros), los logs de acceso se anonimizan
(sirve el patrón, no la identidad), los borradores se eliminan.

**Por qué debe existir.** Sin política de retención, o se guarda todo para siempre
(riesgo regulatorio y costo creciente) o se borra sin criterio (y se pierde
evidencia que la ley obliga a tener).

---

## Paquete: Reportes y Explotación

### `DefinicionReporte` / `definicion_reporte`

**Qué es.** El catálogo de reportes disponibles, con su consulta, parámetros,
permiso requerido y si contiene datos sensibles.

**Para qué sirve (negocio).** Los diez tipos cubren a todos los públicos de la
plataforma: el participante (estado de cuenta), el organizador (estado del grupo,
desempeño), la operación (cartera en mora, conciliación diaria), la dirección
(KPIs) y el regulador (operaciones sospechosas).

`permisoRequerido` y `contieneDatosSensibles` son control de acceso: **el reporte
de cartera en mora contiene nombres y montos de deudas de personas reales**, y no
puede estar disponible para cualquiera con sesión iniciada.

`estimarCosto()` protege la base de datos: un reporte sin filtros sobre tres años
de pagos puede tumbar la producción en el peor momento.

**Por qué debe existir.** Sin catálogo, cada reporte es una consulta escrita a mano
por alguien, sin control de acceso, sin caché y sin registro de quién la ejecutó.

---

### `EjecucionReporte` / `ejecucion_reporte`

**Qué es.** Cada corrida de un reporte, con parámetros, resultado y quién la pidió.

**Para qué sirve (negocio).** Deja rastro de **quién extrajo qué información y
cuándo**. `hashResultado` permite verificar después que un reporte presentado en
una reunión o ante un tercero es el que realmente salió del sistema, y no una hoja
de cálculo editada.

`duracionMs` y `filasGeneradas` son las métricas que permiten detectar reportes que
se degradan a medida que crece la base.

**Por qué debe existir.** Sin registro de ejecuciones, la extracción de datos es
invisible. Y la extracción invisible es exactamente cómo salen los datos de una
organización.

---

### `ExportacionReporte` / `exportacion_reporte`

**Qué es.** El archivo generado a partir de una ejecución: PDF, XLSX, CSV o JSON.

**Para qué sirve (negocio).** El archivo es el objeto que efectivamente sale de la
plataforma, y por eso tiene sus propios controles:
- `estaCifrado`: si contiene datos sensibles, se cifra.
- `expiraEn`: el enlace de descarga caduca. Un CSV con datos de 500 usuarios no
  puede quedar accesible indefinidamente en una URL.
- `descargas` + `registrarDescarga()`: quién lo bajó y cuántas veces.
- `revocarAcceso()`: cortar la descarga si se detecta que el enlace circuló.

**Por qué debe existir.** Es el punto donde los datos dejan el perímetro de la
plataforma. Sin controles en ese punto, todo lo demás es teatro.

**A nivel de sistema.** Deja rastro en `registro_acceso_datos`.

---

### `ProgramacionReporte` / `programacion_reporte`

**Qué es.** Reportes que se generan y envían solos según un cron.

**Para qué sirve (negocio).** Los reportes que hay que pedir no se piden. La
conciliación diaria, el resumen semanal del organizador, el estado mensual del
participante: automatizarlos es lo que hace que la información llegue a quien tiene
que actuar sobre ella, y no que se descubra un problema en el cierre trimestral.

**Por qué debe existir.** Sin programación, los controles periódicos dependen de la
disciplina de una persona.

---

### `IndicadorKPI` / `indicador_kpi`

**Qué es.** Los indicadores de negocio de la plataforma, por dimensión y período.

**Para qué sirve (negocio).** Los cuatro códigos de ejemplo son exactamente los
que definen la salud del negocio:
- `TASA_MOROSIDAD_GLOBAL`: **el indicador más importante.** Si sube
  sostenidamente, el modelo no funciona.
- `GRUPOS_ACTIVOS` y `MONTO_ADMINISTRADO`: escala.
- `TASA_CONCILIACION_AUTOMATICA`: eficiencia operativa. Si baja, hay más trabajo
  manual y más riesgo.

`meta` y `variacionPeriodoAnterior` convierten el dato en gestión: no importa el
valor absoluto, importa si va hacia donde tiene que ir.

`dimension` (global / por grupo / por organizador) permite el mismo indicador a
tres niveles, que es como se detecta que la morosidad global es aceptable pero está
concentrada en los grupos de un organizador específico.

`provisorio` acompaña al número y **no se deduce al consultarlo**. Un indicador
calculado sobre un período cuyos cierres todavía no cuadraron es una opinión, y quien
lo calculó es el único que sabe si cuadraron. Deducirlo al publicar obligaría a
auditoría a mirar el esquema de `nucleo-financiero`, que no puede: por eso el dato
viaja con la fila.

`casos` es el tamaño de la muestra con la que se calculó. Sin él no se puede aplicar
el mínimo de privacidad y el valor se publicaría identificando a las personas que lo
componen: un promedio de tres personas identifica a las tres.

**Por qué debe existir.** Sin KPIs persistidos no hay serie temporal, y sin serie
temporal no hay tendencia — solo fotos sueltas.

---

### `DefinicionIndicador` / `definicion_indicador` — Política configurable

**Qué es.** Qué mide cada indicador, cómo se calcula, de dónde sale el dato, quién
responde por él y para qué lado se cumple su meta. Versionada y con vigencia.

**Para qué sirve (negocio).** Resuelve el problema que no es técnico: **que dos
personas lleguen a una reunión con números distintos de la misma cosa**. Un indicador
es una definición, no una consulta; si dos lugares lo recalculan, ya hay dos
indicadores.

- `sentido_meta` decide de qué lado se cumple. Sin él, «morosidad 7 % con meta 5 %»
  se leería como que cumple, porque 7 es mayor que 5. La mitad de los indicadores de
  riesgo y de cumplimiento son de los que **cuanto menos, mejor**, y el semáforo se
  pinta al revés de lo que sugiere la aritmética ingenua.
- `dueno_familia` es quien escribe la explicación cuando su indicador está en rojo.
  Un indicador sin dueño es un número que nadie defiende y que, la tercera vez que
  aparece en rojo, deja de mirarse.
- `minimo_casos` es el piso de muestra para publicar sin identificar personas.
- `formula` y `fuente` son lo que se responde cuando alguien pregunta «¿y esto de
  dónde salió?» — que es la discusión que se lleva la reunión entera.

**Por qué debe existir.** Por la **reproducibilidad**. `indicador_kpi` apunta a la
versión de definición con la que se calculó, y por eso un número de hace un año vuelve
a salir igual. Cuando la fórmula cambia, se cierra la vigencia de una versión y se
abre otra: la serie vieja conserva la suya, y **el corte se puede señalar en el
gráfico** en vez de aparecer como una mejora del 40 % que nadie explica.

Sin esta tabla la definición vive en el código, y entonces se pierde en cuanto alguien
despliega: el número queda sin forma de auditarse hacia atrás.

> La columna se llama `definicion_indicador_id` y no `definicion_id` a propósito:
> `definicion_id` ya está tomado por `definicion_reporte`, en este mismo módulo, y la
> clave foránea habría resuelto en silencio a la tabla equivocada.

---

## Paquete: Cumplimiento y Prevención (AML)

> **Por qué una plataforma de pasanakus necesita antilavado.**
> Porque es, estructuralmente, un vehículo ideal: muchas personas, aportes
> recurrentes, dinero que entra fraccionado y sale consolidado en una bolsa grande.
> Un grupo con participantes ficticios es una máquina de justificar ingresos. Y la
> responsabilidad legal por no detectarlo recae sobre la plataforma.

### `ReglaCumplimiento` / `regla_cumplimiento` — Política configurable

**Qué es.** Las reglas de detección de operaciones sospechosas, con su umbral,
ventana y acción automática.

**Para qué sirve (negocio).** Las cinco categorías corresponden a tipologías
reales:
- `UMBRAL_MONTO`: operaciones sobre el límite reportable.
- `FRACCIONAMIENTO`: **la más importante.** Alguien divide un monto grande en
  varios chicos para no gatillar el umbral. Se detecta con `ventanaHoras` + suma
  acumulada, nunca operación por operación.
- `VELOCIDAD`: mucho movimiento en poco tiempo.
- `RED_SOSPECHOSA`: varias cuentas conectadas entre sí (misma cuenta bancaria
  destino, mismo dispositivo, mismo pagador).
- `LISTA_RESTRICTIVA`: coincidencia con listas de PEP, OFAC, ONU.

`accionAutomatica` gradúa la respuesta: `ALERTAR` (revisar después),
`RETENER_OPERACION` (frenar hasta revisar), `BLOQUEAR_USUARIO` (casos graves).
Retener una operación tiene costo para un usuario legítimo, así que la
proporcionalidad importa.

**Por qué debe existir.** Con reglas hardcodeadas no se pueden ajustar umbrales
cuando cambia la normativa ni agregar tipologías nuevas cuando aparece un patrón de
abuso.

---

### `AlertaCumplimiento` / `alerta_cumplimiento`

**Qué es.** Cada detección concreta, con su evidencia y su análisis.

**Para qué sirve (negocio).** Es la unidad de trabajo del oficial de cumplimiento.
El flujo `ABIERTA → EN_ANALISIS → DESCARTADA | ESCALADA | REPORTADA` con
`conclusion` obligatoria hace que **cada alerta se cierre con una decisión
fundamentada**. Eso importa por dos razones: la mayoría de las alertas son falsos
positivos (y descartarlas sin fundamento sería negligencia), y ante una inspección
hay que poder mostrar el criterio con que se descartaron.

`detalleDeteccion` guarda por qué saltó: qué operaciones, qué montos, qué ventana.

Las tipologías relevantes en un pasanaku digital, según la nota del modelo:
fraccionamiento de aportes para evitar umbrales, grupos con participantes
ficticios, **una misma cuenta destino para varios usuarios** (detectado en M4 con
`hash_numero_cuenta`), y retiro inmediato tras cobrar el turno.

**Por qué debe existir.** Sin alertas gestionadas, la detección genera ruido que
nadie procesa — que a efectos regulatorios es igual que no detectar.

---

### `ReporteOperacionSospechosa` / `reporte_operacion_sospechosa`

**Qué es.** El reporte formal a la autoridad competente (UIF), consolidando varias
alertas.

**Para qué sirve (negocio).** Es una **obligación legal**, no una opción. Y la
consolidación importa: no se reporta cada alerta por separado, se reporta un caso
—un usuario, un período, una tipología, una narrativa que explica el patrón—.

`narrativa` es el corazón del reporte: hay que **contar la historia** de por qué
esas operaciones son sospechosas. Los datos crudos no constituyen un reporte.

`aprobadoPor` (oficial de cumplimiento) y `numeroRadicado` cierran el circuito
formal.

**Por qué debe existir.** No reportar cuando corresponde tiene consecuencias
legales directas para la plataforma y sus responsables. Y el reporte tiene que
quedar registrado, con fecha y radicado, para poder probar que se cumplió.

---

### `ListaRestrictivaExterna` / `lista_restrictiva_externa` y `CoincidenciaLista` / `coincidencia_lista`

**Qué son.** Las listas de personas restringidas (PEP, OFAC, ONU, locales) y las
coincidencias detectadas contra usuarios.

**Para qué sirven (negocio).** El chequeo contra listas es requisito normativo
básico al dar de alta a un usuario y periódicamente después.

`puntajeSimilitud` y el estado `FALSO_POSITIVO` reconocen el problema real de estas
listas: **los nombres coinciden con frecuencia y la mayoría son falsos positivos.**
"Juan Pérez" va a coincidir con alguien. Sin gestión de falsos positivos, o se
bloquea a gente inocente o se ignoran todas las coincidencias — ambos extremos son
inaceptables.

`revisadaPor` deja constancia de quién decidió que era falso positivo, que es la
decisión que un inspector revisará.

`version` de la lista importa: hay que poder demostrar contra qué versión se
verificó a un usuario en su fecha de alta.

**Por qué debe existir.** Es requisito regulatorio, y sin gestión de coincidencias
el requisito se cumple mal en cualquiera de las dos direcciones.

---

### `UmbralOperativo` / `umbral_operativo` — Política configurable

**Qué es.** Cuánto puede mover un usuario según su nivel de KYC.

**Para qué sirve (negocio).** Implementa el principio de **KYC escalonado**, que
es lo que permite tener a la vez baja fricción y cumplimiento:

- Sin verificación: puede participar en grupos chicos.
- KYC básico: montos medianos.
- KYC avanzado: sin restricción práctica.

Es lo que evita el falso dilema entre "pedir cédula a todos" (que expulsa a la
mayoría del público objetivo en el registro) y "no pedir nada" (que es
incumplimiento normativo). La persona escala su verificación cuando quiere operar
más.

`ACUMULADO_MENSUAL` como concepto es importante: el control no puede ser solo por
operación individual, porque entonces el fraccionamiento lo esquiva trivialmente.

**Por qué debe existir.** Sin umbrales por nivel de KYC, la única política posible
es la misma exigencia para todos — y cualquiera de las dos opciones mata el
producto o lo pone fuera de la ley.

---

## Paquete: Datos Personales y Soporte

### `SolicitudDatosPersonales` / `solicitud_datos_personales`

**Qué es.** El ejercicio de los derechos ARCO: acceso, rectificación, cancelación,
oposición, portabilidad.

**Para qué sirve (negocio).** Es un derecho del usuario y una obligación de la
plataforma, **con plazo legal** (`fechaLimiteLegal`). Incumplir el plazo es
sancionable con independencia de si después se atiende.

El método `rechazarPorObligacionesVigentes(motivo)` es la pieza de negocio más
delicada y merece explicación: **el derecho de cancelación no borra registros con
obligación legal o financiera vigente.** Si el usuario tiene una deuda abierta en
M8 o hay asientos contables suyos en M3, esos datos se conservan y se documenta la
base legal.

Es exactamente la situación que hay que resolver bien: alguien que debe plata pide
que borren sus datos para desaparecer. La respuesta no puede ser "no" a secas ni
"sí" sin más: es una atención parcial, fundamentada, con lo que se conserva y por
qué.

**Por qué debe existir.** Sin gestión formal, las solicitudes llegan por correo, se
pierden y se incumplen los plazos.

---

### `ProcesoAnonimizacion` / `proceso_anonimizacion`

**Qué es.** La ejecución técnica del borrado o seudonimización, con lo que se
afecta y lo que se retiene por ley.

**Para qué sirve (negocio).** Borrar a un usuario de un sistema financiero no es
un `DELETE`. Sus pagos aparecen en asientos contables que deben conservarse; sus
aportes son parte de la historia de un grupo con otras once personas cuyos derechos
también hay que respetar.

`SEUDONIMIZACION` es habitualmente la respuesta correcta: **se rompe el vínculo con
la persona pero se conserva la integridad de los registros.** El asiento contable
sigue cuadrando; ya no dice quién.

`datosRetenidosPorLey` documenta explícitamente qué no se borró y con qué
fundamento. Es lo que se le entrega al usuario como respuesta y lo que se le
muestra a un inspector.

`simular()` antes de ejecutar es indispensable: una anonimización mal ejecutada es
irreversible y puede romper la contabilidad de un grupo entero.

**Por qué debe existir.** Sin proceso formal, el borrado es un script ad-hoc que
alguien corre una vez, sin registro de qué se afectó ni posibilidad de explicarlo
después.

---

### `TicketSoporte` / `ticket_soporte`

**Qué es.** El canal formal de atención al usuario.

**Para qué sirve (negocio).** En una plataforma de dinero, el soporte no es
opcional: **cada problema no resuelto es plata que alguien siente que perdió.**
`referenciaEntidad` + `referenciaId` conectan el ticket con el objeto del problema
(la entrega que no llegó, el pago que no se acreditó), lo que le da al agente el
contexto sin tener que pedírselo al usuario.

`slaHoras` establece el compromiso de tiempo. Un reclamo por una entrega no
recibida no puede tener el mismo SLA que una consulta sobre cómo cambiar el avatar.

**Por qué debe existir.** Y hay una razón adicional: `ticketSoporteId` es la
justificación que exige `RegistroAccesoDatos` para consultar datos sensibles. **Sin
tickets, no hay forma de validar que un acceso a datos personales tenía una razón
legítima.**

---

### `IncidenteOperativo` / `incidente_operativo`

**Qué es.** Un fallo del sistema con impacto en usuarios o en dinero.

**Para qué sirve (negocio).** `impactoUsuarios` e `impactoMonetario` son los campos
que distinguen un incidente técnico de un incidente de negocio. Un servicio caído
dos horas un domingo es una cosa; el mismo servicio caído el día de vencimiento de
cuarenta grupos es otra completamente distinta, y hay que poder cuantificarla —
entre otras cosas para decidir si corresponde condonar la mora de ese día.

`causaRaiz` y `accionesCorrectivas` con post mortem convierten el incidente en
aprendizaje en vez de en un susto que se repite.

**Por qué debe existir.** Sin registro de incidentes no se puede correlacionar un
reclamo masivo de usuarios con la caída que lo causó, ni decidir con fundamento
sobre las consecuencias (perdonar mora, reprogramar entregas, compensar).

---

## Resumen: qué se cae si se quita cada bloque

| Bloque | Si no existe… |
| --- | --- |
| `BitacoraEvento` encadenada | Las disputas se resuelven por la palabra de quien tiene acceso a la base de datos. |
| `suplantandoAUsuarioId` | No se distingue lo que hizo el titular de lo que hizo un empleado de soporte. |
| `EventoDominio` (outbox) | La reputación y las notificaciones se desincronizan del dato real ante cualquier fallo. |
| `RegistroAccesoDatos` | El día de una filtración, no hay forma de saber quién consultó qué. |
| `PoliticaRetencion` | O se guarda todo para siempre, o se borra evidencia que la ley obliga a tener. |
| `DefinicionReporte` + `Ejecucion` | Cada reporte es una consulta a mano, sin control de acceso ni rastro. |
| `ExportacionReporte` | Los datos salen del perímetro sin cifrado, sin caducidad y sin registro. |
| `IndicadorKPI` | No hay tendencia, solo fotos sueltas. |
| `ReglaCumplimiento` + `Alerta` | La plataforma es un vehículo de lavado sin defensas, con responsabilidad legal directa. |
| `ReporteOperacionSospechosa` | Incumplimiento legal con consecuencias para la plataforma y sus responsables. |
| `UmbralOperativo` | Falso dilema: o se pide cédula a todos y se pierde el mercado, o se opera fuera de la ley. |
| `SolicitudDatosPersonales` | Se incumplen plazos legales; o se borran datos que había obligación de conservar. |
| `ProcesoAnonimizacion` | El borrado es un script ad-hoc que puede romper la contabilidad de un grupo. |
| `TicketSoporte` | No hay justificación validable para los accesos a datos sensibles. |
| `IncidenteOperativo` | No se puede correlacionar un reclamo masivo con la caída que lo causó. |
