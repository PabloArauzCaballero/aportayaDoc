---
tags:
  - plan
  - informe
  - carril
titulo: "Carril 1B — 03 Aportes, Pagos QR y Conciliación (libro contable)"
ola: 1
fase: 5
modulo: 03_aportes_pagos_qr
rama: pablo/feature/carril-1B-nucleo-financiero
estado: terminado
---

# Carril 1B — libro contable de `nucleo-financiero`

**Fase** 5 · **Casos de uso** CU-24 · **Puesto** P2 · **Máquina** Ubuntu

> Este archivo lo escribe **solo este carril**. Ningún otro lo toca: es lo que evita
> el conflicto en `informe.md` con cinco máquinas trabajando a la vez.

El libro contable es el primero de los dos tramos de `nucleo-financiero`. El segundo
—billetera y custodia, CU-10–17, 50, 57— es el carril **2A**, y no puede correr a la
vez que este: es la única dependencia serializada que introduce la fusión de módulos
(`07 Carriles de trabajo concurrente` §3).

## Casos de uso

| CU | Contrato | Dominio | Infra | Aplicación | HTTP | Pruebas | Gate |
| :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
| CU-24 | ✅ | ✅ | ✅ | ✅ | n/a | ✅ | ✅ |

**CU-24 no tiene página HTTP y eso no es una omisión:** su propia tabla de
descomposición dice «Página — Sin endpoint: lo dispara el planificador o un evento».
Los códigos de error viven igual en `openapi/nucleo-financiero.yaml`, porque
`verificar_criterios.py` los busca ahí exista o no una ruta.

## Piezas declaradas por nivel

| Pieza | Nivel | CU | Estado |
| --- | --- | :-: | :-: |
| `OrigenAsiento` | Átomo | 24 | ✅ |
| `Partida` | Átomo | 24 | ✅ |
| `CuadrarPartidas` | Átomo | 24 | ✅ |
| `CuentaContableRepositorio` | Molécula | 24 | ✅ |
| `AsientoRepositorio` | Molécula | 24 | ✅ |
| `CU24RegistrarAsiento` | Organismo | 24 | ✅ |

## Fronteras transaccionales respondidas

### CU-24

1. **Todo junto o nada:** el `asiento_contable`, sus `movimiento_contable`, el saldo
   de cada `cuenta_contable` tocada —que lo sincroniza el trigger de R-CTB-09, dentro
   de esta misma transacción— y el evento de outbox. Y —esto es lo que define
   el caso— **junto con el hecho económico que lo origina**, que vive fuera de esta
   clase. Por eso `ejecutar` recibe el `DSLContext` en curso y **no** abre su propia
   transacción ni su propio `conContexto`: si los abriera, el débito y su asiento
   podrían confirmar por separado, que es exactamente lo que el invariante 12
   prohíbe.
2. **Fuera del commit:** nada. El evento `asiento_registrado` se escribe al outbox
   dentro de la misma transacción; publicarlo lo hace el relevo, después del commit.
3. **Clave de idempotencia:** **no la tiene, y es correcto.** La idempotencia de una
   operación de dinero vive en `transaccion_billetera.clave_idempotencia` (`R-BIL`),
   que es del carril 2A. CU-24 no es un borde: no lo invoca un cliente que pueda
   reintentar, lo invoca un organismo que ya está dentro de una transacción. Si esa
   transacción revierte, el asiento no existió.
4. **Qué se bloquea:** la fila de cada `cuenta_contable` tocada, y **la toma la base,
   no este código**: `fn_ctb_recalcular_saldo` (R-CTB-09) hace `SELECT … FOR UPDATE`
   *antes* de leer el libro, igual que su equivalente de billetera. Dos asientos
   simultáneos sobre la misma cuenta se serializan ahí, y el segundo recalcula sobre
   el mayor ya completo. Está probado en `CU24Test.concurrencia`.
