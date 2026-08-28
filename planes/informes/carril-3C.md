# Carril 3C · cumplimiento — CU-40 a 56 y 94

**Estado: cerrado.** Trece casos de uso nuevos (CU-40, 41, 42, 43, 44, 45, 47, 48, 49,
52, 53, 56, 94) sobre los seis que el servicio ya tenía. 271 pruebas de integración en
verde y los cuatro verificadores pasando.

```
./gradlew :servicios:cumplimiento:integrationTest  →  BUILD SUCCESSFUL (271 pruebas)
./gradlew :servicios:cumplimiento:build            →  BUILD SUCCESSFUL
python3 scripts/verificar_boveda.py                →  TODO OK
python3 scripts/verificar_carriles.py              →  TODO OK (con avisos)
python3 scripts/verificar_criterios.py             →  Sin divergencias entre la bóveda y el código
python3 scripts/verificar_seguridad.py             →  TODO OK · 2 aviso(s)
```

## El defecto que hay que arreglar antes de producción

**`fn_uif_registrar_operacion` no puede escribir un formulario PCC-01, y por eso aborta
la recarga de billetera de cualquiera que cruce el umbral.**

La función de la bóveda inserta el registro con `exento = FALSE` y sin `origen_declarado`
ni `destino_declarado` ni `motivo_exencion`. `ck_operelev_declaracion` exige exactamente
uno de los tres:

```sql
CHECK (exento
    OR formulario <> 'PCC-01'
    OR (origen_declarado IS NOT NULL AND destino_declarado IS NOT NULL)
    OR motivo_exencion IS NOT NULL)
```

Verificado con un `INSERT` directo contra el contenedor:

```
ERROR:  new row for relation "registro_operacion_relevante"
        violates check constraint "ck_operelev_declaracion"
```

El disparador que la invoca es `tg_movimiento_umbrales_uif`, un
`CONSTRAINT TRIGGER … DEFERRABLE INITIALLY DEFERRED` sobre `movimiento_billetera`. Corre
al confirmar. Con el umbral sembrado y activo —`PCC-01 / CARGA_BILLETERA / USD 1.000 en
3 días`, `sql/60_semillas/06-umbrales-uif.sql` línea 14— **toda recarga que cruce ese
umbral falla al commit**. No es un caso de borde: es el umbral que más aplica a este
producto.

Y **tampoco se puede arreglar completando la fila después**: `registro_operacion_relevante`
es append-only (R-AUD-01), así que el `UPDATE` que agregaría origen y destino se rechaza.

```
ERROR:  R-AUD-01: registro_operacion_relevante es append-only
```

Las dos restricciones juntas dejan **un solo camino posible**: el registro nace completo,
con la declaración ya tomada. Es el orden inverso al que describe el CU-41 —su paso 4
crea el registro y el 5 pide la declaración— y es el único que la base admite. CU-41 se
implementó así: se detecta el umbral, se pide la declaración por evento de dominio, y la
fila se escribe cuando llega. `CU41RechazosTest.rechazaRUIF02` demuestra el rechazo.

**Cómo se arregla en la bóveda.** Cualquiera de las tres, y es decisión de quien
gobierna `sql/`:

1. Que `fn_uif_registrar_operacion` escriba un `motivo_exencion` explicando que la
   declaración está pendiente (el flujo 5a del propio CU-41 ya usa ese campo así).
2. Que el CHECK acepte un PCC-01 sin declaración mientras esté en un estado «pendiente».
3. Que el motor no corra en el disparador de billetera, sino como consumidor del evento
   de transacción aplicada — que es lo que hace hoy la capa de aplicación.

Mientras tanto, las pruebas del carril arman las transacciones con el umbral apagado y lo
encienden después (`FixturaDeUif.transaccionConUmbralApagado`). **Eso no relaja ninguna
restricción**: la fila que después escribe el caso de uso pasa por todas.

## Los otros ocho huecos

### H-2 · `reporte_regulatorio_id` no se puede escribir nunca

Misma causa: la tabla es append-only, así que la columna que ataría un registro a su
reporte queda siempre en nulo. El enlace se deriva del `periodo_remision`, y lo que
impide reportar dos veces el mismo mes es `uq_reporte_catalogo_periodo`, no una marca en
el registro.

