---
titulo: AportaYa — Maqueta navegable con backend simulado
tipo: maqueta
proyecto: AportaYa
version: 3
estado: presentable
fecha: 2026-08-25
tags: [maqueta, prototipo, frontend, movil, backoffice, demo]
---

# AportaYa — Maqueta navegable

> [!info] Vista interactiva
> [AportaYa-Maqueta.html](AportaYa-Maqueta.html) — abrir en el navegador.
> Publicada además en: https://claude.ai/code/artifact/56d7ecba-d38d-4fe2-9b10-7dd147cee8a0

**Qué es.** El recorrido completo del participante y del operador, navegable, con **cada
llamada al backend simulada en el navegador**: latencia real, estados de carga, reintento
idempotente y rechazo con el código de error del caso de uso. Se hizo para presentar el
producto antes de que exista una sola línea de `apps/`.

**Qué no es.** No hay backend, ni base, ni verificación de identidad. Los montos, nombres y
grupos son de ejemplo. **Ninguna cifra de la maqueta es un dato de negocio.**

## Por qué vive acá y no en `apps/`

`apps/` está en `.gitignore` a propósito —«se reintroduce cuando exista código de verdad»— y
un HTML suelto ahí haría parecer que el frontend arrancó. Esta maqueta es **documentación**:
la especificación visual de las pantallas que [[Flujo de pantallas · app del participante]] y
[[Flujo de pantallas · backoffice administrador]] describen en prosa. Por eso está junto a
[AportaYa-Sistema-Diseno.html](AportaYa-Sistema-Diseno.html) y
[AportaYa-Identidad.html](AportaYa-Identidad.html), que son la misma especie de artefacto.

## Lo que cambió en la versión 2

Nueve cambios, y todos salieron de la misma pregunta: *«¿y esto qué es?»*. Están
recogidos como deltas de planificación en
[[20 Maqueta de referencia · deltas del frontend]], que es lo que corrige las fases del
frontend para que lo construido coincida con esto.

| # | Cambio | Dónde se ve |
| :-: | --- | --- |
| 1 | **La marca real** en la barra, el alta, el backoffice y la notificación | todas |
| 2 | **Escenario del negocio: optimista o adverso**, en el mando de abajo | todas |
| 3 | **Dos backoffices**: financiero y de sistemas, con usuario y rol distintos | mando de abajo |
| 4 | **Alta de cuenta de ocho pasos**, con cámara, prueba de vida y cotejo | app |
| 5 | **Verificación de identidad como expediente** de nueve bloques | backoffice financiero |
| 6 | **Aportes pendientes** con filtro por grupo y por fecha | app |
| 7 | **El sorteo como evento guardado**, reproducible con su semilla | app |
| 8 | **Perfil público de terceros** e insignias con pantalla propia | app |
| 9 | **Publicidad rotulada** como segunda vertical, con su explicación | app |

> [!tip] El escenario adverso es la mitad que se suele esconder
> El selector de abajo cambia **el estado del negocio**, no un filtro de la interfaz: en
> adverso hay mora, el encaje queda bajo el mínimo, el fondo de garantía está cubriendo,
> los desembolsos se frenan, las colas se atrasan y la última restauración probada está
> vencida. Las mismas pantallas, los mismos componentes, otros datos. Una demo que solo
> muestra el día bueno no dice nada sobre el producto.

> [!info] Códigos de verificación
> En la maqueta el código de 6 dígitos es **`000000`**. Cualquier otro se rechaza como lo
> haría el backend, con su código de error, la cuenta de intentos que quedan y el bloqueo
> por hora al tercero.

## Lo que cubre

**App del participante** — doce pantallas, con el camino del dinero completo:

