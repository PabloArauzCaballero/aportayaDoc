---
tags:
  - plan
  - informe
  - carril
titulo: "Carril 4B — 08_garantia"
ola: 4
fase: 4
modulo: 08_garantia_incumplimiento
rama: pablo/feature/carril-4B-garantia
estado: en curso
---

# Carril 4B — garantía

**Fase** 4 · **Casos de uso** 23, 25, 26, 27, 29, 66, 67 · **Máquina** mac

## Casos de uso

| CU | Contrato | Dominio | Infra | Aplicación | HTTP | Pruebas | Gate |
| :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
| CU-23 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ | ✅ |
| CU-25 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ | ✅ |
| CU-26 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ | ✅ |
| CU-27 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ | ✅ |
| CU-29 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ | ✅ |
| CU-66 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ | ✅ |
| CU-67 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ | ✅ |

## Lo que este carril decidió, y por qué

Este módulo es el que decide qué pasa cuando alguien no cumple. Todo lo que hace tiene
consecuencias sobre una persona concreta: le bloquea grupos, le genera una deuda, lo
pone en una lista. Por eso casi cada decisión de diseño terminó siendo la misma:
**que quede escrito, que no se pueda reescribir, y que la persona pueda defenderse.**

### 1 · El expediente es append-only, así que el estado se deriva

`registro_incumplimiento` bloquea UPDATE **y** DELETE. Su columna `estado` guarda el
estado al detectar y no se puede mover. El estado corriente vive en
`historial_estado_incumplimiento` —también append-only—: cada transición es una fila
con su motivo, quién la hizo y cuándo.

No es una limitación que haya que sortear: es la forma. Un expediente cuyo estado se
puede reescribir no prueba nada, y la persona sancionada no tiene contra qué defenderse.

### 2 · El plazo de descargo entra al abrir, o no entra

`notificado_en` y `fecha_limite_subsanacion` están en esa misma tabla. Escribirlos
después es imposible. La consecuencia: **declarar el incumplimiento y notificárselo a la
persona son un solo acto**. Es lo correcto —enterarse después de que el plazo empezó a
correr es no tener plazo— pero conviene saberlo antes de intentar separarlos.

### 3 · Cuatro límites, gana el más chico

`CoberturaAplicable` evalúa el porcentaje del aporte, el tope por participante
descontando lo ya consumido, el tope del período y el saldo del fondo. Aplicar sólo uno
deja los otros tres como decoración. El límite que mandó viaja en la respuesta y en el
evento: el grupo tiene derecho a saber por qué se cubrió lo que se cubrió.

### 4 · Un rechazo también deja fila

Cuando ningún límite deja cubrir nada, se registra la cobertura como `RECHAZADA` con su
motivo. Un rechazo sin fila deja al grupo sin saber por qué no se cubrió y sin nadie a
quien reclamarle.

## Huecos declarados

| # | Qué falta o diverge | Dónde | Qué se hizo |
| :-: | --- | --- | --- |
| H-1 | El plazo de descargo no se puede escribir después de abrir el expediente (tabla append-only) | `sql/` | Declaración y notificación son un solo acto. Es lo correcto, y queda dicho para que nadie intente separarlas |
| H-2 | El CU-25 pide los estados `PRESUNTO` y `REGULARIZADO`, y una columna `fecha_limite_descargo`. `ck_registro_incumplimiento_estado` no los admite; la columna se llama `fecha_limite_subsanacion` | ídem | Manda la DDL: se usa `NOTIFICADO` y `SUBSANADO`. Lo que el criterio protege —el plazo guardado, la cobertura no habilitada— se cumple igual y tiene su prueba |
| H-3 | El CU-27 pide una tabla `restriccion_usuario` con tipo `SIN_GRUPOS_NUEVOS`. Existe `lista_restriccion_interna` con niveles `OBSERVACION`, `LIMITADO`, `VETADO` | ídem | Manda la DDL. El efecto es el mismo; los nombres no |
| H-4 | El CU-23 pide que la entrega quede `BLOQUEADA_POR_FONDO_INCOMPLETO`. Ese estado es de `entrega_fondo`, en `entregas` | invariante 11 | Este servicio publica el evento con lo que sí pudo cubrir y el límite que mandó; `entregas` decide |
| H-5 | El CU-66 pide activar el `plan_contingencia` al vencer la búsqueda de reemplazo. La tabla existe y **ningún caso de uso de este carril la escribe**; el trabajo de vencimiento no está cableado | `sql/`, `trabajos/` | No se inventó un flujo. Lo que sí queda es la señal: un reemplazo `BUSCANDO` sin candidato es la condición que dispara la contingencia |
| H-6 | El CU-67 pide un «factor de prorrata único» cuando lo disponible no alcanza | `dominio/CuadreDeDisolucion` | **No se prorratea: se rechaza.** Repartir de menos sin decirlo es como se pierde la confianza de todos a la vez, y prorratear en silencio es exactamente eso. Quien decida prorratear tiene que hacerlo explícito, no heredarlo de un cálculo |
| H-7 | Los nombres de varios `AP-CUnn-nn` no describen el caso que el código ocupa | `openapi/garantia.yaml` | Se conservan los números —que son los que la bóveda ata a cada rechazo— y el contrato documenta qué significa cada uno |
| H-8 | Ningún trabajo programado está cableado | `trabajos/` vacío | Candidatos: detección diaria por vencimiento, cierre del plazo de descargo, vencimiento de la búsqueda de reemplazo |
| H-9 | La cobertura no escribe el asiento contable ni mueve la cuenta del grupo | invariante 12 | Los pide por evento a `nucleo-financiero`, con el monto y el saldo resultante |

