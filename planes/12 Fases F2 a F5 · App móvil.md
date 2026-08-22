---
tags:
  - plan
  - fase
  - frontend
titulo: "Fases F2 a F5 — App móvil (Expo)"
fases: [F2, F3, F4, F5]
depende_de: [F0, F1]
habilita: [F12]
---

# Fases F2 a F5 — App móvil

> **Se ejecuta en:** Ola F1 · carril M (F2) y Ola F2–F3 · carriles M1, M2, M3 (F3, F4,
> F5). Ver [[16 Carriles de frontend]].

> [!important] Antes de escribir la primera línea
> [[10b Estándar de ejecución del frontend]] aplica en las cuatro fases. **Cada
> pantalla sale de la sección «Interfaz» de su caso de uso** — no se inventa.

> [!tip] La maqueta manda sobre el cómo
> [[20 Maqueta de referencia · deltas del frontend]] fija la referencia visual y de
> comportamiento de estas cuatro fases. Los deltas **D-1** (alta de ocho pasos),
> **D-4** (aportes pendientes), **D-5** (sorteo como evento), **D-6** (perfil público
> e insignias) y **D-7** (publicidad) ya están aplicados abajo.

**Contexto real de uso, que manda sobre todo lo demás:** Android de gama baja, datos
móviles intermitentes, en la calle, con una persona que quizá nunca usó una billetera
digital. Si la abuela no lo entiende, se rehace.

---

# FASE F2 — Shell móvil

> **Objetivo.** Que la app tenga navegación, sesión, tema, biometría, almacenamiento
> seguro y los estados obligatorios funcionando — para que F3, F4 y F5 solo agreguen
> pantallas.

## Alcance

| Pieza | Qué resuelve |
| --- | --- |
| **Expo Router** con tab bar de 3–5 destinos | Enrutamiento por archivos: cada carril agrega pantallas sin tocar un registro común |
| `ProveedorSesion` | Token en `expo-secure-store`, refresco rotado (`R-SEG-09`), cierre de sesión, expiración |
| `ProveedorTema` | Claro/oscuro + preferencia del sistema |
| `usarBiometria()` | `expo-local-authentication` para confirmar operaciones de dinero |
| `usarDispositivo()` | Identificador de dispositivo de confianza (CU-04) |
| `ProveedorConexion` | Estado de red; bloquea operaciones cuando no hay |
| `LimiteDeError` | Captura, muestra en voz de marca y ofrece reintento |
| `usarIdempotencia()` | Genera la clave al abrir un formulario y **la reenvía igual** en el reintento |
| EAS Update | Canal por entorno; correcciones sin pasar por tienda |

## Las tres reglas del shell

1. **Sin conexión, la app muestra el último estado y no deja operar.** No se encola una
   operación de dinero para «cuando vuelva» — eso duplica aportes.
2. **La biometría confirma, no autentica.** Autentica el servidor; la huella solo
   desbloquea el envío.
3. **Nada sensible en `AsyncStorage`.** Token, PIN y datos personales van a
   `expo-secure-store`. Las vistas con saldo bloquean captura de pantalla.

## Gate de salida F2

- [ ] Gate común de §10 del plan maestro del frontend
- [ ] Sesión expirada ⇒ vuelve a login **sin perder el formulario en curso**
- [ ] Toda notificación queda en la bandeja aunque el push no llegue, y varias
      seguidas se encolan en vez de pisarse
- [ ] **Ninguna pantalla ni evento de LGI/FT produce aviso** para el investigado
- [ ] Modo avión ⇒ último estado visible y botones de dinero deshabilitados con motivo
- [ ] Una pantalla nueva se registra **solo creando el archivo**
- [ ] Token y PIN **no** aparecen en ningún log ni traza (revisado con caso real)

---

# FASE F3 — Móvil · identidad y cuenta

**Casos de uso:** CU-01, 02, 03, 04, 05, 06, 07, 09, 40, 46

