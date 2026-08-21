# Módulo 10 — Billetera, Custodia y Dinero Electrónico

> **Pregunta de negocio que responde este módulo:**
> *Si la plataforma guarda plata de la gente, ¿dónde está esa plata, de quién es,
> quién puede moverla, y cómo se demuestra —cualquier día, ante cualquiera— que
> está completa?*

Este módulo es el que convierte al sistema de una **herramienta de organización**
en un **proveedor de servicios de pago**. Es un salto de categoría, no una
funcionalidad más: desde el momento en que un usuario tiene saldo, la plataforma
deja de ser un cuaderno compartido y pasa a ser depositaria de dinero ajeno, con
todo lo que eso arrastra —encaje, límites, conciliación diaria, órdenes de
autoridad, extractos, reclamos y reportes al regulador.

Los tres principios que gobiernan el módulo:

> **1. El saldo no se guarda: se deriva.**
> No existe un `UPDATE cuenta SET saldo = saldo - 500`. Existe un libro de
> movimientos *append-only* y una columna de saldo que es apenas una caché con
> bloqueo optimista. Si la caché y el libro difieren en un centavo, el sistema
> alerta; no ajusta.

> **2. El dinero se mueve por partida doble, también adentro.**
> Un aporte no es "restar 500". Es débito de Bs 500 a la billetera del
> participante y crédito de Bs 500 a la billetera del grupo, en la misma
> transacción atómica. El dinero nunca aparece ni desaparece: cambia de cuenta.

> **3. Todo el dinero electrónico está respaldado peso por peso.**
> La suma de todos los saldos de billetera tiene que ser igual —todos los días—
> al saldo real de la cuenta de custodia en el banco. El dinero de los usuarios
> **no es patrimonio de la plataforma**: es un pasivo exigible.

---

## Por qué este módulo no podía resolverse "agregando un campo saldo"

La tentación obvia es `usuario.saldo DECIMAL(14,2)`. Cuesta cinco minutos y falla
el primer día que algo va mal. Estas son las cinco preguntas que un campo `saldo`
no puede responder y que este módulo sí:

| Pregunta real | Quién la hace | Qué entidad la responde |
| --- | --- | --- |
| "¿Por qué tengo Bs 340 y no Bs 500?" | el usuario | [[movimiento_billetera]] |
| "¿Cuánto tenía esta persona el 31 de marzo?" | un juez, un auditor | [[saldo_diario_billetera]] |
| "¿La plata de los usuarios está o no está?" | el regulador | [[conciliacion_custodia]] |
| "¿Este retiro lo autorizó el titular?" | el área de fraude | [[evaluacion_antifraude]] |
| "¿Por qué no le devolvieron su saldo?" | el defensor del consumidor | [[bloqueo_saldo]] |

Cada una de esas preguntas llega tarde o temprano. La diferencia entre poder
responderla en treinta segundos y no poder responderla nunca está decidida hoy,
en el modelo de datos.

---

## Paquete: Cuentas de billetera

### `CuentaBilletera` / `cuenta_billetera` — Raíz de agregado

**Qué es.** El contenedor de saldo. Una por titular y moneda.

**Para qué sirve (negocio).** Es la pieza que permite que el pasanaku funcione
como funciona en la vida real —plata que entra, se junta y sale— sin que nadie
tenga que confiar en la cuenta bancaria personal de otro. Cuatro familias de
cuentas, y las cuatro son necesarias:

- **`USUARIO`**: el saldo de una persona. Es lo que ve en la app.
- **`GRUPO`**: **la bolsa del pasanaku tiene cuenta propia.** Este es el cambio
  más importante que introduce el módulo respecto del modelo anterior. Antes, el
  dinero del grupo era una abstracción contable; ahora es un saldo real con
  titular jurídico definido: el grupo, no el organizador. El organizador puede
  operarla dentro del reglamento, pero **no es titular y no puede retirar hacia
  una cuenta propia** (RN-18 sigue viva y ahora es estructural).
- **`PLATAFORMA_INGRESOS`** y **`PLATAFORMA_IMPUESTOS_POR_PAGAR`**: donde caen la
  comisión y los impuestos retenidos. Separadas a propósito: el IVA cobrado no es
  ingreso, es plata del fisco que la plataforma solo transporta.
