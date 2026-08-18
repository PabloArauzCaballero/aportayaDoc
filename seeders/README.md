# Seeders

Los datos de carga inicial, en JSON, separados en dos conjuntos con destinos
distintos:

```
seeders/
├── minimos/     catálogos sin los cuales el sistema NO opera. Van también a producción.
└── prueba/      datos de demostración para desarrollo y QA. Nunca a producción.
```

Los JSON son **la fuente de verdad**. El SQL de `sql/60_semillas/` y
`sql/61_prueba/` es un derivado:

```bash
python3 scripts/generar_semillas.py
```

## Por qué dos conjuntos

Con los catálogos vacíos el sistema no opera: la regla de *denegar por omisión*
rechaza toda operación sin límite configurado, sin tarifario y sin licencia
(`R-LIM-01`, `R-LIC-01`). Eso significa que **los mínimos no son opcionales**:
son parte del despliegue, igual que el esquema.

Los datos de prueba son lo contrario: existen para poder ejercitar los flujos en
local y en QA, y no deben tocar producción jamás.

## `minimos/` — también en producción

| Archivo | Carga | Estado |
| --- | --- | --- |
| `01-plan-de-cuentas.json` | 48 cuentas contables, 30 de movimiento | listo |
| `02-politicas.json` | billetera, redondeo, mora y cobertura | revisar con riesgos |
| `03-limites-operativos.json` | 40 límites por nivel de debida diligencia | ⚠ **PROVISIONAL** |
| `04-tarifario.json` | hechos generadores, tarifario v1, conceptos, tramos, segmentos y asignaciones | decisión comercial |
| `05-impuestos.json` | IVA e IT | ⚠ confirmar con tributaria |
| `06-umbrales-uif.json` | 18 umbrales de los arts. 52 y 53 del instructivo vigente | ⚠ confirmar vigencia |
| `07-reportes-regulatorios.json` | calendario de 10 reportes obligatorios | ⚠ confirmar plazos |
| `08-gobierno-y-licencia.json` | licencia, comités, puntos de reclamo, matriz de riesgo, tipologías | listo |
| `09-reglas-operativas.json` | 8 reglas de entrega y 5 antifraude | revisar con riesgos |
| `10-roles-y-permisos.json` | 15 roles, 29 permisos y la matriz que los une | listo |
| `11-contratos-de-adhesion.json` | 4 contratos en borrador | redactar y registrar ante ASFI |
| `12-calendario-habil.json` | 33 feriados nacionales de 2026 a 2028 | listo — ampliar cada año |
| `13-politicas-de-token.json` | 13 políticas de token, una por propósito | listo |
| `14-proveedores-externos.json` | 6 rieles de pago y 5 de mensajería, todos inactivos | ⚠ **PROVISIONAL** |
| `15-eventos-y-plantillas.json` | 33 eventos notificables, 18 plantillas y sus versiones | textos por aprobar |
| `16-reputacion-y-scoring.json` | modelo v1, 6 factores, 17 reglas de impacto, 8 insignias | ⚠ **PROVISIONAL** |
| `17-organizador-y-emparejamiento.json` | 13 requisitos de habilitación, criterio de emparejamiento, 8 reglas de automatización | revisar con riesgos |
| `18-sanciones-y-cobranza.json` | política de sanción, 23 filas de matriz y 6 etapas de cobranza | revisar con riesgos y legal |
| `19-reportes-y-retencion.json` | 10 definiciones de reporte, 5 programaciones, 19 políticas de retención, 11 reglas de cumplimiento, 20 umbrales, 5 listas | ⚠ **PROVISIONAL** |
| `20-control-interno-y-continuidad.json` | 12 políticas internas, 16 controles, 12 activos, 5 planes de continuidad, 6 documentos publicados | ⚠ **PROVISIONAL** |
| `21-contabilidad-y-publicidad.json` | 5 centros de costo, 3 categorías de activo fijo y 4 espacios publicitarios | revisar con contabilidad |

Cada archivo lleva su propio campo `estado`, `advertencia` o `revisar_con`: los
valores marcados **PROVISIONAL** no deben usarse en producción sin confirmación
legal, y cada fila tiene su columna `base_normativa` esperando la cita exacta.

