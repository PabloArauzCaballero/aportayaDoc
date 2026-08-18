---
tags:
  - plan
  - fase
titulo: "Fases 8 a 11 — El circuito del pasanaku"
fases: [8, 9, 10, 11]
depende_de: [3, 4, 5, 6, 7]
habilita: [12, 13, 14, 15, 16, 17]
---

# Fases 8 a 11 — El circuito del pasanaku

> **Qué se construye acá.** El producto, propiamente. Un grupo se arma (F8), cobra
> los aportes del período (F9), entrega el fondo al que le toca (F10) y maneja al que
> no pagó (F11). Al cerrar la Fase 11 el pasanaku funciona de punta a punta con
> dinero real, contabilidad y debido proceso.

> **Se ejecuta en:** Olas 2, 3 y 4 · carriles C (F8), A (F9), D/A (F10), B (F11) — ver [[07 Carriles de trabajo concurrente]] para
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

**Las cuatro fases son secuenciales.** No se paralelizan: F9 necesita los períodos y
obligaciones de F8; F10 necesita el fondo acumulado de F9; F11 necesita saber quién
incumplió, que solo se sabe con F9 cerrada.

**Regla de lectura obligatoria.** Antes de cada CU: leer `docs/CasosDeUso/CU-NN *.md`
completo y copiar de ahí la descomposición atómica, los errores, las restricciones y
los criterios de aceptación. Y responder por escrito las **seis preguntas de frontera
transaccional** — la sexta, restaurada por
[[20 Saneamiento del plan · huecos de la migración a microservicios]] §2: *¿esto
cruza a otro servicio y qué pasa si el otro falla?*

---

# FASE 8 — Grupos, cupos, turnos y gobernanza

**Módulo:** `02_grupos_turnos` (22 tablas)
**Casos de uso:** CU-20, CU-59, CU-60, CU-62, CU-63, CU-64, CU-65, CU-68, CU-69

> **CU-60 (sortear los turnos) va acá, no en la Fase 13.** Escribe `sorteo_turnos` y
> `turno`, ambas del módulo 02, y sin turnos sorteados no hay entrega de fondo
> (Fase 10). Lo que sí queda en la Fase 13 es CU-61 —la verificación pública—, porque
> depende de la cadena de transparencia.

> **Objetivo.** Que exista un grupo con reglamento aceptado, cupos ocupados,
> calendario de períodos proyectado y tarifario congelado — y que las decisiones
> colectivas (permutas, acuerdos, traspasos, retiros) tengan un cauce con quórum y
> evidencia.

## Gate de entrada

- [ ] Contratos de billetera (`nucleo-financiero`) y de `tarifas` publicados en `dev` ([[20 Saneamiento del plan · huecos de la migración a microservicios]] §5)
- [ ] **Riesgo aceptado y anotado:** F8 corre antes del hito de validación de CU-31; si ese hito falla, F8 retrabaja. Mitigación: el hito CU-24 al cierre de T2 ya validó el stack
- [ ] Semillas `11-contratos-de-adhesion.json` y `12-calendario-habil.json` aplicadas

## Leer antes

Los ocho CU · `docs/Restricciones.md` § **R-GRP** (16 restricciones, la segunda
familia más grande) · skills `gobernanza-grupo`, `emparejamiento-ingreso`,
`plazos-habiles`

## 8.1 · El calendario primero (CU-59)

**Se implementa antes que todo lo demás de la fase.** `proyectarCalendario` de CU-20
depende del calendario hábil, y el invariante 8 —los plazos se persisten al crear—
solo se sostiene si el calendario existe antes que el primer plazo.

| Nivel | Pieza | Nota |
| --- | --- | --- |
| Átomo | `sumarDiasHabiles(desde, dias, noHabiles)` | **Pruebas de propiedad** + casos de borde de fin de año |
| Átomo | `siguienteHabil(fecha, noHabiles)` | Corrimiento **a favor del cliente** |
| Molécula | `CalendarioRepositorio` | Consulta por rango y alcance, con **caché de vida corta** |
| Organismo | `CU59CargarCalendario` | Carga masiva con validación y constancia de fuente |
| Página | `POST /v1/calendario` · `GET /v1/calendario/calcular` | |

**Prueba que define el invariante 8:** emitir un plazo, después agregar un feriado
dentro de ese rango, y verificar que **el plazo emitido no se movió**. Solo los
plazos nuevos usan el calendario nuevo.

## 8.2 · Constitución del grupo (CU-20)

