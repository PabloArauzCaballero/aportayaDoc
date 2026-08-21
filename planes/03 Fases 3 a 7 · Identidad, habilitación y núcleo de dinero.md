---
tags:
  - plan
  - fase
titulo: "Fases 3 a 7 — Identidad, habilitación y núcleo de dinero"
fases: [3, 4, 5, 6, 7]
depende_de: [0, 1, 2]
habilita: [8, 9, 10, 11, 12, 13, 14, 15, 16, 17]
---

# Fases 3 a 7 — Identidad, habilitación y núcleo de dinero

> **Qué se construye acá.** Quién es el usuario (F3), si tiene permitido operar
> (F4), cómo se registra el hecho económico (F5), dónde vive la plata (F6) y cuánto
> cobra la plataforma (F7). Al cerrar la Fase 7 el sistema mueve dinero real con
> partida doble, límites, licencia, comisiones e impuestos — sin grupos todavía.

> **Se ejecuta en:** Olas 1 y 2 · carriles A, B, C (F3, F5, F4) y A, B (F6, F7) — ver [[07 Carriles de trabajo concurrente]] para
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

**Regla común a las cinco fases.** Antes de implementar cada caso de uso se leen
`docs/CasosDeUso/CU-NN *.md` **completo** y se copian de ahí: la descomposición
atómica (los nombres de las piezas ya están decididos), los códigos de error, las
restricciones aplicables y los criterios de aceptación. Este plan dice el orden y las
fronteras; el caso de uso dice el contenido.

Y antes de escribir el organismo se responden por escrito las **seis preguntas de
frontera transaccional** de [[Prompt de backend]] — la sexta, restaurada por
[[20 Saneamiento del plan · huecos de la migración a microservicios]] §2: *¿esto
cruza a otro servicio y qué pasa si el otro falla?*

---

# FASE 3 — Identidad, sesión y control de acceso

**Módulo de bóveda:** `01_identidad_usuarios` (25 tablas)
**Casos de uso:** CU-01, CU-04, CU-05, CU-08, CU-09

> **Objetivo.** Que exista un usuario real con credenciales, MFA, dispositivo,
> sesión, roles y permisos — y que el filtro de sesión global (default-deny) de la
> Fase 2 deje de apoyarse en un token de mentira.

## Gate de entrada

- [ ] Fase 2 cerrada; las diez pruebas del pipeline (`CU-00`) en verde
- [ ] Semillas `10-roles-y-permisos.json` (12 roles, 16 permisos) y `13-politicas-de-token.json` aplicadas

## Leer antes

`docs/CasosDeUso/CU-01`, `CU-04`, `CU-05`, `CU-08`, `CU-09` ·
`docs/Arquitectura/ADR-024 Autenticación y sesión distribuida.md` ·
`docs/Restricciones.md` § **R-SEG** (8 restricciones) ·
skills `autenticacion-jwt`, `kyc-onboarding`, `roles-y-accesos`, `seguridad-sesion-rls`

## Capa por capa

### `dominio/` — átomos

| Átomo | CU | Responsabilidad | Prueba clave |
| --- | :-: | --- | --- |
| `evaluarMatrizRiesgo` | 01 | Puntúa factores → nivel y diligencia exigida | Cada combinación de la matriz sembrada |
| `calcularRetencionLegal` | 01 | Hasta cuándo se conserva el expediente | Plazo persistido, no recalculado |
| `evaluarRiesgoDeAcceso` | 04 | Dispositivo + geo + historial → riesgo | Dispositivo nuevo eleva el riesgo |
| `politicaDeToken` | 04 | Vigencia, longitud e intentos por propósito | Lee catálogo, no constantes |
| `armarEvidencia` | 05 | Hash de documento + IP + dispositivo + momento | Determinismo del hash |
| `permisosEfectivos(asignaciones, ahora)` | 08 | Une permisos de roles vigentes a una fecha | Rol vencido no aporta permisos |
| `violaSegregacion(permisos)` | 08 | Detecta pares autorizar/ejecutar incompatibles | Tabla de pares **explícita**, no inferida |
| `evaluarPoliticaDeClave(clave, datosTitular)` | 09 | Complejidad y derivación de datos personales | Clave que contiene el apellido: rechazada |
| `calcularEnfriamiento(politica, ahora)` | 09 | Hasta cuándo se restringe | Plazo guardado al crear |