5. **Si el proceso muere tras el commit:** no se pierde nada. El asiento está
   escrito y el evento quedó en el outbox; el relevo lo publica cuando el proceso
   vuelva.
6. **Cruza servicios:** no. Todo ocurre dentro de `nucleo_financiero`.

## Supuestos declarados

Regla cero: ninguno silencioso.

De los cuatro supuestos con los que cerró la primera vuelta, **tres se resolvieron**
en vez de quedar declarados. Queda uno, y está bloqueado por un módulo que no existe.

| # | Supuesto | Estado |
| :-: | --- | --- |
| 1 | `periodo_contable_id` queda `NULL` | **Sigue abierto, y no se puede cerrar acá.** `periodo_contable` es del módulo 13 (ERP): la tabla no existe todavía. La propia base lo contempla —`fn_ctb_periodo_abierto` acepta explícitamente los asientos «anteriores a M13»— así que no es un agujero, es una fase que falta. `AsientoRepositorio.crear` ya está escrito para que el día que exista entre por ahí y el trigger pase a exigirlo abierto (R-CTB-01). **Lo cierra el carril 5A.** |
| 2 | `grupo_id` queda `NULL` | **Resuelto.** La columna existe y es FK anulable a `grupo`; lo que faltaba era dejar pasarlo. `EntradaAsiento` ahora lo lleva —con una forma corta para los orígenes que no pertenecen a ningún grupo, como comisión o ajuste— y la reversa hereda el del original |
| 3 | La reversa queda `estado = 'CONFIRMADO'` | **Resuelto por `R-AUD-11`.** Ver abajo |
| 4 | La glosa se trunca a 160 al copiarla a `movimiento_contable.descripcion` | **Resuelto: no era una deuda.** La glosa completa (hasta 200) vive siempre en `asiento_contable.glosa`; lo que se recorta es la copia por línea, no el original. Nada se pierde. Quedó escrito en el código, que es donde alguien se lo va a preguntar |

## Micro-PR abiertos al troncal

| Rama | Qué agrega | Estado |
| --- | --- | :-: |
| — (fue directo a `dev`) | `buildSrc/aportaya.jooq.gradle.kts`: el paquete Java del código generado no lleva el guion bajo del esquema | ✅ fusionado |

**Por qué era necesario.** `nucleo_financiero` es el único esquema con guion bajo de
los catorce. `generateJooq` armaba el paquete pegando el nombre del esquema tal cual
(`bo.aportaya.nucleo_financiero.generado`), distinto del paquete del servicio
(`bo.aportaya.nucleofinanciero`), y `ArquitecturaTest.ningunImportCruzado` lo leía
como un import a **otro** servicio: 44 violaciones del invariante 11 sobre código que
el propio build había generado. Ningún otro carril lo habría encontrado, porque
ningún otro esquema tiene guion bajo.

## Restricciones nuevas

Dos, y las dos nacen de una deuda que este carril prefirió pagar antes que declarar.

### `R-CTB-09` · el saldo contable se deriva del libro

`cuenta_billetera.saldo_*` lo mantiene el motor desde `R-BIL-16`.
`cuenta_contable.saldo` no tenía equivalente: lo escribía la aplicación, en la misma
transacción, y funcionaba. El problema no era la corrección de este código sino
**dónde vivía la garantía**: bastaba con que un caso de uso futuro insertara en
`movimiento_contable` y se olvidara del saldo para que el mayor dejara de reflejar la
posición, sin que nada lo impidiera. `frontera-transaccional` §3 es explícita — una
regla que protege valor contable vive en la base.

Ahora `fn_ctb_recalcular_saldo` lo deriva por trigger, con el signo tomado de la
**naturaleza** de la cuenta (deudora: suma el debe; acreedora: suma el haber) y el
bloqueo de fila tomado *antes* de leer, por el mismo motivo que
`fn_bil_recalcular_saldos`. `CuentaContableRepositorio` quedó de solo lectura: ya no
existe un método que pueda escribir el saldo.

