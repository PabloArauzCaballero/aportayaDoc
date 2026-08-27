# AportaYa — Modelo de datos

**AportaYa** es un sistema para crear, administrar y seguir grupos de pasanaku,
**operado como una billetera móvil**: los participantes tienen saldo,
aportan y cobran desde la app, y la plataforma cobra una comisión pequeña por cada
juego. Esta rama contiene el **modelo de datos completo** del sistema.

Ese salto —de herramienta de organización a proveedor de servicios de pago— es lo
que explica los tres módulos finales: custodia del dinero con encaje verificable
(M10), motor de tarifas parametrizable con facturación (M11) y el aparato de
cumplimiento que un supervisor financiero espera encontrar implementado (M12).

La carpeta [`docs/`](docs/Index.md) es además una **bóveda de Obsidian**: ábrala con
`Abrir carpeta como bóveda` y empiece por `Index.md` para recorrer el modelo como
grafo, con una nota por tabla y una por clave foránea
([`docs/Modelos/`](docs/Modelos/Entidades/_Entidades.md), generadas con
`scripts/generar_boveda.py`).

Para cada módulo hay dos archivos en [`docs/entidades/`](docs/entidades/README.md):

- un **`.puml`** con dos diagramas: el **modelo de clases** (diseño orientado a
  objetos, con estereotipos DDD) y el **modelo relacional** (entidad-relación
  listo para traducir a DDL);
- un **`.md`** que documenta, entidad por entidad, **qué hace y por qué debería
  existir**: qué problema real del pasanaku resuelve, qué se rompería si se
  elimina, y qué papel cumple en el sistema.

Cubre el Documento de Requerimientos v2.0 (pago QR, WhatsApp, transparencia,
reputación), el Parche A (organizador digital automatizado, organizador humano sin
comisión y fondo de garantía) y el **Parche B: billetera móvil con custodia,
comisión de plataforma y cumplimiento regulatorio** (módulos 10 a 12).

## De la bóveda al código

La carpeta `docs/` no es solo documentación: es la **especificación ejecutable**
del sistema, en cuatro capas encadenadas.

| Capa | Dónde | Responde |
| --- | --- | --- |
| Norma | [`docs/Cumplimiento.md`](docs/Cumplimiento.md) | qué obliga ASFI, UIF, BCB, el SIN y las ISO |
| Caso de uso | [`docs/CasosDeUso/`](docs/CasosDeUso/_CasosDeUso.md) | cómo se ejecuta cada flujo, paso a paso — 99 casos |
| Restricción | [`docs/Restricciones.md`](docs/Restricciones.md) | qué impide, en la base, que se viole — 141 reglas |
| Seguridad | [`docs/Seguridad.md`](docs/Seguridad.md) | cómo se escribe el código para que resista un ataque, y con qué comando se comprueba cada control |
| Modelo | [`docs/Modelos/`](docs/Modelos/Entidades/_Entidades.md) | dónde vive cada dato |
| Esquema | [`sql/`](sql/README.md) | el DDL ejecutable, generado desde las tres capas anteriores |
| Arquitectura | [`docs/Arquitectura/`](docs/Arquitectura/_Arquitectura.md) | con qué se implementa, y por qué así |
| Stack | [`docs/Stack.md`](docs/Stack.md) | qué tecnología se eligió y qué alternativas perdieron |

```bash
python3 scripts/generar_boveda.py   # notas del modelo (Obsidian) desde los .puml
python3 scripts/generar_ddl.py      # esquema SQL completo desde los .puml + el catálogo
psql -d aportaya -v ON_ERROR_STOP=1 -f sql/aplicar.sql
psql -d aportaya -v ON_ERROR_STOP=1 -f sql/60_semillas/sembrar.sql
psql -d aportaya -f sql/50_verificacion/prueba_humo.sql   # 152 comprobaciones
```

