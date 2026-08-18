# Plan de implementación — Portal Administrativo, Contabilidad ERP y Publicidad

> [!important] Este documento queda subordinado al plan por fases
> Auditoría del 2026-08-18 (ver `planes/20 · Saneamiento del plan` §9): este archivo
> **deja de ser una fuente de verdad paralela**. Es
> **documentación derivada** del descubrimiento y la arquitectura de los módulos 13 y
> 14; manda el plan maestro por fases.
>
> - Su numeración propia ("fase 5 de 12") **no rige**. Sus **fases 5–12** se convierten
>   en el contenido de las fases **18** (backend contabilidad ERP · carril 5A) y **19**
>   (backend publicidad · carril 5B) del plan maestro, y de **F13** (backoffice ERP) y
>   **F14** (backoffice publicidad), en los tramos **T8–T9** de
>   `planes/17 · Plan de acción secuencial` §5.
> - El **contrato es OpenAPI 3.1**, no Zod (ADR-020): donde este documento diga
>   "contrato Zod" se lee el contrato OpenAPI 3.1 del caso de uso.
> - **`apps/backoffice` no existe todavía**: lo levantan los carriles de frontend
>   (shell F6, luego F13/F14). Este documento no lo da por existente.

Fase actual: 5 de 12
Fases completadas: 1, 2, 3, 4
Fases restantes: 5-12
Objetivo de la fase 4 (cerrada): restricciones de base de datos, roles, permisos
y catálogos mínimos de los módulos 13 y 14, todo verificado contra PostgreSQL real.
Gate de salida (fase 3): **aprobado sin excepciones.** La prueba de humo que
había quedado pendiente corrió el 2026-08-16 contra `postgres:16` en Docker:
`aplicar.sql` termina en `COMMIT` sobre base recién creada y la prueba da
**151 `OK`, 0 `FALLA`**.
Gate de salida (fase 4): **aprobado.** Ver §2.2.
Gate de entrada (fase 5): aprobado.

## 0. Alcance decidido con el usuario

Tres decisiones de producto fijan el diseño (respondidas 2026-08-14):

1. **Anunciantes del módulo de publicidad**: tanto negocios externos como
   organizadores pueden anunciar. Se modelan de forma unificada como **partners**
   que solicitaron el servicio publicitario — un `anunciante` con un tipo
   (`ORGANIZADOR` | `SOCIO_COMERCIAL`) y FK condicional al `organizador` existente
   o a un `socio_comercial` nuevo (negocio externo sin cuenta de usuario operativa
   en la plataforma).
2. **Portal administrativo**: se construye sobre `apps/backoffice` (web) —que **aún
   no existe**: lo levantan los carriles de frontend (shell F6, luego F13/F14)— y que
   servirá a cumplimiento/soporte/contabilidad. No se construye una app móvil de
   administración nueva en esta ronda.
3. **Profundidad contable**: ERP financiero completo — plan de cuentas
   jerárquico, centros de costo, presupuestos, activos fijos y depreciación,
   cuentas por pagar/cobrar formales, cierre por período y estados financieros,
   sobre la base de `cuenta_contable`/`asiento_contable`/`movimiento_contable`
   que ya existen en el módulo 03.

## 1. Descubrimiento (fase 1 — cerrada)

Hallazgos relevantes de la bóveda actual, para no duplicar:

- `cuenta_contable` / `asiento_contable` / `movimiento_contable` (módulo 03) ya
  implementan partida doble para las operaciones de billetera (`contabilidad-partida-doble`).
  `cuenta_contable` es **plana** (sin jerarquía padre/hijo, sin tipo detallado de
  balance/resultado): el ERP la extiende, no la reemplaza.
- `campana_promocional` / `aplicacion_promocion` (módulo 11) son **descuentos de
  comisión** para usuarios, no publicidad in-app. No hay ninguna entidad de
  anuncios, segmentación de audiencia, inventario de espacios o facturación
  publicitaria en el modelo. El módulo 14 es enteramente nuevo.