> [!warning] Tres catálogos se siembran deliberadamente "apagados"
> La **licencia** va `EN_TRAMITE`, los **proveedores externos** van `activo = false`
> y las **políticas internas** van `EN_APROBACION`. Son los tres estados reales
> mientras no exista resolución de ASFI, contrato firmado con el proveedor y acta
> de directorio. Con ellos así, la regla de denegar por omisión hace que la
> plataforma no opere — que es exactamente lo correcto. Encenderlos con un seeder
> para "poder probar" convierte un dato falso en la base de un reporte.
>
> El archivo de licencia trae, en `al_otorgarse_la_licencia`, el `UPDATE` que
> corresponde el día que ASFI resuelva.

Tres catálogos tienen una desviación anotada, y conviene conocerla antes de usarlos:

- **Activo fijo y custodia** (`21` con `01`): las tres categorías de activo fijo
  imputan a `1.3.x`, su depreciación acumulada a `1.3.5x` —regularizadoras del
  activo: van bajo `ACTIVO` con naturaleza `ACREEDORA` a propósito— y el gasto a
  `5.3.01`. Ninguna puede apuntar a `1.1.01`: esa es la cuenta de custodia que
  respalda el dinero electrónico de los clientes, y meterle el activo propio de
  la plataforma rompe el ratio de cobertura. La base no lo impide —las tres son
  cuentas de movimiento y pasan `R-CTB-02`—, así que la garantía es esta semilla.

- **Feriados departamentales** (`12`): `dia_no_habil` no tiene columna de
  departamento, así que solo se siembran los nacionales. Sembrar los
  departamentales con alcance `DEPARTAMENTAL` los volvería no hábiles para todo
  el país y correría mal cada plazo legal.
- **Umbrales operativos** (`19`): `umbral_operativo` mide y `limite_operativo_billetera`
  (archivo `03`) impide. Si los dos números divergen manda el del `03`, y la
  divergencia es un hallazgo a corregir, no una configuración válida.

## `prueba/` — solo desarrollo y QA

| Archivo | Carga |
| --- | --- |
| `01-entorno-tecnico.json` | serie diaria de tipo de cambio (−60 a +30 días), cuentas técnicas de plataforma y cuenta de custodia |
| `02-usuarios-y-billeteras.json` | 6 clientes con identidad, KYC, debida diligencia, perfil, contrato aceptado y billetera |
| `03-grupo-demo.json` | organizador con contrato firmado y capacitación, grupo de 6 cupos con Bs 500 mensuales, sorteo verificable, reglamento aceptado por los seis, 6 períodos y las 18 obligaciones |
| `04-fondo-y-cuenta-del-grupo.json` | fondo de garantía y la cuenta de billetera del grupo |
| `05-personal-interno-y-gobierno.json` | 3 personas de backoffice con rol, dispositivos de confianza, actas de comité, oficial de cumplimiento y designaciones regulatorias |
| `06-instrumentos-y-recargas.json` | instrumentos de fondeo verificados, 2 puntos de atención, extracto bancario y las 6 recargas con su movimiento de custodia |
| `07-aportes-y-entrega.json` | constitución del fondo, 6 aportes del período 1, cotización, devengo, impuestos, entrega autorizada y ejecutada por personas distintas, y factura |
| `08-cobros-qr-y-conciliacion.json` | órdenes de cobro con QR, intentos, webhooks firmados, conciliación contra el extracto y constancias |
| `09-mora-cobertura-y-cobranza.json` | expediente de incumplimiento notificado, cobertura del fondo, deuda subrogada, gestión de cobranza y descargo sin resolver |
| `10-notificaciones-cierre-y-reclamos.json` | canales y preferencias, avisos con prueba de entrega, cierre diario, conciliación de custodia, arqueo con faltante, reclamo con plazo y evento de riesgo operativo |
| `11-contabilidad-y-saldos.json` | 25 asientos contables con sus 53 partidas, consumo de límites, saldos diarios sellados y estados de cuenta |
| `12-retiro-y-controles.json` | cuentas bancarias verificadas, desembolso devuelto por el banco con su incidencia, retiro retenido, evaluación antifraude y bloqueo por orden de autoridad |
| `13-cumplimiento-uif.json` | origen de fondos, revisión periódica de KYC, desvío de perfil, alerta escalada a caso, falso positivo de lista y reporte regulatorio con acuse |
| `14-identidad-y-sesiones.json` | direcciones, perfil financiero, consentimientos, segundo factor, sesiones, intentos fallidos, reputación calculada y restricción por incumplimiento |