El esquema son **305 tablas en un archivo cada una**, con las claves foráneas y los
índices en pasadas aparte —el orden que necesita la introspección de tipos— más el
sellado de las tablas append-only y el catálogo de restricciones. Verificado sobre
PostgreSQL 16: aplica sin errores y la prueba de humo confirma que las restricciones
rechazan lo que deben rechazar.

La **arquitectura** está en [`docs/Arquitectura/`](docs/Arquitectura/_Arquitectura.md):
una decisión por documento, cada una con el motivo por el que se tomó y lo que la
revertiría, más el [método](docs/Arquitectura/Método%20de%20arquitectura.md) con el
que se diseña siempre y tres [prompts generalistas](docs/Arquitectura/Prompts/_Prompts.md)
—general, backend y frontend— que imponen dividir todo en átomos, moléculas y
organismos.

Las **skills del proyecto** describen cómo se trabaja sobre esta bóveda, en siete
grupos: método y arquitectura, especificación, construcción, interfaz, dominio,
cumplimiento y control, y operación y entrega. El índice completo —con cuándo se usa
cada una— está en [`.claude/skills/README.md`](.claude/skills/README.md), y
`scripts/verificar_boveda.py` comprueba que ninguna quede fuera de ese índice.

Los lineamientos externos que se incorporaron —y los que se eliminaron por contradecir
una decisión ya tomada— están en
[Lineamientos adoptados y descartados](docs/Arquitectura/Lineamientos%20adoptados%20y%20descartados.md).

## Cumplimiento normativo

El contraste requisito por requisito contra ASFI (Reglamento ETF Res. 540/2025,
protección del consumidor financiero, riesgo operativo, seguridad de la
información), UIF (instructivo vigente y sus umbrales de billetera móvil), BCB
(RD 079/2022), facturación electrónica del SIN e ISO/IEC 27001, 22301, 31000 y
19011 está en [`docs/Cumplimiento.md`](docs/Cumplimiento.md), con el estado de cada
requisito y las brechas abiertas —incluida la principal: **este producto requiere
autorización de ASFI antes de operar con dinero real**.

## Módulos incluidos

| Módulo | Diagramas | Fichas de entidades | Requerimientos |
| --- | --- | --- | --- |
| Identidad, Usuarios y Seguridad de Acceso | [`.puml`](docs/entidades/01_identidad_usuarios.puml) | [`.md`](docs/entidades/01_identidad_usuarios.md) | RF-01, RF-18 (base), RN-01, RN-17 |
| Grupos, Cupos, Turnos y Gobernanza | [`.puml`](docs/entidades/02_grupos_turnos.puml) | [`.md`](docs/entidades/02_grupos_turnos.md) | Núcleo + RF-19 |
| Aportes, Pagos QR, Conciliación y Contabilidad | [`.puml`](docs/entidades/03_aportes_pagos_qr.puml) | [`.md`](docs/entidades/03_aportes_pagos_qr.md) | RF-15, RN-05, RN-17 |
| Entregas de Fondo (liquidación y desembolso) | [`.puml`](docs/entidades/04_entregas_fondo.puml) | [`.md`](docs/entidades/04_entregas_fondo.md) | Núcleo, RN-05 |
| Notificaciones y Comunicaciones | [`.puml`](docs/entidades/05_notificaciones.puml) | [`.md`](docs/entidades/05_notificaciones.md) | RF-16 |
| Transparencia, Reputación y Confianza | [`.puml`](docs/entidades/06_transparencia_reputacion.puml) | [`.md`](docs/entidades/06_transparencia_reputacion.md) | RF-17, RF-18 |
| Organizador y Automatización | [`.puml`](docs/entidades/07_organizador_automatizacion.puml) | [`.md`](docs/entidades/07_organizador_automatizacion.md) | RF-20, RN-18, RN-22 |
| Garantía, Incumplimiento, Cobranza y Sanciones | [`.puml`](docs/entidades/08_garantia_incumplimiento.puml) | [`.md`](docs/entidades/08_garantia_incumplimiento.md) | RF-21, RN-21 |
| Auditoría, Reportes y Cumplimiento | [`.puml`](docs/entidades/09_auditoria_reportes.puml) | [`.md`](docs/entidades/09_auditoria_reportes.md) | RN-17 |
| Billetera, Custodia y Dinero Electrónico | [`.puml`](docs/entidades/10_billetera_custodia.puml) | [`.md`](docs/entidades/10_billetera_custodia.md) | RF-22, RN-23, RN-24 |
| Tarifas, Comisiones, Impuestos y Facturación | [`.puml`](docs/entidades/11_tarifas_comisiones.puml) | [`.md`](docs/entidades/11_tarifas_comisiones.md) | RF-23, RN-25, RN-26 |
| Cumplimiento Regulatorio y Consumidor Financiero | [`.puml`](docs/entidades/12_cumplimiento_asfi.puml) | [`.md`](docs/entidades/12_cumplimiento_asfi.md) | RN-27 a RN-30 |
| Contabilidad Financiera y ERP | [`.puml`](docs/entidades/13_contabilidad_erp.puml) | [`.md`](docs/entidades/13_contabilidad_erp.md) | CU-100 a CU-106 |
| Publicidad y Campañas | [`.puml`](docs/entidades/14_publicidad_campanas.puml) | [`.md`](docs/entidades/14_publicidad_campanas.md) | CU-110 a CU-114 |