| Pantalla | Ruta | Caso de uso | Endpoint · servicio |
| --- | --- | --- | --- |
| Bienvenida | `identidad/bienvenida` | — | — |
| Crear cuenta · paso 1 de 8 | `identidad/registro` | [[CU-01 Registro y apertura de billetera]] | `POST /usuarios` · identidad |
| Confirmar el celular · 2 de 8 | `identidad/verificar-celular` | [[CU-01 Registro y apertura de billetera]] | `POST /identidad/celular/verificar` · identidad |
| Anverso del carnet · 3 de 8 | `identidad/documento/anverso` | [[CU-02 Elevar nivel de debida diligencia]] | `POST /identidad/documentos` · identidad |
| Reverso del carnet · 4 de 8 | `identidad/documento/reverso` | [[CU-02 Elevar nivel de debida diligencia]] | `POST /identidad/documentos`, `GET /identidad/segip/cotejo` · identidad |
| Selfie de seguridad · 5 de 8 | `identidad/vivacidad` | [[CU-02 Elevar nivel de debida diligencia]] | `POST /identidad/vivacidad` · identidad |
| Revisá tus datos · 6 de 8 | `identidad/cotejo` | [[CU-02 Elevar nivel de debida diligencia]] | `PUT /usuarios/{id}/datos-verificados` · identidad |
| Perfil del cliente · 7 de 8 | `identidad/perfil-cliente` | [[CU-03 Declaración PEP y beneficiario final]] | `POST /cumplimiento/perfil-cliente` · cumplimiento |
| Contrato y tarifario · 8 de 8 | `identidad/contrato` | [[CU-05 Aceptar contrato de adhesión y tarifario]] | `POST /usuarios/{id}/contrato` · identidad |
| Tu cuenta está abierta | `identidad/alta-completa` | — | — |
| Iniciar sesión | `identidad/sesion` | [[CU-04 Autenticar con MFA y registrar dispositivo]] | `POST /sesiones` (paso 1) · identidad |
| Segundo factor | `identidad/mfa` | [[CU-04 Autenticar con MFA y registrar dispositivo]] | `POST /sesiones` (paso 2) · identidad |
| Inicio / saldo | `billetera/inicio` | — | `GET /billetera/saldo`, `GET /billetera/movimientos?limite=5` · nucleo-financiero |
| Recargar (QR) | `billetera/recargar` | [[CU-10 Recargar saldo]] | `POST /billetera/recargas`, `POST /qr/cobros/{id}/confirmacion` · nucleo-financiero + aportes |
| Retirar | `billetera/retirar` | [[CU-11 Retirar saldo]] · [[CU-18 Registrar y verificar una cuenta bancaria de destino]] | `GET /cuentas-bancarias`, `POST /cuentas-bancarias`, `POST /billetera/retiros` · entregas + nucleo-financiero |
| **Mis aportes pendientes** (filtro por grupo y por fecha) | `aportes/mis-obligaciones` | [[CU-21 Cobrar el aporte del período]] | `GET /aportes/obligaciones?participante={id}` · aportes |
| Pagar una cuota | `billetera/pagar-aporte` | [[CU-21 Cobrar el aporte del período]] | `POST /aportes/{id}/pago` · aportes |
| Confirmación | `billetera/confirmacion` | — | — |
| Movimientos | `billetera/extracto` | [[CU-15 Emitir extracto y certificado de saldo]] | `GET /billetera/movimientos` · nucleo-financiero |
| Mis pasanakus (pestaña Grupos) | `pasanaku/mis-grupos` | — | `GET /grupos?participante={id}` · grupos |
| **Verificar el sorteo** (evento guardado + reproducción) | `pasanaku/grupo/[codigo]/sorteo` | [[CU-61 Verificar públicamente el sorteo]] | `GET /publico/sorteos/{id}`, `POST /verificar/sorteos/{id}` · transparencia |
| **Perfil público de otro participante** | `pasanaku/participantes/[codigo]` | [[CU-71 Recalcular el puntaje de reputación]] | `GET /reputacion/usuarios/{id}` · transparencia |
| **Detalle de una insignia** | `pasanaku/insignias/[clave]` | [[CU-74 Otorgar y revocar una insignia]] | `GET /reputacion/insignias/{clave}` · transparencia |
| **Sobre este aviso** (publicidad) | `publicidad/espacio` | — | `GET /publicidad/campanas/activa` · publicidad |
| Detalle de grupo y turnos | `pasanaku/grupo/[codigo]` | [[CU-60 Sortear los turnos]] · [[CU-61 Verificar públicamente el sorteo]] | `GET /grupos/{codigo}`, `GET /grupos/{codigo}/turnos/{n}` · grupos |
| Mi perfil (pestaña Perfil) | `pasanaku/reputacion` | [[CU-71 Recalcular el puntaje de reputación]] · [[CU-74 Otorgar y revocar una insignia]] | `GET /reputacion/usuarios/{id}` · transparencia |
| Avisos | `notificaciones/bandeja` | [[CU-80 Despachar una notificación]] · [[CU-12 Transferir saldo entre billeteras]] | `GET /notificaciones` · notificaciones |