- **`SUSPENSO_NO_IDENTIFICADO`**: llegó dinero que no se sabe de quién es. Sin
  esta cuenta, ese depósito se "acomoda" en algún lado y se pierde la trazabilidad.
  Con ella, queda visible hasta que alguien lo identifique o se devuelva.

**Por qué debe existir.** Sin cuenta de grupo, el dinero del pasanaku vuelve a
depender de que alguien lo tenga en su cuenta personal —que es exactamente el
problema que el producto promete resolver. Sin cuenta de plataforma separada, los
ingresos por comisión se mezclan con el dinero de los usuarios y el encaje deja
de ser demostrable.

**A nivel de sistema.**
- `UNIQUE (usuario_id, moneda, tipo)` para cuentas de usuario; `UNIQUE (grupo_id,
  moneda)` para las de grupo.
- `CHECK`: una cuenta de usuario exige `usuario_id`; una de grupo exige `grupo_id`;
  las de plataforma exigen ambos nulos. Es imposible una cuenta huérfana o con
  doble titular.
- `saldo_disponible >= 0` salvo `permite_saldo_negativo`, que solo se habilita en
  cuentas técnicas de liquidación. **Ninguna billetera de usuario puede quedar en
  descubierto**: eso sería crédito, y dar crédito requiere otra licencia.
- `saldo_total = saldo_disponible + saldo_retenido` (columna generada).
- `cuenta_contable_id` enlaza con [[cuenta_contable]] (M3): cada billetera tiene
  su espejo en el mayor, y el mayor y el libro de billetera se cuadran entre sí.

---

### `PoliticaBilletera` / `politica_billetera` — Política configurable

**Qué es.** Los parámetros de comportamiento de las billeteras, versionados.

**Para qué sirve (negocio).** Que "el retiro tiene 24 horas de enfriamiento" o
"desde Bs 500 se pide MFA" sean **filas, no constantes en el código**. El día que
el área de riesgos quiera bajar el umbral de MFA a Bs 200 porque apareció una
modalidad de fraude nueva, eso es un `INSERT` con nueva vigencia y efecto
inmediato, no un despliegue con ventana de cambio.

**Por qué debe existir.** Porque los parámetros de riesgo cambian con la realidad
del fraude, que se mueve más rápido que un ciclo de release. Y porque cuando el
regulador pregunta "¿desde cuándo aplicaban ese umbral?", `vigente_desde` responde
con fecha exacta.

---

### `SaldoDiarioBilletera` / `saldo_diario_billetera` — append-only

**Qué es.** La foto del saldo de cada cuenta al cierre de cada día, encadenada por
hash.

**Para qué sirve (negocio).** Responder "¿cuánto tenía esta persona el 31 de
marzo?" sin recorrer millones de movimientos, y —más importante— **poder probar
que la respuesta no fue fabricada después**. `hash_registro` incluye el hash del
día anterior: alterar el saldo de un día obliga a recalcular todos los días
siguientes, lo que es detectable de inmediato.

**Por qué debe existir.** Un embargo, una sucesión, una auditoría o un reclamo
siempre preguntan por una fecha pasada. Sin esta tabla la única respuesta posible
es "recalculamos y nos dio esto", que es exactamente la respuesta que un auditor
no acepta.

**A nivel de sistema.** `UNIQUE (cuenta_billetera_id, fecha)`. append-only. Job de
cierre diario; si falla, no se abre el día siguiente hasta resolverlo.

---

## Paquete: Libro de movimientos

### `TransaccionBilletera` / `transaccion_billetera` — Raíz de agregado, append-only

**Qué es.** El sobre atómico que agrupa los movimientos de una operación.

**Para qué sirve (negocio).** Es **la unidad de "algo pasó con el dinero"**. Doce
tipos, cada uno con un hecho de negocio detrás: recarga, retiro, aporte a grupo,
entrega de fondo, cobro de comisión, cobro de impuesto, transferencia entre
personas, cobertura del fondo de garantía, reposición, devolución, ajuste
operativo y reverso.

Que la transacción sea una entidad separada de sus movimientos es lo que permite:

1. **Idempotencia real.** `UNIQUE (clave_idempotencia)`. Si la app reintenta
   porque se cortó el 4G, o si la pasarela reenvía el webhook, la segunda vez no
   pasa nada. Este es el defecto más caro y más común de los sistemas de pago
   caseros: acreditar dos veces.
2. **Reversar en vez de editar.** Nunca se corrige un movimiento. Se emite una
   transacción de tipo `REVERSO` que lo compensa, y ambas quedan visibles. El
   extracto del usuario muestra el error *y* su corrección, que es lo que exige
   cualquier norma contable seria.
3. **Contexto forense.** `sesion_id`, `dispositivo_id`, `ip_origen` y `canal`
   quedan pegados a la transacción. Cuando alguien dice "yo no hice esa
   transferencia", esa fila es la que contesta.

**Por qué debe existir.** Sin sobre transaccional, cada movimiento sería
independiente y no habría forma de garantizar que un aporte deje la bolsa
cuadrada: podría acreditarse el crédito al grupo y fallar el débito al
participante.

**A nivel de sistema.**
- append-only, con `secuencia BIGSERIAL` y encadenamiento por hash.
- `origen_tipo` / `origen_id` polimórficos hacia [[obligacion_aporte]] (M3),
  [[entrega_fondo]] (M4), `devengo_comision` (M11) y
  [[cobertura_incumplimiento]] (M8). Se validan por aplicación, no por FK, para
  no acoplar el libro al esquema de los módulos que lo alimentan.
- `asiento_contable_id` conecta con la doble partida del mayor (M3): cada
  transacción de billetera genera su asiento, y el cierre diario cruza ambos.

---

### `MovimientoBilletera` / `movimiento_billetera` — append-only

**Qué es.** Cada débito o crédito individual sobre una cuenta.

**Para qué sirve (negocio).** Es **el extracto**. Lo que el usuario ve cuando
abre "mis movimientos", y lo que el auditor recorre cuando quiere reconstruir un
saldo desde cero. `saldo_disponible_posterior` guarda el saldo resultante de cada
movimiento: eso permite que el extracto muestre la columna de saldo corrido sin
recalcular, y permite detectar de inmediato una inconsistencia (si el saldo
posterior del movimiento N no coincide con el saldo previo del N+1, algo se
insertó fuera de orden).

**Por qué debe existir.** Es el registro primario del que se deriva todo lo demás.
Si se elimina, no hay sistema.

**A nivel de sistema.** Trigger `AFTER` por transacción:
`SUM(monto WHERE sentido='DEBITO') = SUM(monto WHERE sentido='CREDITO')`.
`REVOKE UPDATE, DELETE`. Índice por `(cuenta_billetera_id, registrado_en DESC)`
para el extracto, e índice `BRIN` por fecha para los barridos de auditoría.

---

### `RetencionSaldo` / `retencion_saldo`

**Qué es.** Plata que sigue siendo del titular pero deja de estar disponible.

**Para qué sirve (negocio).** Resuelve seis situaciones que sin ella se resuelven
mal:

- **`APORTE_PROGRAMADO`**: el participante autorizó el débito automático del día
  10. Se retiene el 10 y se ejecuta cuando corresponde. Sin retención, el usuario
  puede gastar el saldo el día 9 y el grupo se queda corto.
- **`ENTREGA_EN_CURSO`**: la bolsa está lista para desembolsar; se congela para
  que nadie la mueva mientras se autoriza.
- **`DISPUTA`** y **`ANTIFRAUDE`**: hay un reclamo o una alerta abierta sobre esa
  plata.
- **`ORDEN_AUTORIDAD`**: llegó un oficio judicial.
- **`COMISION_PENDIENTE`**: la comisión devengada que todavía no se cobró.

**Por qué debe existir.** Sin retenciones, la única forma de "apartar" plata es
moverla a otra cuenta, lo que ensucia el extracto del usuario con movimientos que
no entiende y rompe la trazabilidad. La retención es visible, reversible y
explicable: *"Bs 500 reservados para tu aporte del 10 de marzo"*.

**A nivel de sistema.** `saldo_retenido` de la cuenta = `SUM(retenciones VIGENTES)`.
Las retenciones vencidas se liberan por job; `expira_en` es obligatorio salvo en
las de autoridad.