> [!warning] El set no cubre todavía los módulos 13 y 14
> Contabilidad ERP y publicidad entraron al modelo después de que se armara este
> conjunto: de sus 32 tablas, las 29 que no son catálogo —ejercicio y período contable, terceros,
> facturas de proveedor, activos fijos, anunciantes, campañas y piezas— siguen
> sin un solo dato de prueba. Las comprobaciones `R-CTB-*` y `R-PUB-*` pasan en
> la prueba de humo porque el propio humo se siembra sus filas, no porque el set
> las ejercite. Los catálogos mínimos de los dos módulos (archivo `21`) sí están.

### La historia que cuenta el set

El grupo `GRP-DEMO-01` arrancó hace 45 días y está a mitad de camino:

- **Período 1** (cerrado): entraron Bs 7.400 por recargas, los seis constituyeron
  el fondo con Bs 100 cada uno, los seis aportaron Bs 500 y el turno 1 cobró la
  bolsa de Bs 3.000 menos Bs 10,50 de comisión —de los cuales Bs 1,69 son IVA e
  IT—, con factura emitida.
- **Período 2** (vencido hace 8 días): cinco pagaron por QR interoperable y
  `USR000004` no pagó. Su obligación acumuló Bs 10 de recargo, el fondo cubrió los
  Bs 500 para que el grupo no se frenara y quedó una deuda de Bs 510 a su nombre,
  con el descargo presentado y todavía sin resolver.

**La partida doble cierra en cero.** La suma de todas las cuentas de billetera es
exactamente Bs 0,00 y la custodia tiene Bs 9.900 contra Bs 9.900 de dinero
electrónico emitido: ratio de cobertura 1,000000.

### Reglas que el set permite ejercitar

Cada archivo declara en `reglas_que_permite_probar` qué se puede verificar con él.
Las más caras de reproducir a mano:

| Regla | Cómo la ejercita el set |
| --- | --- |
| `R-ORG-02` | Sin `contrato_organizador` firmado y vigente el grupo no se crea: por eso el archivo `03` siembra el token de firma y el contrato **antes** que el grupo |
| `R-UIF-04` | Las transacciones tienen fecha histórica, así que hay serie diaria de tipo de cambio; sin ella la base rechaza registrar la operación |
| `R-BIL-01` | Las 25 transacciones cuadran débitos contra créditos, incluida la de entrega, que tiene cuatro movimientos |
| `R-SEG-04` y `R-SEG-07` | Riesgos autoriza la entrega y tesorería la ejecuta; nadie se otorga un rol a sí mismo |
| `R-CON-05` | El reclamo se conserva 10 años desde el ingreso, y el plazo de respuesta se guarda al ingresar |
| `R-UIF-10` | `USR000001` es persona expuesta políticamente: debida diligencia `REFORZADA` con segunda revisión independiente |
| `R-GRP-04` | La cuenta del grupo tiene al grupo como titular, nunca al organizador |
| `R-AUD-01` | `transaccion_billetera` es append-only: por eso el asiento contable apunta a la transacción y no al revés |
| `R-BIL-03` | El saldo lo mantienen los triggers; ningún seeder lo escribe a mano |
| `R-UIF-06/07` | Un reporte en cero coincide con su cantidad de registros; una alerta no se cierra sin conclusión de 20 caracteres |

> [!note] Dos cosas que el seeder **no** hace, a propósito
> No escribe saldos —los mantienen `tg_movimiento_sincroniza_saldo` y
> `tg_retencion_sincroniza_saldo`— y no cierra el enlace
> `transaccion_billetera.asiento_contable_id`, porque la tabla es append-only y
> el `UPDATE` se rechaza. En la aplicación, el caso de uso debe crear el asiento
> **antes** de insertar la transacción y pasarle el id ya generado.