**Lo que agregó la versión 3** — siete pantallas que responden a las preguntas que quedaron
abiertas en el apunte de producto. Ninguna inventa reglas: todas muestran algo que el modelo
ya tenía y que no se veía en ninguna parte.

| Pantalla | Ruta | Caso de uso | Endpoint · servicio |
| --- | --- | --- | --- |
| **Tu pedido de cupo** | `pasanaku/solicitudes/[id]` | [[CU-68 Postular a un grupo y ser emparejado]] | `POST /grupos/{cod}/solicitudes-ingreso` · grupos |
| **Quién quiere entrar** (cola del organizador) | `pasanaku/grupo/[codigo]/solicitudes` | [[CU-68 Postular a un grupo y ser emparejado]] | `GET` y `POST …/solicitudes-ingreso/{id}/resolucion` · grupos |
| **Organizar un grupo** | `pasanaku/organizador` | [[CU-90 Postular a organizador y habilitarse]] | `POST /organizadores/postulaciones` · organizador |
| **No voy a poder pagar** | `aportes/[id]/no-puedo-pagar` | [[CU-25 Declarar el incumplimiento con descargo y evidencia]] · [[CU-23 Cubrir un incumplimiento con el fondo]] | `POST /aportes/obligaciones/avisos-de-dificultad` · aportes |
| **Ayuda** | `soporte/ayuda` | [[CU-52 Atender un reclamo en plazo]] | — |
| **Hacer un reclamo** | `soporte/reclamos/nuevo` | [[CU-52 Atender un reclamo en plazo]] · [[CU-53 Elevar un reclamo a segunda instancia]] | `POST /reclamos` · reclamos |
| **Tu nivel** | `pasanaku/nivel` | [[CU-71 Recalcular el puntaje de reputación]] · [[CU-74 Otorgar y revocar una insignia]] | — |

| **Tu turno** (valor de ceder) | `pasanaku/grupo/[codigo]/mi-turno` | [[CU-62 Permutar turnos entre participantes]] | — |
| **Ceder mi turno** (publicar oferta) | `pasanaku/grupo/[codigo]/ofertas/nueva` | [[CU-62 Permutar turnos entre participantes]] | `POST` y `DELETE /grupos/{cod}/ofertas` · grupos |
| **Ofertas del grupo** (aceptar permuta) | `pasanaku/grupo/[codigo]/ofertas` | [[CU-62 Permutar turnos entre participantes]] | `POST …/ofertas/{id}/aceptacion` · grupos |
| **Mis vales** | `alianzas/mis-vales` | [[CU-113 Entregar un anuncio y medir su desempeño]] | — |
| **Usar el vale** | `alianzas/vales/[id]/uso` | [[CU-113 Entregar un anuncio y medir su desempeño]] | `POST /alianzas/vales/{id}/canje` · publicidad |
| **Cobrás tu turno** (con descuento por nivel) | `entregas/mi-turno/cobro` | [[CU-22 Liquidar y entregar el fondo]] · [[CU-31 Devengar y cobrar la comisión]] | `POST /entregas/{cod}/turnos/{n}/cobro` · entregas |

Y una vista de backoffice: **Habilitación de organizadores** (`cumplimiento/organizadores`,
[[CU-90 Postular a organizador y habilitarse]]), que es la respuesta institucional a
«¿quién lo tiene que aceptar?»: no otro usuario ni el grupo, sino el equipo de cumplimiento.