## Decisiones de diseño que atraviesan todo el modelo

Estas son las decisiones que separan un modelo de clase de un modelo que
aguanta producción con dinero de terceros:

1. **Tokens de verificación como agregado propio (M1).** Jerarquía
   `TokenVerificacion` → `TokenOTP` / `TokenEnlaceFirmado` / `TokenRefresco`,
   con `PoliticaToken` parametrizable por propósito, `IntentoValidacionToken`
   para detectar fuerza bruta y clave de idempotencia para evitar doble
   emisión. Nunca se persiste el valor plano: solo su hash con *pepper*.
2. **Incumplimiento como expediente, no como bandera (M8).**
   `RegistroIncumplimiento` con evidencia, línea de tiempo de estados,
   descargo del participante, gestión de cobranza escalonada, deuda exigible,
   subrogación al fondo, aval solidario, sanción proporcional y apelación. La
   reputación es *una consecuencia* de este expediente, no el registro mismo.
3. **Cupo separado de Participante (M2).** Una persona puede tener dos manos o
   media mano; las obligaciones y los turnos cuelgan del cupo. Esto permite
   reemplazar a un moroso conservando la posición económica en el calendario.
4. **Contabilidad de doble partida (M3).** `AsientoContable` +
   `MovimientoContable` con invariante `SUM(debe) = SUM(haber)`. El panel de
   transparencia se calcula desde el mayor, no desde sumas ad-hoc. Nada se
   edita: se reversa.
5. **Idempotencia de extremo a extremo.** Webhooks de pasarela, órdenes de
   cobro, desembolsos, tareas automatizadas y notificaciones llevan
   `clave_idempotencia` única. Un reintento del proveedor no acredita dos veces.
6. **Sorteo de turnos verificable (M2).** Esquema *commit-reveal*: se publica
   el hash de la semilla antes de sortear y se revela después, para que
   cualquiera recompute el orden. El orden de cobro es el punto de
   desconfianza número uno del pasanaku.
7. **Debido proceso en las sanciones (M8).** Matriz
   `tipo × severidad × reincidencia`, plazo de descargo, estado FIRME antes de
   ejecutar y derecho a apelación en dos instancias.
8. **Auditoría encadenada por hash (M9) + outbox transaccional.** Bitácora
   *insert-only* con `hash_anterior`, auditoría de lectura separada de la de
   escritura, y `EventoDominio` escrito en la misma transacción que el cambio.