- `organizador` (módulo 07) existe y tiene invariante **RN-18: no percibe
  comisión** por administrar. El módulo de publicidad **no puede violar RN-18**:
  cuando el anunciante es un organizador, paga él por publicitar su grupo — la
  plataforma cobra, el organizador no recibe nada por ser publicitado más que
  visibilidad. `anunciante` referencia a `organizador` solo como *quien contrata*,
  nunca como beneficiario de un ingreso.
- `apps/backoffice` **está por construirse** como portal administrativo web
  (`web-backoffice`): tablas densas, permisos por servidor, motivo obligatorio,
  nada editable. Los módulos 13 y 14 heredan ese patrón, no inventan uno nuevo.
- Roles y permisos (`roles-y-accesos`) ya tienen el patrón
  `<RECURSO>_<ACCION>` con ámbito `PLATAFORMA`/`GRUPO` y segregación de funciones
  explícita. Los permisos nuevos (`CONTABILIDAD_ERP_*`, `PUBLICIDAD_*`) siguen
  ese catálogo; se agregan en fase 4, no en esta.
- No se detectaron contradicciones en la bóveda existente que bloqueen el
  agregado — es aditivo.

## 2. Arquitectura (fase 2 — cerrada)

### Módulo 13 — Contabilidad Financiera y ERP

> Foco: que cerrar un mes no dependa de un Excel armado a mano.

Extiende `cuenta_contable` (módulo 03) con jerarquía (`cuenta_padre_id`, `nivel`,
`es_cuenta_movimiento`, `tipo_saldo`) y agrega, en `13_contabilidad_erp.puml`:

| Tabla | Qué resuelve |
| --- | --- |
| `ejercicio_fiscal` | Año contable, con apertura/cierre. |
| `periodo_contable` | Mes dentro de un ejercicio; abierto/cerrado; nada se asienta en uno cerrado. |
| `cierre_periodo_contable` | El acto de cerrar: quién, cuándo, saldos que arrastra. |
| `centro_costo` | Unidad de costeo (área, producto, campaña) independiente de la cuenta contable. |
| `presupuesto` | Cabecera de presupuesto por centro de costo y ejercicio. |
| `partida_presupuestaria` | Línea de presupuesto por cuenta contable y período, con ejecutado derivado. |
| `tercero_comercial` | Proveedor o cliente comercial (no es `usuario` ni `proveedor_pago`: es a quien la empresa le compra o le vende fuera del pasanaku). |
| `orden_compra` | Compra a un tercero, previa a la factura. |
| `factura_proveedor` | Cuenta por pagar: factura recibida de un tercero. |
| `pago_a_proveedor` | Egreso que cancela una factura de proveedor (total o parcial). |
| `cuenta_por_cobrar` | Cobro pendiente a un tercero (ej. facturación de publicidad, módulo 14). |
| `cobro_cuenta_por_cobrar` | Ingreso que cancela una cuenta por cobrar. |
| `activo_fijo` | Bien de uso: costo, vida útil, método de depreciación. |
| `categoria_activo_fijo` | Catálogo de categorías con vida útil y cuenta contable por defecto. |
| `depreciacion_activo` | Corrida de depreciación por período, append-only. |
| `asiento_plantilla` | Plantilla de asiento recurrente (alquiler, planilla) para no recapturar cada mes. |
| `estado_financiero_generado` | Snapshot de balance general o estado de resultados por período, reproducible. |

`asiento_contable.origen_tipo` gana los valores `FACTURA_PROVEEDOR`,
`COBRO_CxC`, `DEPRECIACION_ACTIVO` para que todo movimiento de este módulo siga
generando su asiento en la partida doble existente — **no hay un segundo libro
mayor**, hay un solo `asiento_contable`/`movimiento_contable` alimentado desde
más orígenes.

### Módulo 14 — Publicidad y Campañas (Ads)

> Foco: que un partner pueda anunciarse dentro de la app y la plataforma cobre
> por eso sin inventar un segundo sistema de facturación.

Fusiona los conceptos de PedidosYa/Yango Ads (espacio de inventario propio de la
app: banner de inicio, listado destacado de grupos, notificación patrocinada) con
el modelo de Meta Ads (jerarquía cuenta → campaña → conjunto de anuncios →
anuncio, segmentación por audiencia, puja y presupuesto por conjunto, métricas de
entrega). Nuevo `14_publicidad_campanas.puml`:

