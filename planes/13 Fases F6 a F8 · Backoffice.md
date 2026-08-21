---
tags:
  - plan
  - fase
  - frontend
titulo: "Fases F6 a F8 — Backoffice (React + Vite)"
fases: [F6, F7, F8]
depende_de: [F0, F1]
habilita: [F12]
---

# Fases F6 a F8 — Backoffice

> **Se ejecuta en:** Ola F1 · carril B (F6) y Ola F2–F3 · carriles B1, B2 (F7, F8).
> Ver [[16 Carriles de frontend]].

> [!important] Antes de escribir la primera línea
> [[10b Estándar de ejecución del frontend]] aplica en las tres fases. Cada pantalla
> sale de la línea **«Backoffice:»** de la sección Interfaz de su caso de uso.

**Usuario distinto, producto distinto.** Escritorio, pantallas densas, jornada
completa, usuario experto. **El backoffice no es la app estirada**: comparte
componentes de dominio, no comparte layout (ADR-004).

> [!important] Son **dos** backoffices, no uno con más pestañas (delta D-2)
> El **financiero** (F7, F8.A–C) y el **de sistemas** (F8.D) no comparten usuario, ni
> rol, ni la pregunta que vienen a contestar: tesorería mira si el dinero cuadra;
> plataforma mira si el sistema aguanta y si se puede restaurar. Comparten el **shell
> de F6** y nada más: ni menú, ni layout de sección, ni permisos. Mezclarlos es lo que
> termina dándole a un operador financiero permisos sobre la base de datos.
> Ver [[20 Maqueta de referencia · deltas del frontend]].

> **Todo el backoffice va `noindex, nofollow`**, con `X-Robots-Tag` en NGINX además
> de la meta. Es una superficie con datos personales detrás de login: que aparezca en
> un buscador sería un incidente, no un logro de SEO.

---

# FASE F6 — Shell del backoffice

> **Objetivo.** Que exista el esqueleto denso: router, sesión con rol, la **tabla de
> datos** que van a usar las 60 pantallas siguientes, filtros, exportación y el
> registro de acceso a datos.

## Alcance

| Pieza | Qué resuelve |
| --- | --- |
| **TanStack Router** por archivos + **TanStack Query** | Cada carril agrega rutas sin tocar un registro común |
| `ProveedorSesion` con **rol y permisos** | Del token; se muestran u ocultan secciones **por comodidad** — el servidor decide |
| **`TablaDeDatos`** | Toolbar con búsqueda y filtros, orden por columna **con lista blanca**, selección múltiple, paginación del servidor, **virtualización** |
| `BarraDeFiltros` | Chips de filtro con estado en la URL (compartible, sin datos sensibles) |
| `Exportador` | CSV/XLSX **por el endpoint de CU-58**, nunca armado en el cliente |
| `PanelDeEvidencia` | Bitácora, movimientos y trazas de un caso, reutilizado por reclamos, disputas y descargos |
| `RegistroDeAcceso` | Toda vista de datos personales dispara el registro (CU-58, `R-SEG-02`) |

## Las cuatro reglas del shell

1. **La tabla se pagina y ordena en el servidor**, con lista blanca de campos. Ordenar
   por un campo que llega del cliente sin validar es una vulnerabilidad, no una
   comodidad.
2. **La exportación pasa por CU-58**: con permiso, huella, caducidad y tope de
   descargas. **Nunca** un `CSV.stringify` en el navegador — eso saltea el control.
3. **Ver datos personales deja rastro.** Si una pantalla los muestra, registra el
   acceso.
4. **El rol oculta, no protege.** Cada acción se verifica en el servidor.

## Gate de salida F6

- [ ] Gate común de §10 del plan maestro del frontend
- [ ] `TablaDeDatos` probada con 100 000 filas: virtualizada, sin bloquear la interfaz
- [ ] Ordenar por un campo no permitido ⇒ rechazado, no ignorado en silencio
- [ ] Toda exportación pasa por CU-58 (verificado: no hay generación en cliente)
- [ ] `noindex, nofollow` + `X-Robots-Tag` verificados con `curl`
- [ ] Rol sin permiso ⇒ la sección no se ve **y** el endpoint responde `403`

