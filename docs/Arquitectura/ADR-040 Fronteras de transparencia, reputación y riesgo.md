---
tags:
  - arquitectura
  - adr
  - servicios
  - transparencia
titulo: "ADR-040 — Fronteras de transparencia, reputación y riesgo"
estado: aceptada
fecha: 2026-08-26
---

# ADR-040 — Fronteras de transparencia, reputación y riesgo

> Resuelve los seis huecos que aparecieron al declarar el puesto **P3** —carriles `3B`
> transparencia y `4B` garantía— contra la propiedad de datos real de `sql/`. Ninguno
> se puede dejar abierto: los seis tocan la frontera entre servicios, y esa se decide
> antes de escribir el primer organismo, no durante.

## Contexto

[[ADR-017 Propiedad de datos por servicio]] partió el modelo en catorce esquemas con un
rol cada uno, y el invariante 11 lo hace cumplir la base: **ningún servicio puede
consultar el esquema de otro**, aunque quiera. El invariante 12 agrega que el libro
contable solo lo escribe `nucleo-financiero`.

Al leer los dieciséis casos de uso de `transparencia` y `garantia` contra los
`CREATE TABLE` aplicados aparecieron seis choques concretos entre lo que el caso de uso
describe y lo que el esquema permite. No son detalles de implementación: cuatro de ellos
harían que el caso de uso **no se pueda escribir** dentro de un servicio.

| # | El choque |
| :-: | --- |
| H-1 | `CU-72` sella un bloque cuyo contenido son aportes, entregas, coberturas, altas, bajas, acuerdos y el sorteo: cinco servicios, ninguno consultable |
| H-2 | `reputacion_usuario` está en `identidad`, mientras todo el resto de la reputación está en `transparencia` |
| H-3 | `CU-61` verifica el sorteo, pero `sorteo_turnos` y `turno` son de `grupos` |
| H-4 | `CU-61` exige usar **el mismo átomo** que `CU-60`, que vive en otro servicio |
| H-5 | `CU-97` está asignado a `transparencia`, pero `alerta_temprana` y `score_riesgo_incumplimiento` son de `garantia` |
| H-6 | `CU-27` escribe la lista en `garantia`, pero las `restriccion_usuario` que el sistema consulta son de `identidad` |

## Decisión

**1 · El contenido del bloque de transparencia se arma con una proyección local,
alimentada por eventos.** `transparencia` mantiene, en su propio esquema, la proyección
de los hechos sellables que los otros servicios ya publican por outbox
([[ADR-018 Outbox transaccional y mensajería]]). `CU-72` sella **desde su proyección**,
nunca desde una llamada de red. Un período no se puede sellar mientras su proyección
tenga huecos: se detecta comparando el número de secuencia del último evento consumido
por servicio contra el que cada uno publica en su latido.

**2 · `reputacion_usuario` es una proyección de solo lectura en `identidad`.** La
fuente de verdad del puntaje es `puntaje_reputacion`, en `transparencia`. `identidad`
guarda el total y el nivel vigentes porque los necesita en cada decisión de perfil, y
los actualiza consumiendo `reputacion.recalculada`. **Nadie escribe reputación en
`identidad` desde afuera de ese consumidor.**

**3 · El paquete público del sorteo viaja por evento.** Al pasar a `REVELADO`, `grupos`
publica `sorteo.revelado` con el paquete completo —hash comprometido, semilla revelada,
entropías en su orden, método y cupos en su orden original—, y `transparencia` lo
guarda. `CU-61` verifica contra eso. **La ruta pública de verificación no llama a
`grupos`.**

**4 · Los dos átomos del sorteo viven en `plataforma/comun-dominio`.**
`SorteoVerificable.verificarCompromiso` y `SorteoVerificable.barajarDeterminista` son
una sola implementación, usada por `CU-60` para sortear y por `CU-61` para verificar. El
ADR fija además el **protocolo público**, porque `CU-61` promete verificación desde
cualquier lenguaje y eso exige que no quede nada ambiguo:

- preimagen canónica: semilla y entropías, en orden, separadas por salto de línea, UTF-8;
- compromiso: `SHA-256(preimagen)` en hexadecimal minúscula;
- azar: bloques `SHA-256(semilla || ":" || contador)` leídos como enteros de 32 bits sin
  signo, big-endian;
- barajado: Fisher-Yates desde el final, con **muestreo por rechazo** para que no haya
  sesgo de módulo.

**5 · `CU-97` se ejecuta en dos mitades, una por servicio.**

| Mitad | Servicio | Escribe | Disparo |
| --- | --- | --- | --- |
| Métricas del grupo y alerta de cartera | `transparencia` | `metrica_grupo` · `alerta_riesgo` | cierre de período |
| Score de riesgo y alerta temprana sobre una persona | `garantia` | `score_riesgo_incumplimiento` · `alerta_temprana` | evento `metricas.calculadas` |

El modelo **no cambia**: las tablas se quedan donde están, y donde están tiene sentido —
el riesgo de que una persona incumpla es del servicio que maneja el incumplimiento. Lo
que estaba mal era la asignación del caso de uso a un solo carril.

**6 · La restricción efectiva vive en `identidad`; el expediente, en `garantia`.**
`lista_restriccion_interna` es el expediente con su causa, su monto y su historia.
`restriccion_usuario` es lo que el sistema consulta al decidir un ingreso, y por eso
está donde está la sesión. `garantia` publica `restriccion.aplicada` y
`restriccion.levantada`; `identidad` las aplica al consumirlas. **Mientras el evento
viaja, la restricción todavía no rige**, y eso se acepta explícitamente: la ventana es
de segundos y el costo de cerrarla —una llamada sincrónica a `garantia` en cada
evaluación de ingreso— es acoplar la disponibilidad del alta de grupos a la del
servicio de cobranza.

