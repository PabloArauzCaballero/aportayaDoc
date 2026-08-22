---
tags:
  - plan
  - frontend
  - maqueta
titulo: "Maqueta de referencia — deltas del frontend"
fecha: 2026-08-20
depende_de: [F0, F1]
afecta: [F1, F3, F4, F5, F6, F7, F8]
---

# Maqueta de referencia — deltas del frontend

> **Qué manda.** [[AportaYa-Maqueta]] (`docs/Views/AportaYa-Maqueta.html`) pasa a ser
> la **referencia visual y de comportamiento** del frontend. Los casos de uso siguen
> mandando sobre *qué* hace cada pantalla; la maqueta manda sobre *cómo se ve y cómo
> se comporta*. Cuando la maqueta y un plan de fase no coincidan, gana la maqueta y
> **se corrige el plan**, no al revés.

> [!warning] Lo que la maqueta **no** es
> No es diseño final ni código a copiar: es HTML de una sola pieza con un simulador de
> backend adentro. Lo que se toma de ella es el **inventario de pantallas, el
> comportamiento y el nivel de desglose**. Los tokens siguen saliendo de
> `disenar-frontend §0` y los componentes de `packages/ui`.

---

## 1 · Los deltas

| # | Delta | Fases que cambian |
| :-: | --- | --- |
| **D-1** | El alta de cuenta es de **ocho pasos**, no de cuatro | F3 |
| **D-2** | **Dos backoffices** distintos: financiero y de sistemas | F6, F7, F8 |
| **D-3** | La verificación de identidad se maqueta **como expediente**, no como fila | F8.A |
| **D-4** | *Pagar mi aporte* se reemplaza por **aportes pendientes con filtros** | F4, F5 |
| **D-5** | El sorteo se muestra como **evento guardado y reproducible** | F5 |
| **D-6** | **Perfil público de terceros** y catálogo de insignias explicable | F5 |
| **D-7** | La **publicidad** es una superficie del producto, rotulada y con control | F4, F5 |
| **D-8** | El alta entra por un **tour** de cuatro pantallas | F3 |
| **D-9** | La cuenta nueva abre con **bono de bienvenida** y su propio estado | F3, F4 |
| **D-10** | Entrar a un grupo es **canjear una invitación**, con todo a la vista antes | F5 |
| **D-11** | Toda notificación **se guarda en la bandeja**, y hay eventos que no notifican | F2, F5 |
| **D-12** | *Mis aportes* tiene **dos vistas**: la lista y el calendario del mes | F4, F5 |
| **D-13** | La invitación se **escanea o se escribe**, y el enlace lo emite **quien organiza** | F5 |
| **D-14** | La portada abre con **dónde estás**, no con cuánto tenés | F4, F5 |

Y una regla transversal, que es la que produjo casi todos los deltas:

> **Si una pantalla muestra un número, una decisión o un estado, tiene que mostrar de
> dónde salió.** Un puntaje sin sus factores, un riesgo sin su composición, un rechazo
> sin su causal o un orden de turnos sin su semilla son pantallas incompletas. No es
> una preferencia de estilo: es lo que hace que el producto se pueda defender ante un
> cliente, un auditor o el regulador.

---

## D-1 · El alta de cuenta tiene ocho pasos

[[12 Fases F2 a F5 · App móvil]] decía «alta guiada en cuatro pasos». Son ocho, con
barra de progreso visible y su evidencia guardada paso a paso:

