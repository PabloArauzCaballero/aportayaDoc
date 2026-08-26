---
tags:
  - plan
  - frontend
  - maqueta
titulo: "Maqueta de referencia — deltas del frontend"
fecha: 2026-08-25
depende_de: [F0, F1]
afecta: [F1, F3, F4, F5, F6, F7, F8, "2B", "2C", "2E", "4B", "5B"]
---

# Maqueta de referencia — deltas del frontend

> **Qué manda.** [[AportaYa-Maqueta]] (`docs/Views/AportaYa-Maqueta.html`) pasa a ser
> la **referencia visual y de comportamiento** del frontend. Los casos de uso siguen
> mandando sobre *qué* hace cada pantalla; la maqueta manda sobre *cómo se ve y cómo
> se comporta*. Cuando la maqueta y un plan de fase no coincidan, gana la maqueta y
> **se corrige el plan**, no al revés.

> [!important] Los deltas D-15 a D-22 tocan también el **backend**
> Los primeros catorce deltas eran de frontend: la maqueta mostraba con más desglose algo
> que el backend ya resolvía. Los ocho nuevos no. Cambian una **transacción** (D-15: el
> canje ya no ocupa cupo), un **contrato** (D-19: el descuento por nivel es un concepto de
> tarifa, no un cálculo del cliente) y una **respuesta** (D-22: aportes devuelve el ciclo
> completo). Los carriles de backend afectados están en la columna derecha de la tabla, en
> negrita, y **no pueden enterarse cuando el frontend llegue a componer**.

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
| **D-15** | Canjear la invitación **no da cupo**: abre una solicitud que el organizador resuelve | F5, **2C**, **F7** |
| **D-16** | Ser organizador es una **habilitación con requisitos medibles**, y la otorga cumplimiento | F5, F7, **2E** |
| **D-17** | *No voy a poder pagar* existe como **botón**, y cambia la etapa de cobranza | F4, F5, **4B** |
| **D-18** | El **Punto de Reclamo** tiene puerta en la app, con el plazo guardado al ingresar | F5, F7 |
| **D-19** | Subir de nivel **no paga un bono en efectivo**: levanta topes y baja la comisión | F4, F5, **2B** |
| **D-20** | El **mercado de turnos** tiene dos lados, y el riesgo se evalúa después de aceptar | F5, **2C** |
| **D-21** | El **vale** lo paga el comercio y no toca la custodia | F4, **5B** |
| **D-22** | El calendario llega **hasta que se cierra el último pasanaku** | F4, F5 |

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
transacción** que abre la solicitud de ingreso: no existe la ventana donde dos personas
usan la misma invitación. Si ya no quedan cupos, se dice así (`AP-CU68-04`) y la
invitación queda viva.

> [!warning] Corregido por D-15 — leer los dos juntos
> Hasta la versión 2 de la maqueta, este punto decía que el token se canjeaba «en la
> misma transacción **que ocupa el cupo**». Eso contradice
> [[CU-68 Postular a un grupo y ser emparejado]] §4, donde el organizador revisa y
> acepta o rechaza, y contradice la restricción `ck_solicitud_ingreso_resuelta`, que
> impide cerrar una solicitud sin `revisada_por` ni `fecha_resolucion`. **Lo que se
> consume en esa transacción es el token; el cupo lo da el organizador después.**
> El botón de confirmar dice *Pedir mi cupo*, no *Entrar al grupo*.

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
  vacío no informa nada y hace dudar de si la app perdió los datos. Con el ciclo entero
  cargado (D-22) eso ya no recorta nada: todos los meses del pasanaku tienen cuota.
- **Un día puede tener cuotas de dos grupos.** El fondo del día lo pinta el estado más
  urgente y los puntos dicen cuántas son y de qué color es cada una: un solo color por día
  escondería una cuota vencida detrás de otra al día.
- **La semana empieza el lunes**, y *hoy* se marca con un **aro** y no con relleno — el
  relleno ya está diciendo el estado de pago. El aro es `box-shadow` además del borde,
  para que gane sobre el borde de estado sin taparlo.
