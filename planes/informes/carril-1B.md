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
   de cada `cuenta_contable` tocada y el evento de outbox. Y —esto es lo que define
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
4. **Qué se bloquea:** nada explícitamente. El `UPDATE … SET saldo = saldo + ?` de
   `cuenta_contable` toma el bloqueo de fila del propio motor, y por ser un
   incremento relativo —no un `SET saldo = <valor leído antes>`— dos asientos
   simultáneos sobre la misma cuenta no se pisan. Está probado en `CU24Test.concurrencia`.
5. **Si el proceso muere tras el commit:** no se pierde nada. El asiento está
   escrito y el evento quedó en el outbox; el relevo lo publica cuando el proceso
   vuelva.
6. **Cruza servicios:** no. Todo ocurre dentro de `nucleo_financiero`.

## Supuestos declarados

Regla cero: ninguno silencioso.

| # | Supuesto | Por qué | Qué lo cerraría |
| :-: | --- | --- | --- |
| 1 | `periodo_contable_id` queda `NULL` | La propia base lo documenta: `fn_ctb_periodo_abierto` acepta explícitamente los asientos «anteriores a M13». `periodo_contable` es del módulo 13 (ERP), que todavía no existe | El carril 5A, cuando construya el ERP |
| 2 | `grupo_id` queda `NULL` | CU-24 es genérico —lo disparan pago, entrega, comisión, ajuste—; el grupo lo conoce quien origina el hecho, no el asiento | Que un CU diga cuándo poblarlo |
| 3 | La reversa queda `estado = 'CONFIRMADO'`, no `'REVERSADO'` | Es el único valor sobre el que corre `tg_asiento_cuadrado`: una reversa con estado `REVERSADO` no se verificaría a sí misma y R-AUD-05 dejaría de aplicarle | Que un CU diga qué asiento lleva `REVERSADO` — probablemente el original, en un `UPDATE` que la tabla append-only no permite |
| 4 | La glosa se trunca a 160 caracteres al copiarla a `movimiento_contable.descripcion` | `asiento_contable.glosa` es `VARCHAR(200)` y `movimiento_contable.descripcion` es `VARCHAR(160)`: el modelo no dice qué hacer con la diferencia | Una decisión de bóveda, si alguna vez importa |

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

## Correcciones a la bóveda

| Qué | Por qué |
| --- | --- |
| `CU-24` citaba `R-BIL-11` en «Restricciones aplicables» | `R-BIL-11` vive entero en `conciliacion_custodia` y `orden_retiro` —tablas que CU-24 no toca— y `docs/Restricciones.md` lo asigna a **CU-50**. Se reemplazó por `R-CTB-02`, que sí actúa sobre `movimiento_contable` y ahora tiene su prueba de rechazo |
| `docs/Views/Maqueta-Crecimiento/README.md` enlazaba a `planes/21` como wikilink | La bóveda tiene su raíz en `docs/`, así que un `[[wikilink]]` a `planes/` no resuelve nunca. Va como ruta, misma convención que `18d659a` aplicó a `planes/20` |

## Bloqueos

Ninguno. El carril cerró completo.

**Lo que este carril deja listo para otros:** `CU24RegistrarAsiento` es el punto por
el que los carriles 2A (billetera), 3A (aportes) y 4A (entregas) escriben el libro
contable. La forma de su entrada y su salida ya no se negocia: está probada.

## Matriz de gates

| Área | Gate | Evidencia | Estado |
| --- | --- | --- | --- |
| Especificación | Criterios de aceptación cubiertos | `verificar_criterios.py` → «Sin divergencias entre la boveda y el codigo» | Pass |
| Datos | Restricciones citadas con prueba de rechazo | 4/4 — R-AUD-01, R-AUD-05, R-AUD-06, R-CTB-02 en `CU24RechazosTest` | Pass |
| Dinero | Cuadre y asiento equilibrado | `CuadrarPartidasPropiedadTest`: 1000 asientos generados, todos cuadran | Pass |
| Seguridad | `verificar_seguridad.py` | TODO OK · 2 avisos (preexistentes, S-8 y S-9) | Pass |
| Seguridad | Prueba negativa de RLS | **No aplica todavía**: `asiento_contable` no tiene política de fila; el aislamiento es por esquema y lo cubre `AislamientoEsquemaTest` del troncal | n/a |
| Plazos | Vencimiento y aviso previo | No aplica: CU-24 no tiene plazo legal | n/a |
| Arquitectura | Piezas por nivel, sin saltos | `ArquitecturaTest` 5/5 — incluido `ningunImportCruzado` | Pass |
| Operación | Evento de outbox por caso de uso | `asiento_registrado` y `asiento_reversado`, dentro de la transacción | Pass |
| Entrega | Lint, tipos, pruebas, build | `./gradlew build` + `integrationTest` → BUILD SUCCESSFUL | Pass |

## Gate de salida — evidencia

Ejecutado sobre PostgreSQL 16 real (Testcontainers) con el esquema de `sql/` aplicado
sobre volumen recién creado, en la máquina Ubuntu.

### Variante backend (carril Java · Gradle)

- [x] `./gradlew :servicios:nucleo-financiero:spotlessCheck` — BUILD SUCCESSFUL
- [x] `./gradlew :servicios:nucleo-financiero:build` — BUILD SUCCESSFUL
- [x] `./gradlew :servicios:nucleo-financiero:integrationTest` — BUILD SUCCESSFUL
- [x] **24 pruebas · 0 fallas · 0 saltadas**, repartidas así:

| Corredor | Clase | Pruebas |
| --- | --- | :-: |
| `test` | `ArquitecturaTest` | 5 |
| `test` | `CuadrarPartidasTest` | 6 |
| `test` | `CuadrarPartidasPropiedadTest` (1000 casos generados) | 1 |
| `testBarrido` | `BarridoTest` | 2 |
| `integrationTest` | `CU24Test` | 6 |
| `integrationTest` | `CU24RechazosTest` | 4 |

- [x] Cada criterio de aceptación con su `@Test` + `@DisplayName` nombrado igual —
      lo verifica `verificar_criterios.py`, no una afirmación de este informe
- [x] Cada `R-XXX-nn` citado con prueba de rechazo — 4/4
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

- **`cuenta_contable.saldo` lo actualiza la aplicación, no un trigger.** A diferencia
  de `cuenta_billetera.saldo_*`, que tiene `tg_movimiento_sincroniza_saldo`
  (`R-BIL-16`), el plan de cuentas no tiene quien derive su saldo en la base. CU-24
  lo hace en la misma transacción, que es lo que el paso 4 del caso de uso pide, pero
  la garantía queda en la aplicación y no en el motor — y `frontera-transaccional` §3
  dice que una regla que protege valor contable vive en la base. **Es una restricción
  que falta, no un defecto de este código**: proponerla es trabajo de bóveda
  (`restriccion`), no de carril.

## Ver también

[[informe]] · [[07 Carriles de trabajo concurrente]] · [[00b Estándar de ejecución · código limpio, pruebas y calidad]] · [[CU-24 Registrar el asiento contable de una operación]]