| # | Paso | Lo que exige la pantalla |
| :-: | --- | --- |
| 1 | Datos de la persona | Nombre, documento con extensión, nacimiento, celular, correo, contraseña con medidor |
| 2 | **Confirmar el celular** | Código de 6 dígitos, tope de 3 intentos y bloqueo por hora |
| 3 | Anverso del documento | Marco de cámara y **cuatro controles de calidad a la vista** |
| 4 | Reverso del documento | Código de barras y zona de lectura mecánica |
| 5 | Selfie con prueba de vida | Reto de movimiento, puntaje contra umbral, cotejo 1:1 y búsqueda 1:N |
| 6 | **Cotejo** de lo declarado contra lo leído | Campo por campo, con la diferencia marcada y editable |
| 7 | Perfil del cliente | Domicilio, actividad, **origen de fondos**, propósito, movimiento esperado, PEP y beneficiario final |
| 8 | Contrato y consentimientos | Tres consentimientos **separados**, con hash del documento |
| — | Resultado | Nivel asignado, **límites concretos**, qué falta y el plazo de revisión |

**Por qué importa:** el paso 7 no es burocracia, es lo que después usa el monitoreo
para comparar. Y el paso 6 evita el motivo más común de rechazo, que es un dato mal
tipeado. Los dos estaban ausentes del plan.

**Cambio en F3:** la fila de CU-01 pasa a *«Alta guiada en ocho pasos con captura de
documento y prueba de vida»*, y el gate suma: cotejo campo a campo visible, y códigos
de verificación con tope de intentos y bloqueo probado.

---

## D-2 · Son dos backoffices, no uno

El financiero y el de sistemas **no comparten usuario, ni rol, ni pregunta**.
Mezclarlos en un panel con más pestañas es lo que termina dándole a un operador de
tesorería permisos sobre la base de datos.

| | Backoffice financiero | Backoffice de sistemas |
| --- | --- | --- |
| **Usuario tipo** | `TESORERIA`, `CONTABILIDAD`, `ANALISTA_CUMPLIMIENTO`, `OFICIAL_CUMPLIMIENTO` | `PLATAFORMA`, `SEGURIDAD` |
| **Pregunta que responde** | ¿El dinero cuadra y estamos en plazo? | ¿El sistema aguanta y se puede restaurar? |
| **Secciones** | Operación · Cobranza y mora · Finanzas · Cumplimiento | Plataforma · Datos · Integraciones · Seguridad |
| **Fases** | F7, F8.A, F8.B, F8.C | **F8.D** (nueva) |

**Comparten** el shell de F6 —`TablaDeDatos`, `BarraDeFiltros`, `Exportador`,
`RegistroDeAcceso`, sesión con rol— y **no comparten nada más**: ni menú, ni layout de
sección, ni permisos.

Pantallas del backoffice de sistemas, todas maquetadas:

- **Estado de servicios** — criticidad por servicio y qué se cae con qué
- **Salud y SLO** — disponibilidad, p95/p99 y **presupuesto de error consumido**
- **Despliegues** — versión por servicio, quién la puso, reversión automática, e
  **interruptores de funcionalidad** con doble firma para los que tocan dinero
- **Base y migraciones** — versión del esquema, migraciones pendientes, retraso de la
  réplica, conexiones
- **Respaldos y restauración** — RPO y RTO **objetivo contra medido**, y la fecha de
  la última restauración probada
- **Proveedores externos** — éxito, latencia, **costo real por operación** y reglas de
  conmutación escritas de antemano
- **Outbox y trabajos** — colas con pendientes, fallidos y descartados; trabajos
  programados con su última corrida
- **Webhooks entrantes** — duplicados, fuera de orden, firma inválida, y **qué hace la
  plataforma con cada caso**
- **Accesos y roles** · **Incidentes y riesgo** (se mudan desde el backoffice financiero)

---

## D-3 · La verificación de identidad es un expediente

Una fila con un botón de aprobar no alcanza para firmar una decisión que después hay
que defender. La pantalla de F8.A pasa a ser **cola + expediente**, con nueve bloques:

1. **Identidad**: lo declarado contra lo leído del documento, campo por campo, más el
   cotejo con el registro civil
2. **Autenticidad del documento**: cinco controles (lectura mecánica, dígito
   verificador, holograma, alteración digital, vigencia)
3. **Biometría**: prueba de vida con su umbral, reto usado, intentos, cotejo 1:1 y
   **búsqueda 1:N** contra los rostros ya registrados
