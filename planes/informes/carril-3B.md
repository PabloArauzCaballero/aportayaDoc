# Carril 3B · transparencia — CU-61, 70, 71, 72, 73, 74, 75, 76, 97

**Estado: cerrado.** Nueve casos de uso, 118 pruebas de integración en verde, contrato
OpenAPI con 17 rutas y 35 esquemas, y los cuatro verificadores pasando.

```
./gradlew :servicios:transparencia:integrationTest   →  BUILD SUCCESSFUL (118 pruebas)
./gradlew :servicios:transparencia:build             →  BUILD SUCCESSFUL
python3 scripts/verificar_boveda.py                  →  TODO OK
python3 scripts/verificar_carriles.py                →  TODO OK (con avisos)
python3 scripts/verificar_criterios.py               →  Sin divergencias entre la bóveda y el código
python3 scripts/verificar_seguridad.py               →  TODO OK · 2 aviso(s)
```

## Lo que se construyó

| Nivel | Piezas |
| --- | --- |
| Átomos (7) | `PuntajeDeReputacion` · `CadenaDeBloques` · `ContenidoCanonico` · `CriterioDeInsignia` · `CertificadoVerificable` · `ModeracionDeResena` · `SenalDeRiesgo` |
| Moléculas (8) | `ModeloRepositorio` · `ReputacionRepositorio` · `SnapshotRepositorio` · `CadenaRepositorio` · `InsigniaRepositorio` · `CertificadoRepositorio` · `ResenaRepositorio` · `RiesgoRepositorio` |
| Organismos (9) | `CU61VerificarSorteo` · `CU70RegistrarEventoReputacion` · `CU71RecalcularPuntaje` · `CU72SellarBloque` · `CU73VerificarCadena` · `CU74EvaluarInsignias` · `CU75EmitirCertificado` · `CU76PublicarResena` · `CU97EvaluarRiesgo` |

`SorteoVerificable` **no se duplicó**: CU-61 usa el átomo que `plataforma/comun-dominio`
ya tenía y que CU-60 usa para sortear. Si la verificación tuviera su propia
implementación, comprobaría que dos códigos nuestros coinciden entre sí, no que el
sorteo es correcto.

## Los diez huecos declarados

Todos son divergencias entre lo que el caso de uso promete y lo que la bóveda permite.
En cada uno ganó la bóveda —precedencia— y hay una prueba que demuestra el
comportamiento real en vez de afirmar el deseado.

### H-1 · El bloque génesis no se puede escribir · **defecto bloqueante**

`ck_bloque_genesis` exige `numero_bloque = 1 AND hash_bloque_anterior IS NULL`, pero la
columna está declarada `VARCHAR(64) NOT NULL`. Las dos condiciones juntas hacen que
**ningún bloque número 1 se pueda insertar jamás**.

```
CHECK (((numero_bloque = 1) AND (hash_bloque_anterior IS NULL))
    OR ((numero_bloque > 1) AND (hash_bloque_anterior IS NOT NULL)))
hash_bloque_anterior nullable=NO
```

La cadena arranca en `CadenaDeBloques.PRIMER_NUMERO = 2`, con 64 ceros como hash
anterior, que es lo único que la base acepta. `CU72RechazosTest.rechazaRREP04` demuestra
el rechazo del número 1.

**Cómo se arregla en la bóveda:** o la columna pasa a nullable, o el CHECK admite el
hash convencional de génesis. Es una línea en `sql/`, y es troncal.

### H-2 · No hay historia de puntajes

`sql/30_indices` crea `uq_puntaje_reputacion_usuario_id` (único por usuario) y
`sql/40_reglas` crea `ex_puntaje_vigente` (exclusión gist sobre el rango de vigencia).
El índice es más restrictivo, así que **la restricción de exclusión nunca puede
disparar** y `vigente_hasta` no sirve para nada: no puede haber dos filas del mismo
usuario ni con rangos disjuntos.