### `infraestructura/` — moléculas

`VerificacionKycAdaptador` (proveedor de identidad, tras interfaz) ·
`ListaRestrictivaRepositorio` · `CredencialRepositorio` (Argon2id **con pepper**,
nunca la clave en claro, con historial) · `SesionRepositorio` · `TokenAdaptador` ·
`TokenRecuperacionRepositorio` · `ContratoRepositorio` · `AceptacionRepositorio` ·
`RolRepositorio` · `AsignacionRolRepositorio` · `InvalidadorDeSesiones` ·
`EvaluadorDeObligaciones` (enumera lo que impide la baja, **con detalle legible**).

### `aplicacion/` — organismos

`CU01RegistrarUsuario` · `CU04Autenticar` · `CU05AceptarContrato` ·
`CU08AsignarRol` · `CU08RevocarRol` · `CU09CambiarCredencial` · `CU09SolicitarBaja`

**Fronteras transaccionales que hay que respetar:**

| CU | Todo-junto-o-nada | Fuera del commit |
| :-: | --- | --- |
| 01 | usuario + KYC + diligencia + calificación + expediente + evento `identidad.usuario_creado` | Notificación de bienvenida, alta en bandeja, **apertura de la cuenta de billetera** |
| 04 | intento registrado + validación + apertura de sesión | Aviso de acceso desde dispositivo nuevo |
| 08 | validación de segregación + escritura + bitácora + evento + **invalidación de sesiones** | — |
| 09 | escritura + cierre de sesiones + bitácora + evento | Correo de confirmación |

> [!important] La cuenta de billetera ya no está en la transacción de CU-01
> La cuenta la abre `nucleo-financiero` al **consumir** `identidad.usuario_creado`
> (S4 de [[20 Saneamiento del plan · huecos de la migración a microservicios]] §2),
> con reintento idempotente ante consumo duplicado o fuera de orden. `identidad` no
> toca `cuenta_billetera`; la pantalla puede mostrar "billetera en apertura" hasta
> que llegue el evento de vuelta.

### `web/` — páginas

`POST /v1/usuarios` (**pública**: marcada en el contrato OpenAPI y en la lista de
rutas públicas de `SecurityConfig` — [[00c Recetario · implementar un caso de uso]]) · `POST /v1/sesiones` ·
`POST /v1/contratos/:id/aceptaciones` · `POST /v1/accesos/asignaciones` ·
`DELETE /v1/accesos/asignaciones/:id` · `POST /v1/cuenta/clave` ·
`POST /v1/cuenta/recuperacion` · `POST /v1/cuenta/baja`

**Rate limit obligatorio** en `/sesiones`, `/cuenta/recuperacion` y `/usuarios`.

### Trabajos programados del servicio

`identidad.usuario_creado` → notificación (stub hasta F12) y apertura de cuenta en
`nucleo-financiero` (S4) · `billetera.abierta` → cálculo inicial de límites (F4) ·
`sesion.sospechosa` → aviso.

## Restricciones que exigen prueba de rechazo

`R-SEG-01` `R-SEG-02` `R-SEG-03` `R-SEG-04` `R-SEG-06` `R-SEG-07` `R-SEG-08` `R-SEG-09` ·
`R-BIL-04` `R-BIL-05` `R-BIL-09` `R-BIL-13` · `R-UIF-09` `R-UIF-10` `R-UIF-11` ·
`R-CON-05` `R-CON-06` · `R-LIC-01` · `R-AUD-01` `R-AUD-02` `R-AUD-04` `R-AUD-08`
`R-AUD-09`

## Gate de salida F3

- [ ] Gate común (§9 del plan maestro)
- [ ] **`CU-00` se elimina** y sus diez pruebas de pipeline apuntan a CU-01 y CU-04
- [ ] El filtro de sesión global (default-deny) funciona con JWT real; un endpoint nuevo exige sesión salvo que esté marcado público en el contrato **y** en `SecurityConfig`
- [ ] Prueba de RLS con usuario real: el usuario A no ve una fila del usuario B — **cero filas**, no `403`
- [ ] `R-SEG-07` (segregación de funciones) probada con un par autorizar/ejecutar real
- [ ] **`R-SEG-09`: reusar un token de refresco revoca la familia entera y sus sesiones** (probado)
- [ ] Ninguna clave, PIN ni token aparece en un log (revisión de la redacción de PII en Logback con un caso real)