4. **Listas restrictivas**: ONU, OFAC, PEP nacional y lista propia, con **puntaje de
   coincidencia difusa** y su resolución escrita
5. **Perfil y origen de fondos**: actividad, origen, propósito, movimiento esperado,
   beneficiario final y PEP
6. **Dispositivo y sesión del alta**: huella, IP, coherencia geográfica, emulador,
   VPN, **altas desde la misma huella** y tiempo del registro
7. **Composición del riesgo**: seis factores con su aporte guardado y el total contra
   el umbral
8. **Historial del expediente**: cada evento con hora, autor y hash, sin edición
9. **Decisión**: aprobar, observar o rechazar, con **causal del catálogo obligatoria**
   y segunda firma cuando el riesgo es alto

**Regla nueva para el gate de F8.A:** *rechazar u observar sin causal del catálogo
tiene que ser imposible en la interfaz*, y *quien carga no decide*.

---

## D-4 · Aportes pendientes, no «pagar mi aporte»

Una persona está en más de un grupo: un botón que paga «el» aporte no existe. La
tarjeta de saldo lleva a **una lista de obligaciones** con:

- filtro **por grupo** y filtro **por estado y fecha** (por pagar, vencidos, próximos
  30 días, pagados, todo el historial)
- total a pagar y cantidad de cuotas del filtro activo
- por cuota: grupo, período, identificador de la obligación, vencimiento, monto,
  **recargo por mora desglosado** y si el fondo de garantía ya la cubrió
- comprobante descargable en las pagadas

**Cambio en F4 y F5:** la fila *«Mi aporte: monto, fecha límite y un botón»* pasa a
*«Aportes pendientes: lista filtrable por grupo y fecha; el pago de una cuota es un
detalle de ella»*.

---

## D-5 · El sorteo es un evento guardado

El orden de turnos es lo único que reparte ventaja en un pasanaku. El botón
*Verificar* deja de ser un veredicto de una línea y pasa a mostrar el **evento
completo**: compromiso publicado antes, lista de cupos sellada, semilla de fuente
externa tomada después del compromiso, ejecución y notificación — cada paso con su
hash. Se **reproduce en pantalla** con la misma semilla y se compara el hash contra el
guardado.

**Cambio en F5:** la fila de CU-61/62 pasa a *«Sorteo: evento guardado, con
reproducción y comparación de hash»* y el gate suma *el orden reproducido coincide con
el guardado y los cinco pasos del evento se ven con su hash*.

---

## D-6 · Perfil público de terceros e insignias explicables

Desde el orden de turnos se toca a cualquier participante y se abre **su perfil
público**: puntaje, nivel, ciclos completados, porcentaje de aportes a tiempo,
incumplimientos declarados e insignias. Nada más: ni documento, ni teléfono, ni saldo.

Las insignias dejan de ser una grilla decorativa: cada una tiene ícono propio y abre
una pantalla con **qué mide, cómo se gana, para qué sirve y cuánta gente la tiene**.

**Regla:** cuando una persona en mora aparece en el perfil, la pantalla dice que está
**dentro de su plazo para regularizar** y que todavía no hay incumplimiento declarado.
Llamarle deudor antes de eso es lo que después se cae en una demanda.

---

## D-7 · La publicidad es una superficie del producto

La segunda vertical de negocio se maqueta, no se insinúa. **Un solo banner, y solo en
la portada**: no aparece en movimientos, ni en aportes, ni en el perfil. Rotulado como
publicidad, con el anunciante visible y una ilustración en marco fijo.

**No se puede cerrar, y esa es la decisión.** Un espacio que cada persona apaga no se
le puede vender a nadie, y entonces habría que cobrarle al usuario lo que hoy es
gratis. Lo que sí se apaga es la **segmentación**, sin que cambie ninguna condición de
la cuenta: cobrar por «quitar la publicidad» sería cobrar por los datos.