Consecuencia directa: el criterio de CU-71 «*el anterior conserva su modelo_id
original*» **no se puede cumplir** — el puntaje anterior no puede coexistir. Lo que sí
conserva la versión del modelo es la foto (`snapshot_reputacion`), y contra eso afirma
`CU71Test.criterio3`.

### H-3 · R-REP-03 no se sostiene al editar los componentes

`tg_puntaje_cuadra` es `AFTER INSERT OR UPDATE` **sobre `puntaje_reputacion`**. Borrar
un `componente_score` no lo dispara: se puede recortar la explicación y dejar el número
intacto. La regla vale al escribir el puntaje, no después.
`CU71RechazosTest.rechazaRREP03` afirma las dos mitades: el UPDATE del total se rechaza,
el DELETE del componente pasa.

### H-4 · El hash del bloque cubre cinco componentes, no tres

CU-72 dice `hash_bloque = SHA256(numero || hash_anterior || hash_contenido)`. La tabla no
tiene `hash_contenido`: tiene `raiz_merkle`. Y el período cubierto
(`periodo_cubierto_desde` / `_hasta`) queda fuera de esos tres, de modo que se podría
mover qué período cubre un bloque sin que su hash cambiara.

Se implementó con cinco componentes —número, hash anterior, raíz de Merkle, desde,
hasta—, por el mismo argumento que la propia bóveda escribe en `R-AUD-02/03`: *«el hash
cubre TODO lo que hay que poder probar, no un subconjunto cómodo»*. El contrato lo
declara y `CU73RechazosTest.rechazaRAUD03` lo demuestra.

### H-5 · `uq_bloque_grupo_numero` es inalcanzable por la vía normal

`tg_bloque_encadenado` corre `BEFORE INSERT` y ve el salto de numeración antes de que se
llegue al índice único. Cualquier intento de repetir un número muere con `R-REP-04` del
trigger. El índice queda como segunda línea, correcta pero no ejercitable.
`CU72Test.criterio3` afirma `R-REP-04`, que es lo que importa.

### H-6 · `verificacion_publica` y `evento_dominio` no son append-only

La lista de tablas selladas incluye `evento_reputacion` y `registro_sellado`, pero **no**
`verificacion_publica`. El resultado de una verificación fallida —la evidencia de que
alguien detectó una alteración— se puede borrar. Lo mismo con `evento_dominio`, que es la
bandeja de salida.

`CU61RechazosTest.rechazaRAUD01` prueba el append-only que sí existe (sobre
`registro_sellado`) y deja el hueco escrito en lugar de afirmar lo contrario.

### H-7 · `snapshot_reputacion` tampoco es append-only

La foto de la que se emite un certificado se puede editar. Si se cambia el puntaje de la
foto, el hash del certificado deja de significar lo que decía. Lo que hoy lo sostiene es
`uq_certificado_reputacion_snapshot_id`: una foto, un certificado, así que al menos no se
puede emitir otro sobre la misma foto alterada.

### H-8 · `ck_alerta_riesgo_cierre` nunca protege una fila guardable

```
ck_alerta_riesgo_estado : estado IN ('ABIERTA','CONFIRMADA','DESCARTADA','EN_REVISION')
ck_alerta_riesgo_cierre : estado <> 'CERRADA' OR cerrada_en IS NOT NULL
```

`'CERRADA'` no está en el catálogo de estados, así que el CHECK del cierre solo puede
dispararse sobre una fila que ya es inválida por otro motivo. Cerrar como `CONFIRMADA`
sin `cerrada_en` **pasa**. Quien exige el desenlace y la fecha es CU-97, no la base.

Además, `alerta_temprana` (esquema `garantia`) tiene índice único parcial para la
abierta; `alerta_riesgo` **no tiene ninguno**, así que dos evaluaciones simultáneas
pueden abrir dos alertas por la misma causa. `CU97Test.concurrencia` afirma el rango real
(1 ó 2) en vez de un ideal.

### H-9 · Vocabularios que no se cruzan