> [!important] El cambio de comportamiento más importante de la versión 3
> Canjear una invitación **ya no da cupo**. Antes, `#u-ent` hacía
> `POST /grupos/{cod}/miembros` y la persona quedaba adentro en la misma transacción.
> Ahora abre una `solicitud_ingreso` que el organizador resuelve, con 48 horas de plazo.
> El token se sigue consumiendo una sola vez; lo que cambió es que consumirlo no ocupa
> el lugar. Un QR se reenvía: quien lo emite conoce al invitado, quien lo recibe de
> tercera mano no. Y el criterio de emparejamiento admite **un solo participante en mora
> por grupo** — si el cupo se toma solo, esa regla no la aplica nadie.

### El recorrido que se puede caminar en la demo

Las pantallas nuevas no son cuadros sueltos: encadenan cuatro recorridos completos, con
efecto real sobre el estado simulado.

1. **Vender el turno.** Grupos → La Ramada → *Tu turno y el mercado*. Ahí se ve el rango
   estimado por ceder, se publica una oferta eligiendo hasta qué turno correrse y cuánto
   pedir —con el tope del 5 % de la bolsa marcado **antes** de pisarlo, no después— y se
   puede cancelar mientras nadie la haya tomado.
2. **Aceptar la permuta de otro.** En la misma pantalla, *Ver las ofertas del grupo*.
   Aceptar dispara la validación de riesgo con sus cinco factores; recién si pasa se mueve
   el turno y se acredita la compensación menos el 8 % de comisión. El escenario de rechazo
   del mando muestra la permuta bloqueada por riesgo, con su motivo.
3. **Aceptar un solicitante.** Grupos → Compañeras del taller (es el grupo que María
   organiza) → *N esperan que las aceptes*. Aceptar ocupa el cupo; rechazar **abre el campo
   de motivo y no deja confirmar en blanco**.
4. **Ser aceptado siendo cuenta nueva.** *Reiniciar demo* → crear cuenta → escanear el QR →
   *Pedir mi cupo* → **Simular que Rosa te acepta**. Recién ahí se crea el grupo y la
   primera obligación.
5. **El descuento, en sus tres formas.** El bono de Bs 10 y el vale de bienvenida llegan con
   el alta; *Vales* en los accesos rápidos lleva al vale con su QR rotativo y al canje; y
   *Cobrar la bolsa*, en un grupo donde te toca, muestra el desglose con el descuento de
   comisión por nivel.

### Tres arreglos en la pantalla de aportes

1. **El calendario llegaba hasta septiembre y el pasanaku dura hasta abril.** Los escenarios
   traen a mano solo las cuotas que la demo necesita contar —la del mes, la siguiente y una
   pagada—, así que la navegación se quedaba sin meses a los dos toques de flecha.
   `completarObligaciones()` deriva las que faltan de `turnos[].mes`, que ya dice en qué mes
   cobra cada turno: quedan **20 cuotas y 14 meses**, de marzo de 2026 a abril de 2027, que es
   exactamente la unión de los dos ciclos. Las cuotas escritas a mano mandan: la función solo
   rellena huecos, nunca pisa una que el escenario haya definido.
2. **Los colores del calendario no se distinguían.** Las celdas usaban los tintes de chip
   (`--ok-bg` y compañía), que a 36 px son todos el mismo claro; y la leyenda usaba los colores
   **sólidos**, así que explicaba un calendario distinto del que se veía. Ahora la celda lleva
   una mezcla más cargada **más un borde del color del estado** —el contorno es lo que separa
   una celda de la de al lado— y la muestra de la leyenda se pinta con las mismas reglas.
   El día de hoy pasó a tener anillo, para que se encuentre esté del color que esté.
3. **El selector de lista y calendario era invisible.** `.selvista` pintaba la pista con
   `--field` y el segmento activo con `--surface`, que en tema claro son **el mismo blanco**
   (`#FFFFFF`) y en oscuro dos verdes casi iguales; lo único que los separaba era una sombra al
   10 %. Ahora la pista va en `--surface-2` y el segmento elegido en verde sólido, que es el
   relleno que el resto de la maqueta ya usa para «esto está seleccionado». Afecta también al
   selector de QR/código de *Canjear invitación*, que tenía el mismo problema.

Con el ciclo completo cargado, la **lista** habría pasado a mostrar once tarjetas, nueve de
ellas «todavía no se abre». Como la lista contesta *qué pago ahora*, muestra entera la parte
exigible, asoma las **dos** próximas y cierra con una línea que dice cuántas quedan y manda al
calendario, que es donde vive la pregunta *cómo vengo*.