- **El color de la celda no puede ser solo relleno.** Una celda mide unos 36 px, y a ese
  tamaño los tintes de chip (`--ok-bg`, `--warn-bg`, `--err-bg`) son todos el mismo
  claro: verde pálido y amarillo pálido no se distinguen. Cada estado lleva **relleno más
  borde del color del estado**; el contorno es lo que separa una celda de la de al lado.
- **La leyenda se pinta con las mismas reglas que la celda.** Un cuadrado de color sólido
  al pie no se parece a nada de lo que hay arriba: explicaría un calendario distinto del
  que se está viendo. La leyenda incluye también la marca de *hoy*.
- **La tarjeta de la cuota es la misma en las dos vistas.** Se llega distinto; no se
  informa distinto ni se puede hacer menos.

**El selector entre las dos vistas tiene que verse elegido.** Es un `SelectorSegmentado`,
y la maqueta se equivocó una vez de una forma que conviene no repetir: pintaba la pista con
`--field` y el segmento activo con `--surface`, que en tema claro son **el mismo
`#FFFFFF`** y en oscuro dos verdes a tres unidades de distancia. Lo único que los separaba
era una sombra al 10 %, así que no se veía cuál vista estaba puesta ni que aquello fuera un
interruptor. **Regla para `packages/ui`: el estado elegido de un segmentado no puede
depender de que dos tokens de fondo sean distintos.** Va con relleno de marca —
`--verde-solido` sobre `--sobre-verde-solido`— que es el mismo que el resto del sistema usa
para «esto está seleccionado».

**Cambio en F4 y F5:** la fila de *aportes pendientes* pasa a *«Mis aportes: lista y
calendario del mes, con selector entre las dos»*, y el gate de F4 suma las reglas de
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

## D-15 · Canjear la invitación no da cupo: abre una solicitud

Este delta **corrige a D-10** y es el más caro de descubrir tarde, porque toca la
transacción, no la pantalla.

**Lo que hace el canje.** Consume el token de un solo uso y crea una
[[solicitud_ingreso]] en estado `PENDIENTE`, con `grupo_id`, `usuario_id`, el canal por
el que llegó (QR o código) y el puntaje explicable congelado. **No ocupa cupo.** El cupo
lo ocupa la resolución del organizador, y la restricción
`ck_solicitud_ingreso_resuelta` exige que esa resolución venga con `revisada_por` y
`fecha_resolucion`: un `UPDATE` que cierre la solicitud sin decir quién la cerró es
rechazado por la base, no por la aplicación.

**Por qué, si el token ya es de un solo uso.** Porque un QR se reenvía. Quien lo emite
conoce al invitado; quien lo recibe de tercera mano, no. Y hay una regla del modelo que
sin este paso no la aplica nadie: `criterio_emparejamiento.max_morosos_por_grupo = 1`.
Si el cupo se toma solo, el segundo moroso entra igual y el grupo se entera cuando ya
está adentro.

### Las tres pantallas

| Pantalla | Ruta | Quién la ve |
| --- | --- | --- |
| Confirmación del canje | `pasanaku/unirse` | Quien pide · el botón dice ***Pedir mi cupo***, no *Entrar al grupo* |
| **Tu pedido de cupo** | `pasanaku/solicitudes/[id]` | Quien pidió, mientras espera |
| **Quién quiere entrar** | `pasanaku/grupo/[codigo]/solicitudes` | Solo quien organiza · guard duro con `AP-CU68-07` |

### Lo que exige *Tu pedido de cupo*

Esperar sin saber qué se espera es un limbo, y un limbo con plata de por medio se lee
como que algo salió mal. La pantalla dice cuatro cosas y ninguna es decorativa:

1. **Quién decide**, con nombre. No «el sistema».
2. **Cuánto tiene para decidir**: 48 horas, calculadas y **guardadas al crear la
   solicitud**. No se recalculan al consultar — misma regla que los plazos de reclamo y
   de descargo.