| Nivel | Pieza |
| --- | --- |
| Átomo | `congelarConceptos` — serializa el tarifario y calcula su hash |
| Átomo | `proyectarCalendario` — genera períodos y fechas desde la periodicidad |
| Molécula | `GrupoRepositorio` · `TarifaCongeladaRepositorio` |
| Organismo | `CU20CrearGrupo` — **una transacción**: grupo, configuración, snapshot, calendario y evento `grupos.grupo_activado` |
| Página | `POST /v1/grupos` |

> [!important] La cuenta de billetera del grupo ya no está en la transacción de CU-20
> La cuenta cuyo titular es el grupo la abre `nucleo-financiero` al **consumir**
> `grupos.grupo_activado` (S5 de [[20 Saneamiento del plan · huecos de la migración a microservicios]] §2),
> con reintento idempotente ante consumo duplicado o fuera de orden. `grupos` no toca
> `cuenta_billetera`; el grupo no opera dinero hasta que llega el evento de vuelta.
> Por eso `CuentaBilleteraRepositorio` **desaparece de `grupos`**: solo
> `nucleo-financiero` lo tiene ([[20 Saneamiento del plan · huecos de la migración a microservicios]] §3).

El snapshot del tarifario congelado es lo que la Fase 7 ya prevé en
`TarifarioRepositorio.congelado()` (`R-TAR-07`, `R-TAR-12`).

## 8.3 · El sorteo de turnos (CU-60)

Orden de cobro verificable con **commit-reveal**: primero se publica el compromiso
(hash de la semilla), después se revela y cualquiera puede recomputar el orden.

| Nivel | Pieza | Nota |
| --- | --- | --- |
| Átomo | `barajarDeterminista(semilla, cupos)` | Fisher-Yates **puro y reproducible**, sin IO |
| Átomo | `verificarCompromiso(semilla, entropias, hash)` | Comparación de hash, pura |
| Molécula | `SorteoRepositorio` | Lee y escribe `sorteo_turnos` y `turno` |
| Organismo | `CU60SortearTurnos` | Abre la transacción y **ordena las dos fases** |
| Página | `POST /v1/grupos/:id/sorteo` | |

> **Los dos átomos son los mismos que usa CU-61 para verificar.** Una sola
> implementación para generar y para verificar: si fueran dos, la verificación
> pública podría dar por bueno un sorteo que el generador hizo mal. La Fase 13 los
> importa, no los reescribe.

`CU60SortearTurnos` emite `sorteo.sellado`, que la Fase 13 consume para encadenar el
bloque de transparencia. Hasta entonces el evento queda registrado sin consumidor —
que es exactamente lo que el outbox permite sin romperse.

**Pruebas:** la misma semilla produce el mismo orden, siempre · una semilla que no
coincide con su compromiso es rechazada · un sorteo ya ejecutado no se repite
(`R-GRP-05`).

## 8.4 · Ingreso: emparejamiento e invitaciones (CU-68, CU-69)

| Nivel | Piezas |
| --- | --- |
| Átomos | `puntajeCompatibilidad(perfil, grupo, criterio)` (pesos **vigentes y versionados**) · `explicarPuntaje(componentes)` · `concentracionDeRiesgo(participantes, candidato)` · `normalizarTelefono(texto)` (E.164 boliviano) · `puedeInvitar(emisor, grupo)` |
| Moléculas | `SolicitudIngresoRepositorio` · `PropuestaGrupoRepositorio` · `MotorDeEmparejamiento` · `InvitacionRepositorio` · `ReferenciaRepositorio` · `EmisorDeTokens` (**un solo uso, con vencimiento y registro de intentos**) |
| Organismos | `CU68AceptarIngreso` · `CU68MaterializarPropuesta` · `CU69Invitar` · `CU69AceptarInvitacion` |
| Páginas | `POST /v1/grupos/:id/solicitudes` · `POST /v1/emparejamiento` · `POST /v1/propuestas/:id/respuesta` · `POST /v1/grupos/:id/invitaciones` · `POST /v1/invitaciones/:token/aceptar` · `POST /v1/referencias` |

`explicarPuntaje` no es cosmético: es la defensa contra la acusación de
discriminación arbitraria. **Un puntaje sin explicación guardada no se emite.**

## 8.5 · Gobernanza: permutas, acuerdos, traspasos, retiros (CU-62 … CU-65)