> [!note] Por qué el descuento de comisión se ve chico en la demo
> Con una bolsa de Bs 3.000 la comisión da Bs 9, así que manda el **piso de Bs 10**, y el
> 25 % de descuento son Bs 2,50. La pantalla lo dice con todas las letras y agrega una tabla
> con bolsas de Bs 10.000 y Bs 20.000, donde sí se nota. Inflar la bolsa de la demo para que
> el número luciera mejor sería mentir sobre el tarifario.

### Los datos que estas pantallas muestran no son de ejemplo

A diferencia del resto de la maqueta, estas siete traen cifras que salen del proyecto:

| Qué se muestra | De dónde sale |
| --- | --- |
| Los 14 requisitos de habilitación, en 4 niveles | `seeders/minimos/17-organizador-y-emparejamiento.json` |
| Las 6 etapas de cobranza con sus topes de contacto | `seeders/minimos/18-sanciones-y-cobranza.json` |
| Descargo de 5 días hábiles, apelación 5, prescripción 365 | `politica_sancion` v1, mismo seeder |
| Plazo de reclamo de 5 días hábiles, prórroga a 10 | [[Cumplimiento]] §1.3 · RNSF Libro 4 Título I |
| La comisión sobre la que se calcula el costo de un bono | `seeders/minimos/04-tarifario.json` · `COM_ENTREGA` |

### Preparada para una audiencia mixta

La maqueta se presenta a financieros, jefaturas de TI, personal técnico y directorio a la vez, y
cada uno pregunta otra cosa. Por eso trae, además del recorrido:

- **Cinco pantallas que responden preguntas de negocio** que el recorrido no cubría: *Ingresos y
  tarifario* (de dónde sale la plata de la empresa), *Libro contable* (partida doble y cierre),
  *Fondo de garantía* (qué pasa si varios no pagan a la vez), *Arquitectura* (qué pasa si se cae
  un servicio, y **en qué estado real está el proyecto**) y *Licencia ASFI* (si se puede operar).
- **Un panel de 24 preguntas anticipadas**, agrupadas por audiencia, cada una con la respuesta
  corta y **dónde mostrarla en la demo**. Está debajo del banco de aparatos.

> [!important] La pantalla de Arquitectura dice la verdad incómoda a propósito
> Declara que el modelo, los casos de uso, las restricciones y la matriz normativa están
> completos, y que **el código de los catorce servicios todavía no existe**. Una maqueta
> presentada como producto terminado se descubre en la primera pregunta técnica, y ahí se pierde
> la credibilidad de todo lo demás — incluido lo que sí está hecho.

### Cada pantalla del backoffice dice para qué sirve

Arriba de cada una de las quince hay una banda **«Para qué sirve»** con una frase de negocio, no
técnica: qué problema resuelve y qué pasa si no existe. Es lo que permite que un gerente recorra
el backoffice sin que nadie le traduzca, y es también una prueba de diseño — una pantalla cuyo
propósito no se puede escribir en una frase no debería existir. Quedó como regla §0.6 de
[[Flujo de pantallas · backoffice administrador]].

### El saldo se mueve de verdad

La maqueta lleva estado: recargar **sube** el saldo, pagar el aporte y retirar lo **bajan**, y el
movimiento aparece en el extracto. Pero lo hace como lo va a hacer la app real: después de cada
operación con efecto se llama otra vez a `GET /billetera/saldo` y se **relee**, en vez de sumar o
restar el monto en memoria (regla §0.2b de [[Flujo de pantallas · app del participante]]). La
consola de red lo deja ver: la relectura aparece como una llamada propia.

Tres detalles que conviene señalar en la presentación:

- **El QR no acredita nada por sí solo.** Mientras está en pantalla, con su orden y su cuenta
  regresiva, el saldo **no cambia**. Cambia cuando el proveedor confirma. Acreditar antes sería
  prestarle plata al usuario.