### `R-AUD-11` · qué asiento lleva el estado `REVERSADO`

El `CHECK` de `estado` admitía `REVERSADO` y ningún caso de uso decía a cuál de los
dos asientos le tocaba. La lectura natural —marcar el original— resulta **imposible**:
`asiento_contable` es append-only, su estado no se puede cambiar después. De modo que
`REVERSADO` solo puede escribirse al insertar, y el único asiento que se inserta
sabiendo que corrige a otro es el inverso.

Se hace cumplir como equivalencia en las dos direcciones (`ck_asiento_reversado_enlazado`),
y de paso se corrigió algo más grave que estaba al lado: `tg_asiento_cuadrado` solo
disparaba sobre `CONFIRMADO`, así que **la corrección de un error contable era
justamente el único movimiento que podía descuadrar impunemente**. Ahora el cuadre se
le exige a los dos estados.

## Correcciones a la bóveda

| Qué | Por qué |
| --- | --- |
| `CU-24` citaba `R-BIL-11` en «Restricciones aplicables» | `R-BIL-11` vive entero en `conciliacion_custodia` y `orden_retiro` —tablas que CU-24 no toca— y `docs/Restricciones.md` lo asigna a **CU-50**. Se reemplazó por `R-CTB-02`, que sí actúa sobre `movimiento_contable` y ahora tiene su prueba de rechazo |
| `docs/Views/Maqueta-Crecimiento/README.md` enlazaba a `planes/21` como wikilink | La bóveda tiene su raíz en `docs/`, así que un `[[wikilink]]` a `planes/` no resuelve nunca. Va como ruta, misma convención que `18d659a` aplicó a `planes/20` |
| Cinco documentos citaban «140 restricciones» | Son 142 con las dos nuevas. Lo detectó `verificar_boveda.py`, que es exactamente para lo que existe ese gate |

## Bloqueos

Ninguno. El carril cerró completo.

**Lo que este carril deja listo para otros:** `CU24RegistrarAsiento` es el punto por
el que los carriles 2A (billetera), 3A (aportes) y 4A (entregas) escriben el libro
contable. La forma de su entrada y su salida ya no se negocia: está probada.

## Matriz de gates

| Área | Gate | Evidencia | Estado |
| --- | --- | --- | --- |
| Especificación | Criterios de aceptación cubiertos | `verificar_criterios.py` → «Sin divergencias entre la boveda y el codigo» | Pass |
| Datos | Restricciones citadas con prueba de rechazo | 6/6 — R-AUD-01, R-AUD-05, R-AUD-06, R-AUD-11, R-CTB-02 y R-CTB-09 | Pass |
| Dinero | Cuadre y asiento equilibrado | `CuadrarPartidasPropiedadTest`: 1000 asientos generados, todos cuadran | Pass |
| Seguridad | `verificar_seguridad.py` | TODO OK · 2 avisos (preexistentes, S-8 y S-9) | Pass |
| Seguridad | Prueba negativa de RLS | **No aplica todavía**: `asiento_contable` no tiene política de fila; el aislamiento es por esquema y lo cubre `AislamientoEsquemaTest` del troncal | n/a |
| Plazos | Vencimiento y aviso previo | No aplica: CU-24 no tiene plazo legal | n/a |
| Arquitectura | Piezas por nivel, sin saltos | `ArquitecturaTest` 5/5 — incluido `ningunImportCruzado` | Pass |
| Operación | Evento de outbox por caso de uso | `asiento_registrado` y `asiento_reversado`, dentro de la transacción | Pass |
| Rendimiento | Sin N+1 | La reversa leía la naturaleza por movimiento; ahora ni la lee: el saldo lo deriva el motor | Pass |
| Entrega | Lint, tipos, pruebas, build | `./gradlew build` + `integrationTest` → BUILD SUCCESSFUL | Pass |