## Formato

```json
{
  "descripcion": "Para qué sirve este archivo",
  "entorno": "minimo",
  "bloques": [
    {
      "tabla": "cuenta_contable",
      "conflicto": ["codigo"],
      "comentario": "opcional, se emite como comentario SQL",
      "filas": [ { "codigo": "1.1.01", "nombre": "…" } ]
    }
  ]
}
```

| Clave del bloque | Efecto |
| --- | --- |
| `conflicto: ["col"]` | `ON CONFLICT (col) DO NOTHING` |
| `conflicto: []` | `ON CONFLICT DO NOTHING` |
| `conflicto: "ninguno"` | sin cláusula |
| `solo_si_vacia: true` | solo inserta si la tabla está vacía (para tablas sin clave natural) |
| `sql: "UPDATE …"` | bloque de SQL suelto, sin `tabla` |

Valores especiales dentro de una fila:

| Valor | Se traduce a |
| --- | --- |
| `{"$ref": "tarifario", "codigo": "GENERAL", "version": 1}` | `(SELECT id FROM tarifario WHERE codigo=… AND version=…)` |
| `{"$sql": "now()"}` | se emite tal cual |
| `{"$fecha": "30 days"}` | `(current_date + interval '30 days')` |
| objetos y listas comunes | literal `jsonb` |

Las referencias pueden anidarse: `{"$ref":"participante","grupo_id":{"$ref":"grupo",...}}`.

Cuando la clave natural es un índice único por expresión, `conflicto` acepta la
expresión tal cual —`["fecha", "alcance", "COALESCE(grupo_id, '000…'::uuid)"]`—
porque se emite literal dentro del `ON CONFLICT`. Es lo que hace idempotente al
calendario de feriados: sin eso, la segunda corrida duplicaría cada día.

Las claves de nivel de archivo son documentación y el generador las ignora:
`descripcion`, `entorno`, `nota`, `estado`, `advertencia`, `revisar_con` y
`reglas_que_permite_probar` —esta última lista qué regla se puede ejercitar con
los datos de ese archivo, para que agregar datos de prueba obligue a decir para
qué sirven.

## Aplicar

```bash
# 1) esquema
psql -d aportaya -v ON_ERROR_STOP=1 -f sql/aplicar.sql
# 2) catálogos (también en producción)
psql -d aportaya -v ON_ERROR_STOP=1 -f sql/60_semillas/sembrar.sql
# 3) datos de prueba (nunca en producción)
psql -d aportaya -v ON_ERROR_STOP=1 -f sql/61_prueba/sembrar_prueba.sql
```

Los mínimos son idempotentes: volver a ejecutarlos no duplica nada.

### Evidencia de la última verificación

Sobre PostgreSQL 16 y base recién creada, aplicando esquema → mínimos → mínimos
otra vez → prueba → prueba de humo:

```
minimos  → sql/60_semillas: 21 archivos, 626 filas
prueba   → sql/61_prueba:   14 archivos, 572 filas
prueba de humo: 151 OK, 0 FALLA
tablas con datos: 173 de 382
suma de saldo_total de todas las cuentas de billetera: 0,00
transacciones de billetera con débitos ≠ créditos: 0
asientos contables con debe ≠ haber: 0 (25 asientos, 53 movimientos)
custodia: libro 9.900,00 · banco 9.900,00 · ratio de cobertura 1,000000
```

La segunda corrida de los mínimos no inserta ni una fila: si alguna vez falla ahí,
hay un bloque sin `conflicto` o con una clave natural que no existe.

## Usarlos desde la aplicación

El mismo JSON sirve para sembrar desde código sin transformarlo: cada bloque es una
tabla y cada fila un objeto, insertable con jOOQ tal cual. Hay que resolver los
`$ref` contra la tabla correspondiente antes de insertar y respetar el orden del
`manifiesto.json`, que es el orden en que las claves foráneas resuelven.

En las pruebas se aplica el SQL generado, no el JSON: es exactamente lo que corre
en producción, y probar otra cosa es probar otro sistema (skill `pruebas-cu`).