| CU | Pantalla | Lo que el CU exige |
| :-: | --- | --- |
| 01 | **Alta guiada en ocho pasos** con captura de documento y prueba de vida (D-1) | Cotejo campo a campo de lo declarado contra lo leído, y al terminar **los topes concretos que le corresponden** |
| 02 | *Aumentá tu límite* | Muestra **qué desbloquea cada nivel antes** de pedir papeles |
| 03 | Declaración PEP | Las cinco categorías **en lenguaje llano, no en jerga normativa** |
| 04 | Ingreso con teléfono y PIN o biometría | Dispositivo nuevo **siempre** pide segundo factor · el código tiene **tope de 3 intentos y bloqueo por hora**, visible en pantalla |
| 05 | Contrato a pantalla completa | Resumen de comisiones arriba, **con impuestos incluidos** |
| 06 | Aviso de actualización de datos | **Qué falta y por qué se pide** |
| 07 | *Mis datos*: descargar, corregir u oponerse | **Con el plazo de respuesta a la vista** |
| 09 | *Cuenta → Seguridad*: cambio de clave | Medidor de fortaleza; baja con impedimentos listados |
| 40 | Antes de operar | **Cuánto queda del límite del mes** |
| 46 | Servicio no habilitado | **Lo explica sin jerga** |

## Lo que define esta fase

- **CU-01 es la primera impresión del producto, y son ocho pasos.** Datos, celular
  verificado, anverso, reverso, prueba de vida, **cotejo de lo declarado contra lo
  leído**, perfil del cliente con origen de fondos, y contrato con los tres
  consentimientos separados. Al final, los límites concretos — no un «bienvenido»
  vacío. El desglose completo está en [[20 Maqueta de referencia · deltas del frontend]].
- **El paso de cotejo evita el rechazo más común**, que es un dato mal tipeado. Sin él,
  la persona se entera al día siguiente de que su alta no pasó.
- **El perfil del cliente no es burocracia:** es contra lo que después compara el
  monitoreo. Un alta sin origen de fondos declarado deja al monitoreo sin referencia.
- **CU-03 en lenguaje llano** es un requisito escrito, no una sugerencia de estilo:
  una declaración PEP que la persona no entiende es una declaración inválida.
- **CU-46**: mientras la licencia esté `EN_TRAMITE` la app tiene que explicar, sin
  jerga, qué no se puede hacer todavía. Esa pantalla se usa desde el día uno.

## Gate de salida F3

- [ ] Gate común
- [ ] `bienvenida → tour → registro`: el tour se puede saltar y no se repite
- [ ] Al terminar el alta se acredita el **bono de bienvenida** y la app abre en el
      estado de cuenta nueva, no en el de alguien con historial
- [ ] Cada lista vacía dice **qué hacer**, no «no hay resultados»
- [ ] Alta completa de **ocho pasos** con cámara, probada en Android de gama baja
- [ ] El cotejo campo a campo se ve y **la diferencia se puede corregir** antes de seguir
- [ ] Prueba de vida bajo el umbral ⇒ reintento con motivo, y a los 3 intentos pasa a
      revisión asistida (probado)
- [ ] Código de verificación: tope de intentos y bloqueo por hora, con el mensaje que
      dice **cuántos quedan**
- [ ] Los tres consentimientos se guardan **por separado**; el de publicidad se puede
      rechazar sin perder el alta
- [ ] Dispositivo nuevo ⇒ segundo factor, siempre (probado)
- [ ] El contrato muestra comisiones **con impuestos** antes de aceptar
- [ ] Plazo de respuesta visible en *Mis datos* (CU-07)
- [ ] Servicio no habilitado ⇒ explicación sin jerga, no un error

---

# FASE F4 — Móvil · billetera

**Casos de uso:** CU-10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 30, 31, 32, 33, 57

Es la fase donde la app toca dinero. **Todos los invariantes de dinero se verifican
acá.**