| Tabla | Qué resuelve |
| --- | --- |
| `socio_comercial` | Negocio externo (no `organizador`, no `usuario` operativo) que contrata publicidad. |
| `anunciante` | Unifica quién anuncia: `tipo` (`ORGANIZADOR`\|`SOCIO_COMERCIAL`) + FK condicional a `organizador` o `socio_comercial`. |
| `cuenta_publicitaria` | Cuenta de facturación del anunciante: límite de gasto, moneda, estado. |
| `campana_publicitaria` | Objetivo, presupuesto total, vigencia, estado. |
| `conjunto_anuncios` | Presupuesto diario, puja, segmentación y espacio dentro de una campaña (equivalente al *ad set* de Meta). |
| `segmento_audiencia` | Criterio de targeting (ubicación, nivel KYC, actividad en grupos, etc.) reutilizable entre conjuntos. |
| `espacio_publicitario` | Catálogo de inventario propio: `BANNER_INICIO`, `LISTADO_GRUPOS_DESTACADO`, `PUSH_PATROCINADO`, con capacidad máxima simultánea. |
| `pieza_creativa` | Imagen/copy/video de un anuncio, con estado de moderación. |
| `anuncio` | Une un conjunto con una pieza creativa concreta y su estado de entrega. |
| `revision_creativa` | Aprobación/rechazo de una pieza creativa, con motivo — nada se publica sin revisión. |
| `impresion_anuncio` | Evento append-only de exhibición. |
| `clic_anuncio` | Evento append-only de clic. |
| `conversion_anuncio` | Evento atribuido (ej. postulación a grupo, registro) ligado a una impresión o clic. |
| `factura_publicidad` | Liquidación periódica de gasto de una cuenta publicitaria; genera la `cuenta_por_cobrar` del módulo 13. |

Reglas de diseño que evitan romper invariantes existentes:

- Ninguna tabla de este módulo apunta a `organizador` como beneficiario de un
  ingreso: `anunciante` es quien **paga**, igual que en M11 nadie le paga al
  organizador (RN-18, ver `07_organizador_automatizacion.md`).
- `impresion_anuncio` y `clic_anuncio` son de alto volumen: van a `APPEND_ONLY` y
  son candidatas a partición por fecha en fase 5, igual que `bitacora_evento`.
- La facturación no crea un tarifario paralelo: usa `factura_electronica` del
  módulo 11 para emitir el comprobante fiscal y `cuenta_por_cobrar` del módulo 13
  para el seguimiento del cobro. `factura_publicidad` es la liquidación de
  origen, no un tercer libro de facturación.

### Portal administrativo

No es una entidad nueva: es alcance de UI sobre lo anterior. `apps/backoffice` —que
levantan los carriles de frontend (shell F6)— recibe en **F13/F14** las pantallas de
gobierno de datos (roles, catálogos, auditoría) más los módulos 13 y 14. No requiere
ADR de arquitectura nueva; sigue `web-backoffice` tal cual está escrito.

## 2.1 Estado real de la verificación (2026-08-14)

Ejecutado en este entorno, en orden:

| Comando | Resultado |
| --- | --- |
| `python3 scripts/generar_boveda.py` | Verde. `sin_resolver: []`. 275 → **307 tablas**, 633 FK, 328 cruzan de módulo. |
| `python3 scripts/generar_ddl.py` | Verde. `Sin pendientes a nivel de datos.` 307 tablas · 633 FK · 701 índices · 425 CHECK. Semillas: 1127 filas validadas contra el modelo. |
| 12 casos de uso nuevos (`CU-100`..`CU-106`, `CU-101` a `CU-106` de M13; `CU-110`..`CU-114` de M14) | Escritos con la plantilla completa de `caso-de-uso`: 13 secciones, ≥3 Gherkin, ≥4 flujos alternativos, contrato OpenAPI 3.1 con errores correlativos `AP-CU<NN>-<nn>`. 87 → **99 casos de uso**. |
| `scripts/verificar_boveda.py` | Requería códigos de 3 dígitos (`CU-100`+), que rompían un supuesto de ancho fijo (`stem[3:5]`) en el script. Se corrigió para extraer el número con regex; 2 dígitos existentes no cambian de comportamiento. |
| `python3 scripts/verificar_boveda.py` (segunda corrida) | **`TODO OK`.** 99 casos de uso, 307 entidades todas cubiertas, 124 restricciones balanceadas, índice de casos y de skills completos, cero wikilinks rotos. |
| Prueba de humo contra Postgres real (`docker run postgres:16` + `aplicar.sql`) | **No ejecutada**: este entorno no tiene `docker` ni `psql` disponibles. Es la única verificación de esta fase que queda pendiente — corre en cualquier entorno con Docker, antes de empezar la fase 4. |