**La contrapartida es que al anunciante se le exige ser discreto.** No diseña el aviso:
escribe dos líneas y da su monograma; la tipografía, la paleta, el tamaño y el lugar
los pone la plataforma. El estándar es parte del producto, no una guía de estilo:

| Se impone | Se prohíbe |
| --- | --- |
| Un solo formato: ilustración en marco cuadrado, título ≤ 46 caracteres, detalle ≤ 60 | Texto, logos o precios dentro de la ilustración |
| Una sola ubicación: la portada, al pie | Mayúsculas sostenidas, exclamaciones repetidas, urgencia inventada |
| Tipografía y paleta de la plataforma | Que el anunciante elija color |
| Siempre al pie del contenido | Cerca del saldo o durante una operación con dinero |
| Rotulado, con el anunciante visible | Botones o íconos que imiten los de la app |
| Revisión previa de cada campaña | Animación, sonido, autoexpansión, cuenta regresiva |

> Un aviso feo o engañoso no daña la marca del anunciante: **daña la nuestra**, porque
> aparece adentro de la app donde la gente tiene su plata. Por eso la revisión es
> previa y la campaña que no cumple no sale.

**Cambio en F4/F5:** entra el organismo `BannerDePauta` —sin cierre, con formato
único— y la pantalla *Sobre este aviso*, contra el servicio `publicidad`.

---

## D-8 · El alta entra por un tour

Antes de pedirle un dato a nadie hay que explicarle qué es esto. Cuatro pantallas
con ilustración, saltables, y la última abre la cuenta:

1. **El pasanaku de siempre, sin el cuaderno** — los mismos turnos, sin que nadie tenga
   que juntar ni guardar
2. **Tu plata está en custodia** — cuenta bancaria separada, comprobante por movimiento
3. **El turno se sortea a la vista de todos** — semilla pública, reproducible
4. **Si alguien no aporta, el grupo no se frena** — el fondo cubre y la deuda queda viva

Las cuatro son las cuatro objeciones que aparecen siempre. Contestarlas antes del
formulario es lo que hace que alguien complete ocho pasos de verificación.

**Cambio en F3:** `bienvenida → tour → registro`, con `BarraDePuntos` y botón de saltar.

---

## D-9 · La cuenta nueva no es la cuenta llena

Al terminar el alta se acredita un **bono de bienvenida de Bs 10** —parámetro de
catálogo con su campaña, no un número en el código— y la app abre en un estado
**propio de cuenta nueva**, distinto del de alguien con historial:

| | Cuenta nueva | Cuenta con historial |
| --- | --- | --- |
| Portada | Panel de bienvenida con el bono y los tres pasos siguientes | Tarjeta del próximo aporte |
| Grupos | Estado vacío que invita a entrar con código | Tira con avance de turno |
| Movimientos | Un solo asiento: el bono | Extracto del período |
| Reputación | *Sin historial*, con los factores en cero y su motivo | Puntaje con desglose |

> **Los estados vacíos son la mitad de la app que nadie diseña**, y son justo la mitad
> que ve una persona el primer día. Si el primer día se ve roto, no hay segundo.

El bono se acredita con un asiento como cualquier otro, contra una cuenta de gasto de
la empresa: es costo de adquisición, y alguien lo tiene que aprobar y poder cambiar.

---

## D-10 · Entrar a un grupo es un acto informado

El botón *Unirme con un código* no mete a nadie en ningún lado: abre el canje de una
invitación (CU-69), y el canje tiene tres momentos.

**1 · El código.** Cinco dígitos, de **un solo uso** y con vencimiento. Dos rechazos
distintos, y la diferencia importa:

| Caso | Respuesta | Por qué |
| --- | --- | --- |
| Código que no existe | `404`, **sin código de negocio** · «no encontramos ninguna invitación» | No se dice si hay un grupo detrás: si no, se prueban códigos hasta acertar. Un código de negocio propio ya confirmaría que la búsqueda dio con algo |
| Código ya canjeado o vencido | `AP-CU69-05` (`TOKEN_INVALIDO`) · «pedí una nueva» | El token murió; el grupo puede seguir existiendo |