| CU | Pantalla | Lo que el CU exige |
| :-: | --- | --- |
| 10 | *Cargar saldo*: monto, medio, **QR a pantalla completa con cuenta regresiva** | El QR vence a la vista |
| 11 | *Retirar*: destino, monto, **costo y neto antes de confirmar** con biometría | El neto en grande |
| 12 | Enviar | **Nombre y foto del destinatario antes de aceptar** |
| 13 | Saldo retenido **separado**, con motivo y hasta cuándo | No se mezcla con disponible |
| 14 | Extracto con **cabecera de período** (saldo inicial → saldo de hoy, entró y salió), cinco filtros y lista **agrupada por día con un solo saldo por día** | Muestra **el movimiento y su corrección**, nunca solo el resultado. El ícono dice **qué pasó**, no si el número sube o baja |
| 15 | *Movimientos → Descargar* | Extracto por mes y certificado con folio |
| 16 | *Cerrar mi cuenta* | **Lista de lo que falta**, no un «no» genérico |
| 17 | Monto no disponible | **Número de oficio y a quién consultar** |
| 18 | *Cobros → Mis cuentas* | Número **enmascarado**, banco, estado de verificación |
| 19 | Detalle del pago | El reembolso **como movimiento**, con su motivo |
| 30 | Antes de confirmar | Costo **con impuestos incluidos y su desglose** |
| 31 | Detalle | La comisión como **línea con nombre**, nunca descuento anónimo |
| 32 | Detalle de la operación | La factura queda disponible |
| 33 | Extracto | El abono con **el motivo escrito** |
| 57 | Mapa/lista de puntos | Dónde recargar o retirar en efectivo |

## Las cinco reglas de esta fase

1. **El cliente no calcula ni un centavo.** Muestra lo que el servidor devolvió.
2. **Costo total con impuestos, antes de confirmar.** `R-CON-07`, CU-30.
3. **Doble envío bloqueado** en las cinco operaciones de dinero, con la misma clave de
   idempotencia en el reintento.
4. **El extracto muestra la corrección, no el resultado limpio.** Un reverso se ve
   como movimiento nuevo — es `R-AUD-06` hecho interfaz.
5. **El número de cuenta siempre enmascarado**, en pantalla y en cualquier traza.
6. **No existe un botón «pagar mi aporte»** (D-4). Una persona está en varios grupos:
   la tarjeta de saldo lleva a *aportes pendientes*, filtrable por grupo y por fecha,
   y el pago de una cuota es el detalle de esa cuota.
7. **La publicidad va rotulada, no se cierra y vive en una sola pantalla** (D-7): la
   portada, al pie. Un espacio que el usuario apaga no se le puede vender a nadie: lo
   que se apaga es la **segmentación**, sin que cambie ninguna condición de la cuenta.
   A cambio, el formato es único y lo impone la plataforma —dos líneas y una
   ilustración en marco fijo, sin animación y sin urgencia inventada—, con revisión
   previa de cada campaña.

## Gate de salida F4

- [ ] Gate común
- [ ] **Prueba de doble envío en las cinco operaciones de dinero** (recarga, retiro,
      transferencia, aporte, cierre)
- [ ] Ningún `toFixed`, `Intl.NumberFormat` ni aritmética sobre importes (lint)
- [ ] El costo con impuestos aparece **antes** del botón de confirmar, en las tres
      operaciones con comisión
- [ ] Lista de movimientos virtualizada y paginada, probada con 5 000 filas en gama baja
- [ ] Movimientos con cabecera de período y **un saldo por día**, no uno por línea: en
      una lista de celular el saldo corrido en cada fila es ruido, no información
- [ ] **Ninguna barra de filtros se desliza en horizontal**: los chips se acomodan en
      varias líneas y se ve todo lo que hay. Un carrusel de filtros esconde opciones
      detrás de un gesto que nadie descubre
- [ ] **Cada filtro lleva su ícono**, y no hay filtro «Otros»: una categoría que se
      llama «otros» es una que no se pensó
- [ ] *Mis aportes* filtra por **una sola cosa**: a tiempo o en mora. El historial de
      lo pagado vive en Movimientos, que es donde se busca un comprobante