---

### `ReversoTransaccion` / `reverso_transaccion`

**Qué es.** El expediente de por qué se deshizo una operación.

**Para qué sirve (negocio).** Separar el *hecho* (la transacción compensatoria) de
la *justificación* (quién lo autorizó, por qué, con qué respaldo). Cuatro tipos:
anulación, contracargo, error operativo y orden de autoridad. Cada uno tiene
consecuencias distintas: un error operativo alimenta el registro de pérdidas por
riesgo operativo (M12); un contracargo puede generar cargo al usuario.

**Por qué debe existir.** Sin él, en el libro solo se ve que hubo un movimiento
inverso, pero no por qué. Y "por qué" es la primera pregunta de toda auditoría.

---

## Paquete: Entrada y salida de dinero

### `InstrumentoFondeo` / `instrumento_fondeo`

**Qué es.** El medio por el que entra o sale plata: cuenta bancaria, tarjeta, QR,
agente, efectivo.

**Para qué sirve (negocio).** Que el usuario no tenga que tipear su cuenta cada
vez, y que la plataforma pueda exigir **coincidencia de titularidad**:
`titular_coincide` es la bandera que impide sacar plata hacia la cuenta de un
tercero. Ese solo control corta de raíz el uso de la billetera como puente de
lavado y la mayoría de las estafas de "prestame tu cuenta".

**Por qué debe existir.** Es también el punto donde se decide qué se guarda y qué
no: se persiste `token_proveedor` y `hash_identificador`, **nunca el número
completo de tarjeta ni de cuenta en claro**.

**A nivel de sistema.** `UNIQUE (usuario_id, hash_identificador)`.
`bloqueado_hasta` implementa el período de enfriamiento al dar de alta un
instrumento nuevo: si alguien toma control de la cuenta, no puede sacar el dinero
inmediatamente hacia una cuenta recién agregada.

---

### `OrdenRecarga` / `orden_recarga` y `OrdenRetiro` / `orden_retiro`

**Qué son.** El *cash-in* y el *cash-out*: los dos puntos donde el dinero cruza la
frontera entre el mundo bancario y el saldo de la app.

**Para qué sirven (negocio).** Son las operaciones de mayor riesgo del sistema, y
por eso son asimétricas a propósito:

- **La recarga es optimista**: se acredita cuando el banco confirma, se enlaza con
  [[pago]] (M3) y con el movimiento real de la cuenta de custodia.
- **El retiro es pesimista**: exige MFA verificado, puede exigir doble aprobación
  por monto, tiene ventana de enfriamiento configurable y reserva el saldo con una
  retención antes de intentar el pago. Si el proveedor falla, la retención se
  libera; el saldo nunca queda "perdido en el limbo".

**Por qué deben existir separadas de [[pago]] y [[orden_desembolso]].** Porque
esas entidades hablan de *obligaciones del grupo*; estas hablan de *saldo del
titular*. Un retiro no es una entrega de fondo: es una persona sacando su propio
dinero, sin relación con ningún pasanaku.

**A nivel de sistema.** `UNIQUE (clave_idempotencia)` en ambas;
`UNIQUE (referencia_externa)` para que el mismo depósito bancario no se acredite
dos veces. En retiro, `UNIQUE (retencion_id)`: una retención respalda un solo
retiro.

---

### `TransferenciaP2P` / `transferencia_p2p`

**Qué es.** Movimiento de saldo entre dos billeteras.

**Para qué sirve (negocio).** Es el mecanismo por el que **el aporte al pasanaku
se vuelve instantáneo y gratuito**: el participante ya tiene saldo, el grupo tiene
cuenta, y el aporte es una transferencia interna que no toca la red bancaria, no
paga comisión de pasarela y se acredita en el acto. Ese es el argumento comercial
central de la billetera.

`obligacion_id` enlaza la transferencia con la [[obligacion_aporte]] que salda,
para que un aporte hecho desde saldo tenga exactamente la misma trazabilidad que
uno hecho por QR.

---

### ~~`PuntoAtencion` / `ArqueoPuntoAtencion`~~ — retiradas del modelo