- **Retirar pide monto y cuenta de destino**, y si no hay cuenta se agrega ahí mismo. Pero la
  cuenta recién agregada **no cobra**: queda en verificación y enfriamiento, y el intento se
  rechaza con `AP-CU11-03`. Es la demora que corta el fraude de toma de cuenta.
- **Recargar es solo por QR.** No hay opción de efectivo: la propuesta de valor es evitar
  complicaciones, y «andá a un punto con billetes» es la complicación que el producto viene a
  sacar. Ojo con el alcance: los casos de uso de puntos de atención y arqueo **siguen en la
  bóveda**; sacar el efectivo del producto entero es un ADR, no un cambio de pantalla.
- **Notificación de dinero recibido.** El botón «Recibir transferencia» de la barra dispara la
  notificación emergente de Android con el monto en el título, agrega el movimiento y **relee**
  el saldo. El aviso trae el hecho, no la cifra: un saldo pintado desde el cuerpo de un push
  sería un saldo inventado.
- **El tarifario de la maqueta es el sembrado, y corrige un error mío anterior.** Aportar, cargar
  saldo, retirar y transferir **no cuestan nada**; el único cobro es la comisión por cobrar el
  turno: 0,3 % de la bolsa, mínimo Bs 10, tope Bs 50, con IVA e IT incluidos. La versión previa
  de la maqueta cobraba una comisión por aporte que el catálogo no tiene.
- **El extracto en PDF** sale con folio y hash, y no se emite si no cuadra contra el saldo diario
  sellado (`AP-CU15-01`, `AP-CU15-02`). La descarga termina en un snackbar.

**Backoffice** — quince pantallas, agrupadas como las verá el operador:

| Grupo | Pantalla | Ruta | Caso de uso | Endpoint · servicio |
| --- | --- | --- | --- | --- |
| Acceso | Ingreso | `acceso/ingreso` | [[CU-04 Autenticar con MFA y registrar dispositivo]] | `POST /sesiones` (paso 1) · identidad |
| Acceso | Segundo factor | `acceso/desafio` | [[CU-04 Autenticar con MFA y registrar dispositivo]] | `POST /sesiones` (paso 2) · identidad |
| Operación | Tablero | `operacion/tablero` | [[CU-98 Publicar el tablero de indicadores]] | `GET /indicadores`, `GET /auditoria/estado` · auditoria |
| Operación | Conciliación | `operacion/conciliacion` | [[CU-50 Conciliar la custodia y verificar el encaje]] | `GET /conciliacion`, `POST /conciliacion/{id}/excepcion` · aportes |
| Operación | Cierre diario | `operacion/cierre-diario` | [[CU-51 Ejecutar el cierre diario]] | `GET /custodia/cierre-diario` · nucleo-financiero |
| Operación | Desembolsos | `operacion/desembolsos` | [[CU-28 Emitir la orden de desembolso y ejecutar el intento]] | `POST /desembolsos/{id}/ejecutar` · entregas |
| Operación | Reclamos | `operacion/reclamos` | [[CU-52 Atender un reclamo en plazo]] · [[CU-53 Elevar un reclamo a segunda instancia]] | `GET /reclamos` · cumplimiento |
| Cobranza | Cartera en mora | `operacion/mora` | [[CU-27 Restringir al deudor e incluirlo en la lista interna]] | `GET /cobranza/cartera` · garantia |
| Cobranza | Incumplimientos | `operacion/incumplimientos` | [[CU-25 Declarar el incumplimiento con descargo y evidencia]] · [[CU-23 Cubrir un incumplimiento con el fondo]] · [[CU-26 Ejecutar el aval y subrogar la deuda]] | `GET /incumplimientos/{id}`, `POST /garantia/coberturas`, `POST /cobranza/subrogaciones` · garantia |
| Cumplimiento | Verificaciones | `cumplimiento/verificaciones` | [[CU-01 Registro y apertura de billetera]] · [[CU-02 Elevar nivel de debida diligencia]] | `GET /cumplimiento/verificaciones`, `POST /usuarios/{id}/verificacion/decision` · cumplimiento |
| Cumplimiento | Antifraude | `cumplimiento/antifraude` | evaluación previa a mover dinero | `GET /billetera/antifraude/evaluaciones` · nucleo-financiero |
| Cumplimiento | Alertas LFT | `cumplimiento/alertas` | [[CU-44 De alerta de monitoreo a reporte de operación sospechosa]] | `GET /cumplimiento/alertas`, `POST /cumplimiento/casos`, `POST /cumplimiento/casos/{id}/ros` · cumplimiento |
| Cumplimiento | UIF | `cumplimiento/uif` | [[CU-41 Detectar umbral y registrar formulario PCC-01]] · [[CU-43 Remitir los reportes mensuales a la UIF]] | `GET /uif/reportes` · cumplimiento |
| Riesgo | Riesgo y seguridad | `cumplimiento/riesgo` | [[CU-54 Registrar un evento de riesgo operativo]] · [[CU-55 Gestionar un incidente de seguridad]] | `GET /auditoria/eventos-riesgo` · auditoria |
| Plataforma | Accesos | `operacion/roles` | [[CU-08 Asignar y revocar roles de operador]] · [[ADR-038 Acceso administrativo · segundo factor y recuperación asistida]] | `GET /roles`, `POST /usuarios/{id}/roles` · identidad |