---

# FASE F7 — Backoffice · operación

**Casos de uso:** CU-01, 04, 05, 08, 09, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 21,
22, 23, 25, 26, 27, 28, 29, 51, 52, 53, 57

| Área | Pantallas que exige la bóveda |
| --- | --- |
| **Altas y accesos** | Cola de altas con **KYC observado** · auditoría de accesos con dispositivo e IP · recuperaciones asistidas. ***Accesos*** y ***Incidentes y riesgo*** se mudan al backoffice de sistemas (F8.D) |
| **Billetera** | Monitor de recargas **por proveedor**, con tasa de acreditación y tiempo medio · cola de retiros con **puntaje antifraude y decisión del motor** · retenciones vigentes · **formulario de reverso con doble aprobación y motivo obligatorio** · alta de oficios **sin archivo no se ejecuta** · verificación de cuentas por comprobante |
| **Cobranza y entregas** | Estado de cobranza por grupo y período **con la brecha para completar la bolsa** · tablero de entregas del día con **doble control** · cola de desembolsos por estado y antigüedad, con intentos · panel del fondo por grupo · lista interna con antigüedad, monto y causa |
| **Cierre** | **Cierre diario con el detalle de lo que impide cuadrar** · panel del punto de atención con el **teórico en vivo** |
| **Consumidor** | Bandeja de reclamos **con vencimientos primero**; sin reparación **no se cierra** un favorable · seguimiento de instancias · bandeja de reembolsos con **doble firma** · tablero de disputas |

## Las cinco reglas de esta fase

1. **Los vencimientos van primero.** Toda bandeja con plazo legal ordena por
   vencimiento por defecto, y marca lo vencido antes de que alguien lo busque.
2. **El doble control se ve en la interfaz.** Reversos, reembolsos y entregas muestran
   quién autorizó y quién ejecutó — **y la interfaz no deja que sea la misma persona**
   (`R-SEG-07`).
3. **El cierre diario muestra qué impide cuadrar**, no un «no cuadra». La lista de
   excepciones abiertas es la pantalla.
4. **Sin archivo no se ejecuta** el bloqueo por oficio (CU-17): la carga del documento
   es parte del formulario, no un adjunto opcional.
5. **Un reclamo favorable no se cierra sin su reparación** (CU-52, `R-CON-03`). La
   interfaz lo impide.

## Gate de salida F7

- [ ] Gate común
- [ ] Toda bandeja con plazo ordena por vencimiento y destaca lo vencido
- [ ] Reverso y reembolso: la interfaz **rechaza** que autorice y ejecute la misma persona
- [ ] Reclamo favorable sin reparación ⇒ **no se puede cerrar** (probado)
- [ ] Oficio sin archivo ⇒ no se ejecuta (probado)
- [ ] El cierre diario lista las excepciones que lo bloquean

---

# FASE F8 — Backoffice · cumplimiento y gobierno

**Casos de uso:** CU-02, 03, 06, 07, 20, 24, 30, 31, 32, 33, 34, 35, 36, 40, 41, 42,
43, 44, 45, 46, 47, 48, 49, 50, 54, 55, 56, 58, 59, 90, 91, 92, 93, 94, 95, 96, 97, 98

Es la fase más grande del frontend. **Se parte en tres sub-fases con gate propio.**

## F8.A · UIF y monitoreo

**Verificación de identidad como cola + expediente** (delta D-3), con sus nueve
bloques: identidad declarada contra leída, autenticidad del documento, biometría con
prueba de vida y búsqueda 1:N, listas restrictivas con puntaje de coincidencia
difusa, perfil y origen de fondos, dispositivo y sesión del alta, composición del
riesgo factor por factor, historial sin edición, y decisión con **causal del catálogo
obligatoria** y segunda firma en riesgo alto · bandeja de diligencias **con doble
revisión obligatoria para PEP** · panel de PEP con próxima revisión · tablero de
revisiones vencidas **ordenado por riesgo** · bandeja de formularios PCC-01 del
período · consulta de ROG con exportación · **bandeja de alertas y casos, con plazo y
decisión obligatoria** · **editor de reglas con simulador al lado** · bandeja de
oficios con plazo, alcance y respuesta archivada.