3. **Qué ve de vos quien decide**, en dos columnas explícitas. Ve nombre, puntaje con
   sus factores, ciclos completados e incumplimientos declarados. **No ve** saldo,
   movimientos, en qué otros grupos estás, ni tu documento. Decide sobre tu historial
   de cumplimiento, no sobre tu plata.
4. **Las tres salidas**, antes de que ocurran: aceptada (recién ahí se genera la primera
   obligación), sin cupo al resolver (lista de espera, con tu lugar en la fila) y
   rechazada (motivo escrito y **desde cuándo podés volver a pedir**).

**Mientras espera no se le cobra nada y no se le aparta saldo.** Hay que decirlo en la
pantalla: es la primera duda de cualquiera que ve un pedido pendiente.

### Lo que exige *Quién quiere entrar*

- **El puntaje llega descompuesto**, en motivos a favor y en contra, nunca como número
  suelto. Un `712` no le dice nada a quien organiza; «2 pasanakus completos, 14 aportes
  en fecha, es su primer grupo de este monto» sí.
- **Rechazar exige motivo escrito, y el sistema no deja confirmar en blanco.** No es
  burocracia: sin motivo no hay nada que apelar, y el debido proceso del modelo se apoya
  en que toda decisión que perjudica a alguien se pueda mostrar. El motivo viaja al
  postulante tal cual se escribió, junto con la fecha desde la que puede volver a pedir.
- **La cola muestra el plazo de cada pedido**, y el que vence hoy va en rojo. El plazo
  corre contra el organizador, no contra el postulante.
- **Aceptar es idempotente.** Dos toques del mismo botón no ocupan dos cupos.

**Cambio en F5 y 2C:** la fila de *unirse con código* pasa a *«canje que abre solicitud,
seguimiento del pedido y cola de resolución del organizador»*. El contrato de **2C** suma
`POST /grupos/{cod}/solicitudes-ingreso` y
`POST /grupos/{cod}/solicitudes-ingreso/{id}/resolucion`. **F7** suma la vista de
solicitudes escaladas cuando el organizador deja vencer el plazo.

---

## D-16 · Organizar es una habilitación, y no la da otro usuario

**La pregunta que contesta:** ¿qué se le pide a alguien para administrar la plata de un
grupo, y quién se lo aprueba?

**La respuesta corta:** catorce requisitos medibles en cuatro niveles, y lo aprueba el
**equipo de cumplimiento**, no otro participante y no el grupo. Administrar plata de
terceros es la actividad que el regulador mira; la habilitación es la evidencia de que
se le dio a alguien que cumplía una lista que se puede mostrar.

### Los cuatro niveles

Los umbrales **no se escriben en la pantalla ni en el código**: son las catorce filas de
`requisito_habilitacion` de `seeders/minimos/17-organizador-y-emparejamiento.json`, y son
acumulativas — para Senior hay que cumplir además todo lo de Estándar.

| Nivel | Antigüedad | Reputación | Capacitación | Garantía | Qué habilita |
| --- | --- | --- | --- | --- | --- |
| `APRENDIZ` | 3 meses | 600 | 1 módulo | — | 1 grupo · hasta Bs 500 de aporte |
| `ESTANDAR` | 6 meses | 650 | 2 módulos | — | 3 grupos · hasta Bs 1.500 |
| `SENIOR` | 12 meses | 720 | 2 módulos | Bs 2.000 | 8 grupos · hasta Bs 5.000 |
| `MAESTRO` | 24 meses | 800 | 3 módulos, con LGI/FT | Bs 5.000 | Sin tope |

Todos exigen además **debida diligencia reforzada vigente**: quien maneja plata de otros
se conoce mejor que quien maneja la propia.

### Lo que exige la pantalla del participante