## Supuestos declarados

1. **Antes del plazo de mora el fondo no se toca.** Si cubriera el primer día de atraso,
   dejaría de ser una garantía y pasaría a ser un adelanto automático — y nadie volvería
   a pagar a tiempo.
2. **La deuda de quien incumplió la deja la cobertura, no la devolución del fondo.**
   Cobrársela en los dos lugares sería cobrarle el mismo incumplimiento dos veces.
3. **Lo que el fondo gastó lo pierden todos en proporción.** Esa es la idea de un fondo
   mutual; descontárselo sólo a quien incumplió sería una deuda, y para eso está la
   subrogación.
4. **El remanente del reparto se ajusta en la última devolución.** Sin eso, repartir
   entre tres deja centavos flotando que nadie sabe de quién son.
5. **Pagar la deuda y ver su estado nunca se restringen.** Cerrarle esa puerta a quien
   debe es asegurarse de que no vuelva.
6. **Castigar la deuda no levanta la restricción.** Si lo hiciera, castigar sería el
   camino más corto para volver a empezar sin haber pagado.
7. **Una suspensión no le quita al organizador ni al participante lo que ya administra.**
   Dejar un grupo sin nadie que responda le hace más daño a los participantes que al
   sancionado.

## Fronteras transaccionales respondidas

### CU-25 · Declarar
1. **Todo junto o nada:** el expediente, su evidencia, la transición y el evento.
2. **Fuera del commit:** la notificación al participante, que hace `notificaciones`.
3. **Clave de idempotencia:** la obligación, única en la base.
4. **Qué se bloquea:** `uq_registro_incumplimiento_obligacion_id`, y `FOR UPDATE` al
   resolver el descargo.
5. **Si el proceso muere tras el commit:** el expediente existe con su plazo.

### CU-23 · Cubrir
1. **Todo junto o nada:** la cobertura, el movimiento del fondo, el saldo, la deuda y la
   transición del expediente.
2. **Fuera del commit:** el asiento y el movimiento de la cuenta del grupo.
3. **Clave de idempotencia:** el expediente.
4. **Qué se bloquea:** el fondo con `FOR UPDATE`, más versión optimista sobre el saldo.
5. **Si el proceso muere tras el commit:** el fondo bajó y la deuda existe; el evento
   sale por el relay.

### CU-26 · Ejecutar el aval
1. **Todo junto o nada:** la ejecución, la subrogación y la marca en la deuda.
2. **Fuera del commit:** el cobro real al avalista.
3. **Clave de idempotencia:** `(aval, expediente)`.
4. **Qué se bloquea:** el expediente con `FOR UPDATE`; el tope lo verifica el trigger
   con su propio `FOR UPDATE` sobre el aval.
5. **Si el proceso muere tras el commit:** la ejecución quedó notificada con su plazo.

### CU-27 · Restringir
1. **Todo junto o nada:** la restricción y su evento.
2. **Fuera del commit:** el aviso a la persona.
3. **Clave de idempotencia:** una vigente por usuario.
4. **Qué se bloquea:** el `WHERE retirado_en IS NULL` al levantar.
5. **Si el proceso muere tras el commit:** la restricción está vigente con su motivo.