Tres catálogos cerrados nombran cosas que el caso de uso pide y no existen, y uno se
contradice con las semillas:

| Dónde | El CU pide | El catálogo admite |
| --- | --- | --- |
| `alerta_riesgo.ambito` | PARTICIPANTE, GRUPO, CARTERA | GRUPO, ORGANIZADOR, **USUARIO** |
| `resena_participante.dimension` | PUNTUALIDAD, COMUNICACION, **COLABORACION** | PUNTUALIDAD, COMUNICACION, **ORGANIZACION** |
| `verificacion_publica.tipo_documento` | un valor para sorteo y otro para cadena | CERTIFICADO_REPUTACION, CONSTANCIA_PAGO, ESTADO_GRUPO |
| `registro_sellado.tipo_entidad` | altas/bajas de participantes, resultado del sorteo | ACUERDO, COBERTURA, ENTREGA, PAGO, SANCION |
| `snapshot_reputacion.motivo` | emisión de certificado | AUDITORIA, CIERRE_DE_GRUPO, INGRESO_A_GRUPO, PERIODICO |

Se usó en cada caso el valor más cercano y verdadero: `USUARIO` para el participante,
`ORGANIZACION` para la tercera dimensión, `ESTADO_GRUPO` con prefijo en el código
(`SOR-…` / `CAD-…`) para distinguir sorteo de cadena. **Nada se inventó.**

### H-10 · El catálogo de eventos y el de reglas de impacto son disjuntos

Este es el más grave después de H-1.

`regla_impacto_evento` viene sembrado con **17** `tipo_evento`.
`ck_evento_reputacion_tipo` admite **19** valores. La intersección es de **tres**:
`APORTE_PUNTUAL`, `APORTE_ANTICIPADO`, `DEUDA_CASTIGADA`.

Los otros catorce tipos reglados —`APORTE_TARDIO`, `MORA_MAYOR_15_DIAS`,
`COBERTURA_APLICADA`, `INCUMPLIMIENTO_FIRME`, `SANCION_FIRME`, `EXPULSION_DEL_GRUPO`,
`PROMESA_INCUMPLIDA`, `DEUDA_REGULARIZADA`, `PLAN_REGULARIZACION_CUMPLIDO`,
`CICLO_COMPLETADO`, `MES_DE_ANTIGUEDAD`, `GRUPO_ADMINISTRADO_SIN_INCIDENCIAS`,
`SANCION_ORGANIZADOR_FIRME`, `RETIRO_ACORDADO`— **tienen regla de impacto pero no se
pueden guardar**. Y los dieciséis tipos del CHECK que no tienen regla no se puntúan,
porque CU-70 rechaza puntuar sin regla en vez de inventar un impacto.

En la práctica, hoy la reputación solo se mueve por tres clases de hecho. Se implementó
así a propósito: **sin regla no se puntúa** (invariante 9), porque inventarle un impacto
a un tipo de evento es decidir a mano cuánto vale la conducta de alguien.

**Cómo se arregla en la bóveda:** alinear los dos vocabularios. Son dos listas en `sql/`
que tienen que decir lo mismo, y hoy dicen cosas distintas.

## Dos defectos propios, encontrados y corregidos

**Orden de escritura invertido.** Escribí los componentes antes que el puntaje, razonando
que `tg_puntaje_cuadra` los sumaría al insertar. La realidad es al revés:
`fk_componente_score_puntaje_id` **no** es diferible (un componente no existe antes que su
puntaje) y `tg_puntaje_cuadra` **sí** es `DEFERRABLE INITIALLY DEFERRED` (corre al
confirmar, cuando los componentes ya están). Primero el puntaje, después sus partes.

**El puntaje base no cuadraba.** `calcular` devolvía 500 puntos de arranque con la lista
de componentes vacía, de modo que `SIN_HISTORIAL` habría sido rechazado por R-REP-03 en
cuanto se intentara guardar. El arranque entró como componente `PUNTAJE_BASE`, y el
recorte contra el piso o el techo como `AJUSTE_POR_LIMITE`. Además de cuadrar, mejora la
explicación: «arrancás en 500, la puntualidad suma 250, la mora resta 40».