- **Lista de cumplidos y faltantes, no un sí o un no.** Lo pide
  [[CU-90 Postular a organizador y habilitarse]] §2, y la razón es que un «no calificás»
  sin decir qué falta es una puerta cerrada sin picaporte. Cada requisito se muestra con
  **tu valor al lado del umbral** y el código de la fila (`HAB_APRENDIZ_REPUTACION`), que
  es lo que permite discutir el criterio en vez de discutir el resultado.
- **El puntaje se congela al postular.** Si los requisitos cambian entre la postulación y
  la resolución, se evalúa con los vigentes al momento de postular: no se mueve el arco
  con la pelota en el aire.
- **Los cinco pasos a la vista**: pedir · evaluar · aprobar la capacitación · firmar el
  contrato · quedar con un nivel. Sin firma no se crean grupos, aunque esté aprobado.
- **Capacitación vencida suspende, no elimina.** No abre grupos nuevos, pero **sigue
  administrando los que tiene**: dejar grupos huérfanos es peor que tener un organizador
  con el curso vencido. Hay que decirlo en la pantalla porque es contraintuitivo.

### Lo que exige la vista de backoffice

`cumplimiento/organizadores`, en el backoffice **financiero** (grupo «Cumplimiento», fase **F8.C**), con
la cola de postulaciones pendientes y, por cada una, el nivel pedido, el puntaje
congelado y cuántos requisitos cumple sobre el total. Y un bloque de **lo que no puede
pasar**, que es el que se lee antes de aprobar la primera:

| No puede pasar | Por qué |
| --- | --- |
| Aprobar sin dejar firma | La restricción de base rechaza el `UPDATE` sin `revisada_por` |
| Rechazar sin decir qué faltó | Un rechazo sin camino de vuelta es una expulsión encubierta |
| Evaluar con requisitos posteriores a la postulación | Mover el arco con la pelota en el aire |
| Dejar grupos huérfanos al suspender | La suspensión frena grupos nuevos, no los vigentes |
| Habilitar con incumplimiento en curso | Rechazo automático hasta que se resuelva y pase la ventana |

**Cambio en F5, F7 y 2E:** F5 suma `pasanaku/organizador`; **F8.C** suma
`cumplimiento/organizadores`; el contrato de **2E** expone los requisitos evaluados con su
código, no solo el veredicto.

---

## D-17 · *No voy a poder pagar* es un botón

Todo el aparato del incumplimiento —fondo de garantía, escalera de cobranza, matriz de
sanción, descargo, apelación— ya estaba en el modelo y **vivía únicamente en el
backoffice**. El que debe no veía nada de eso: veía un recargo creciendo.

**La decisión de producto:** avisar antes existe como acción. No perdona la deuda —el
importe es el mismo— pero **cambia la etapa de cobranza en la que entra**, y con eso
cambian los canales, la frecuencia, el tope de contactos y si interviene una persona.

### Las cuatro salidas, cada una con su costo a la vista

| Salida | Qué hace | Costo que se muestra |
| --- | --- | --- |
| Pagar en dos partes | Mitad ahora, mitad en 15 días; el fondo cubre la diferencia | Sin recargo si cumple las dos fechas |
| Plan de regularización | Hasta 3 cuotas, lo aprueba quien organiza, queda escrito con fechas | Congela el recargo mientras se cumpla |
| Que el fondo cubra la cuota | El grupo no ve el faltante; queda deuda con el fondo | Suma deuda con el fondo y baja el puntaje |
| Ceder el cupo | Alguien de la lista de espera toma el lugar | Cierra la participación en ese grupo |

**Ninguna opción se ofrece sin decir qué cuesta.** Una lista de salidas sin costos es una
lista de trampas.

### La escalera, que no la decide nadie

Las seis etapas de `estrategia_cobranza` se muestran completas, con sus canales y su tope
de contactos por semana. El punto de mostrarla no es informar: es que **nadie decide a
mano cuánto insistirle a quien debe**. La etapa la fija el atraso.