9. **Entrega como liquidación, no como transferencia (M4).** Bolsa bruta,
   deducciones línea a línea (deuda propia, reposición de cobertura) y neto
   contra cuenta bancaria verificada con periodo de enfriamiento.
10. **El organizador no cobra ni custodia (M7).** El organizador es un
    participante más, con funciones administrativas y responsabilidad de
    desempeño, pero sin ingreso por administrar y sin ser cuenta de paso del
    dinero del grupo (RN-18). Ninguna tabla de comisiones (M11) tiene una clave
    foránea hacia `organizador`: **quien cobra es la plataforma por el servicio,
    con tarifario público, no la persona que administra.**
11. **El saldo no se guarda, se deriva (M10).** No existe
    `UPDATE cuenta SET saldo = saldo - 500`. Hay un libro *append-only*
    (`transaccion_billetera` + `movimiento_billetera`) con partida doble interna:
    todo movimiento tiene contrapartida, y la columna de saldo es apenas una caché
    que un job verifica contra el libro.
12. **Encaje 100 % verificado todos los días (M10).** `conciliacion_custodia`
    compara la suma de todos los saldos de billetera contra el saldo real de la
    cuenta de custodia. Si el ratio de cobertura baja de 1, el sistema entra en
    modo restringido solo y el descuadre escala como incidente y como evento de
    riesgo operativo. El dinero de los usuarios es un pasivo exigible, no
    patrimonio de la empresa.
13. **La política de cobro es dato, no código (M11).** Sobre qué hecho se cobra,
    sobre qué monto, con qué fórmula, a cargo de quién, por qué vía y cuándo son
    seis columnas de `concepto_tarifa`. Cambiar la política comercial es cargar un
    tarifario nuevo, simularlo y publicarlo con preaviso; no es un despliegue. Y
    el precio se congela por grupo (`tarifa_congelada_grupo`): **no cambia a mitad
    del juego**.
14. **Cumplir es dejar fila (M12).** Matriz de riesgo, calificación del cliente,
    debida diligencia, tipologías de monitoreo parametrizables, casos de
    investigación, calendario de reportes con constancia de envío, reclamos con
    plazo guardado y base de pérdidas operativas. Todo control que no deja fila no
    existe para un supervisor.

## Cómo se relacionan los módulos entre sí

Los módulos comparten claves foráneas, señaladas en notas dentro de cada
diagrama. Mapa general de dependencias:

```
1. Identidad y Seguridad ─── usuario_id, token_id ──┬──► 2, 3, 4, 5, 7, 8
        │                                            │
        │ restriccion_usuario ◄───────────────────── 8 (incumplimiento)
        ▼
2. Grupos, Cupos y Turnos ──► 3. Aportes y Pagos QR ──► 4. Entregas de Fondo
        │  grupo/cupo/periodo      │ obligacion_id            │
        │                          │                          │ deducciones
        │                          ▼                          ▼
        │                  8. Garantía e Incumplimiento ◄─────┘
        │                          │  cobertura, deuda, sanción
        │                          ▼
        └────────────────► 6. Transparencia y Reputación ◄──── eventos
                                   ▲
2. Grupos ──► 7. Organizador y Automatización ──► 3 (cobro) y 4 (entrega)

5. Notificaciones consume eventos de 2, 3, 4, 7 y 8 (cobranza)
3 / 4 / 7 / 8 ──► 9. Auditoría, Reportes y Cumplimiento (transversal)

                    ── capa de dinero real (Parche B) ──

10. Billetera y Custodia ◄── aporta/cobra ──► 2, 3, 4 y 8
        │  saldo, retenciones, encaje 100%
        │                      ▲
        ▼                      │ cobro por débito o deducción
11. Tarifas y Comisiones ──────┘──► 4 (deducción de la bolsa) y 3 (mayor)
        │  devengo, factura, ingreso
        ▼
12. Cumplimiento Regulatorio ◄── vigila ── 10 (saldo y operaciones)
        │  límites por nivel, monitoreo, reportes, reclamos
        └──► 10 (bloqueo de saldo) · 9 (auditoría) · 11 (devolución por reclamo)
```