**Decisiones tomadas al escribir los CU nuevos**: se abrieron los rangos
`CU-100..109` (contabilidad ERP) y `CU-110..119` (publicidad), documentados en
la skill `caso-de-uso` y en `docs/CasosDeUso/_CasosDeUso.md`. Las
restricciones citadas (`R-AUD-01`, `R-AUD-05`, `R-AUD-06`, `R-SEG-04`) son
todas ya existentes — ningún caso nuevo inventó una restricción sin
implementación real de base de datos detrás; agregar los `CHECK`/trigger que
las hagan cumplir en las tablas nuevas queda para la skill `restriccion` en la
fase 4.

## 2.2 Fase 4 — restricciones, roles y catálogos (2026-08-16)

### Restricciones nuevas

Dos familias nuevas en `docs/Restricciones.md`, 124 → **138 restricciones**:

| Familia | Qué garantiza |
| --- | --- |
| `R-CTB-01`..`R-CTB-08` | Un período por ejercicio y mes y **nada se asienta en uno cerrado** (trigger); una cuenta sumarizadora no recibe movimientos; un presupuesto por centro de costo y ejercicio; una factura por proveedor y número con saldo acotado; **quien aprueba una factura no autoriza su pago** (trigger); no se cobra más de lo que se debe; una depreciación por activo y período; un estado financiero por período y tipo. |
| `R-PUB-01`..`R-PUB-06` | Un anunciante es organizador **o** socio comercial, nunca ambos ni ninguno; una cuenta publicitaria por anunciante con gasto acotado al límite; consumo de campaña ≤ presupuesto y aprobación con responsable; **ninguna pieza creativa sin revisión aprobada llega a un anuncio** (trigger); quien sube no modera; un período de facturación por cuenta publicitaria. |

Cada restricción se probó **por su rechazo**, como exige la skill `restriccion`:
22 casos nuevos en `sql/50_verificacion/prueba_humo.sql`, más casos positivos
donde el lado feliz también importa (un pagador distinto sí puede pagar; un
movimiento contra una cuenta de movimiento sí entra).

### Roles, permisos y segregación

`seeders/minimos/10-roles-y-permisos.json`: 3 roles nuevos
(`OPERADOR_PUBLICIDAD`, `MODERADOR_CONTENIDO`, `ANUNCIANTE`), 13 permisos
(`CONTABILIDAD_ERP_*` ×8, `PUBLICIDAD_*` ×5) y 14 asignaciones. La segregación
se sostiene en el reparto, no solo en el trigger: `CONTABILIDAD` aprueba la
factura y `TESORERIA` autoriza el pago, y **ningún rol acumula los dos
permisos** (verificado por consulta contra la base). Los dos pares nuevos se
agregaron a la tabla explícita de la skill `roles-y-accesos`.

### Catálogos

- `seeders/minimos/21-contabilidad-y-publicidad.json` (nuevo): 5 centros de
  costo, 3 categorías de activo fijo con sus tres cuentas contables, y los 4
  espacios publicitarios del inventario de la app.
- `01-plan-de-cuentas.json`: 19 → **33 cuentas**. Se agregaron las 14 cuentas
  mayores (niveles 1 y 2) y se marcó la jerarquía completa.

### Dos defectos encontrados y corregidos en esta fase

1. **Todas las cuentas del plan quedaban como sumarizadoras.**
   `es_cuenta_de_movimiento` es `BOOLEAN NOT NULL` y el generador de DDL le
   asigna `DEFAULT FALSE`; el seeder no traía la columna, así que las 19
   cuentas se sembraban con `false` y `R-CTB-02` habría bloqueado **todo
   asiento contable del sistema**. Se corrigió marcando explícitamente las
   cuentas hoja como cuentas de movimiento.