| Etapa | Días | Canales | Tope semanal | Persona | Quita |
| --- | --- | --- | :-: | :-: | :-: |
| Preventiva | −3 a 0 | Push · WhatsApp · en la app | 1 | no | no |
| Temprana | 1 a 7 | Push · WhatsApp · SMS | 3 | no | no |
| Administrativa | 8 a 30 | WhatsApp · SMS · llamada | 3 | **sí** | no |
| Prejudicial | 31 a 90 | WhatsApp · llamada · correo | 2 | sí | **sí** |
| Judicial | 91 a 365 | Correo · llamada | 1 | sí | sí |
| Castigo | 366 a 1.095 | Solo correo | 1 | sí | sí |

Y los dos plazos del debido proceso, **guardados el día que empiezan**: 5 días hábiles de
descargo y 5 de apelación, con la apelación resuelta por **otra persona**. Los feriados
no cuentan, y ninguno de los dos se recalcula al consultar.

**Cierra con el fondo de garantía, y no es un consuelo:** el grupo cobra igual, el que
sigue en el turno recibe su bolsa completa y a tiempo, y lo que queda abierto es una
deuda con el fondo, no un agujero entre vecinos. Es la diferencia entre este producto y
un pasanaku de cuaderno, y el momento de decirlo es justo cuando la persona está por no
pagar.

**Cambio en F4, F5 y 4B:** la tarjeta de cuota exigible suma la acción *No voy a poder
pagar*; el gate de F5 suma que **el participante ve su propio expediente**, no solo el
operador.

---

## D-18 · El Punto de Reclamo tiene puerta en la app

El backoffice tenía la bandeja de reclamos desde el principio. Lo que no existía era
**la puerta por donde entran**. Un Punto de Reclamo es una exigencia formal de la RNSF
Libro 4 Título I, y el canal en la app es uno de sus tipos (`punto_reclamo.tipo`).

**Lo que hace distinta a esta pantalla de un formulario de contacto:** el plazo se
calcula y **se guarda cuando el reclamo entra**. Después no se recalcula nunca, aunque el
caso se mire seis semanas más tarde. Es `reclamo_cliente.plazo_respuesta`, y es la
columna que hace auditable el cumplimiento.

| Lo que la pantalla entrega al enviar | Por qué |
| --- | --- |
| **Número correlativo único** (`REC-2026-08-0157`) | `codigo` es `UNIQUE`; con ese número se sigue el caso por cualquier canal |
| **Fecha límite concreta**, no «a la brevedad» | 5 días hábiles administrativos, guardados |
| Que puede extenderse a 10 **avisando dentro del plazo** | `plazo_prorrogado_hasta` + `prorroga_comunicada_al_cliente_en` |
| Que más de 10 se le informa **por escrito a la ASFI** | `prorroga_comunicada_al_organismo_en` |
| Que existe **segunda instancia** y después la ASFI | Y que reclamar acá primero **no le hace perder ese derecho** |

**Los cuatro canales son el mismo Punto de Reclamo.** App, WhatsApp, teléfono y oficina
entran al mismo expediente con el mismo número y el mismo plazo. Si cada canal abriera su
propio caso, el reporte mensual a la ASFI contaría cuatro reclamos donde hay uno.

**Y la pantalla distingue cuatro cosas que la gente confunde**, porque cada una tiene otro
camino y otros plazos: consulta (no abre expediente), reclamo (abre caso con plazo),
denuncia (sobre la conducta de otro, puede ser anónima) y desconocimiento de cargo (va por
CU-19, con plazos propios).

> **Nadie de AportaYa pide la contraseña ni el PIN para atender un reclamo.** La frase va
> en la pantalla, no en un instructivo: el canal de soporte es donde más se intenta la
> suplantación.

**Cambio en F5 y F7:** F5 suma `soporte/ayuda` y `soporte/reclamos/nuevo`; el gate de F5
ya exigía el plazo guardado y ahora tiene dónde verificarlo desde el lado del cliente.

---

## D-19 · Subir de nivel no paga un bono en efectivo

**La pregunta:** ¿cuánto se le da a alguien que sube de nivel?

**La respuesta de la maqueta es una aritmética, no una cifra**, porque una cifra sola no
se puede discutir. Dos datos mandan.