| CU | Átomos | Organismo | Página |
| :-: | --- | --- | --- |
| 62 | `puedePermutar(turnoA, turnoB, deudas)` — devuelve **el motivo** del rechazo o `null` | `CU62PermutarTurnos` | `POST /v1/turnos/permutas` · `/:id/aceptar` |
| 63 | `computarVotacion(votos, quorum)` (**pruebas de propiedad**) · `esParteInteresada` (abstención forzada) | `CU63ResolverAcuerdo` + `EjecutorDeAcuerdo` | `POST /v1/acuerdos` · `/:id/votos` |
| 64 | `separarObligaciones(obligaciones, fechaCorte)` · `cumpleRequisitosDeIngreso(usuario, grupo)` | `CU64TraspasarCupo` | `POST /v1/cupos/:id/traspasos` |
| 65 | `calcularPosicionDeSalida(aportes, cobros, deuda, recargos)` (**pruebas de propiedad sobre el cuadre**) | `CU65RetirarParticipante` | `POST /v1/participantes/:id/retiro` |

`EjecutorDeAcuerdo` despacha al efecto según el `tipo` del acuerdo. **Catálogo
cerrado de efectos**: un acuerdo no puede ejecutar código arbitrario (skill
`motor-de-reglas`).

## Restricciones con prueba de rechazo

`R-GRP-04` `R-GRP-05` `R-GRP-06` `R-GRP-07` `R-GRP-08` `R-GRP-09` `R-GRP-10` `R-GRP-11`
`R-GRP-12` `R-GRP-14` `R-GRP-15` `R-GRP-16` · `R-TAR-07` · `R-BIL-04` `R-BIL-05` ·
`R-CON-01` `R-CON-02` `R-CON-07` · `R-UIF-09` · `R-SEG-03` · `R-NOT-01` `R-NOT-02`
`R-NOT-03` · `R-LIC-01` · `R-AUD-01` `R-AUD-04` `R-AUD-05`

## Gate de salida F8

- [ ] Gate común
- [ ] **Plazo emitido no se mueve al agregar un feriado posterior** (invariante 8)
- [ ] `sumarDiasHabiles` con pruebas de propiedad y bordes de fin de año en verde
- [ ] El evento `grupos.grupo_activado` abre la cuenta del grupo en `nucleo-financiero`; consumido dos veces o fuera de orden ⇒ una sola cuenta (S5, probado)
- [ ] Un tarifario nuevo no altera el precio congelado del grupo (regresión de F7)
- [ ] Token de invitación usado dos veces ⇒ el segundo falla (`R-NOT-02`)
- [ ] Votación: parte interesada **no** puede votar; la abstención queda **registrada**
- [ ] Todo puntaje de emparejamiento tiene su explicación guardada
- [ ] La misma semilla de sorteo produce el mismo orden; un compromiso que no coincide se rechaza

---

# FASE 9 — Aportes, pagos QR, conciliación y cierre diario

**Módulo:** `03_aportes_pagos_qr` (23 tablas; la parte contable ya está de la F5)
**Casos de uso:** CU-21, CU-19, CU-51, CU-99

> **Objetivo.** Que *"pagué"* signifique *"el dinero está en la cuenta del grupo"*,
> nunca una declaración del usuario; que un reintento no cobre dos veces; y que el
> día cierre cuadrado o no cierre.

Es la fase de mayor riesgo operativo del proyecto: acá conviven webhooks duplicados,
pagos fuera de orden, conciliación bancaria y un cierre que no puede correr dos veces.

## Gate de entrada

- [ ] Fase 8 cerrada
- [ ] Semilla `14-proveedores-externos.json` aplicada
- [ ] Al menos un grupo de prueba con período abierto y obligaciones generadas

## Leer antes

`CU-21`, `CU-19`, `CU-51`, `CU-99` · `docs/Restricciones.md` § **R-BIL** ·
skills `qr-pagos`, `idempotencia-reintentos`, `reembolsos-disputas`,
`proveedores-externos`, `contabilidad-partida-doble`

## 9.1 · Enrutamiento de proveedores (CU-99) — primero

Se implementa **antes** que CU-21 porque el cobro por QR pasa por acá.

| Nivel | Pieza |
| --- | --- |
| Átomo | `elegirProveedor(candidatos, operacion, salud, costo)` — cobertura, prioridad, salud y costo |
| Átomo | `costoEstimado(monto, fija, porcentual)` — con `Dinero` y el redondeo de la política |
| Molécula | `ProveedorPagoRepositorio` · `EnlacePagoRepositorio` (**unicidad de código y de URL corta**) |
| Molécula | `AdaptadorPasarela` — uno por proveedor, misma interfaz, **declara qué soporta** |
| Molécula | `RegistroDeSalud` — ventana móvil por proveedor y operación |
| Organismo | `CU99EnrutarCobro` |
| Páginas | `POST /v1/proveedores-pago` · `POST /v1/cobros` · `POST /v1/webhooks/:proveedor` |

> **La conmutación es automática pero nunca silenciosa.** Cambiar de proveedor emite
> evento y deja rastro; si no, nadie se entera de que el principal está caído hasta
> la factura de fin de mes.