> **Corrección.** La primera versión de la maqueta usaba `AP-CU69-01` y `AP-CU69-02`
> para estos dos casos, pero en [[CU-69 Invitar a un contacto y registrar sus referencias]]
> esos códigos ya son `SIN_CUPOS_LIBRES` y `DESTINATARIO_SUPRIMIDO`. Manda el caso de
> uso: el rechazo del token es `AP-CU69-05` y el inexistente no lleva código propio.

**2 · Lo que se está por firmar.** Antes de confirmar, la pantalla muestra el grupo
(quién invita, aporte, periodicidad, cupos, cuándo arranca, cómo se sortea el turno),
**el compromiso completo del ciclo** —cuánto aporta en total y cuánto cobra, con la
comisión descontada— y el reglamento entero, con la aclaración de que lo aprobó el
grupo por votación.

> **Nadie entra a un pasanaku sin saber el total.** Un asistente que solo dice «Bs 200
> al mes» esconde que son Bs 2.000 en diez meses. Es la misma razón por la que un
> crédito muestra el costo total y no solo la cuota, y acá el argumento es más fuerte:
> del otro lado no hay un banco, hay nueve vecinos que cuentan con esa plata.

**3 · La confirmación explícita.** Una casilla que dice el compromiso en números, y
recién ahí el botón. Al confirmar, el token se marca canjeado **en la misma
transacción** que ocupa el cupo: no existe la ventana donde dos personas entran con la
misma invitación. Si el último cupo se lo llevó otro, se dice así (`AP-CU68-04`) y la
invitación queda viva.

---

## D-11 · La notificación se muestra y se guarda

Dos reglas, y la segunda es la que se olvida.

**El aviso emergente es un atajo, no el canal.** Toda notificación se escribe en la
bandeja en la misma operación que la manda (ADR-035). Que el push no llegue —teléfono
apagado, permiso denegado, proveedor caído— no puede significar que la persona no se
entere nunca. En la maqueta la jornada dispara 17 avisos y los 17 quedan en la bandeja
con su ícono y su tono.

**Cuando llegan varios juntos, se encolan.** Uno pisando al anterior es un aviso
perdido. Se muestran de a uno, con el contador de los que esperan; tocarlos abre la
bandeja y limpia la cola.

**Y hay eventos que no notifican, a propósito:**

| Evento | Notifica | Por qué |
| --- | :-: | --- |
| Aporte cobrado, entrega, recarga, transferencia | Sí | Es su plata moviéndose |
| Vencimiento próximo, mora, cobertura del fondo, incumplimiento | Sí | Tiene un plazo que correr |
| Retiro frenado por antifraude, sesión desde un equipo nuevo | Sí | Puede no haber sido la persona |
| Reclamo recibido y resuelto, acuerdo del grupo, sorteo | Sí | Hay un plazo o una decisión que la afecta |
| **Alerta de lavado, caso, reporte a la UIF** | **No** | **Deber de reserva (CU-44): avisarle al investigado es delito** |
| **Límite de intentos de acceso alcanzado** | **No** | Confirmaría que la cuenta existe |

> Una notificación de más en la bandeja de LGI/FT no es un problema de producto: es un
> aviso al investigado. Por eso la regla se escribe acá y no queda a criterio de quien
> implemente la pantalla.

---

## D-12 · El calendario no es otro filtro: es otra pregunta

*Mis aportes* pasa a tener **dos vistas de las mismas cuotas**, con un selector arriba.

| Vista | La pregunta que contesta | Qué muestra |
| --- | --- | --- |
| **Lista** | «¿Qué tengo que pagar ahora?» | Solo lo abierto —a tiempo o en mora—, ordenado por vencimiento |
| **Calendario** | «¿Cómo vengo?» | El mes entero, con lo pagado adentro |