## Una regresión que dejé en carril 2B, arreglada acá

`./gradlew :servicios:tarifas:testBarrido` estaba **rojo** desde el cierre de tarifas y no
lo había corrido. Dos hallazgos, los dos reales:

- `PoliticaDeRedondeo.alCentavo()` tenía `new BigDecimal("0.01")` — una cifra literal
  indistinguible de un umbral de negocio. Ahora es `BigDecimal.ONE.movePointLeft(2)`.
- `FixturaDeTarifas` tenía 532 líneas. Se partió en `FixturaDeTarifas` (catálogo),
  `FixturaDeFacturacion` (lo ya cobrado) y `FixturaDeGrupo` (el escenario).

`:servicios:tarifas:integrationTest` sigue en verde después del corte (95 pruebas).

## Lo que se movió a configuración

El barrido `sin-umbral-literal` marcó dieciséis cifras dentro del dominio, y tenía razón
en todas. Cada una pasó a ser dato:

| Antes, dentro de un `if` | Ahora |
| --- | --- |
| Los cinco cortes de nivel de confianza (90/75/60/40/25 %) | `PuntajeDeReputacion.Corte`, inyectado en CU-71 |
| Los cortes de riesgo (60/80) y de severidad (0,50/0,25/0,10) | `SenalDeRiesgo.Escala`, inyectada en CU-97 |
| La atenuación del peso de una reseña (÷2) | `ModeracionDeResena.Atenuacion`, inyectada en CU-76 |
| El desempeño mínimo de ORGANIZADOR_CONFIABLE (80) | parámetro de CU-74 |

Las multiplicaciones por 100 y por 10 pasaron a `movePointRight`: correr la coma no es
aplicar un umbral, y ahora se distingue de un vistazo.

## Lo que este carril NO hizo

- **No hay capa `web/`.** Igual que en los cinco carriles anteriores. El contrato está
  escrito y los organismos son invocables; falta el adaptador HTTP.
- **No hay `trabajos/`.** CU-73 nombra un control diario, CU-74 una reevaluación tras
  cada recálculo, CU-75 un trabajo que marca vencidos y CU-97 uno por cierre de período.
  Ninguno está cableado con `@Scheduled` + ShedLock.
- **No se escribe `auditoria.incidente_operativo` ni `garantia.alerta_temprana`**
  (invariante 11). Se piden por evento de dominio con su severidad y su taxonomía
  puestas. Los consumidores de esos eventos están en otros carriles.

## Preguntas que ninguna máquina responde

**¿Los nombres dicen lo que las cosas son?** Sí, con una excepción incómoda:
`registro_sellado.hash_contenido` guarda el hash de la forma canónica del hecho, no del
bloque, y a dos líneas de distancia `bloque_transparencia.raiz_merkle` guarda el hash del
conjunto. Son dos cosas distintas con nombres que se parecen; el javadoc lo dice, pero es
una trampa que la bóveda pone.

**¿La frontera transaccional es la correcta?** Sí. El único caso dudoso era CU-72: el
estado de la conciliación vive en el núcleo financiero y consultarlo dentro de la
transacción de sellado sería una llamada de red adentro (invariante 6). Llega como
entrada, resuelto antes de abrir.

**¿Qué supuse que no estaba en la bóveda?** Que la cadena arranca en 2 (H-1), que
`ESTADO_GRUPO` es el tipo de documento más cercano para un sorteo y para una cadena
(H-9), y que un evento compensatorio lleva `referencia_origen_id` nulo — porque el
catálogo cerrado de tipos no tiene un valor para «reversa» y con la referencia puesta
chocaría contra el propio evento que viene a deshacer.

**¿Qué dejé peor de como lo encontré?** Nada, y arreglé algo que había dejado peor antes:
el barrido de tarifas.