> **Una fila con un botón de aprobar no alcanza.** El analista tiene que poder firmar
> una decisión y defenderla seis meses después: por eso el expediente muestra la
> evidencia entera y el riesgo se explica por sus factores, no por un número. Y por
> eso **rechazar u observar sin causal del catálogo es imposible en la interfaz**: la
> causal viaja en la notificación al titular, que tiene derecho a saber qué subsanar.

> **Deber de reserva (CU-44).** El titular **no** ve nada de esto y la interfaz no
> puede filtrarlo: ninguna pantalla del backoffice de LGI/FT genera un aviso, una
> notificación ni un rastro visible para el usuario investigado.

> **Ninguna regla se activa sin simulación registrada** (CU-48). El botón de activar
> está deshabilitado hasta que exista la corrida contra el histórico.

## F8.B · Reportes, contabilidad y tarifas

Calendario regulatorio **con vencimientos, estado y constancias** · ***Reportes***:
catálogo con **el permiso que exige cada uno** e historial · libro mayor con filtro
por cuenta, período y origen · **cierre mensual con el cuadre a la vista, que no se
puede confirmar si no cuadra** · **tablero de encaje** con el ratio por día y los
descuadres abiertos · monitor fiscal (emitidas, offline pendientes, rechazadas) ·
**editor de tarifario con simulación obligatoria antes de aprobar** · editor de
segmentos · simulador de tarifas para atención · calendario anual de días no hábiles
**con la fuente de cada día**.

## F8.C · Gobierno, riesgo y organizador

***Cumplimiento → Productos***: matriz de riesgo por producto con su **no objeción** ·
***Gobierno → Cumplimiento***: titular y suplente con vigencia · base de pérdidas ·
**panel de incidentes con los tres relojes** y su vencimiento · calendario de pruebas
de continuidad · actas de comité con **quórum y voto nominal** · habilitación de
organizadores, contratos, evaluación de desempeño, sanciones y apelaciones · editor de
reglas de automatización con **vista previa en lenguaje llano** y confirmación humana
que caduca · **tablero de indicadores** con meta, variación y lo provisorio marcado
como tal.

## Las cuatro reglas de esta fase

1. **Nada que dependa de una simulación se activa sin ella**: reglas de cumplimiento
   (CU-48), tarifario (CU-34), segmentos (CU-36), automatización (CU-95).
2. **El cuadre bloquea.** Cierre mensual y diario no se confirman si no cuadran, y la
   interfaz muestra por qué.
3. **Lo provisorio se marca.** Un indicador sin período cerrado se muestra como
   provisorio (CU-98); y los catálogos sembrados `⚠ PROVISIONAL` se señalan en
   pantalla.
4. **El voto y la abstención se registran con nombre** (CU-94). El acta no se cierra
   sin quórum, y la parte interesada no puede votar.

## Gate de salida F8

- [ ] Los tres gates de sub-fase (F8.A, F8.B, F8.C)
- [ ] Gate común
- [ ] Ninguna regla, tarifario, segmento ni automatización se activa sin simulación
- [ ] Cierre mensual y diario **no se confirman** si no cuadran (probado)
- [ ] Ninguna pantalla de LGI/FT produce rastro visible para el investigado
- [ ] Rechazar u observar una verificación **sin causal del catálogo** ⇒ imposible
- [ ] Expediente de riesgo alto ⇒ la interfaz exige segunda firma **de otra persona**
- [ ] Quien cargó los datos de un alta **no aparece** como revisor posible
- [ ] Acta sin quórum ⇒ no se cierra; parte interesada ⇒ no puede votar
- [ ] Indicador provisorio marcado como tal; indicador bajo el mínimo de casos,
      suprimido