### Lo que muestra cada una que no se ve en un documento

- **Cartera en mora** — las **cuatro etapas de cobranza sembradas** (`PREVENTIVA`, `TEMPRANA`,
  `ADMINISTRATIVA`, `PREJUDICIAL`) con su rango de días, su canal y si la gestión es automática
  o humana. La etapa la fija el día de mora, no el criterio del operador.
- **Incumplimientos** — el expediente completo de quien no paga: evidencia inmutable con hash,
  historial de estados, el plazo de descargo **guardado al inicio**, y la sanción que sale de la
  **matriz** (tipo × severidad × reincidencia), no de la decisión de nadie. Con los botones de
  cobertura del fondo y subrogación.
- **Antifraude** — las **cinco reglas sembradas** (`RETIRO_INSTRUMENTO_NUEVO`,
  `DISPOSITIVO_NUEVO_MONTO_ALTO`, `VELOCIDAD_RETIROS`, `CAMBIO_CREDENCIAL_Y_RETIRO`,
  `GEO_INUSUAL`) con puntaje, decisión y latencia por evaluación.
- **Verificaciones** — la detección de **altas coordinadas**: doce cuentas en nueve minutos
  desde la misma IP con selfies que fallan el reto de vivacidad. La señal es automática; la
  decisión es de una persona y queda con su nombre y su causal.
- **Alertas LFT** — el camino alerta → caso → ROS con la **segregación en pantalla**: el
  analista propone y no ve el botón de reportar, porque `AP-CU44-02` exige que el revisor sea
  otra identidad.
- **Desembolsos** — las **ocho reglas de entrega** evaluándose antes de liberar el dinero, con
  `CONFIRMACION_DATOS` marcada como la única omitible.
- **Cierre diario** — bloqueado por el descuadre de la conciliación, sin rol que lo omita.
- **Accesos** — un operador con rol asignado que **no puede entrar** porque no enroló su TOTP, y
  la cola de restablecimientos que aprueba otra identidad.

El servicio dueño de cada ruta se resuelve con el mismo mapa que usa el proyecto
(`scripts/modelo.py` → `PREFIJOS`), y la consola lo muestra en cada llamada.

## Reproducir jornada

El botón **▶ Reproducir jornada** de la barra superior dispara veinte llamadas seguidas que
simulan un día de tráfico: aportes cobrados, una recarga por QR, un pago que compensa por saldo
insuficiente, recordatorios de cobranza, un retiro rechazado por antifraude, dos altas del patrón
coordinado con su alerta, un intento de fuerza bruta que topa contra el límite por origen, una
entrega ejecutada, un incumplimiento nuevo, la cobertura del fondo, el cierre bloqueado y la
remisión mensual a la UIF. Los contadores de la barra llevan la cuenta de llamadas y rechazos.

Sirve para lo que un documento no puede: mostrar el sistema **en movimiento**, con su mezcla
real de éxitos y rechazos, mientras se explica cualquier otra pantalla.

## Los tres escenarios de red

El selector de la barra superior cambia cómo responde el simulador. Los tres existen para
mostrar decisiones de ingeniería que de otro modo no se ven:

| Escenario | Qué hace | Qué demuestra |
| --- | --- | --- |
| **Todo responde bien** | 200 con latencia de 200–500 ms | Los cuatro estados de cada pantalla |
| **Red intermitente** | La primera llamada con efecto corta con 503 y el cliente reintenta | La **clave de idempotencia**: un reintento no duplica el aporte |
| **El backend rechaza** | Devuelve el código del CU: `AP-CU01-03`, `AP-CU04-01`, `AP-CU04-04`, `AP-CU04-06`, `AP-CU21-03` | Que el error se **traduce** a lenguaje del usuario, no se muestra crudo |

El escenario de rechazo en el backoffice usa `AP-CU04-06` (`FACTOR_NO_ENROLADO`): es
`R-SEG-10` en acción — un operador sin TOTP confirmado no abre sesión
([[ADR-038 Acceso administrativo · segundo factor y recuperación asistida]]).

## Fidelidad al sistema de diseño

Los tokens son los de [Sistema-Diseno/estilos.css](Sistema-Diseno/estilos.css), **copiados,
no reinterpretados**: los mismos hex, las mismas escalas de espaciado y radio, Poppins para
display y cifras, Inter para interfaz. Se usan los roles de texto con contraste verificado
(`--text-3`, `--brand-texto`, `--accent-texto`, `--ok-texto`, `--aviso-texto`), no los tonos
de marca crudos, que no llegan a AA en texto chico.

> [!warning] Regla de sincronización
> La maqueta **duplica** los tokens en vez de importar `estilos.css`, porque tiene que
> funcionar como archivo único publicable. Si cambia un token en `estilos.css`, hay que
> cambiarlo acá también. Un hex que diverja convierte a la maqueta en una fuente de verdad
> falsa, que es peor que no tenerla.

Lo único que agrega es la familia monoespaciada de la consola de red (JetBrains Mono), que
llena el hueco de `--mono`; el sistema lo declara sin nombrar una fuente.

## Divergencias encontradas al construirla

[[Flujo de pantallas · app del participante]] §5.1 describe el canje de la invitación como
`POST /grupos/invitaciones/{codigo}/canje` con resultado «participante **con cupo**». Pero
[[CU-68 Postular a un grupo y ser emparejado]] §4 dice que *el organizador revisa y acepta o
rechaza*, y [[Restricciones]] `ck_solicitud_ingreso_resuelta` impide cerrar una solicitud sin
`revisada_por` ni `fecha_resolucion`. Por la precedencia de
[[Contrato de implementación para IA]] §1 **gana el caso de uso**: la maqueta canjea el token,
abre la solicitud y espera. **El documento de pantallas queda anotado para corregir §5.1**, y
le falta además la pantalla del lado del organizador.

## Divergencia de rutas de sesión

[[Flujo de pantallas · app del participante]] §2.5 y §2.6 dicen `POST /sesion` y
`POST /sesion/mfa`; [[CU-04 Autenticar con MFA y registrar dispositivo]] dice `POST /sesiones`
para los dos pasos, y el backoffice también. Por la precedencia de
[[Contrato de implementación para IA]] §1 **gana el caso de uso**, así que la maqueta usa
`POST /sesiones`. Queda anotado para corregir el documento de pantallas.

## Qué hace con esto la Fase F0.0

La maqueta **no es un borrador que se tira**: es el criterio de aceptación visual de la
primera fase del frontend (`planes/11` §F0.0). Cuando se levante el monorepo, cada pantalla
de `apps/movil` y `apps/backoffice` se compara contra la suya acá, y los manejadores de MSW
reproducen exactamente estas rutas, estos códigos de error y estos tres escenarios.

## Ver también

[[Flujo de pantallas · app del participante]] · [[Flujo de pantallas · backoffice administrador]] ·
[[Flujo funcional · recorrido del usuario]] · [[Flujo funcional · usuario administrador]] ·
[[ADR-004 Frontend]] · [[ADR-038 Acceso administrativo · segundo factor y recuperación asistida]] ·
[[Seguridad]] · `planes/10 · Plan maestro del frontend` · `planes/11 · Fases F0 y F1` ·
`disenar-frontend`