---

# FASE 4 — Habilitación: licencia, diligencia y límites

**Módulos:** `12_cumplimiento_asfi` (parcial) · `09_auditoria_reportes` (parcial: `umbral_operativo`) · `10_billetera_custodia` (parcial: `limite_operativo_billetera`, `consumo_limite`)
**Casos de uso:** CU-02, CU-03, CU-06, CU-40, CU-46

> **Objetivo.** Que exista la respuesta a *"¿este usuario puede hacer esta
> operación?"* antes de que exista una sola operación. Es el invariante 9 —denegar
> por omisión— convertido en dos organismos que toda fase posterior invoca.

**Esta fase se hace antes que la billetera a propósito.** Si CU-40 y CU-46 llegaran
después, cada caso de uso de dinero nacería sin control y habría que retrofitearlo.

## Gate de entrada

- [ ] Contrato de `identidad` publicado en `dev` (T1) — gate en artefactos, no en "fase cerrada" ([[20 Saneamiento del plan · huecos de la migración a microservicios]] §5)
- [ ] Semillas `03-limites-operativos.json` (40 límites), `06-umbrales-uif.json` (18), `08-gobierno-y-licencia.json` aplicadas

## Leer antes

`CU-02`, `CU-03`, `CU-06`, `CU-40`, `CU-46` · `docs/Cumplimiento.md` ·
`docs/Restricciones.md` § **R-LIM**, § **R-LIC**, § **R-UIF** ·
skills `cumplimiento-uif`, `kyc-onboarding`, `norma-nueva`

## Capa por capa

### `dominio/` — átomos

| Átomo | CU | Responsabilidad |
| --- | :-: | --- |
| `faltantesDeNivel` | 02 | Requeridos contra recibidos |
| `calcularProximaRevision` | 02 | Periodicidad según riesgo |
| `clasificarPep` | 03 | Declaración → nivel de riesgo |
| `compararPerfil` | 06 | Declarado contra observado → desvío y severidad |
| `periodicidadPorRiesgo` | 06 | Meses hasta la próxima revisión |
| `evaluarTope` | 40 | Acumulado + monto contra tope. **Nunca contra `NULL`** |
| `resolverVentana` | 40 | Inicio y fin de la ventana vigente |
| `resolverHabilitacion` | 46 | Licencia o sandbox, con sus límites |

> **`evaluarTope` nunca compara contra `NULL`.** Un tope ausente **no** es "sin
> límite": es rechazo (`R-LIM-01`). Esa es la prueba más importante de la fase.

### `infraestructura/` — moléculas

`DebidaDiligenciaRepositorio` · `CalificacionRiesgoRepositorio` (cierra la vigencia
anterior e inserta la nueva) · `DeclaracionPepRepositorio` ·
`PerfilTransaccionalRepositorio` · `RevisionKycRepositorio` · `LimiteRepositorio` ·
`ConsumoLimiteRepositorio` (**acumulado de la ventana, con bloqueo**) ·
`LicenciaRepositorio` · `SandboxRepositorio`

### `aplicacion/` — organismos

`CU02ElevarDiligencia` · `CU03DeclararPep` · `CU06RevisarConocimiento` (trabajo
programado, **idempotente por usuario y período**) · `CU40EvaluarLimites` ·
`CU46VerificarAlcance`

**CU-40 y CU-46 son una verificación por lectura local, no una llamada** (S8 de
[[20 Saneamiento del plan · huecos de la migración a microservicios]] §2,
[[ADR-029 Catálogo legible por todos los servicios|ADR-029]]): el servicio que va a
mover dinero lee límites, licencia y tarifario **del esquema `catalogo` de su propia
base, antes de abrir la transacción** de la operación. No hay HTTP a `cumplimiento`
ni a `tarifas` dentro de ninguna transacción. Sin dato vigente en `catalogo` ⇒ la
operación se **rechaza** (denegar por omisión, `R-LIM-01`). Y el registro PCC-01/ROG
que la evaluación pueda disparar va por **evento post-commit** que `cumplimiento`
consume (S9) — nunca dentro de la transacción de la operación.