- [ ] Todo reporte muestra **el permiso que exige** antes de ejecutarse

---

# FASE F8.D — Backoffice de sistemas

> **Objetivo.** Que plataforma y seguridad tengan **su propio producto**, con su
> usuario y su rol, para contestar dos preguntas que el backoffice financiero no
> contesta: *¿esto aguanta?* y *¿esto se puede restaurar?*

**Usuario:** `PLATAFORMA` y `SEGURIDAD`. **Reutiliza** el shell de F6 completo.
**No reutiliza** el menú ni el layout de sección del financiero.

## Alcance

| Sección | Pantallas |
| --- | --- |
| **Plataforma** | **Estado de servicios** con criticidad y qué se cae con qué · **Salud y SLO**: disponibilidad, p95/p99 y **presupuesto de error consumido** por servicio · **Despliegues**: versión por servicio, quién la puso, reversiones automáticas e **interruptores de funcionalidad** |
| **Datos** | **Base y migraciones**: versión del esquema, migraciones pendientes, retraso de la réplica, conexiones del pool · **Respaldos y restauración**: RPO y RTO **objetivo contra medido**, y la **fecha de la última restauración probada** |
| **Integraciones** | **Proveedores externos**: éxito, latencia, **costo real por operación** y reglas de conmutación escritas de antemano · **Outbox y trabajos**: colas con pendientes, fallidos y **descartados**, más los trabajos programados con su última corrida · **Webhooks entrantes**: duplicados, fuera de orden, firma inválida, y qué hace la plataforma con cada caso |
| **Seguridad** | **Accesos y roles** (viene de F7) · **Incidentes y riesgo** con sus relojes de reporte (viene de F8.C) |

## Las cinco reglas de esta fase

1. **El presupuesto de error es una decisión, no un gráfico.** Cuando un servicio lo
   agota, la pantalla lo dice y la regla escrita se aplica: se congela todo cambio que
   no sea de estabilidad. Sin eso, el SLO es decoración.
2. **Un respaldo que nunca se restauró no es un respaldo.** La pantalla muestra la
   **fecha de la última restauración probada** y la marca vencida a los 30 días. El
   número que importa es cuánto tardó, no que el trabajo corrió.
3. **La conmutación de proveedor es automática pero nunca silenciosa.** Cambiar de
   banco cambia el costo por operación: la pantalla muestra el costo real contra el
   contratado, porque eso lo tiene que saber alguien de negocio.
4. **Ningún mensaje se pierde, y ninguno se reintenta a ciegas.** Lo que agota sus
   reintentos queda en la cola de descartados, visible, con su motivo. Reintentar un
   desembolso sin mirar es como se paga dos veces.
5. **Los interruptores que tocan dinero exigen dos personas.** La interfaz lo muestra
   y lo impide, igual que un reverso.

## Gate de salida F8.D

- [ ] Gate común de §10 del plan maestro del frontend
- [ ] Un rol financiero **no ve** este backoffice **y** sus endpoints responden `403`
- [ ] Presupuesto de error agotado ⇒ la pantalla lo declara, no lo insinúa
- [ ] Restauración probada hace más de 30 días ⇒ marcada como vencida
- [ ] Cola de descartados visible con motivo por mensaje; reintentar exige confirmar
- [ ] Interruptor que toca dinero ⇒ la interfaz rechaza que lo mueva una sola persona
- [ ] Migración pendiente y retraso de réplica **se muestran en la pantalla que los
      sufre**, no solo acá

## Ver también

[[00c Recetario · implementar un caso de uso]] · [[16 Carriles de frontend]] · [[10 Plan maestro del frontend]] · [[12 Fases F2 a F5 · App móvil]] · [[14 Fases F9 a F11 · Sitio público, SEO y GEO]] · [[20 Maqueta de referencia · deltas del frontend]] · [[Cumplimiento]]