## 9.2 · El cobro del aporte (CU-21) — el corazón del producto

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `aplicarPagoAObligacion` | Distribuye el pago entre capital y recargo |
| Átomo | `clasificarMora` | Días de atraso y severidad según política |
| Molécula | `ObligacionRepositorio` | Toma la obligación para actualizar, **con bloqueo** |
| Molécula | `OrdenCobroRepositorio` | Emite la orden y su QR conciliable |
| Molécula | `ConciliacionRepositorio` | Cruce contra el extracto bancario |
| Organismo | `CU21CobrarAporte` | **Orquesta la saga S1**: local en `aportes` (pago + obligación + estado de saga); pasos remotos en `nucleo-financiero` y `tarifas` |
| Página | `POST /v1/aportes` | |

### Frontera transaccional de CU-21 — saga S1, escrita

CU-21 **cruza tres servicios**: por eso deja de ser una transacción ACID local (la
herencia del monolito) y pasa a ser la saga orquestada S1 de
[[20 Saneamiento del plan · huecos de la migración a microservicios]] §2, según
[[ADR-028 Mecánica de saga|ADR-028]].

| Pregunta | Respuesta |
| --- | --- |
| Todo junto o nada (local en `aportes`) | `obligacion_aporte.monto_pagado` + estado, y el paso actual en `estado_saga`, en la misma transacción |
| **¿Cruza a otro servicio y qué pasa si el otro falla?** | **Sí: saga S1.** `aportes` → `nucleo-financiero` (crédito a la cuenta del grupo + `asiento_contable`) → `tarifas` (devengo). Orquestador: `aportes`. **Compensación**: reverso del crédito en `nucleo-financiero` y la obligación vuelve a `PENDIENTE` — nunca se edita el libro, se reversa (ADR-028) |
| Fuera del commit | El QR al proveedor, el aviso al participante, la cancelación de recordatorios, y la evaluación de umbrales UIF por **evento post-commit** que `cumplimiento` consume (S9) |
| Clave de idempotencia | **Del cliente** para el pago con saldo; **del proveedor** (referencia) para el webhook; **derivada de id de saga + número de paso** para los pasos remotos (ADR-028) |
| Qué se bloquea | La fila de `obligacion_aporte`, con `SELECT … FOR UPDATE`. Granularidad: la obligación, no el grupo |
| Si el proceso muere entre pasos | El `@Scheduled` + ShedLock de `aportes` barre `estado_saga`, reintenta el paso o compensa; el consumidor despacha el aviso al reiniciar. No se pierde nada |

Prueba obligatoria: **`CU21CobrarAporteSagaTest`** — camino feliz, paso remoto que
falla ⇒ compensación (reverso del crédito, obligación en `PENDIENTE`), y reintento
idempotente por id de saga + paso.

### Las reglas de CU-21 que se implementan como código, no como comentario

- El recargo por mora es una **obligación nueva** con `obligacion_origen_id`. **Nunca**
  se modifica el monto original (append-only, `R-AUD-06`).
- Pago de más ⇒ el excedente queda como **saldo a favor**, no se pierde.
- Pago por monto distinto ⇒ `PAGADO_PARCIAL` o excepción de conciliación.
- Pago no conciliado con el extracto ⇒ `excepcion_conciliacion` abierta que **bloquea
  el cierre diario** (`R-BIL-12`).

## 9.3 · Reembolsos y disputas (CU-19)

| Nivel | Pieza |
| --- | --- |
| Átomos | `reembolsableRestante(pago, reembolsosPrevios)` · `armarDescargo(pago, intentos, acuses)` |
| Moléculas | `ReembolsoRepositorio` · `DisputaRepositorio` · `AdaptadorReembolsoProveedor` |
| Organismos | `CU19EjecutarReembolso` (**doble firma**) · `CU19AtenderDisputa` |
| Páginas | `POST /v1/pagos/:id/reembolsos` · `POST /v1/webhooks/:proveedor/disputas` |

`armarDescargo` no busca evidencia nueva: **estructura la que ya existe** (intentos,
acuses, conciliaciones). Ese es el pago de haber registrado cada intento desde la
Fase 2.

## 9.4 · El cierre diario (CU-51)

| Nivel | Pieza |
| --- | --- |
| Átomo | `evaluarCuadre` — reúne las condiciones y devuelve **el motivo** del no cuadre |
| Molécula | `CierreDiarioRepositorio` · `SaldoDiarioRepositorio` (**sella los saldos encadenados por hash**) |
| Organismo | `CU51CerrarDia` — trabajo diario |
| Página | `POST /v1/contabilidad/cierres/:fecha` |