### `web/`

`POST /v1/usuarios/:id/diligencia` · `POST /v1/usuarios/:id/pep` ·
CU-06, CU-40 y CU-46 **no tienen endpoint**.

### Trabajos

`revision-kyc.cron` (mensual, bloqueo por identificador) ·
`limite.rechazo` → aviso con el disponible restante.

## El detalle que hace fallar todo en local

`seeders/minimos/08-gobierno-y-licencia.json` siembra la licencia **`EN_TRAMITE`** a
propósito: con ese estado `fn_lic_servicio_habilitado()` devuelve `false` y **ningún
servicio financiero se habilita** (`R-LIC-01`). Es el estado real mientras no haya
resolución de ASFI.

| Entorno | Qué hacer |
| --- | --- |
| Producción | Se deja `EN_TRAMITE`. El `UPDATE` de habilitación está en el campo `al_otorgarse_la_licencia` del propio seeder |
| Local y pruebas | La semilla de **prueba** (`sql/61_dev/`) habilita la licencia |
| **Nunca** | Desactivar la validación en el código para "poder trabajar". Eso es el riesgo 7 del plan maestro |

Y una prueba explícita: **con licencia `EN_TRAMITE`, toda operación de dinero es
rechazada con `R-LIC-01`.**

## Advertencia de catálogos provisionales

Tres seeders están marcados `⚠ PROVISIONAL` (límites operativos, impuestos, umbrales
UIF). El arranque de la API **emite un `warn` por cada catálogo con
`estado: PROVISIONAL`**, con el archivo y el motivo. No bloquea, pero deja rastro:
que nadie descubra en una inspección que el umbral era un borrador.

## Restricciones con prueba de rechazo

`R-LIM-01` `R-LIM-02` `R-LIM-03` · `R-LIC-01` `R-LIC-02` `R-LIC-03` ·
`R-UIF-09` `R-UIF-10` `R-UIF-11` · `R-BIL-02` · `R-SEG-04` · `R-AUD-04` `R-AUD-08`

## Gate de salida F4

- [ ] Gate común
- [ ] Base **sin** catálogo de límites ⇒ toda operación rechazada (invariante 9, probado)
- [ ] Licencia `EN_TRAMITE` ⇒ rechazo `R-LIC-01` (probado)
- [ ] `evaluarTope` con tope `NULL` ⇒ rechazo, no permiso (probado)
- [ ] Concurrencia: dos operaciones simultáneas del mismo usuario no superan el tope entre las dos
- [ ] El arranque advierte por cada catálogo `PROVISIONAL`

---

# FASE 5 — Contabilidad de partida doble

**Módulo:** `03_aportes_pagos_qr` (subconjunto contable: `cuenta_contable`, `asiento_contable`, `movimiento_contable`)
**Caso de uso:** CU-24

> **Objetivo.** Que exista el libro antes que el dinero. Todo hecho económico de
> `nucleo-financiero` escribe su asiento en la misma transacción; los hechos que
> nacen en otro servicio llegan como paso de saga o como evento. Si el libro llega
> después, hay que reescribir cada flujo.

Es la fase más chica del plan y una de las dos más importantes.

## Gate de entrada

- [ ] Fases 1 y 2 cerradas; nada más — el libro no depende de habilitación ([[20 Saneamiento del plan · huecos de la migración a microservicios]] §5)
- [ ] Semilla `01-plan-de-cuentas.json` (19 cuentas) aplicada

## Leer antes

`CU-24` · `docs/Restricciones.md` § **R-AUD** · skill `contabilidad-partida-doble`

## Capa por capa

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `cuadrarPartidas` | Verifica la igualdad y normaliza signos. **Puro, con pruebas de propiedad** |
| Molécula | `AsientoRepositorio` | Alta del asiento y sus movimientos, **append-only** |
| Molécula | `CuentaContableRepositorio` | Resuelve el código a la cuenta |
| Organismo | `CU24RegistrarAsiento` | **Se ejecuta dentro de la transacción del hecho económico — solo dentro de `nucleo-financiero`** |
| Página | — | Sin endpoint |