**Primero, cuánto hay para repartir.** La única comisión que cobra la plataforma es la de
la entrega: `COM_ENTREGA`, 0,3 % de la bolsa con piso Bs 10 y techo Bs 50, una vez por
turno cobrado. Todo lo demás está en Bs 0. En un grupo de Bs 10.000 son **Bs 30 por
persona y por ciclo**. Un bono de Bs 20 se come dos tercios de eso.

**Segundo, y es el que decide:** el saldo de la billetera es **pasivo exigible con
respaldo uno a uno** en `cuenta_custodia`. Regalar Bs 20 de saldo obliga a depositar
Bs 20 reales, o el encaje deja de cuadrar en la próxima conciliación. No es una
promoción: es emitir dinero electrónico sin haberlo recibido.

| Modelo de premio | Lo que cuesta | Veredicto |
| --- | --- | --- |
| Bs 20 en efectivo al subir | 66 % del ingreso del ciclo **y** hay que respaldarlo en custodia | **No** |
| 50 % de descuento en la comisión del próximo turno | Sale del margen, no de la custodia | **Sí** |
| Vale que paga un comercio aliado | Bs 0 para la plataforma | **Sí** (ver D-21) |

**Y el premio que ya existía y nadie mostraba:** subir de nivel **levanta los topes**. De
`SIMPLIFICADA` a `REFORZADA` el saldo máximo va de Bs 2.000 a Bs 50.000. Eso no cuesta
plata, lo paga el propio historial de la persona, y es lo que realmente estaba pidiendo
quien pedía «un premio».

**Dónde se ve el descuento aplicado.** En el cobro del turno, con el desglose completo:
bolsa, comisión, descuento por nivel, acreditado. Y con una advertencia que la pantalla
dice sola: **en una bolsa chica la comisión toca el piso y el descuento vale poco**. En
la demo, sobre Bs 3.000 la comisión da Bs 9 y manda el piso de Bs 10, así que el 25 % son
Bs 2,50. La pantalla lo dice y agrega la tabla con bolsas de Bs 10.000 y Bs 20.000.
Inflar la bolsa de ejemplo para que el número luciera mejor sería mentir sobre el
tarifario.

**Cambio en F4, F5 y 2B:** F5 suma `pasanaku/nivel`; F4 suma el desglose del cobro con la
línea de descuento; el contrato de **2B** expone el descuento **como concepto de tarifa
con su regla**, no como un ajuste calculado en el cliente.

---

## D-20 · El mercado de turnos tiene dos lados

Un turno temprano da liquidez antes; uno tardío obliga a esperar. Esa diferencia tiene
precio, y el mercado existe para que se pacte de forma ordenada en vez de arreglarse por
WhatsApp entre dos personas.

**La asimetría que ordena todo:** quien quiere **adelantar, paga**; quien acepta
**esperar, cobra**. Y de ahí sale qué se habilita antes: ceder no le agrega riesgo al
grupo, adelantar sí.

| Pantalla | Ruta | Qué resuelve |
| --- | --- | --- |
| **Tu turno** | `pasanaku/grupo/[codigo]/mi-turno` | Qué vale ceder, con demanda e interesados |
| **Ceder mi turno** | `.../ofertas/nueva` | Publicar: hasta dónde me corro y cuánto pido |
| **Ofertas del grupo** | `.../ofertas` | Aceptar la de otro, con la validación de riesgo |

### Las cinco reglas que el carril no puede negociar

1. **El valor se muestra como rango y se dice que es estimado.** Publicar un número
   exacto sería fijar precio en un mercado de dos partes, y convertiría una estimación en
   una promesa.
2. **El tope de compensación es regulatorio, no comercial.** 5 % de la bolsa. Sin ese
   techo, compensar cinco meses de espera es una tasa de interés, y prestar plata es otra
   licencia — la de «pagos y plataformas de pago» no la cubre. **La pantalla marca el
   tope antes de que se pise**, no después: el botón de publicar se bloquea con
   `AP-CU62-05` y el motivo escrito.