**Riesgo 9 del plan maestro.** El cierre no puede correr dos veces: `job_key` de
ShedLock **más** bloqueo consultivo. La prueba obligatoria levanta **dos
réplicas del servicio `aportes`** y verifica que el cierre se ejecute una sola vez.

Y el cierre **no cuadra** si hay una `excepcion_conciliacion` abierta o un descuadre
de custodia sin resolver (F6). Esa dependencia es el control, no un inconveniente.

## Restricciones con prueba de rechazo

`R-BIL-01` `R-BIL-02` `R-BIL-06` `R-BIL-10` `R-BIL-12` · `R-GRP-03` ·
`R-SEG-01` `R-SEG-04` · `R-AUD-01` `R-AUD-05` `R-AUD-07` · `R-CON-01` ·
`R-LIC-01` · `R-RIS-03` · `R-UIF-02`

## Gate de salida F9

- [ ] Gate común
- [ ] **Webhook duplicado ⇒ un solo pago acreditado** (`R-BIL-06`)
- [ ] Webhook fuera de orden (confirmación antes que creación) ⇒ manejado, no error
- [ ] Dos participantes pagando la misma obligación a la vez ⇒ uno gana, sin doble acreditación
- [ ] Pago no conciliado ⇒ el cierre diario **no puede** marcarse cuadrado
- [ ] Recargo de mora es obligación nueva con `obligacion_origen_id`; el original intacto
- [ ] **Dos réplicas del servicio `aportes` ⇒ el cierre diario se ejecuta una sola vez** (riesgo 9)
- [ ] Proveedor principal caído ⇒ conmutación al secundario **con evento**, no en silencio
- [ ] **`CU21CobrarAporteSagaTest` en verde**: camino feliz, compensación (reverso del crédito, obligación en `PENDIENTE`) y reintento idempotente por id de saga + paso (S1)
- [ ] Los tres criterios de aceptación `gherkin` de CU-21 tienen su `@Test` con `@DisplayName` del mismo nombre

---

# FASE 10 — Entregas de fondo y desembolsos

**Módulo:** `04_entregas_fondo` (10 tablas)
**Casos de uso:** CU-18, CU-22, CU-28

> **Objetivo.** Que la plata salga **una vez**, a la cuenta correcta, con acuse y
> conciliada. Es el punto donde un error no se corrige con un asiento: se corrige
> llamando a un banco.

## Gate de entrada

- [ ] Fase 9 cerrada
- [ ] `09-reglas-operativas.json` (8 reglas de entrega) aplicada

## Leer antes

`CU-18`, `CU-22`, `CU-28` · `docs/Restricciones.md` § **R-DES**, § **R-BIL** ·
skills `desembolsos-payouts`, `proveedores-externos`

## 10.1 · La cuenta de destino (CU-18) — primero

Sin cuenta verificada no hay desembolso posible. Se implementa antes que CU-22.

| Nivel | Pieza | Nota |
| --- | --- | --- |
| Átomo | `enmascarar(numero)` | Deja visibles los **últimos cuatro** dígitos |
| Átomo | `coincideTitularidad(cuenta, titular)` | Documento y nombre normalizados |
| Molécula | `CifradorDeInstrumentos` | Cifra, **descifra solo bajo demanda justificada**, calcula el hash |
| Molécula | `CuentaBeneficiariaRepositorio` | Unicidad por hash · **principal única** |
| Molécula | `VerificadorDeTitularidad` | Un adaptador por método, misma interfaz |
| Organismos | `CU18RegistrarCuenta` · `CU18VerificarCuenta` | |
| Páginas | `POST /v1/cuentas-bancarias` · `/:id/verificacion` | |

> **El número de cuenta nunca aparece en claro en un log, una respuesta o una traza.**
> La API devuelve el enmascarado; el descifrado deja registro de quién y por qué
> (`R-SEG-01`, `R-SEG-02`, `R-BIL-17`).

## 10.2 · Liquidar y entregar (CU-22)

| Nivel | Pieza |
| --- | --- |
| Átomo | `calcularDeducciones` — arma las líneas **en orden** y devuelve el neto. **Pruebas de propiedad** |
| Átomo | `componerAsientoDeEntrega` — partidas de bolsa, beneficiario, ingreso e impuesto |
| Molécula | `EntregaRepositorio` · `ValidacionPreEntregaRepositorio` (ejecuta y **registra** cada regla) |
| Organismo | `CU22LiquidarEntrega` — **orquesta la saga S2**: local la liquidación (sin estados intermedios); débito + asiento en `nucleo-financiero` |
| Página | `POST /v1/entregas/:turnoId` |