**Por qué lo pagado aparece acá y no en la lista.** La regla del gate de F4 —*el historial
de lo pagado vive en Movimientos*— sigue en pie **para la lista**: una lista de cosas por
hacer con cosas hechas adentro deja de ser una lista de cosas por hacer. Pero un
calendario sin lo pagado miente por omisión, porque un mes cumplido y un mes en blanco se
ven igual, y quien quiere saber si viene cumpliendo no tiene dónde mirarlo. El calendario
**no filtra: ubica en el tiempo**, y por eso puede mostrar lo que la lista deja fuera.

**Tres colores, y un gris que no es un cuarto color.**

| Estado | Cómo se ve | Qué significa |
| --- | --- | --- |
| `PAGADA` | verde | Se pagó · el comprobante está en el detalle |
| `PENDIENTE` | amarillo | Se debe y todavía está en plazo |
| `VENCIDA` | rojo | Se pasó el plazo · el recargo va desglosado |
| `FUTURA` | gris punteado | El período **no se abrió todavía**: no se debe |

Lo `FUTURA` va punteado y no pintado a propósito: se ve para que la persona sepa lo que
viene, pero **no suma a ninguna de las tres cifras del mes**, y el importe se dice aparte
en una línea. Es la misma regla del gate de F4 —*el total a pagar cuenta solo lo
exigible*— llevada al calendario, donde la tentación de sumar todo es mayor porque el mes
«se ve completo».

**Lo demás que fija la vista:**

- **La unidad es el mes**, porque el pasanaku cobra por mes. Arriba las tres cifras del
  mes con su cantidad de cuotas, después la grilla, y abajo las cuotas del mes —o las del
  día que se toque, que se elige y se suelta.
- **Se navega solo entre los meses que tienen cuotas.** Una flecha que lleva a un mes
  vacío no informa nada y hace dudar de si la app perdió los datos.
- **Un día puede tener cuotas de dos grupos.** El fondo del día lo pinta el estado más
  urgente y los puntos dicen cuántas son y de qué color es cada una: un solo color por día
  escondería una cuota vencida detrás de otra al día.
- **La semana empieza el lunes**, y *hoy* se marca con un aro y no con relleno — el
  relleno ya está diciendo el estado de pago.
- **La tarjeta de la cuota es la misma en las dos vistas.** Se llega distinto; no se
  informa distinto ni se puede hacer menos.

**Cambio en F4 y F5:** la fila de *aportes pendientes* pasa a *«Mis aportes: lista y
calendario del mes, con selector entre las dos»*, y el gate de F4 suma las tres reglas de
arriba.

---

## D-13 · La invitación se escanea o se escribe, y el enlace lo emite quien organiza

Tres cosas, y la tercera es la que cambia el modelo de la pantalla.

**1 · Dos caminos para el mismo token.** *Unirme a un pasanaku* abre en **Escanear QR**,
con **Con el código** a un toque. El QR y los cinco dígitos **son el mismo token**: uno se
apunta con la cámara y el otro se dicta por teléfono y se escribe a mano. El código corto
no es redundancia — es la vía de quien no puede escanear, y sacarlo dejaría afuera a parte
de la gente que este producto dice servir. Los dos caminos llegan a **la misma pantalla de
confirmación** de D-10: cambia cómo llegó el token, no qué se valida ni qué se muestra
antes de aceptar.

**2 · El rol es del vínculo, no de la persona.** La misma persona organiza un grupo y
participa de otro. Por eso el rol se dice **en cada tarjeta de grupo** —«Organizás este
grupo» / «Organiza Rosa Aduviri Q.»— y no una sola vez en el perfil. Es también la razón de
que el botón de invitar esté en una tarjeta y no en la de al lado: no es un permiso del
usuario, es un permiso de ese vínculo.