### CU-29 · Devolver el fondo
1. **Todo junto o nada:** todas las devoluciones, el movimiento y el saldo.
2. **Fuera del commit:** el pago a cada participante.
3. **Clave de idempotencia:** el saldo en cero — repetir no vuelve a repartir.
4. **Qué se bloquea:** versión optimista del fondo, más
   `uq_devolucion_fondo_participante`.
5. **Si el proceso muere tras el commit:** el fondo quedó en cero y las devoluciones
   calculadas.

### CU-66 · Reemplazar
1. **Todo junto o nada:** el reemplazo y su evento.
2. **Fuera del commit:** la búsqueda del candidato.
3. **Clave de idempotencia:** el estado del reemplazo.
4. **Qué se bloquea:** los `WHERE` sobre el estado.
5. **Si el proceso muere tras el commit:** el estado dice en qué punto quedó.

### CU-67 · Disolver
1. **Todo junto o nada:** la disolución con su cuadre.
2. **Fuera del commit:** los pagos y cobros a cada participante.
3. **Clave de idempotencia:** el grupo, único en la base.
4. **Qué se bloquea:** `uq_disolucion_anticipada_grupo_id`; el cierre lo valida
   `tg_disolucion_cuadra`.
5. **Si el proceso muere tras el commit:** la disolución quedó calculada, no cerrada.

## Piezas declaradas por nivel

| Pieza | Nivel | CU | Estado |
| --- | --- | :-: | :-: |
| `CoberturaAplicable` | átomo | 23 | ✅ |
| `PlazoDeDescargo` | átomo | 25 | ✅ |
| `EstadoDelExpediente` | átomo | 23, 25 | ✅ |
| `TopeDelAval` | átomo | 26 | ✅ |
| `RestriccionInterna` | átomo | 27 | ✅ |
| `DevolucionDelFondo` | átomo | 29 | ✅ |
| `CuadreDeDisolucion` | átomo | 67 | ✅ |
| `ExpedienteRepositorio` | molécula | 23, 25, 26, 27, 66 | ✅ |
| `FondoRepositorio` | molécula | 23, 26, 27, 29 | ✅ |
| `GestionRepositorio` | molécula | 26, 27, 66, 67 | ✅ |
| `CU23`, `CU25`, `CU26`, `CU27`, `CU29`, `CU66`, `CU67` | organismos | — | ✅ |

## Micro-PR abiertos al troncal

| Rama | Qué agrega | Estado |
| --- | --- | :-: |
| — | Ninguno. Todo lo que hizo falta ya estaba | — |

## Bloqueos

Ninguno.

## Matriz de gates

| Área | Gate | Evidencia | Estado |
| --- | --- | --- | --- |
| Especificación | Criterios de aceptación cubiertos | `verificar_criterios.py --servicio garantia`: «7 verificados · Sin divergencias» | ✅ |
| Datos | Restricciones citadas con prueba de rechazo | 4+5+6+6+7+4+4 = 36 rechazos, uno por restricción citada | ✅ |
| Seguridad | Debido proceso | Plazo persistido, evidencia inmutable, quien resuelve no es el imputado | ✅ |
| Plazos | Vencimiento y aviso previo | `PlazoDeDescargo` desde `notificado_en`, guardado al abrir | ✅ |
| Arquitectura | Piezas por nivel, sin saltos | tabla de arriba | ✅ |
| Operación | Health, readiness, trazas | pendiente: capa `web/` | ⬜ |
| Entrega | Pruebas | `integrationTest` en verde | ✅ |

## Gate de salida — evidencia

- [x] `./gradlew :servicios:garantia:integrationTest` — **BUILD SUCCESSFUL**
- [x] `python3 scripts/verificar_criterios.py --servicio garantia` — Sin divergencias
- [x] Cada criterio de aceptación con su `@Test` + `@DisplayName` nombrado igual
- [x] Cada `R-XXX-nn` citado con prueba de rechazo
- [ ] `./gradlew spotlessCheck check`

> Lo verificado es lo que tiene su comando pegado arriba. Lo que **no** está verificado:
> la capa HTTP, el arranque del servicio, los trabajos programados, y el asiento
> contable que `nucleo-financiero` escribe al consumir los eventos de este servicio.

## Ver también

[[informe]] · [[07 Carriles de trabajo concurrente]] · [[carril-3D]] · [[carril-3A]]