2. **`$ref` a la propia tabla no resolvía** (`scripts/generar_semillas.py`).
   El generador emitía un único `INSERT` multi-fila, y los subselects de
   `$ref` se evalúan contra el estado previo a la sentencia: ninguna fila veía
   a las anteriores, así que `cuenta_padre_id` quedaba en `NULL` en toda la
   jerarquía **sin que nada fallara**. Ahora, cuando un bloque se
   autorreferencia, se emite una sentencia por fila. Verificado: cero cuentas
   de nivel > 1 sin padre.

Ambos son del tipo que no rompe nada al aplicar y aparece meses después: por
eso la verificación de esta fase no se quedó en "el DDL aplica".

### Evidencia (corrida limpia, 2026-08-16)

| Paso | Resultado |
| --- | --- |
| `generar_boveda.py` | 307 entidades · 633 FK · `sin_resolver: []` |
| `generar_ddl.py` | 307 tablas · 633 FK · 701 índices · 425 CHECK · `Sin pendientes a nivel de datos.` · 1183 filas de semilla validadas |
| `verificar_boveda.py` | **`TODO OK`** — 99 casos de uso, 138 restricciones todas citadas, cero wikilinks rotos |
| `aplicar.sql` sobre `postgres:16` recién creada | `COMMIT` |
| 21 archivos de semillas mínimas | cargados sin error |
| `prueba_humo.sql` | **151 `OK`, 0 `FALLA`** |
| `verificaciones.sql` (consultas de control) | las 11 devuelven **cero filas** |

## 3. Fases restantes

| # | Fase | Cierra cuando |
| --- | --- | --- |
| 3 | **(esta fase)** Esquema: `.puml` de M13/M14, extensión de `cuenta_contable`, registro en `scripts/modelo.py`, semillas mínimas | `verificar_boveda.py` → `TODO OK`, `generar_ddl.py` → `Sin pendientes`, prueba de humo en verde |
| ~~4~~ | ~~Roles, permisos y catálogos~~ | **Cerrada** (2026-08-16). Ver §2.2 |
| 5 | Contratos por caso de uso (OpenAPI), derivados de los CU escritos en el cierre de la fase 3 | `EntradaCUNN`/`SalidaCUNN`/`ErroresCUNN` con códigos `AP-CU<NN>-<nn>` para cada CU nuevo de M13/M14 |
| 6 | Persistencia (jOOQ) | Repositorios + pruebas de molécula contra Postgres real |
| 7 | Casos de uso y reglas de negocio | Criterios de aceptación como pruebas (`pruebas-cu`) |
| 8 | API, permisos y OpenAPI | Pruebas negativas de permisos en verde |
| 9 | Worker/outbox: cierre de período automático, liquidación de publicidad periódica, depreciación mensual | Reintento/duplicado probados (`trabajos-outbox`) |
| 10 | Frontend: `apps/backoffice` — pantallas de M13/M14 | Cuatro estados por pantalla, tablas con exportación registrada |
| 11 | Observabilidad y rendimiento | Métricas de cierre contable y de entrega publicitaria |
| 12 | Cierre: documentación y auditoría | Matriz de `definicion-de-terminado` sin gate crítico rojo |

## 4. Riesgos declarados

| Riesgo | Impacto | Mitigación |
| --- | --- | --- |
| `impresion_anuncio`/`clic_anuncio` crecen sin techo | Tabla no particionada satura el índice | Particionar por fecha en fase 5 (mismo patrón que `bitacora_evento`) |
| Doble contabilización si M14 y M13 no comparten un solo asiento | Descuadre en `movimiento_contable` | `factura_publicidad` → `cuenta_por_cobrar` → `asiento_contable`, un solo camino, sin atajos |
| Un anunciante organizador podría interpretarse como ingreso propio | Viola RN-18 | `anunciante` nunca es destino de un movimiento a favor del organizador; solo origen de pago |

## 5. Cambios de alcance

Ninguno todavía. Si aparece un requisito nuevo (ej. reportes de rendimiento de
campaña en tiempo real, atribución multi-touch), se evalúa contra fase 9/11 antes
de tocar el esquema de nuevo.