### Cómo lo consumen los otros casos de uso

Dentro de `nucleo-financiero`, en la misma transacción del hecho:

```java
// dentro de la transacción, en el organismo del hecho económico
registrarAsiento(dsl, new Asiento(
    "RECARGA_ACREDITADA",
    ordenRecargaId,
    List.of(debe(cuenta("1101"), monto), haber(cuenta("2101"), monto))
));
```

`registrarAsiento` **exige** el `DSLContext` transaccional: no compila sin él. Es la
garantía de que no existe un asiento fuera de la transacción de su hecho.

**Y esa garantía no cruza servicios.** El asiento va en la misma transacción **solo**
para hechos que nacen en `nucleo-financiero` (recarga, retiro, transferencia). Para
hechos que nacen en otro servicio —cobrar un aporte, liquidar una entrega, cubrir un
incumplimiento— el asiento es el paso de `nucleo-financiero` dentro de la saga
correspondiente (S1–S3), o el consumo de un evento para hechos sin movimiento de
dinero (S6 de [[20 Saneamiento del plan · huecos de la migración a microservicios]]
§2). Nadie llama a `registrarAsiento` por HTTP desde adentro de su propia
transacción.

### Pruebas de propiedad para `cuadrarPartidas`

Con jqwik, no con ejemplos sueltos:

- Para cualquier conjunto de partidas, `suma(debe)` igual a `suma(haber)` o **lanza**.
- Un asiento de una sola partida siempre lanza.
- Redondeo: 1000 partidas de dos decimales cuadran al centavo con `BigDecimal`.
- Signos normalizados: el orden de las partidas no cambia el resultado.

## Restricciones con prueba de rechazo

`R-AUD-01` (append-only) · `R-AUD-05` (cuadre) · `R-AUD-06` (corrección por reverso) ·
`R-BIL-11`

## Gate de salida F5

- [ ] Gate común
- [ ] `UPDATE` sobre `asiento_contable` y `movimiento_contable` **rechazado por la base** (`REVOKE`), probado
- [ ] Asiento descuadrado ⇒ rechazado por `R-AUD-05`, probado
- [ ] Pruebas de propiedad de `cuadrarPartidas` en verde
- [ ] `registrarAsiento` no compila sin el `DSLContext` transaccional (verificado por el compilador)

---

# FASE 6 — Billetera, custodia y efectivo

**Módulo:** `10_billetera_custodia` (25 tablas)
**Casos de uso:** CU-10, CU-11, CU-12, CU-13, CU-14, CU-15, CU-16, CU-17, CU-50, CU-57

> **Objetivo.** La billetera funcionando: entra plata, sale plata, se retiene, se
> reversa, se certifica y **cuadra todos los días contra la custodia**.

Es la fase más grande de este documento. Se subdivide en cuatro bloques con revisión
intermedia, aunque el gate sea uno solo.

## Gate de entrada

- [ ] Fase 5 cerrada · CU-40 y CU-46 disponibles (F4 cerró en T2 — [[20 Saneamiento del plan · huecos de la migración a microservicios]] §5)
- [ ] `02-politicas.json` (billetera, redondeo) y `14-proveedores-externos.json` aplicadas

## Leer antes

Los diez CU · `docs/Restricciones.md` § **R-BIL** (18 restricciones, la familia más
grande) · skills `contabilidad-partida-doble`, `dinero-decimal`,
`qr-pagos`

## El invariante que gobierna esta fase

> **El saldo no se escribe: se deriva.** Se insertan movimientos con contrapartida;
> la caché de saldo se sincroniza en la misma transacción, por trigger. Ningún
> repositorio hace `UPDATE cuenta_billetera SET saldo = …`.

Si en algún momento aparece esa sentencia, la fase está mal implementada, no
importa que las pruebas pasen.

## Bloque 6.A — Movimientos (CU-12, CU-13, CU-14)

| Nivel | Piezas |
| --- | --- |
| Átomos | `componerParDeMovimientos` (débito y crédito que **suman cero**) · `aplicarAObligacion` · `vigenciaDeRetencion` · `espejarMovimientos` |
| Moléculas | `MovimientoBilleteraRepositorio` · `TransaccionBilleteraRepositorio` · `RetencionSaldoRepositorio` · `ReversoRepositorio` · `TransferenciaRepositorio` |
| Organismos | `CU12TransferirSaldo` · `CU13RetenerSaldo` · `CU14ReversarTransaccion` |
| Páginas | `POST /v1/billetera/transferencias` · `/retenciones` · `/transacciones/:id/reverso` |