> [!danger] Retiradas por [[ADR-039 Sin efectivo · la plataforma no opera dinero físico]] · 20 de agosto de 2026
> La plataforma **no opera dinero en efectivo**. Estas dos tablas ya no existen, y
> [[CU-57 Operar un punto de atención y arquear el efectivo]] quedó obsoleto con su número
> reservado. La sección se conserva **porque contiene el argumento en contra**, y ese argumento
> sigue siendo válido.

**Qué eran.** La red física —agencias, agentes corresponsales, cajeros— y su cuadre de caja
diario.

**El argumento que sostenían, y que la decisión acepta perder.** En Bolivia una parte grande del
público carga y retira en efectivo. Sin red física, **la billetera solo sirve a quien ya está
bancarizado, que es justamente quien menos necesita el pasanaku digital.** Eso no dejó de ser
cierto porque se haya tomado la decisión contraria: es el costo que
[[ADR-039 Sin efectivo · la plataforma no opera dinero físico]] declara explícitamente en sus
consecuencias.

**Lo que se puso del otro lado de la balanza.** La coherencia de la propuesta de valor —evitar
complicaciones—, un alcance de licencia más chico ante ASFI, y sacarse de encima el umbral
PCC-01 por efectivo, el arqueo, la custodia física y el faltante de caja como evento de riesgo.
La compensación es la **interoperabilidad del QR**, que el reglamento del BCB (RD 079/2022)
exige para todo el sistema financiero nacional: cualquiera con una cuenta en cualquier banco
puede fondear.

**Si el efectivo vuelve**, este texto y `CU-57` son el punto de partida: no hay que reconstruir
el razonamiento, solo revertir la decisión con otro ADR y una migración sobre tablas vacías.

---

## Paquete: Custodia y encaje

Este paquete es el corazón regulatorio del módulo.

### `CuentaCustodia` / `cuenta_custodia`

**Qué es.** La cuenta bancaria real donde está depositado el dinero que respalda
todo el saldo de todas las billeteras.

**Para qué sirve (negocio).** Materializa la separación patrimonial: **el dinero
de los usuarios no está en la caja de la empresa**. Está en una cuenta específica,
idealmente en fideicomiso, cuyo único propósito es respaldar dinero electrónico.
Si la plataforma quiebra, ese dinero no forma parte de la masa concursal.

**Por qué debe existir.** Porque sin ella el encaje es una afirmación, no un
hecho verificable. Y porque es la primera cosa que revisa cualquier supervisión:
"muéstrenme la cuenta y muéstrenme el extracto".

---

### `MovimientoCustodia` / `movimiento_custodia` — append-only

**Qué es.** Cada movimiento real de esa cuenta bancaria.

**Para qué sirve (negocio).** Es el lado "banco" de la conciliación. Se enlaza con
[[movimiento_bancario]] (M3) para reutilizar la ingesta de extractos que ya
existe: no hay dos pipelines de conciliación, hay uno.

---

### `ConciliacionCustodia` / `conciliacion_custodia` — Raíz de agregado

**Qué es.** El cuadre diario entre lo que el sistema dice que la gente tiene y lo
que el banco dice que hay.

**Para qué sirve (negocio).** Es **la prueba diaria de solvencia del dinero
electrónico**. Tres cifras y una división:

```
ratio_cobertura = saldo_custodia / saldo_dinero_electronico
cumple_encaje   = ratio_cobertura >= 1.0000
```

Cuando el ratio baja de 1, el sistema entra en modo restringido de forma
automática: se siguen aceptando recargas y aportes (que aumentan la cobertura),
se suspenden los retiros discrecionales, y el descuadre escala como incidente
operativo (M9) y como evento de riesgo operativo (M12).

**Por qué debe existir.** Porque el modo de fallar de todas las billeteras que
quebraron es el mismo: el descuadre existió durante meses y nadie lo vio, porque
nadie lo calculaba todos los días. Esta tabla obliga a calcularlo todos los días
y deja rastro de cada cálculo.