### H-3 · Dos CHECK de evaluación de producto que nunca pueden dispararse

```
ck_evaluacion_no_objecion      : estado <> 'VIGENTE' OR NOT requiere_no_objecion OR (…)
ck_evaluacion_vigente_aprobada : estado <> 'VIGENTE' OR fecha_aprobacion IS NOT NULL
ck_evaluacion_riesgo_producto_estado : estado IN ('APROBADA','BORRADOR','EN_EVALUACION','RECHAZADA')
```

`'VIGENTE'` no está en el catálogo de estados. **R-LIC-04 no lo sostiene la base**: lo
exige CU-47. `CU47RechazosTest.rechazaRLIC04` lo demuestra insertando una fila con
`requiere_no_objecion = true` y sin fecha de aprobación — entra sin protesta.

### H-4 · Vocabularios que no se cruzan

| Dónde | El CU pide | El catálogo admite |
| --- | --- | --- |
| `evaluacion_riesgo_producto.estado` | EN_ELABORACION, EN_APROBACION, VIGENTE, OBSERVADA, CERRADA | APROBADA, BORRADOR, EN_EVALUACION, RECHAZADA |
| `regla_monitoreo_lft.accion_automatica` | SOLO_ALERTAR, RETENER, BLOQUEAR, RECHAZAR | SOLO_ALERTAR, RETENER_OPERACION, BLOQUEAR_CUENTA |
| `limite_operativo_billetera.ventana` | «mensual» | ANIO, DIA, MES, OPERACION, SEMANA |

Se usó el valor más cercano y verdadero en cada caso. Nada se inventó.

### H-5 · `instancia_reclamo` no guarda el pedido de información del supervisor

CU-53 pide que «su fecha límite quede guardada y aparezca en el tablero de vencimientos».
La tabla no tiene columna para el pedido ni para su plazo. Lo que sí alimenta el tablero
es la fecha de elevación y el número de expediente.

### H-6 · No hay índice único para la instancia abierta

`alerta_temprana` tiene `uq_alerta_temprana_abierta` (índice parcial). `instancia_reclamo`
no tiene equivalente, así que dos elevaciones simultáneas ante el mismo organismo solo
las separa la comprobación previa. `CU53Test.concurrencia` afirma el rango real (1 ó 2).

### H-7 · El `codigo` de `hallazgo_auditoria` es VARCHAR(20)

Corto para llevar el código del catálogo de reportes y el período juntos. Se prioriza el
período —que es lo que distingue un vencimiento de otro— y del código del catálogo entra
lo que quepa.

### H-8 · CU-05 sigue apuntando a `openapi/identidad.yaml`

Ya declarado y resuelto en `carril-0T.md`: CU-05 cambió de dueño a cumplimiento porque
`aceptacion_contrato` y `contrato_adhesion` viven en su esquema (invariante 11). La
cabecera del CU no se actualizó porque `docs/` es troncal, y por eso
`verificar_criterios.py` lo sigue listando como pendiente en identidad.

### H-9 · Los tres umbrales de UIF que sí se pueden guardar

Ya declarado en el carril de transparencia (H-10 de aquel informe) y confirmado aquí:
`regla_impacto_evento` siembra 17 tipos y `ck_evento_reputacion_tipo` admite 19, con solo
tres en común. No afecta a este carril directamente, pero es la misma clase de defecto y
conviene arreglarlos juntos.

## Un defecto propio, encontrado y corregido

**CU-47 aceptaba una licencia revocada.** Comprobaba `licencia.vigente()`, que solo mira
la fecha de fin, y no el estado. Una licencia `REVOCADA` con vigencia futura pasaba el
control y habilitaba el lanzamiento de un producto. Lo encontró
`CU47RechazosTest.rechazaRLIC01` al revocar la licencia y esperar el rechazo. Ahora exige
las tres condiciones: `estado.habilitaServicioFinanciero()` —solo OTORGADA—, vigencia no
vencida, y el servicio dentro del alcance.

## Lo que se construyó