## Bloque 6.B — Entrada y salida (CU-10, CU-11)

| Nivel | Piezas |
| --- | --- |
| Átomos | `calcularAcreditacion` (bruto − costo del proveedor) · `resolverConceptoUif` · `calcularNetoDeRetiro` · `puedeRetirar` (reúne las condiciones duras y devuelve **el motivo** del rechazo) |
| Moléculas | `OrdenRecargaRepositorio` · `OrdenRetiroRepositorio` · `PasarelaAdaptador` (idempotente **por referencia**) · `DesembolsoAdaptador` (misma clave de idempotencia) |
| Organismos | `CU10RecargarSaldo` · `CU11RetirarSaldo` |
| Páginas | `POST /v1/billetera/recargas` · `POST /v1/billetera/retiros` |

> **CU-11: retención primero, pago después. Nunca al revés.** Está escrito así en la
> bóveda y es la diferencia entre un retiro y un descubierto.

## Bloque 6.C — Ciclo de vida y control (CU-15, CU-16, CU-17)

| Nivel | Piezas |
| --- | --- |
| Átomos | `componerExtracto` (saldo corrido y totales) · `verificarContraSaldoDiario` · `listarImpedimentos` (**todos**, no solo el primero) · `calcularAlcance` |
| Moléculas | `SaldoDiarioRepositorio` · `DocumentoAdaptador` · `SolicitudCierreRepositorio` · `CuentaBilleteraRepositorio` · `BloqueoSaldoRepositorio` |
| Organismos | `CU15EmitirExtracto` · `CU16CerrarBilletera` · `CU17BloquearSaldo` |
| Páginas | `GET /v1/billetera/extractos` · `POST /v1/billetera/cierre` · `POST /v1/cumplimiento/bloqueos` |

`DocumentoAdaptador` genera el PDF y lo guarda **por el puerto `AlmacenArchivos`**
de `plataforma/comun-dominio` ([[20 Saneamiento del plan · huecos de la migración a microservicios]] §3),
con SHA-256 en base. La bóveda pide *object lock*: queda
como deuda declarada, no como omisión silenciosa.

`CU15EmitirExtracto` usa la **conexión de lectura** contra la réplica (ADR-031).

## Bloque 6.D — Custodia y efectivo (CU-50, CU-57)

| Nivel | Piezas |
| --- | --- |
| Átomos | `calcularCobertura` (**con guarda de división por cero**) · `clasificarDescuadre` · `saldoTeorico(inicial, recargas, retiros)` · `clasificarDiferencia(teorico, contado, umbral)` |
| Moléculas | `ConciliacionCustodiaRepositorio` · `MovimientoCustodiaRepositorio` · `PuntoAtencionRepositorio` · `ArqueoRepositorio` (**unicidad por punto y fecha**) · `AcumuladorDeEfectivo` |
| Organismos | `CU50ConciliarCustodia` (trabajo diario; **bloquea el cierre si no cuadra**) · `CU57CerrarArqueo` |
| Páginas | `POST /v1/puntos/:id/arqueos` · `POST /v1/arqueos/:id/cierre` |

`CU50ConciliarCustodia` es un cron con **bloqueo consultivo**: uno a la vez, por
definición.

## Restricciones con prueba de rechazo

Las 20 de la familia **R-BIL** (`R-BIL-01` … `R-BIL-20`) más
`R-LIM-01` `R-LIM-02` · `R-SEG-02` `R-SEG-04` `R-SEG-06` · `R-CON-05` `R-CON-08` ·
`R-AUD-01` `R-AUD-03` `R-AUD-04` `R-AUD-05` `R-AUD-06` `R-AUD-07` `R-AUD-08`
`R-AUD-10` ·
`R-UIF-02` · `R-LIC-01` · `R-GRP-03`

## Gate de salida F6