**3 · El enlace abierto lo emite solo quien organiza.** CU-69 deja invitar a un
participante **o** al organizador, y eso sigue valiendo para la invitación dirigida a un
teléfono conocido. Pero un **enlace abierto no va dirigido a nadie**: entra cualquiera que
lo reciba y lo reenvíe. Ese canal lo emite quien responde por el grupo, y a cualquier otro
se le contesta `AP-CU69-07` (`EMISOR_NO_HABILITADO`). Es la misma razón por la que el token
queda ligado a su emisor: toda incorporación por invitación tiene un responsable con
nombre.

La pantalla de la invitación muestra, además del QR y del código: cuántos cupos quedan,
cuándo vence, que es de un solo uso, **qué ve quien la recibe** —quién invita, el aporte y
la periodicidad, nunca los nombres ni los teléfonos de los demás— y **qué pasa en cada
borde**: vencida o canjeada (`AP-CU69-05`), quien ya está en el grupo (`AP-CU69-03`), el
último cupo tomado mientras miraba (`AP-CU68-04`, la invitación queda viva). Y se puede
anular, que es lo que hace que emitir un enlace no sea irreversible.

**De paso, un defecto que quedaba a la vista.** Un grupo **en formación** mostraba «te toca
en el turno *null*» y el botón de verificar el sorteo, con la etiqueta «Sorteado». No hay
orden de turnos antes de que el grupo se llene: la pantalla ahora dice cuántos cupos faltan
y ofrece lo único que corresponde ahí, que es invitar. Repartir turnos con el grupo a
medias sería darle lugar en la fila a gente que todavía puede no entrar.

**Cambio en F5:** la fila del canje pasa a *«Canjear una invitación: por QR o por código,
con el compromiso completo antes de confirmar»*, se suma *«Emitir una invitación por
enlace»* como pantalla del organizador, y el gate suma las cinco reglas de arriba.

---

## D-14 · La portada abre con dónde estás, no con cuánto tenés

Un pasanaku **ya es un juego**: tenés un lugar en una fila que se sorteó, el grupo se
llena de a una persona, y cada mes que pagás a tiempo suma. Lo que faltaba no era
inventar diversión: era dejar de esconderla debajo de un extracto bancario. La portada
pasa a abrir con tres cosas, en este orden:

| # | Bloque | Qué es, y por qué no es un adorno |
| :-: | --- | --- |
| 1 | **Racha** | Meses corridos sin que se te pase ninguna cuota, en ninguno de tus grupos. Es **el mismo hecho** que mide la insignia «Doce meses sin mora», y por eso el hito que muestra es ese y no un número redondo elegido a dedo |
| 2 | **Riel del turno** | El orden **sorteado**, con tu lugar marcado y cuánto falta para que te toque. Se muestra el grupo donde estás más cerca de cobrar: es la pregunta que la persona se hace al abrir la app, y hasta ahora había que entrar al grupo para contestarla |
| 3 | **Grupo en formación** | Cupos tomados sobre cupos totales, y cuántas personas faltan para que arranque. A quien lo organiza, el botón de invitar al lado (D-13) |

El saldo **no desaparece**: queda arriba en una línea —«Disponible Bs 1.240,00»— y la
tarjeta completa, con la custodia, lo retenido y lo puesto en pasanakus, baja a después
de las acciones rápidas. El gate de F4 sigue cumpliéndose: *cuánto tengo*, *qué tengo
que hacer hoy* y *cómo van mis grupos* se contestan sin desplazar. Lo que cambió es
cuál de las tres es el titular.

> **La regla que ordena todo esto: si el número no es un hecho, no va.** La racha son
> meses reales sin atraso, el riel es el orden que salió del sorteo y los cupos son
> personas que faltan. Nada de puntos por abrir la app, nada de niveles inventados.

**Y lo que explícitamente no se hace.** Esto es plata de la gente y un producto
regulado, así que la diversión va en avanzar y nunca en perder:

- **La mora no se gamifica.** La racha rota se dice sin regañar y con la salida al lado
  —«venías de 6 meses; poné al día la cuota y arranca otra»—. Una app que además reta
  empuja a esconderse, y quien se esconde no regulariza.