3. **`EN_VALIDACION` va después de `ACEPTADA`, nunca antes.** Validar antes obligaría a
   correr el motor de riesgo contra cada interesado hipotético. La consecuencia es
   incómoda y hay que asumirla: **una permuta pactada puede caerse**, y por eso el dinero
   recién se mueve en `EJECUTADA`.
4. **El motor mira al que adelanta, no al que espera.** Los cinco factores —cuotas que le
   quedan por aportar, historial, mora abierta, riesgo agregado del grupo, tope de
   compensación— se muestran con su valor y su umbral. Cuando bloquea, el motivo es sobre
   la exposición del grupo, no sobre la persona: «lo dejaría cobrando la bolsa con 6
   cuotas por aportar».
5. **Hay tope de permutas por ciclo** (2). Si no, el orden del grupo se vuelve un mercado
   permanente y el sorteo deja de significar algo.

**El acceso depende del nivel.** Por debajo de 480 puntos la pantalla **no se abre y
explica por qué**, con el enlace a *Tu nivel*: ceder un turno compromete a todo el grupo,
así que se pide historial antes que confianza.

**Cambio en F5 y 2C:** F5 suma las tres pantallas; el contrato de **2C** expone la oferta
con sus once estados y la aceptación como operación idempotente que devuelve el veredicto
de riesgo con sus factores.

---

## D-21 · El vale lo paga el comercio, y por eso puede ser grande

Es la contracara de D-19. El descuento de un vale **no sale de la plataforma ni de la
custodia**: lo pone el comercio aliado, que a cambio llega a alguien que va a tener plata
en la mano en pocas semanas. Por eso puede ser mucho más grande que cualquier bono que la
plataforma pudiera regalar.

| Lo que exige la pantalla | Por qué |
| --- | --- |
| **QR que rota cada 30 segundos** | Un vale es plata: con código fijo, una foto compartida vale lo mismo que el vale |
| El código corto al lado del QR | Misma razón que en la invitación: quien no puede escanear, dicta |
| **Estado en fila propia** | Disponible, reservado, utilizado, expirado, cancelado, bloqueado |
| De dónde salió el vale | Campaña, insignia, ciclo completado, referido — el origen es parte del beneficio |
| Las condiciones, completas | Sucursales, acumulable o no, tope por persona |
| **El rechazo por doble canje** | `AP-VAL-03` · es la protección que hace que un comercio acepte poner el descuento |

**Dos reglas del canje que el carril hereda del módulo de alianzas:**

1. **El beneficio se congela en el canje.** Si mañana la campaña baja del 8 % al 6 %, el
   vale ya usado siguió valiendo lo de ese día — misma regla que el tarifario congelado
   por grupo.
2. **El presupuesto corta la emisión, nunca el canje.** Un vale en manos de alguien es una
   obligación asumida.

Y una que es de esta pantalla: **al canjear se muestra cuánto se ahorró en bolivianos**,
y que **no se descontó nada del saldo**. Si no se dice, la mitad de la gente va a creer
que el vale le sacó plata de la billetera.

**Cambio en F4 y 5B:** F4 suma `alianzas/mis-vales` y `alianzas/vales/[id]/uso` en los
accesos rápidos de la portada; **5B** expone el canje como operación idempotente con
firma, y la validación del lado del comercio vive en el portal partner, fuera de este
repositorio de app.

---

## D-22 · El calendario llega hasta que se cierra el último pasanaku

**El defecto:** los escenarios de la maqueta traían a mano solo las cuotas que la demo
necesitaba contar —la del mes, la siguiente y una pagada—, así que el calendario se
quedaba sin meses a los dos toques de flecha. **Un pasanaku de doce períodos se veía como
si durara tres.**

**La corrección, y de dónde sale el dato.** Un pasanaku tiene duración conocida desde el
día que se sortea: un período por cupo. Y esa duración ya está escrita —`turnos[].mes`
dice en qué mes cobra cada turno—, así que las cuotas que faltan **no se inventan, se
derivan**. Quedan exactamente tantas cuotas como turnos.