### Frontera transaccional de CU-22 — saga S2

CU-22 **cruza a `nucleo-financiero` y a `tarifas`**: es la saga orquestada S2 de
[[20 Saneamiento del plan · huecos de la migración a microservicios]] §2. `entregas` →
`nucleo-financiero` (débito de la cuenta del grupo + `asiento_contable`), orquestada
por `entregas` según [[ADR-028 Mecánica de saga|ADR-028]]. **Compensación**: reverso
del débito; la entrega vuelve a `APROBADA`.

La comisión **no** se devenga dentro de la transacción de la entrega: `tarifas`
**consume** el evento `entregas.entrega_liquidada` y devenga en su propia transacción,
idempotente por `id_evento` (S7). Por eso `DevengoComisionRepositorio` vive en
`tarifas`, no en `entregas`. El devengo usa **el tarifario congelado del grupo**
(F7 + F8), no el vigente — es `R-TAR-06` y la razón por la que `TarifarioRepositorio`
tiene dos métodos.

Prueba obligatoria: **`CU22LiquidarEntregaSagaTest`** — camino feliz, compensación
(reverso del débito, entrega en `APROBADA`) y reintento idempotente por id de saga + paso.

`ValidacionPreEntregaRepositorio` **registra cada regla evaluada**, no solo el
resultado: ante un reclamo hay que poder mostrar qué se verificó antes de entregar.

## 10.3 · La orden de desembolso (CU-28)

| Nivel | Pieza |
| --- | --- |
| Átomo | `esReintentable(codigoError)` — clasifica el error del proveedor. **Tabla explícita** |
| Átomo | `proximoIntento(numero, politica)` — espera creciente **con jitter** |
| Molécula | `OrdenDesembolsoRepositorio` · `IntentoDesembolsoRepositorio` (unicidad por clave) |
| Molécula | `EnrutadorDeProveedores` — entidad, moneda, prioridad y salud |
| Molécula | `AdaptadorDesembolso` — uno por proveedor, misma interfaz |
| Organismos | `CU28EmitirOrden` · `CU28ProcesarAcuse` |
| Páginas | Trabajo `desembolsar` · `POST /v1/webhooks/:proveedor/desembolsos` |

### La regla que evita pagar dos veces

La **misma clave de idempotencia** viaja desde la orden hasta el proveedor. Si el
acuse se pierde y se reintenta, el proveedor reconoce la clave y no vuelve a pagar.
Si el proveedor no soporta claves de idempotencia, `AdaptadorDesembolso` lo **declara
en sus capacidades** y el enrutador lo trata distinto: nunca reintenta a ciegas.

Un error **no** clasificado en la tabla de `esReintentable` se trata como **no
reintentable** y escala a una persona. Denegar por omisión también acá.

## Restricciones con prueba de rechazo

`R-DES-01` `R-DES-02` · `R-BIL-01` `R-BIL-02` `R-BIL-06` `R-BIL-09` `R-BIL-12`
`R-BIL-17` · `R-GRP-01` `R-GRP-02` · `R-TAR-04` `R-TAR-06` · `R-SEG-01` `R-SEG-02`
`R-SEG-04` · `R-AUD-01` `R-AUD-04` `R-AUD-05`

## Gate de salida F10

- [ ] Gate común
- [ ] **Reintento del desembolso con la misma clave ⇒ un solo pago** (probado con adaptador simulado)
- [ ] Acuse duplicado ⇒ un solo cambio de estado
- [ ] El número de cuenta **no** aparece en ningún log, respuesta ni traza (revisión con caso real)
- [ ] Cada descifrado de instrumento deja registro de quién y por qué
- [ ] `calcularDeducciones`: pruebas de propiedad, sin centavos perdidos
- [ ] La entrega usa el tarifario **congelado**, no el vigente (probado con tarifario nuevo publicado)
- [ ] **`CU22LiquidarEntregaSagaTest` en verde**: compensación (reverso del débito, entrega en `APROBADA`) y reintento idempotente por id de saga + paso (S2)
- [ ] Error de proveedor desconocido ⇒ **no** reintenta y escala

---

# FASE 11 — Garantía, incumplimiento, cobranza y sanciones

**Módulo:** `08_garantia_incumplimiento` (**33 tablas** — el segundo más grande)
**Casos de uso:** CU-23, CU-25, CU-26, CU-27, CU-29, CU-66, CU-67

> **Objetivo.** Que el que no paga tenga consecuencias **con debido proceso**, que el
> grupo siga funcionando, y que la deuda no se perdone sola.

Por tamaño, la fase se parte en **cuatro sub-fases con gate propio**. Es el riesgo 10
del plan maestro.

## Gate de entrada