- [ ] *Mis aportes* tiene **dos vistas de las mismas cuotas** (D-12): la lista contesta
      qué pagar ahora y el calendario contesta cómo viene la persona. **Lo pagado
      aparece en verde en el calendario y no en la lista**, y eso no contradice la regla
      de arriba: el calendario no filtra, ubica en el tiempo
- [ ] En el calendario, una cuota de un período **que todavía no se abrió** se ve
      punteada, se dice su importe aparte y **no suma** a ninguna de las tres cifras del mes
- [ ] Un día con cuotas de **dos grupos** las muestra las dos, con un punto por cuota; el
      color del día es el del estado más urgente
- [ ] El total a pagar cuenta **solo lo exigible**: una cuota de un período que
      todavía no se abrió no se debe, y sumarla infla la deuda
- [ ] La portada abre contestando tres cosas sin desplazar: cuánto tengo, qué tengo
      que hacer hoy y cómo van mis grupos
- [ ] Y el **titular** de esas tres es *cómo voy*: racha de meses al día, riel del
      turno sorteado y grupo en formación. El saldo va arriba en una línea y su
      tarjeta completa —custodia, retenido, puesto en pasanakus— más abajo
- [ ] **Si el número no es un hecho, no va**: no hay puntos por abrir la app, ni
      niveles inventados, ni recompensa variable, ni urgencia fabricada
- [ ] **La mora no se gamifica**: la racha rota se dice sin regañar y con la salida al
      lado, y **no hay tabla de posiciones** entre participantes
- [ ] Ningún elemento de juego **empuja a poner más plata**: el progreso se gana
      cumpliendo lo firmado, no aportando de más
- [ ] *Aportes pendientes* filtra por grupo y por fecha, y muestra el **recargo por
      mora desglosado** cuando lo hay
- [ ] El banner de publicidad aparece **solo en la portada**, está rotulado, **no tiene
      cierre**, va al pie y respeta el formato único (título ≤ 46, detalle ≤ 60,
      ilustración sin texto adentro, sin animación)
- [ ] Apagar la segmentación **no cambia** comisiones, límites ni acceso a grupos
- [ ] Retiro sin conexión ⇒ bloqueado con motivo, **nunca encolado**
- [ ] Número de cuenta enmascarado en pantalla, en logs y en capturas

---

# FASE F5 — Móvil · pasanaku y comunidad

**Casos de uso:** CU-20, 21, 22, 23, 25, 26, 27, 28, 29, 52, 53, 55, 59, 60, 61, 62,
63, 64, 65, 68, 69, 70, 71, 74, 75, 76

Es el producto propiamente dicho: el grupo.

| Grupo | CU | Pantallas |
| --- | --- | --- |
| **Crear y entrar** | 20, 68, 69 | Asistente que **muestra el costo total del ciclo antes de confirmar** · postulación · **canje de invitación**: código de un solo uso, ficha del grupo, compromiso del ciclo en números y reglamento entero antes de la casilla de confirmación (D-10) |
| **El ciclo del dinero** | 21, 22 | ***Mi aporte***: monto, fecha límite y **un** botón · ***Cobrar mi turno***: bolsa, cada deducción con su motivo, **el neto en grande** |
| **Turnos** | 59, 60, 61, 62 | *Tu turno* con **cuenta regresiva al revelado** · **el sorteo como evento guardado** (D-5): compromiso, semilla externa, ejecución y notificación, cada uno con su hash, reproducible en pantalla contra el hash guardado · proponer permuta · fecha corrida por feriado **explicada** |
| **Decisiones** | 63, 64, 65 | *Grupo → Decisiones*: tarjeta por acuerdo con propuesta, plazo y quórum · traspaso · retiro con su liquidación |
| **Cuando algo sale mal** | 23, 25, 26, 27, 28, 29 | Aviso de incumplimiento con **qué se le imputa y hasta cuándo puede responder** · *Mis avales* con tope consumido · aviso de restricción **persistente pero no bloqueante**, con motivo y monto · *Mi entrega*: línea de tiempo real · *Fondo de garantía*: lo que puso y lo que se consumió |
| **Reclamos** | 52, 53, 55 | *Ayuda → Reclamo*: **código, plazo y estado siempre a la vista** · elevación a segunda instancia · aviso de incidente que lo afecta |
| **Reputación** | 70, 71, 74, 75, 76 | Puntaje **con su desglose**, insignias **con pantalla propia** (qué mide, cómo se gana, para qué sirve, cuánta gente la tiene), **perfil público de terceros** desde el orden de turnos (D-6), certificado compartible, reseñas |