- **Sin tabla de posiciones entre participantes.** Un ranking expone a quien está en
  mora dentro de su plazo, y todavía no hay incumplimiento declarado.
- **Sin recompensa variable, sin sorpresa, sin urgencia inventada.** El único azar del
  producto es el sorteo de turnos, y está a la vista con su semilla (D-5).
- **Ningún juego empuja a poner más plata.** El progreso se gana cumpliendo lo que ya
  firmaste, no aportando de más.

**Cambio en F4 y F5:** la fila de la portada pasa a *«abre con racha, riel del turno y
grupo en formación; el saldo va en una línea arriba y la tarjeta de custodia más
abajo»*, y el gate suma las cuatro prohibiciones de arriba.

---

## 2 · Componentes que suma `packages/ui` (F1)

La maqueta usa nueve piezas que el inventario de [[11 Fases F0 y F1 · Cimientos y sistema de diseño]] no tenía. Se agregan a F1 **antes** de que los carriles compongan:

| Componente | Nivel | Dónde se usa |
| --- | :-: | --- |
| `BarraDePasos` | molécula | Alta de cuenta (8 pasos) |
| `MarcoDeCamara` | organismo | Captura de documento y selfie, con controles de calidad |
| `FilaDeCotejo` | molécula | Declarado contra leído, y cualquier comparación campo a campo |
| `ChipsDeFiltro` | molécula | Movimientos, aportes, cualquier bandeja |
| `ResumenDePeriodo` | molécula | Entró/salió del período, totales de un filtro |
| `FilaDeMovimiento` | molécula | Tipada por lo que pasó, con saldo corrido |
| `BannerDePauta` | organismo | Publicidad rotulada |
| `SeccionDeExpediente` | organismo | KYC, incumplimientos, disputas, reclamos |
| `SelectorSegmentado` | átomo | Cambio entre vistas equivalentes |
| `BarraDePuntos` | átomo | Progreso del tour |
| `PanelBienvenida` | organismo | Portada de cuenta nueva, con el bono y los pasos |
| `EstadoVacio` | molécula | Cada lista sin datos dice qué hacer, no «no hay resultados» |
| `TarjetaDeRacha` | molécula | Meses corridos al día, con su hito y su estado roto |
| `RielDeTurnos` | molécula | El orden sorteado con tu lugar y cuánto falta |
| `CalendarioDeCuotas` | organismo | El mes de los aportes por estado de pago (D-12) |
| `CodigoQR` | átomo | Depósito e invitación: el mismo dibujo, distinto payload |

Y dos reglas de estilo que la maqueta fija y `disenar-frontend` recoge:

1. **El ícono dice qué pasó, no si el número sube o baja.** Un aporte, una recarga por
   QR, una comisión y un débito rechazado tienen íconos distintos; el color y el signo
   ya dicen la dirección.
2. **Los movimientos se agrupan por día**, con el neto del día y el **saldo corrido**
   al costado de cada línea. Una lista plana de importes no es un extracto.

---

## 3 · Cómo se usa esto al tomar un carril

1. Abrí la maqueta y andá a la pantalla del carril, en los **dos escenarios**
   (optimista y adverso) — el estado vacío y el estado feo son parte del alcance.
2. Leé la sección «Interfaz» del caso de uso: sigue mandando sobre qué hace.
3. Si la maqueta muestra un desglose que el CU no pide, **el desglose entra igual** y
   se anota en la ficha del carril. Ese desglose es la razón de ser de la maqueta.
4. Si la maqueta y el CU se contradicen en *qué* hace la pantalla, gana el CU y se
   corrige la maqueta.

## Ver también

[[AportaYa-Maqueta]] · [[10 Plan maestro del frontend]] · [[10b Estándar de ejecución del frontend]] · [[11 Fases F0 y F1 · Cimientos y sistema de diseño]] · [[12 Fases F2 a F5 · App móvil]] · [[13 Fases F6 a F8 · Backoffice]] · [[16 Carriles de frontend]]