- [ ] Fase 10 cerrada
- [ ] `02-politicas.json` (mora y cobertura) aplicada

## Leer antes

Los siete CU · `docs/Restricciones.md` § **R-GAR**, § **R-GRP** ·
skills `garantia-mora-cobranza`, `debido-proceso`, `alertas-riesgo-temprano`

## El patrón que se repite en toda la fase

Toda decisión que perjudica a alguien sigue el mismo circuito (skill
`debido-proceso`), y se implementa **una sola vez** como piezas reutilizables:

```
causal escrita → notificación probada → plazo calculado y GUARDADO →
descargo con evidencia → decisión motivada → apelación única resuelta por OTRO →
prescripción → reversión con compensación
```

`HistorialEstadoRepositorio` **escribe la transición junto con el cambio, nunca
después**. Si el historial se escribe en otra transacción, hay un instante donde el
estado cambió sin rastro — y ese instante es exactamente el que una inspección busca.

## Sub-fase 11.A — Declaración del incumplimiento (CU-25)

| Nivel | Pieza |
| --- | --- |
| Átomo | `calcularPlazoHabil(desde, dias, calendario)` — reutiliza el calendario de F8 |
| Átomo | `armarEvidenciaAutomatica(obligacion, avisos, intentos)` — estructura lo que **ya sabemos** |
| Molécula | `RegistroIncumplimientoRepositorio` — **con bloqueo por obligación** |
| Molécula | `HistorialEstadoRepositorio` |
| Organismos | `CU25DeclararIncumplimiento` · `CU25ResolverDescargo` |
| Páginas | `POST /v1/incumplimientos` · `/:id/descargo` |

**Gate 11.A:** el plazo de descargo se guarda al declarar y no se recalcula al
consultar; un descargo fuera de plazo se rechaza con el plazo **guardado**, no con
uno recalculado.

## Sub-fase 11.B — Cobertura, aval y deuda (CU-23, CU-26)

| Nivel | Pieza |
| --- | --- |
| Átomo | `evaluarPoliticaCobertura` — topes → cuánto se puede cubrir |
| Átomo | `topeDisponible(aval, ejecucionesPrevias)` |
| Átomo | `cubreElHecho(aval, registro)` — alcance y vigencia **a la fecha del hecho** |
| Molécula | `FondoGarantiaRepositorio` (movimiento **append-only**) · `DeudaRepositorio` · `AvalRepositorio` · `EjecucionAvalRepositorio` (bloqueo por aval) · `SubrogacionRepositorio` |
| Organismos | `CU23CubrirIncumplimiento` · `CU26EjecutarAval` |
| Páginas | `POST /v1/fondo/coberturas` · `POST /v1/avales/:id/ejecuciones` · `/ejecuciones-aval/:id/respuesta` |

> **La subrogación convierte al que pagó en acreedor.** No es un detalle contable: es
> lo que hace que el aval sirva de algo. En `garantia`, en la misma transacción local,
> se escribe cobertura + deuda + subrogación + estado de saga, todo junto o nada.

### Frontera transaccional de CU-23 — saga S3

La cobertura **cruza a `nucleo-financiero`**: es la saga orquestada S3 de
[[20 Saneamiento del plan · huecos de la migración a microservicios]] §2. `garantia` →
`nucleo-financiero` (movimiento + `asiento_contable`), orquestada por `garantia` según
[[ADR-028 Mecánica de saga|ADR-028]]. El asiento **no** va dentro de la transacción de
la cobertura: es el paso remoto de la saga. **Compensación**: reverso del movimiento;
la cobertura queda `FALLIDA` con una incidencia abierta.

Prueba obligatoria: **`CU23CoberturaSagaTest`** — camino feliz, compensación (reverso,
cobertura `FALLIDA` con incidencia) y reintento idempotente por id de saga + paso.

`cubreElHecho` evalúa la vigencia **a la fecha del hecho**, no a hoy. Un aval vencido
hoy cubre un incumplimiento de cuando estaba vigente.

**Gate 11.B:** ejecutar el mismo aval dos veces no supera el tope firmado
(concurrencia probada); la deuda subrogada aparece con el avalista como acreedor;
**`CU23CoberturaSagaTest` en verde** (compensación + reintento idempotente).

## Sub-fase 11.C — Restricción y cobranza (CU-27)

| Nivel | Pieza |
| --- | --- |
| Átomo | `nivelSegunPolitica(causa, monto, antiguedad)` |
| Átomo | `restriccionesDe(nivel)` — expande el nivel en restricciones concretas |
| Molécula | `ListaRestriccionRepositorio` · `RestriccionUsuarioRepositorio` |
| Molécula | `EvaluadorDeRestricciones` — responde *"¿puede hacer X?"* **con el motivo** |
| Organismos | `CU27AplicarRestriccion` · `CU27LevantarRestriccion` |
| Páginas | `POST /v1/restricciones` · `DELETE /v1/restricciones/:id` |