## Las cuatro reglas de esta fase

1. **El debido proceso se ve.** CU-25: la persona ve qué se le imputa, con qué
   evidencia y **hasta cuándo puede responder**, con el plazo guardado — no uno
   recalculado en el cliente.
2. **La restricción explica, no castiga en silencio.** CU-27: aviso persistente **no
   bloqueante**, con motivo, monto y cómo salir.
3. **La reputación es explicable.** CU-71: nunca un número solo. Siempre su desglose,
   porque un puntaje que no se puede discutir no se puede usar.
4. **La transparencia es una función, no un eslogan.** CU-61: el sorteo se ve entero
   —compromiso previo, semilla de fuente externa tomada después, ejecución y hash— y
   se reproduce en pantalla. Un veredicto de una línea no es verificable.
5. **Del perfil de otro se muestra su comportamiento, nada más.** Puntaje, nivel,
   ciclos, aportes a tiempo e insignias. Ni documento, ni teléfono, ni saldo. Y a
   quien está en mora dentro de su plazo **no se le llama deudor**: todavía no hay
   incumplimiento declarado.

## Gate de salida F5

- [ ] Gate común
- [ ] *Mi aporte* funciona con saldo en **un** toque, con doble envío bloqueado
- [ ] *Cobrar mi turno* muestra **cada deducción con su motivo** y el neto destacado
- [ ] El plazo de descargo que se muestra es **el guardado por el servidor**, no
      recalculado en el cliente
- [ ] El puntaje de reputación **nunca** se muestra sin su desglose
- [ ] *Verificar* el sorteo **reproduce el orden con la semilla guardada**, compara el
      hash y muestra los cinco pasos del evento con el suyo
- [ ] El perfil público de un tercero **no expone** documento, teléfono ni saldo
- [ ] Canjear una invitación muestra **el total del ciclo**, no solo la cuota, y exige
      confirmación explícita con el compromiso escrito en números
- [ ] Código inexistente y código vencido dan **respuestas distintas**, y la del
      inexistente no revela si hay un grupo detrás
- [ ] El token se marca canjeado en la **misma transacción** que ocupa el cupo
- [ ] La invitación se canjea **por QR o por código escrito**, y las dos entradas
      llegan a la misma pantalla de confirmación: cambia cómo llegó el token, no qué
      se valida ni qué se muestra antes de aceptar
- [ ] El **código corto siempre existe** aunque haya QR: se dicta por teléfono y se
      escribe a mano, y hay gente que no puede escanear
- [ ] El **enlace abierto de invitación lo emite solo quien organiza el grupo**
      (`AP-CU69-07`), y el token queda ligado a su emisor. Un participante puede
      invitar a un conocido por su teléfono; lo que no puede es emitir un enlace que
      entra cualquiera
- [ ] La tarjeta de cada grupo dice **el rol en ese grupo**: la misma persona organiza
      uno y participa de otro, y por eso el botón de invitar está en unas tarjetas y
      en otras no
- [ ] Un grupo **en formación no muestra orden de turnos** ni botón de verificar el
      sorteo: todavía no se sorteó, y lo que le falta son personas
- [ ] Cada insignia abre su pantalla con qué mide y cómo se gana
- [ ] Las 26 pantallas tienen sus cuatro estados

## Ver también

[[00c Recetario · implementar un caso de uso]] · [[16 Carriles de frontend]] · [[10 Plan maestro del frontend]] · [[10b Estándar de ejecución del frontend]] · [[13 Fases F6 a F8 · Backoffice]] · [[_CasosDeUso]]