En el frontend real esto **no es una función del cliente**: `GET /aportes/obligaciones`
tiene que devolver el ciclo completo, con las futuras marcadas como tales. Derivarlo en
la app sería recalcular en el cliente algo que el período ya fijó al abrirse.

| Regla | Detalle |
| --- | --- |
| Rango del calendario | De la primera cuota del pasanaku más viejo a la última del más nuevo |
| Estado por período | Anterior al mes en curso `PAGADA` · el mes en curso `PENDIENTE` · posterior `FUTURA` |
| Lo escrito a mano manda | Si el período ya tiene una obligación con su mora y su recargo, **no se pisa** |
| Ubicación | «Mes 6 de 14, hasta que se cierre el último pasanaku» |
| **Volver a hoy** | Con catorce meses, regresar a flechazos es inaceptable |

### El efecto sobre la lista, que hay que resolver y no ignorar

Con el ciclo completo, la lista pasaría a mostrar once tarjetas, nueve de ellas «todavía
no se abre», enterrando las dos que importan. La lista contesta *qué pago ahora*, así que:

- muestra **entera** la parte exigible —pendiente y vencida—;
- asoma **las dos próximas** que todavía no se abren;
- y cierra con una línea que dice **cuántas quedan y hasta cuándo**, con el paso al
  calendario, que es donde vive la pregunta *cómo vengo*.

**Cambio en F4 y 2C/3A:** el gate de F4 suma que el calendario cubre el ciclo completo y
que la lista no se deja invadir por lo que todavía no se debe; el contrato de aportes
devuelve el ciclo entero.

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
| `ListaDeRequisitos` | organismo | Cumplidos y faltantes con tu valor al lado del umbral (D-16) |
| `RelojDePlazo` | molécula | Un plazo guardado, con lo que falta y de qué norma sale (D-15, D-17, D-18) |
| `TarjetaDeSolicitud` | organismo | Pedido de ingreso con puntaje descompuesto y las dos acciones (D-15) |
| `OpcionConCosto` | molécula | Una salida y lo que cuesta elegirla, en la misma tarjeta (D-17) |
| `EscaleraDeEtapas` | molécula | Tramos contiguos con su canal y su tope (D-17) |
| `Vale` | organismo | Cupón con muesca, QR rotativo, estado y condiciones (D-21) |
| `MedidorDeRango` | molécula | Valor estimado entre un mínimo y un máximo, con su escala (D-20) |
| `TarjetaDeOferta` | organismo | Permuta: lo que te dan, lo que te piden y la compensación (D-20) |
| `PanelDeFactores` | organismo | Veredicto con los factores que lo produjeron y sus umbrales (D-20) |
| `DesgloseDeCobro` | molécula | Bruto, comisión, descuento y neto, en filas con total separado (D-19) |

Y dos reglas de estilo que la maqueta fija y `disenar-frontend` recoge:

1. **El ícono dice qué pasó, no si el número sube o baja.** Un aporte, una recarga por
   QR, una comisión y un débito rechazado tienen íconos distintos; el color y el signo
   ya dicen la dirección.
2. **Los movimientos se agrupan por día**, con el neto del día y el **saldo corrido**
   al costado de cada línea. Una lista plana de importes no es un extracto.
3. **Un estado elegido no puede depender de que dos tokens de fondo sean distintos.**
   `--field` y `--surface` son el mismo blanco en tema claro y dos verdes casi iguales en
   oscuro; un segmentado que los usaba para pista y segmento activo quedaba invisible en
   los dos temas. Lo elegido va con relleno de marca (D-12).
4. **Un color de estado en una superficie chica lleva relleno y borde.** Por debajo de
   unos 40 px, los tintes al 12–15 % son indistinguibles entre sí. Y la muestra de la
   leyenda se pinta con **las mismas reglas** que la pieza que explica (D-12).

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