## Motivo

**Porque el invariante 11 no es una recomendación.** Las seis situaciones tienen la
misma forma: un caso de uso escrito antes de que el modelo se partiera en catorce
servicios. Resolverlas «cuando toque implementar» significa resolverlas mal y tarde: el
carril que llegue primero va a elegir la llamada sincrónica, que es lo que sale natural,
y va a acoplar la disponibilidad de un servicio público a la de cuatro internos.

**Porque la verificación pública tiene que sobrevivir a la caída de lo demás.** Las
cuatro rutas sin sesión —`CU-61`, `CU-72`, `CU-73`, `CU-75`— existen para que un tercero
compruebe sin confiar en nosotros. Una que responde `503` porque `grupos` está caído no
cumple esa promesa: quien desconfía va a leer la caída como conveniente.

**Porque dos implementaciones del mismo hash no verifican nada.** Lo dice el propio
`CU-61`: si sortear y verificar usan códigos distintos, lo que se comprueba es que dos
códigos coinciden. El átomo compartido es la única forma de que la promesa sea cierta, y
`plataforma/comun-dominio` es el único lugar donde los dos servicios pueden verlo.

**Porque mover una tabla de esquema es más caro que corregir un plan.** En H-5 la
tentación era mover `alerta_temprana` a `transparencia`. Habría cambiado el modelo, las
claves foráneas, los permisos y las clases generadas de dos servicios — para acomodar
una fila de una tabla de asignación de carriles.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Llamadas sincrónicas a los cinco servicios al sellar** (H-1) | El cierre de período dejaría de poder ejecutarse si cualquiera de los cinco está caído, y sellaría tarde y en cascada cuando vuelvan. Además metería red dentro de la ventana de una operación que ya toma un bloqueo por grupo |
| **Un esquema `transparencia` con permiso de lectura sobre los otros cinco** | Es exactamente el invariante 11 al revés. Y el día que alguien cambie una columna en `aportes`, se entera `transparencia` en producción |
| **Duplicar el átomo del sorteo en los dos servicios, con pruebas cruzadas** (H-4) | Las pruebas cruzadas prueban que hoy coinciden. La divergencia llega con el primer arreglo que solo uno recibe, y llega en silencio |
| **Mover `alerta_temprana` y `score_riesgo_incumplimiento` a `transparencia`** (H-5) | Cambio de modelo para acomodar una asignación de carril. Y quedarían lejos del incumplimiento, que es de lo que hablan |
| **Consultar `garantia` sincrónicamente en cada evaluación de ingreso** (H-6) | Acopla la disponibilidad del alta de grupos a la del servicio de cobranza, para cerrar una ventana de segundos |
| **Que `transparencia` escriba directo en `identidad`** (H-2) | Dos dueños para la misma fila. La primera corrección concurrente decide quién gana, y no hay forma de auditar cuál era la buena |

## Consecuencias

**Lo que se gana.** Las cuatro rutas públicas quedan servidas por datos locales: siguen
respondiendo con medio sistema caído. El sorteo tiene una sola implementación y un
protocolo publicable. `CU-97` pasa a ser implementable.

**Lo que cuesta, dicho sin adornos.**

1. **`transparencia` gana una proyección que hay que mantener.** Cada hecho sellable
   nuevo exige su evento y su consumidor. Un evento que nadie consume es un bloque
   incompleto que se sella igual, y por eso el sellado se bloquea con la proyección
   incompleta en vez de continuar.
2. **Aparece consistencia eventual donde antes se leía la tabla.** El puntaje que
   `identidad` muestra puede ir unos segundos atrás del que `transparencia` calculó, y
   la restricción tarda en regir. Las dos ventanas se aceptan por escrito acá; ninguna
   toca dinero.
3. **`CU-97` cruza dos carriles del mismo puesto.** `4B` no puede cerrar su mitad hasta
   que `3B` publique `metricas.calculadas`. Es una dependencia declarada, no una
   sorpresa de integración.
4. **El protocolo del sorteo queda congelado.** Cambiarlo invalidaría la verificación de
   todos los sorteos anteriores. Si algún día hace falta otro, se versiona en el campo
   `metodo` y conviven.

## Cómo se verifica

- `SorteoVerificableTest` en `plataforma/comun-dominio`: el compromiso se recomputa, la
  preimagen es canónica —`("ab","c")` y `("a","bc")` **no** colisionan—, el barajado es
  determinista, es una permutación y ningún cupo queda cautivo de una posición.
- `AislamientoEsquemaTest` ya prueba lo que hace innecesario confiar en esta decisión:
  `transparencia` **no puede** consultar `grupos`, `aportes` ni `nucleo_financiero`
  aunque el código lo intente. El motor lo rechaza.
- Cuando `3B` se implemente: una prueba que sella un bloque con la proyección incompleta
  y comprueba que **se rechaza**, y una que apaga el consumidor de `sorteo.revelado` y
  comprueba que `CU-61` responde con el sorteo que ya tenía, no con un error.
- Cuando `4B` se implemente: una prueba de que `identidad` niega el ingreso a un grupo
  después de consumir `restriccion.aplicada`, y lo permite después de
  `restriccion.levantada`.

## Ver también

[[ADR-017 Propiedad de datos por servicio]] · [[ADR-018 Outbox transaccional y mensajería]] ·
[[ADR-022 Comunicación entre servicios]] · [[CU-60 Sortear los turnos]] ·
[[CU-61 Verificar públicamente el sorteo]] · [[CU-72 Sellar el bloque de transparencia]] ·
[[CU-97 Anticipar el riesgo con alertas tempranas]] ·
[[CU-27 Restringir al deudor e incluirlo en la lista interna]]