- [ ] Gate común
- [ ] **Ninguna sentencia escribe `cuenta_billetera.saldo` directamente** — regla de ArchUnit: solo el módulo de saldo de `nucleo-financiero` referencia la columna generada `CUENTA_BILLETERA.SALDO` ([[20 Saneamiento del plan · huecos de la migración a microservicios]] §7.2)
- [ ] Cuadre: para 1000 operaciones aleatorias, `SUM(movimiento)` = saldo derivado, al centavo
- [ ] Webhook de pasarela duplicado ⇒ **un solo** pago acreditado (`R-BIL-06`)
- [ ] **`R-BIL-19`: el reintento devuelve la primera respuesta, no un error** (probado)
- [ ] **`R-BIL-20`: una transacción que mezcla monedas se rechaza** (probado)
- [ ] Retiro concurrente por el saldo total ⇒ uno gana, el otro falla limpio; nunca descubierto
- [ ] Retención antes que pago, verificado por orden de escritura en la transacción
- [ ] Conciliación de custodia que no cuadra ⇒ el cierre diario **no** puede marcarse cuadrado (`R-BIL-12`)
- [ ] Arqueo duplicado por punto y fecha ⇒ rechazado por la base
- [ ] Extracto emitido lee de la **réplica** y su hash coincide con el saldo sellado

---

# FASE 7 — Tarifas, comisiones, impuestos y facturación

**Módulo:** `11_tarifas_comisiones` (27 tablas)
**Casos de uso:** CU-30, CU-31, CU-32, CU-33, CU-34, CU-35, CU-36

> **Objetivo.** Cobrar bien: cotizar antes de operar, devengar, cobrar, facturar ante
> el SIN, devolver con nota de crédito y cerrar la liquidación mensual.

## 🎯 Hito de validación del stack

[[Stack]] fija que **CU-31 de punta a punta valida la elección tecnológica**: toca
dinero, tarifario congelado, partida doble, outbox e impuestos. Al cerrar esta fase
se hace la evaluación explícita:

> Si el stack sostuvo CU-31 con **todos** sus criterios de aceptación como pruebas,
> incluida la de rechazo de cada restricción citada, sostiene el resto del sistema.
> Si no, **se detiene el avance** y se revisa ADR-014 antes de la Fase 8.

Esa evaluación se escribe en `planes/informes/carril-P<N>.md` del carril que cierra
la fase, con evidencia, no como opinión.

## Gate de entrada

- [ ] Fase 5 cerrada · contrato de billetera (`nucleo-financiero`) publicado en `dev` — el cargo real de S7 se prueba al cerrar T3 ([[20 Saneamiento del plan · huecos de la migración a microservicios]] §5)
- [ ] Semillas `04-tarifario.json` y `05-impuestos.json` (IVA, IT) aplicadas

## Leer antes

`CU-30` … `CU-36` · `docs/Restricciones.md` § **R-TAR** · skills `facturacion-sin`,
`dinero-decimal`, `reembolsos-disputas`

## Capa por capa

### `dominio/` — átomos

| Átomo | CU | Responsabilidad |
| --- | :-: | --- |
| `resolverConcepto` | 30 | Elige el concepto por hecho y ámbito |
| `calcularComision` | 30 | Método, escalones, piso, techo y **redondeo declarado** |
| `calcularImpuestos` | 30 | IVA e IT sobre la base, según el concepto |
| `componerDevengo` | 31 | Base, comisión, descuentos e impuestos |
| `elegirViaDeCobro` | 31 | Traduce forma de cobro a operación concreta |
| `componerCuf` | 32 | Código único desde NIT, fecha, sucursal y correlativo |
| `calcularVigenciaCufd` | 32 | Ventana de validez del código diario |
| `simularSobreHistoria` | 34 | Recalcula la historia con el tarifario nuevo |
| `calcularEntradaEnVigencia` | 34 | Fecha de aviso + días de preaviso |
| `consolidarDevengos` | 35 | Agrega por estado y período |
| `compararConMayor` | 35 | Total cobrado contra saldo contable |

### `infraestructura/` — moléculas