La restricción es **proporcional y reversible**: `restriccionesDe(nivel)` no puede
devolver "todo bloqueado" para una mora chica. Y `EvaluadorDeRestricciones` devuelve
el motivo para que la app lo explique en lenguaje llano — no un `403` mudo.

**Gate 11.C:** una restricción vencida deja de aplicar sin intervención; el
levantamiento deja evidencia; el usuario restringido recibe el motivo, no un error
genérico.

## Sub-fase 11.D — Continuidad del grupo (CU-29, CU-66, CU-67)

| CU | Átomos | Nota |
| :-: | --- | --- |
| 29 | `calcularDevolucion(aportado, consumido, recuperado)` (piso en cero) · `repartirRemanente(saldo, derechos)` | **Prorrata con reparto exacto del centavo residual** |
| 66 | `evaluarCandidato(usuario, grupo)` · `deudaQueNoSeTraspasa(obligaciones, coberturas)` | La deuda **no** se perdona: queda con el saliente |
| 67 | `calcularPrelacion(posiciones, disponible, reglamento)` · `factorProrrata(disponible, aDevolver)` | **Pruebas de propiedad: lo repartido nunca supera lo disponible** |

Organismos: `CU29DevolverFondo` · `CU66ReemplazarParticipante` ·
`CU67DisolverGrupo` (**una transacción para toda la liquidación**).
Páginas: `POST /v1/fondos/:id/devoluciones` · `POST /v1/incumplimientos/:id/reemplazo`
· `POST /v1/grupos/:id/disolucion`. Trabajo: `cerrar-fondo`.

**El centavo residual es el tema de esta sub-fase.** Repartir Bs 100 entre 3 personas
da 33.33 × 3 = 99.99. El centavo que sobra tiene que ir a algún lado, de forma
declarada y determinista. `repartirRemanente` y `factorProrrata` lo asignan según la
política de redondeo sembrada, y las pruebas de propiedad verifican que **la suma
repartida iguala exactamente lo disponible**, siempre.

**Gate 11.D:** disolución de un grupo con 7 participantes y saldo indivisible ⇒ la
suma repartida iguala lo disponible al centavo; ningún participante recibe de más.

## Restricciones con prueba de rechazo

`R-GAR-01` … `R-GAR-06` · `R-GRP-02` `R-GRP-10` `R-GRP-11` `R-GRP-13` ·
`R-BIL-01` `R-BIL-02` `R-BIL-06` `R-BIL-12` · `R-SEG-03` `R-SEG-04` ·
`R-CON-01` `R-CON-05` · `R-AUD-01` `R-AUD-04` `R-AUD-05`

## Gate de salida F11

- [ ] Los cuatro gates de sub-fase (11.A a 11.D) cerrados
- [ ] Gate común
- [ ] **El circuito de debido proceso completo probado**: causal → notificación → plazo guardado → descargo → decisión → apelación resuelta por **otra** persona
- [ ] La apelación la resuelve alguien distinto del que decidió (`R-SEG-07`, segregación)
- [ ] Ningún reparto pierde ni inventa un centavo (pruebas de propiedad)
- [ ] Un participante reemplazado sigue debiendo lo que debía
- [ ] Todo movimiento del fondo de garantía es append-only, verificado contra la base

---

## 🏁 Hito: el pasanaku funciona

Al cerrar la Fase 11, el sistema hace el recorrido completo con dinero real:

```
usuario registrado → billetera abierta → grupo creado con tarifario congelado →
turnos sorteados → período abierto → aportes cobrados y conciliados →
fondo entregado al beneficiario → comisión devengada y facturada →
incumplimiento declarado con descargo → fondo de garantía cubre → deuda subrogada →
cierre diario cuadrado
```

**Este recorrido tiene que existir como una prueba E2E** (`PasanakuCompletoE2ETest.java`)
antes de pasar a la Fase 12. Es la prueba que demuestra que el producto existe.

## Ver también

[[00c Recetario · implementar un caso de uso]] · [[07 Carriles de trabajo concurrente]] · [[00b Estándar de ejecución · código limpio, pruebas y calidad]] · [[00 Plan maestro]] · [[03 Fases 3 a 7 · Identidad, habilitación y núcleo de dinero]] · [[05 Fases 12 a 16 · Plataforma, reputación y cumplimiento]] · [[_CasosDeUso]] · [[Restricciones]]