| Nivel | Piezas |
| --- | --- |
| Átomos (9) | `EvaluacionDeLimites` · `UmbralAlcanzado` · `ConceptoRog` · `ArchivoRegulatorio` · `ExpresionDeRegla` · `RiesgoDelProducto` · `QuorumDeComite` · `ObjetivosDeContinuidad` · `CoberturaDeCapacitacion` · `PlazoDelReclamo` |
| Moléculas (7 nuevas) | `OperacionRelevanteRepositorio` · `ReporteRegulatorioRepositorio` · `MonitoreoLftRepositorio` · `RequerimientoRepositorio` · `EvaluacionProductoRepositorio` · `ReclamoRepositorio` · `ContinuidadRepositorio` · `GobiernoRepositorio` |
| Organismos (13) | `CU40EvaluarLimites` … `CU94ElevarAlComite` |

`ConceptoRog` **no reinterpreta la norma**: reproduce exactamente `fn_uif_concepto` de la
bóveda, y `CU42RechazosTest.rechazaRUIF02` lo verifica llamando a la función de la base y
comparando. Si difirieran, el mismo hecho iría a dos formularios distintos.

## Lo que se movió a configuración

El barrido marcó tres cifras dentro del dominio, y tenía razón: los cortes de riesgo de
producto (15 y 8 sobre probabilidad × impacto) pasaron a `RiesgoDelProducto.Escala`,
inyectada en CU-47. Mover el corte de «alto» cambia qué productos exigen control
declarado, y esa palanca es de la política de riesgos, no de un `if`.

Los demás parámetros de política del carril viven juntos y con nombre en
`PoliticaDeCarril`: el techo de tráfico de una regla de monitoreo, los días hábiles de
respuesta a un reclamo, el tope de la prórroga, los roles incompatibles con ser oficial
de cumplimiento y los plazos de investigación por severidad.

## Lo que este carril NO hizo

- **No hay capa `web/` para los trece CU nuevos.** El controlador existente cubre los
  seis anteriores; el contrato de los nuevos está escrito y los organismos son
  invocables, pero falta el adaptador HTTP.
- **No hay `trabajos/` nuevos.** CU-43 nombra un control diario de vencimientos, CU-45
  otro para oficios vencidos, CU-48 uno que escala alertas sin analista, CU-49 el control
  mensual de capacitación, CU-52 el de reclamos vencidos, CU-56 el de pruebas vencidas y
  CU-94 el de comités que no sesionan. Los repositorios tienen los métodos de consulta
  (`vencidosSinEnviar`, `vencidosSinResponder`, `abiertasSinAnalista`, `conPruebaVencida`,
  `procesosSinPlan`); falta cablearlos con `@Scheduled` + ShedLock.
- **No se escribe `auditoria.reporte_operacion_sospechosa` ni el registro de accesos**
  (invariante 11). El ROS llega ya radicado y el acceso se pide por evento con el número
  de oficio como justificación.
- **CU-40 decide, no descuenta.** El consumo del límite lo escribe `nucleo-financiero` al
  aplicar la operación, con el `FOR UPDATE` de `fn_lim_evaluar`. `CU40Test.concurrencia`
  afirma lo que este servicio garantiza, no lo que garantiza el otro.

## Preguntas que ninguna máquina responde

**¿Los nombres dicen lo que las cosas son?** Con una excepción heredada:
`motivo_exencion` se usa en el flujo 5a del CU-41 para explicar por qué *no* hay
declaración, aunque `exento` siga en false. El nombre dice una cosa y el uso otra, y esa
ambigüedad es parte de lo que produce el hueco H-1.

**¿La frontera transaccional es la correcta?** Sí. El caso dudoso era CU-43: el estado de
la conciliación y el envío al organismo son de fuera del proceso. El envío llega ya
resuelto (`aceptadoPorElOrganismo`) para no meter una llamada de red dentro de la
transacción (invariante 6).

**¿Qué supuse que no estaba en la bóveda?** Que un PCC-01 se escribe completo o no se
escribe (H-1), que el enlace reporte–registro se deriva del período (H-2), y que
`ORGANIZACION` y `USUARIO` son los valores más cercanos a lo que los CU llaman
`COLABORACION` y `PARTICIPANTE`.

**¿Qué dejé peor de como lo encontré?** Nada. Y encontré un agujero propio —CU-47
aceptando una licencia revocada— antes de que llegara a ninguna parte.