`TarifarioRepositorio` (**vigente o snapshot congelado del grupo**) ·
`CotizacionRepositorio` · `DevengoRepositorio` (append-only, con clave de
idempotencia) · `CargoRepositorio` · `ImpuestoRepositorio` · `FacturaRepositorio` ·
`SiatAdaptador` (en línea y por lote, **con reintentos**) ·
`EventoSignificativoRepositorio` (contingencia con inicio, fin y plazo) ·
`CambioTarifarioRepositorio` · `DocumentoPublicadoRepositorio` ·
`LiquidacionIngresosRepositorio`

### `aplicacion/` — organismos

`CU30CotizarComision` · `CU31DevengarComision` · `CU32EmitirFactura` (**disparado
por evento**, nunca por HTTP) · `CU33DevolverComision` (doble firma) ·
`CU34PublicarTarifario` · `CU35CerrarLiquidacion` · `CU36AplicarSegmento`

**Frontera transaccional de CU-31 (S7 de
[[20 Saneamiento del plan · huecos de la migración a microservicios]] §2).**
`tarifas` **consume** los eventos del hecho — `aportes.aporte_pagado` y
`entregas.entrega_liquidada` — y devenga en su propia transacción local, idempotente
por `id_evento`. El devengo **no** corre "dentro de la transacción del hecho
económico": ese hecho vive en otro servicio. El **cargo** posterior es una saga corta
`tarifas` → `nucleo-financiero` (débito + asiento), orquestada por `tarifas` según
[[ADR-028 Mecánica de saga|ADR-028]]; su compensación es el reverso del cargo más la
nota de crédito. Prueba obligatoria: `CU31DevengoSagaTest`.

### `web/`

`POST /v1/comisiones/cotizaciones` · `POST /v1/tarifarios` ·
`POST /v1/contabilidad/liquidaciones/:periodo/cierre` · CU-31, CU-32: **sin endpoint**.

### Trabajos

`comision.devengada` → `CU32EmitirFactura` · `comision.incobrable` → alta en cuenta
por cobrar y gestión de cobranza (F11) · `tarifario.publicado` → programación del
aviso de preaviso (F12) · `liquidacion.mensual.cron`.

## El tarifario congelado — la trampa de esta fase

`tarifa_congelada_grupo` existe porque **un grupo pacta su precio al constituirse**
(CU-20, `R-TAR-12`). Un tarifario nuevo **no** puede cambiar el precio de un grupo ya
constituido. `TarifarioRepositorio` tiene por eso dos métodos distintos —vigente y
congelado— y el organismo elige explícitamente cuál usa. Nunca hay un método "el
tarifario" que resuelva solo.

Prueba obligatoria: publicar un tarifario nuevo y verificar que un grupo existente
sigue cotizando al precio congelado.

## Restricciones con prueba de rechazo

`R-TAR-01` … `R-TAR-13` · `R-CON-07` · `R-LIC-03` · `R-AUD-01` `R-AUD-05` `R-AUD-06`
`R-AUD-08` · `R-BIL-12`

## Gate de salida F7

- [ ] Gate común
- [ ] **CU-31 de punta a punta con todos sus criterios de aceptación en verde** ← hito de validación
- [ ] **`CU31DevengoSagaTest` en verde**: devengo idempotente al consumir el evento duplicado; compensación del cargo probada (reverso + nota de crédito)
- [ ] Evaluación del stack escrita en `planes/informes/carril-P<N>.md` con evidencia
- [ ] Tarifario nuevo **no** altera el precio congelado de un grupo existente (probado)
- [ ] Preaviso: un cambio sin los días de preaviso es rechazado (`R-TAR-08`)
- [ ] Devengo duplicado por la misma clave ⇒ un solo devengo (`R-TAR-04`)
- [ ] SIAT caído ⇒ contingencia registrada con inicio, fin y plazo; la factura no se pierde
- [ ] Nota de crédito enlazada a su factura; el reverso no edita el original (`R-AUD-06`)
- [ ] Liquidación mensual cuadra contra el mayor, al centavo

## Ver también

[[00c Recetario · implementar un caso de uso]] · [[07 Carriles de trabajo concurrente]] · [[00b Estándar de ejecución · código limpio, pruebas y calidad]] · [[00 Plan maestro]] · [[02 Fases 1 y 2 · Capa de datos y núcleo transversal]] · [[04 Fases 8 a 11 · Circuito del pasanaku]] · [[_CasosDeUso]] · [[Restricciones]]