**A nivel de sistema.** `UNIQUE (cuenta_custodia_id, fecha)`. El
[[cierre_diario]] (M3) no puede marcarse `cuadrado` si existe una conciliación de
custodia `DESCUADRADA` de esa fecha. `saldo_en_transito` separa el dinero que
salió del banco pero todavía no impactó (o viceversa) del descuadre real: sin esa
columna, todo desfase temporal se vería como faltante.

---

### `DescuadreCustodia` / `descuadre_custodia`

**Qué es.** El expediente de cada diferencia detectada.

**Para qué sirve (negocio).** Distingue los cuatro tipos de diferencia —faltante,
sobrante, desfase temporal y error de registro— porque cada uno tiene un dueño y
un plazo distintos. Y obliga a escribir la explicación y el plan de acción: un
descuadre sin explicación queda abierto y visible para siempre.

**Por qué debe existir.** Porque "hubo una diferencia de Bs 1.240 el 12 de mayo"
sin expediente es exactamente el tipo de cabo suelto que aparece en una inspección
dos años después, cuando ya nadie recuerda qué pasó.

---

## Paquete: Límites, fraude y medidas restrictivas

### `LimiteOperativoBilletera` / `limite_operativo_billetera` — Política configurable

**Qué es.** Los techos de operación por nivel de debida diligencia y ventana de
tiempo.

**Para qué sirve (negocio).** Es la traducción operativa del principio "a menos
conocimiento del cliente, menos capacidad de operación". Una persona verificada
solo con celular puede tener saldo bajo y mover poco; una con documento validado y
domicilio verificado puede más. Eso es lo que permite ofrecer apertura de cuenta
en dos minutos **sin** que la plataforma se convierta en un canal cómodo para
mover plata anónima.

**Por qué debe existir como tabla y no como constante.** Porque los techos cambian
por norma y por decisión de riesgo, y cambiar un techo no puede requerir un
despliegue. `base_normativa` guarda la referencia al artículo que obliga cada
techo: cuando el supervisor pregunta "¿de dónde sacaron este límite?", la
respuesta está en la fila.

**A nivel de sistema.** `UNIQUE (concepto, nivel_debida_diligencia, ventana)` con
vigencias. Nunca se borra un límite: se cierra su vigencia y se crea el nuevo, de
modo que siempre se puede reconstruir qué límite regía el día de una operación
cuestionada.

---

### `ConsumoLimite` / `consumo_limite`

**Qué es.** Cuánto lleva consumido cada cuenta de cada límite en la ventana actual.

**Para qué sirve (negocio).** Poder decir "te quedan Bs 1.400 de tu límite mensual
de retiro" antes de que el usuario intente la operación, en vez de rechazarla al
final. Y poder auditar por qué una operación fue rechazada un día concreto.

**A nivel de sistema.** `UNIQUE (cuenta_billetera_id, limite_id, ventana_inicio)`.
Se actualiza en la misma transacción que el movimiento; si el movimiento se
reversa, el consumo se devuelve.

---

### `ReglaAntifraude` / `regla_antifraude` y `EvaluacionAntifraude` / `evaluacion_antifraude`

**Qué son.** El motor de decisión en tiempo real y el registro de cada decisión.

**Para qué sirven (negocio).** La regla es **declarativa** (`expresion JSONB`):
"más de tres retiros en una hora hacia un instrumento agregado hoy" se escribe
como dato, no como `if`. La evaluación guarda el resultado, el puntaje, qué reglas
se dispararon y cuánto tardó.

Guardar la evaluación importa por dos razones opuestas: cuando el motor **acertó**,
justifica ante el cliente por qué se le pidió MFA o se le rechazó una operación;
cuando **se equivocó**, permite medir la tasa de falsos positivos y afinarlo. Sin
registro, el motor antifraude es una caja negra que nadie puede mejorar ni
defender.

---

### `BloqueoSaldo` / `bloqueo_saldo`

**Qué es.** Una orden de autoridad —judicial, de la unidad de inteligencia
financiera, del supervisor— que inmoviliza saldo.

**Para qué sirve (negocio).** Cumplir el oficio **sin mentirle al usuario ni
perder el rastro del dinero**. El bloqueo se materializa como una retención: la
plata sigue siendo del titular y sigue apareciendo en su saldo total, pero no está
disponible, y la app puede explicar por qué.

