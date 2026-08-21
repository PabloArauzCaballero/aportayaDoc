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

## 1 · Los siete deltas

| # | Delta | Fases que cambian |
| :-: | --- | --- |
| **D-1** | El alta de cuenta es de **ocho pasos**, no de cuatro | F3 |
| **D-2** | **Dos backoffices** distintos: financiero y de sistemas | F6, F7, F8 |
| **D-3** | La verificación de identidad se maqueta **como expediente**, no como fila | F8.A |
| **D-4** | *Pagar mi aporte* se reemplaza por **aportes pendientes con filtros** | F4, F5 |
| **D-5** | El sorteo se muestra como **evento guardado y reproducible** | F5 |
| **D-6** | **Perfil público de terceros** y catálogo de insignias explicable | F5 |
| **D-7** | La **publicidad** es una superficie del producto, rotulada y con control | F4, F5 |

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

La segunda vertical de negocio se maqueta, no se insinúa. Banner al pie del inicio,
de movimientos y de la lista de aportes, siempre **rotulado como publicidad**, con el
anunciante visible y un control para ocultarlo. Al tocarlo se abre una pantalla que
dice **por qué te aparece** (el anunciante compra un segmento, nunca una lista),
**por qué existe** (paga la parte gratuita del producto) y deja **apagar la
segmentación sin perder ninguna condición del servicio**.

**Cambio en F4/F5:** entra el organismo `BannerDePauta` y la pantalla
*Sobre este aviso*, contra el servicio `publicidad`.

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