## Gate de salida — evidencia

Ejecutado sobre PostgreSQL 16 real (Testcontainers) con el esquema de `sql/` aplicado
sobre volumen recién creado, en la máquina Ubuntu.

### Variante backend (carril Java · Gradle)

- [x] `./gradlew :servicios:nucleo-financiero:spotlessCheck` — BUILD SUCCESSFUL
- [x] `./gradlew :servicios:nucleo-financiero:build` — BUILD SUCCESSFUL
- [x] `./gradlew :servicios:nucleo-financiero:integrationTest` — BUILD SUCCESSFUL
- [x] **30 pruebas · 0 fallas · 0 saltadas**, repartidas así:

| Corredor | Clase | Pruebas |
| --- | --- | :-: |
| `test` | `ArquitecturaTest` | 5 |
| `test` | `CuadrarPartidasTest` | 6 |
| `test` | `CuadrarPartidasPropiedadTest` (1000 casos generados) | 1 |
| `testBarrido` | `BarridoTest` | 2 |
| `integrationTest` | `CU24Test` | 6 |
| `integrationTest` | `CU24RechazosTest` | 7 |
| `integrationTest` | `CU24SaldoDerivadoTest` | 3 |

- [x] Cada criterio de aceptación con su `@Test` + `@DisplayName` nombrado igual —
      lo verifica `verificar_criterios.py`, no una afirmación de este informe
- [x] Cada `R-XXX-nn` citado con prueba de rechazo — 6/6
- [ ] `sagaTest` — **no aplica**: CU-24 no cruza servicios
- [x] Prueba de humo sobre base nueva — **165 OK · 0 FALLA**
- [x] `verificar_boveda.py` → **TODO OK**
- [x] `verificar_seguridad.py` → **TODO OK** · 2 avisos preexistentes
- [x] `generar_k8s.py` → 34 archivos, reglas de ADR-037 verificadas

### Lo que no verifica ninguna máquina

- **¿Los nombres dicen lo que las cosas son?** `cuadrarPartidas`, `Partida`,
  `OrigenAsiento` salen del vocabulario contable y de la tabla de descomposición del
  propio CU. `Partida` es el término del dominio, no `Linea` ni `Movimiento` —
  `movimiento_contable` es la fila que queda escrita, la partida es lo que entra.
- **¿La frontera transaccional es la correcta, o solo pasa las pruebas?** Es la
  correcta y es la parte no obvia de este carril: la decisión de **no** abrir
  transacción es lo que hace que el invariante 12 se sostenga. Un `@Transactional`
  en `CU24RegistrarAsiento` habría pasado todas las pruebas de este carril y roto el
  cuadre en el primer cobro real de la Ola 3.
- **¿Qué supuse que no estaba en la bóveda?** Los cuatro de arriba, todos por
  escrito.
- **¿Qué dejé peor de como lo encontré?** Nada que sepa. Dejé mejor tres cosas: el
  generador de jOOQ (que rompía para este esquema), la cita equivocada de `R-BIL-11`
  en CU-24, y el wikilink roto de la maqueta que arrastraba `verificar_boveda.py`.

### Deuda declarada

**Ninguna abierta.** La que quedó de la primera vuelta —el saldo contable sin
trigger— se pagó: es `R-CTB-09`.

Lo único que este carril no puede cerrar es `periodo_contable_id`, y no es deuda sino
**dependencia**: la tabla pertenece al módulo 13 y no existe todavía. Está anotado
arriba, en el código y en el `AsientoRepositorio`, con el punto exacto por el que va a
entrar cuando el ERP se construya.

## Ver también

[[informe]] · [[07 Carriles de trabajo concurrente]] · [[00b Estándar de ejecución · código limpio, pruebas y calidad]] · [[CU-24 Registrar el asiento contable de una operación]]