**Por qué debe existir.** Porque llegan oficios, y la alternativa habitual —bajar
el saldo a mano o "congelar la cuenta" con una bandera— destruye la contabilidad y
deja a la empresa sin defensa cuando el titular reclama. Aquí quedan el número de
oficio, la autoridad, el alcance, el documento y su hash.

**A nivel de sistema.** `UNIQUE (numero_oficio)`. Un bloqueo vigente impide cerrar
la cuenta y bloquea el retiro por el monto afectado, no por el total (salvo alcance
`TOTAL`).

---

## Paquete: Servicio al titular

### `EstadoCuentaBilletera` / `estado_cuenta_billetera`

**Qué es.** El extracto periódico emitido y archivado.

**Para qué sirve (negocio).** No basta con que el usuario pueda ver sus
movimientos en pantalla: hay que **emitir** el extracto, guardarlo con su hash y
poder demostrar que se puso a disposición. Esa es una obligación típica de
transparencia con el consumidor financiero, y también lo que el usuario necesita
para un trámite de crédito o un juicio.

---

### `CertificadoSaldo` / `certificado_saldo`

**Qué es.** Un documento con folio que certifica el saldo a una fecha.

**Para qué sirve (negocio).** Trámites de terceros: visas, créditos, procesos
judiciales. `folio` + `hash_documento` permiten que quien lo recibe verifique su
autenticidad contra la plataforma, lo que evita el certificado falsificado en
Photoshop.

---

### `SolicitudCierreBilletera` / `solicitud_cierre_billetera`

**Qué es.** El proceso de cerrar la cuenta y devolver el saldo.

**Para qué sirve (negocio).** El derecho a irse con la plata es tan importante
como el derecho a abrir. La solicitud valida que no queden obligaciones abiertas
—aportes pendientes, deuda, retenciones vigentes, grupos activos— y deja
constancia del destino del saldo.

**Por qué debe existir.** Porque el cierre de cuenta es el momento de mayor
fricción y el que más reclamos genera. Sin expediente, es la palabra del usuario
contra la del soporte.

---

## Cómo se conecta con el resto del modelo

| Con | Por dónde | Para qué |
| --- | --- | --- |
| **M1 Identidad** | `usuario_id`, `sesion_id`, `dispositivo_id` | saber quién operó, desde dónde y con qué factor |
| **M2 Grupos** | `grupo_id` en [[cuenta_billetera]] | la bolsa del grupo es una cuenta con titular propio |
| **M3 Pagos** | `pago_id`, `movimiento_bancario_id`, `cuenta_contable_id`, `asiento_contable_id` | la recarga reutiliza el circuito de pagos y todo espeja al mayor |
| **M4 Entregas** | `origen_id` de la transacción | la entrega de fondo se paga desde la cuenta del grupo |
| **M8 Garantía** | transacciones de cobertura y reposición | el fondo de garantía también es una billetera |
| **M9 Auditoría** | `incidente_operativo_id`, evento de dominio | los descuadres escalan como incidentes |
| **M11 Tarifas** | `origen_id` = `devengo_comision.id` | la comisión se cobra debitando la billetera |
| **M12 Cumplimiento** | límites por nivel de debida diligencia, bloqueos | el saldo es el objeto que la norma vigila |

---

## Lo que este módulo hace posible y antes no lo era

1. **Aportar en un toque, sin comisión bancaria.** El aporte se vuelve una
   transferencia interna instantánea.
2. **Cobrar el turno sin dar tu número de cuenta a nadie.** La bolsa se acredita a
   tu billetera; retirás cuando querés y a donde querés.
3. **Deducir la comisión sin perseguir a nadie.** La plataforma cobra donde ya
   tiene el dinero, no emitiendo una factura que alguien tiene que ir a pagar.
4. **Demostrar solvencia todos los días**, con una cifra, ante cualquiera.
5. **Cumplir un oficio judicial en minutos** y poder probar que se cumplió.

Y el costo, dicho con todas las letras: **desde el momento en que hay saldo, la
plataforma custodia dinero de terceros.** Eso exige licencia, encaje, límites,
reportes y supervisión. Este módulo, junto con el 11 y el 12, existe para que ese
costo sea un requisito cumplido y no una sorpresa.