Puntos de integración concretos más usados:

- `token_verificacion` (M1) respalda invitaciones (M2), enlaces de pago (M3/M5),
  confirmación de entrega (M4), firma de reglamento (M2) y aceptación de aval (M8).
- `obligacion_aporte` (M3) es el eje: la cubre el fondo (M8), la deduce la
  entrega (M4) y la puntúa la reputación (M6).
- `acuerdo` (M2) autoriza lo que no puede ser unilateral: condonaciones,
  expulsiones, permutas, cambio de reglamento y disolución.
- `evento_dominio` (M9) es el canal por el que M5, M6 y el cumplimiento se
  enteran de lo que pasa, sin acoplar los módulos entre sí.
- `cuenta_billetera` (M10) da titularidad al dinero: hay una cuenta del **grupo**
  —no del organizador— y el aporte es una transferencia interna instantánea.
- `devengo_comision` (M11) se cobra por tres vías intercambiables por
  configuración: deducción de la entrega (M4), débito de billetera (M10) u
  obligación de aporte (M3).
- `calificacion_riesgo_cliente` (M12) gobierna los `limite_operativo_billetera`
  (M10): subir de nivel de verificación es lo que habilita operar más.

## Convenciones usadas

**Diagramas de clases**

- Visibilidad `-` privado, `#` protegido, `+` público; `{static}` para
  operaciones de clase.
- Estereotipos: `<<AR>>` raíz de agregado, `<<VO>>` objeto de valor,
  `<<Svc>>` servicio de dominio, `<<Pol>>` política configurable.
- Todo agregado persistente lleva implícitamente `creadoEn`, `actualizadoEn`,
  `creadoPor`, `version` (bloqueo optimista) y `eliminadoEn` (borrado lógico).
- Las clases se agrupan en paquetes por subdominio para poder leer el diagrama
  por partes.

**Modelo relacional**

- `*` PK, `#` FK, `<<UQ>>` único, `<<IDX>>` indexado, `<<CK>>` restricción
  CHECK, `<<NULL>>` admite nulos. Nombres en `snake_case`.
- Importes en `DECIMAL(14,2)` (o `16,2` para acumulados) siempre acompañados de
  `moneda CHAR(3)` ISO-4217. Fechas en `TIMESTAMPTZ`.
- Las tablas *append-only* están marcadas en las notas: bitácora, eventos de
  reputación, movimientos de fondo, asientos contables y abonos de recuperación.
  A esas se les revoca `UPDATE`/`DELETE` a nivel de rol de base de datos.
- Las referencias polimórficas (bitácora, deducciones, alertas) se indican en
  notas y se validan por aplicación o trigger, no con FK física.
- Las notas al pie de cada diagrama señalan las claves foráneas hacia **otros
  módulos**, para que cada diagrama se lea de forma independiente.

## Cómo renderizar

**VS Code**: extensión *PlantUML* (jebbs), `Alt+D` para previsualizar (se
generan dos vistas por archivo, una por cada `@startuml`).

**Línea de comandos** (requiere Java + `plantuml.jar`):

```bash
java -jar plantuml.jar -tsvg -charset UTF-8 docs/entidades/*.puml
```

Genera un `.svg` por diagrama, nombrado según el título interno del `@startuml`
(`..._clases.svg` y `..._relacional.svg`). Para PNG use `-tpng`; los diagramas
grandes (módulos 1, 2, 7 y 8) se leen mejor en SVG.

**En línea**: pegue el contenido en https://www.plantuml.com/plantuml. Si el
visor solo renderiza un diagrama a la vez, copie cada bloque
`@startuml ... @enduml` por separado.
