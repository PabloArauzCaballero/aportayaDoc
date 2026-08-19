---
tags:
  - auditoria
  - seguridad
fecha: 2026-08-14
alcance: modelo de datos, restricciones, casos de uso
estado: corregido y verificado contra PostgreSQL 16
---

# Auditoría de robustez — calidad banca

> **Veredicto original.** El diseño era serio: partida doble en dos niveles,
> append-only por privilegios *y* por disparador, denegación por omisión en
> límites, plazos guardados, claves foráneas sin `CASCADE`. Lo que fallaba no era
> la concepción sino **la ejecución de cinco controles críticos**, y en tres de
> ellos el control existía en el papel pero no se aplicaba en la base.

**Estado: los 19 hallazgos están corregidos**, salvo dos que quedan
explícitamente diferidos con su motivo ([P1-8](#p1-8) y [P2-3](#p2-3)). Todo el
esquema fue aplicado y ejercitado contra PostgreSQL 16 real; la evidencia está
[al final](#evidencia-de-verificación).

> [!warning] Instantánea del 2026-08-14 — cuerpo P0–P2 sin reescribir
> Los hallazgos P0–P2 de abajo se corrieron sobre el modelo de **274 tablas**, antes de
> M13 (contabilidad ERP) y M14 (publicidad) y antes de la migración a microservicios.
> Las cifras "de 274" son de ese momento y no se reescriben. La **extensión a M13/M14 y
> al esquema por servicio** está resuelta en la sección de abajo (2026-08-19).

## Extensión 2026-08-19 · M13/M14 bajo el esquema por servicio

El pendiente que dejó la instantánea era doble: contar y escribir la RLS de las tablas
nuevas, y reconsiderar los controles bajo el esquema **por servicio** (`svc_*`,
[[ADR-017 Propiedad de datos por servicio]], [[ADR-031 Lecturas, réplica y rol auditor]])
en vez del `rol_aplicacion` único del monolito. Revisadas las **32 tablas** de M13 (18) y
M14 (14), el hallazgo es acotado:

**Ninguna tabla de M13/M14 amerita RLS por titular.** La RLS de fila (`fn_seg_aplicar_rls`)
protege tablas donde **un usuario final es dueño de filas concretas** (condición sobre
`usuario_id` o `cuenta_billetera_id`). Ni la contabilidad/ERP ni la publicidad tienen ese
patrón: son **datos administrativos de un servicio**, cuyas filas pertenecen a la empresa,
no a un participante. El control correcto es la **frontera del GRANT** —`svc_erp` y
`svc_publicidad` solo ven su propio esquema (ADR-017)—, no una política de fila. Escribir
RLS "de titular" sobre una `factura_proveedor` o una `campana_publicitaria` sería inventar
un dueño que no existe.

| Grupo | Tablas | Control de acceso | RLS de titular |
| --- | --- | --- | :-: |
| M13 · contabilidad/ERP | las 18 (ejercicio, período, presupuesto, terceros, órdenes, facturas, activos, plantillas, estados) | GRANT: solo `svc_erp`; 7 son append-only y ya están selladas | **No aplica** |
| M14 · publicidad (config y facturación) | anunciante, cuenta/campaña/conjunto, segmento, espacio, pieza, revisión, anuncio, factura, socio | GRANT: solo `svc_publicidad` | **No aplica** |
| M14 · telemetría con dato personal | `impresion_anuncio`, `clic_anuncio` (`usuario_id` = quién vio/clicó) | GRANT: solo `svc_publicidad`; append-only | **No** (ver abajo) |

**El hallazgo real, más chico que el temido: dos columnas de dato personal.**
`impresion_anuncio.usuario_id` y `clic_anuncio.usuario_id` guardan **quién** vio o clicó un
anuncio — dato personal de comportamiento. No pide RLS de titular (el acceso es del servicio
de medición, no de la sesión del usuario, y la frontera del GRANT ya impide que otro servicio
o participante las lea), sino **minimización y borrado**:

- `usuario_id` es **anulable** (ya lo es) y debe poder **anonimizarse** sin perder la métrica
  agregada; la medición no necesita la identidad, solo el conteo.
- Falta una **política de retención/anonimización** para esas dos tablas y para
  `tercero_comercial.numero_documento` (documento de una contraparte). Esto es exactamente el
  tema abierto **«datos personales en eventos y copias»** de `planes/20` §8 (borrado del
  titular vs. tablas retenidas); se resuelve con ese ADR, no acá.

**Resto de controles bajo el esquema por servicio:** los invariantes de P0–P2 (append-only por
disparador y por privilegio, denegación por omisión, claves foráneas sin `CASCADE`, dinero en
`numeric`) siguen valiendo tabla por tabla; lo que cambió es que el `REVOKE`/`GRANT` ahora se
emite **por rol de servicio** (`generar_ddl.py` → `03_permisos.sql`) y ya no por un
`rol_aplicacion` único. La prueba de humo lo ejercita: 152 comprobaciones, incluidas las de
frontera cruzada (un `svc_*` no lee el esquema ajeno) y el sellado append-only (29 tablas).

---

## P0 — Bloqueantes

### P0-1 · Se podía sobregirar una billetera con operaciones concurrentes ✅

`fn_bil_recalcular_saldos` calculaba el saldo con un `SELECT SUM(...)` **sin
bloqueo** y después hacía `UPDATE`. Bajo `READ COMMITTED`, dos transacciones
sobre la misma cuenta leían snapshots que no se veían entre sí; la segunda
esperaba el bloqueo de fila y al despertar escribía el valor que ya había
calculado. Se perdía un movimiento del saldo — y como `ck_cuenta_saldo_no_negativo`
se evalúa sobre esa columna, **el control de saldo no negativo pasaba a
evaluarse contra un saldo falso**.

No era teórico. Medido con la prueba nueva, sobre una cuenta con 100 y ocho
débitos simultáneos de 100: **se aceptaron 7 de 8**. Se sacaron 700 de una cuenta
que tenía 100.

**Corregido** en `R-BIL-07` con un bloqueo tomado *antes* de leer, que obliga a la
segunda transacción a releer el libro ya completo:

```sql
PERFORM 1 FROM cuenta_billetera WHERE id = p_cuenta FOR UPDATE;
```

Ahora pasa exactamente 1 de 8. La función además incrementa `version`, que estaba
declarada para bloqueo optimista y nadie usaba ([P2-4](#p2-4)).

### P0-2 · La seguridad a nivel de fila no protegía nada ✅

Tres problemas encadenados: **2 tablas de 274** con política, `ENABLE` sin
`FORCE`, y `rol_aplicacion` sin un solo `GRANT` en todo el archivo —sólo
`REVOKE`s—, lo que sugería que la API se conectaba como dueña del esquema. Si era
así, las dos políticas existentes **no se evaluaban nunca**, porque el dueño de
una tabla omite RLS siempre. Y ambas eran `FOR SELECT`: la escritura sobre filas
ajenas quedaba abierta.

**Corregido** en `R-SEG-03`, y no con una lista escrita a mano —que se
desactualiza en el primer módulo nuevo, y una tabla olvidada no falla: queda
abierta en silencio—. El recorrido va sobre el catálogo: toda tabla con
`usuario_id` o `cuenta_billetera_id` recibe política, hoy y cuando se agregue la
próxima. Se pasó de **2 políticas a 92**, con `FORCE` en 90 tablas.

El reparto tiene dos regímenes, y la diferencia importa. El usuario ve su
identidad, su billetera y sus operaciones. Lo que **no** puede ver jamás es lo que
cumplimiento escribió sobre él: alertas de monitoreo, casos de investigación,
coincidencias con listas restrictivas, su calificación de riesgo. Darle acceso a
su propia fila ahí no es una fuga de privacidad: es soplo, y la UIF lo prohíbe.
Por eso la lista blanca es de tablas *visibles por el titular* y todo lo demás
cae en privilegiado — denegar por omisión, como el principio 2 del catálogo.

También se agregaron los `GRANT` explícitos a `rol_aplicacion` y
`ALTER DEFAULT PRIVILEGES`, sin el cual cada tabla nueva nacía invisible para el
auditor ([P2-2](#p2-2)).

### P0-3 · La cadena de hash se bifurcaba, y la bitácora se firmaba sola ✅

El encadenamiento leía el predecesor con `ORDER BY secuencia DESC LIMIT 1` sin
bloqueo: dos inserciones concurrentes producían eslabones hermanos. El hash no
cubría `estado`, `moneda`, `canal` ni `clave_idempotencia`. Y `bitacora_evento`
**no tenía disparador**: sus hashes los escribía la aplicación, que es
exactamente el adversario del que hay que defenderse ante un regulador.

**Corregido** con `pg_advisory_xact_lock` sobre la cadena, cobertura de hash
ampliada, y un disparador propio para la bitácora (`R-AUD-09`) con génesis de 64
ceros, porque `hash_anterior` es `NOT NULL` y la primera fila no tenía cómo
entrar.

Con las funciones vulnerables, la prueba de concurrencia reporta *eslabones
hermanos*; con las corregidas, cadena íntegra.

> **Lo que las patas no cubren, y por qué.** Sellar los movimientos dentro del
> hash del encabezado exigiría un `UPDATE` sobre `transaccion_billetera`, que es
> append-only: el sello se bloquearía a sí mismo. Se intentó y se descartó. No
> hace falta: `movimiento_billetera` es append-only por derecho propio. Lo que sí
> hay que detectar —una pata huérfana, una transacción aplicada sin patas— lo
> cubren las consultas de control nuevas (`R-AUD-10`).

### P0-4 · Se podía consultar datos personales sin justificación ✅

```sql
CHECK (length(trim(justificacion)) >= 10)   -- sobre una columna nullable
```

Con `NULL`, `length(trim(NULL))` es `NULL`, y **un CHECK que evalúa a `NULL` se
acepta**. La restricción se saltaba dejando el campo vacío. Los otros tres CHECK
de longitud del archivo sí llevaban el `IS NOT NULL` explícito; éste no.

**Corregido**: la columna es `NOT NULL` en el modelo y la restricción lleva el
`IS NOT NULL` explícito.

### P0-5 · La idempotencia era global y no guardaba la respuesta ✅

`UNIQUE (clave_idempotencia)` sin ámbito de titular convierte la clave en un
recurso compartido entre usuarios: quien reutilice la de otro hace que la
operación legítima del otro sea rechazada. Y sin respuesta almacenada, el
reintento tras un timeout —el caso exacto para el que existe la idempotencia—
chocaba contra la violación de unicidad y devolvía un error indistinguible de
"falló".

**Corregido** en `R-BIL-06`: cada clave se ampara en su titular u objeto, en las
**14 tablas** que la usan, no sólo en las cuatro de dinero. Y `R-BIL-19` agrega
`respuesta_idempotente`, con `hash_solicitud` para que la misma clave con otro
cuerpo sea un conflicto y nunca una reejecución silenciosa.

---

## P1 — Altos

### P1-1 · El cuatro-ojos del retiro era inaplicable ✅

`orden_retiro` **no tenía columna `solicitada_por`**: no había con qué comparar al
aprobador, y `requiere_doble_aprobacion` existía sin ninguna restricción que lo
hiciera valer. La salida de dinero de mayor riesgo era la única sin segregación
exigible en la base.

**Corregido**: columna agregada al modelo con su FK, y `ck_retiro_doble_aprobacion`
en `R-SEG-04`.

### P1-2 · Los hashes de búsqueda eran reversibles ✅

El problema no era SHA-256 sino **el espacio de entrada**: un CI boliviano son
~10⁷ valores, un PAN con BIN conocido ~10⁶. La tabla completa de digests se
precalcula en segundos, así que el hash de búsqueda revelaba exactamente el dato
que el cifrado de la columna de al lado protegía.

**Corregido**: `fn_seg_hash_busqueda` es ahora el único camino permitido — HMAC
con una pimienta que vive fuera de la base, y **falla si no está configurada**.

### P1-3 · Las columnas cifradas no decían con qué llave ✅

Sin versión de llave, rotar exige descifrar y recifrar el universo en una ventana
atómica; en la práctica, no se rota nunca. **Corregido**: `version_llave` en las
cinco tablas con texto cifrado, con su CHECK.

### P1-4 · Rotación de refresco sin detección de reuso ✅ *(hallazgo corregido)*

El informe original decía que faltaba el modelo de token de refresco. **Era
inexacto**: el modelo ya existía dentro de `token_verificacion`
(`tipo_token='REFRESCO'`, `hash_token`, `familia_id`, `rotado_de_id`). Lo que
faltaba era la **regla**, y eso sí era real: nada detectaba el reuso.

**Corregido** con `R-SEG-09`. Un refresco consumido que vuelve a presentarse es
la firma de un robo, y como no se sabe cuál de los dos es el legítimo, se
invalida la familia entera y se cortan sus sesiones. El disparador no lanza
excepción a propósito: si lo hiciera, la propia revocación se iría en el
`ROLLBACK`.

### P1-5 · La partida doble no verificaba la moneda ✅

`fn_bil_transaccion_cuadrada` sumaba importes sin mirar la moneda: una
transacción que debitaba 100 USD y acreditaba 100 BOB **cuadraba**. Latente
mientras el sistema sea monomoneda; fuga silenciosa el día que entre la segunda.
**Corregido** con `R-BIL-20`, diferido al COMMIT, más el contraste de moneda entre
orden y cuenta en recarga y retiro.

### P1-6 · Los límites se evadían con concurrencia ✅

Misma clase que P0-1: `fn_lim_evaluar` leía `consumo_limite.monto_acumulado` sin
bloqueo, y dos operaciones simultáneas pasaban el mismo tope diario.
**Corregido** con `FOR UPDATE`.

### P1-7 · `ENCAJE_INCUMPLIDO` se prometía y no se aplicaba ✅

`AP-CU11-06` estaba declarado en CU-11 pero ninguna regla lo hacía valer: se
registraba el incumplimiento y se seguía pagando retiros, que es el escenario
clásico de la corrida. **Corregido** con `R-BIL-11b`: con el encaje roto no se
autorizan salidas nuevas, pero las órdenes ya autorizadas siguen su curso —
frenarlas a mitad dejaría dinero retenido sin pagar ni devolver, que es peor.

### P1-8 · Dos modelos paralelos de destino ⏸️ diferido {#p1-8}

`orden_retiro` apunta a `instrumento_fondeo`; `orden_desembolso` a
`cuenta_bancaria_beneficiario`. Cada uno con sus propios controles de
verificación. **No se unificó**: son dos flujos regulatorios distintos (retiro del
titular contra desembolso a un beneficiario) y fusionarlos es un cambio de
dominio, no una corrección de robustez. Queda como decisión pendiente para un
ADR. El riesgo se mitigó verificando que ambos caminos tengan control equivalente
(`R-BIL-09` y `tg_orden_desembolso_cuenta_verificada`).

---

## P2 — Medios

### P2-1 · Particionado ✅
`bitacora_evento`, `evento_dominio` y `registro_acceso_datos` pasan a
`PARTITION BY RANGE` mensual, con partición por defecto y 24 meses precreados.
Es la única forma de purgar una tabla append-only sin violar `R-AUD-01`: se
desprende la partición vencida, nunca se ejecuta un `DELETE`.

`notificacion` y `transaccion_billetera` quedaron **fuera a propósito**: reciben 5
y 14 claves foráneas, y una tabla particionada no puede ser destino de una FK por
`id` a secas. Perder esa integridad referencial cuesta más que el tamaño.

### P2-2 · Privilegios por omisión ✅ {#p2-2}
`ALTER DEFAULT PRIVILEGES` para aplicación, auditor, backoffice y cumplimiento.

### P2-3 · El saldo se recomputa en O(n) ⏸️ mitigado, no eliminado {#p2-3}
El bloqueo de P0-1 hace el cálculo **correcto**, pero sigue siendo una suma de
todo el histórico en cada movimiento. En `PLATAFORMA_INGRESOS` o
`PUENTE_CUSTODIA`, que concentran todos los movimientos del sistema, esto se
degrada. **No se cambió a delta incremental** porque hacerlo sin una prueba de
carga real es cambiar un problema medido por uno supuesto. Es el primer candidato
cuando haya volumen para medir.

### P2-4 · `version` sin usar ✅ {#p2-4}
`fn_bil_recalcular_saldos` la incrementa.

### P2-5 · El auditor ve todo ✅ documentado
Sigue teniendo `SELECT` sobre todo el esquema —es la definición del rol— pero
ahora está escrito en un `COMMENT ON ROLE` y es rol privilegiado a efectos de
RLS. Su acceso a datos sensibles debe caer en `registro_acceso_datos`; eso se
sostiene por procedimiento, no por disparador.

### P2-6 · Verificación de las cadenas ✅
Cinco consultas de control nuevas: eslabones rotos en la cadena de transacciones,
ídem en la bitácora, patas huérfanas, transacciones que mezclan monedas, y tablas
con `usuario_id` sin RLS. Esta última encontró un hueco real durante la propia
corrección: 47 tablas que la primera versión de la lista había omitido.

---

## Hallazgo adicional, encontrado al ejecutar

### `min(uuid)` no existe en PostgreSQL ✅

`fn_uif_acumulado` (`R-UIF-03`) calculaba el inicio de la ventana acumulada con
`min(x.id)` sobre una columna UUID. PostgreSQL no define `min()` para `uuid`: la
función **fallaba con error en cuanto se la invocaba**, y como nunca se había
ejecutado contra una base real, nadie lo había visto. Cualquier operación que
disparara el motor de umbrales acumulados moría ahí.

Y aunque `min()` existiera, el criterio estaba mal: el UUID menor no es la
operación más antigua. **Corregido** con
`(array_agg(x.id ORDER BY x.ocurrida_en, x.secuencia))[1]`, que es lo que el
formulario tiene que citar.

Es el argumento entero de este trabajo: 119 restricciones escritas con cuidado y
ninguna había tocado un motor.

---

## Nota de diseño: el costo del bloqueo de la cadena

`pg_advisory_xact_lock` sobre la cadena de transacciones garantiza que no haya
bifurcaciones, pero **serializa globalmente toda escritura de billetera**: dos
transacciones de dos usuarios sin ninguna relación entre sí esperan una a la
otra. Para el volumen de un piloto es irrelevante; para escala es el techo del
sistema.

Se descubrió midiendo: el bloqueo enmascaraba la carrera de P0-1 en la prueba,
porque nunca dejaba correr dos transacciones a la vez. Cuando haya que
levantarlo, las salidas son cadena por cuenta o sellado por bloques —el modelo ya
tiene `registro_sellado` para eso— y ambas merecen un ADR antes que una línea de
código.

---

## Evidencia de verificación

Contra PostgreSQL 16.14, base creada de cero:

| Qué | Resultado |
| --- | --- |
| `sql/aplicar.sql` completo | aplica sin errores · 350 relaciones, 3 particionadas |
| `sql/50_verificacion/prueba_humo.sql` | **100 OK · 0 FALLA** |
| Consultas de control (`verificaciones.sql`) | las 11 devuelven cero filas |
| `scripts/probar_concurrencia.sh` | ambos escenarios pasan |
| `scripts/verificar_boveda.py` | sin fallas · 275 entidades · 124 restricciones |
| RLS | 92 políticas · `FORCE` en 90 tablas *(antes: 2 políticas, 0 forzadas)* |

La prueba de concurrencia es la que no existía y que habría detectado P0-1, P0-3
y P1-6. Se verificó en las dos direcciones — que detecta el defecto y que la
corrección lo elimina:

| Escenario | Con el defecto | Corregido |
| --- | --- | --- |
| Interleaving determinista (débitos de 40 y 30 sobre 100) | saldo 70 · libro 30 → **movimiento perdido** | saldo 30 = libro 30 |
| 8 débitos simultáneos de 100 sobre saldo 100 | **7 aceptados** → se sacaron 700 de una cuenta con 100 | 1 aceptado |
| Cadena de hash bajo concurrencia | **eslabones hermanos** | íntegra |

Un detalle metodológico que casi arruina la verificación: lanzar N procesos en
paralelo **no reproduce** estos defectos. El arranque de cada `psql` cuesta más
que la ventana de carrera, así que se serializan solos y la prueba pasa aunque el
bug esté presente. Hay que forzar el solapamiento con una transacción que se
mantiene abierta mientras entra la otra. Por eso el escenario 1 existe.

---

## Lo que estaba bien y no se tocó

- **Partida doble en dos niveles** (`R-BIL-01` interna, `R-AUD-05` contable), ambas
  con `CONSTRAINT TRIGGER ... DEFERRABLE INITIALLY DEFERRED`: validar al `COMMIT`
  es la única forma correcta de hacerlo.
- **Append-only por privilegios y por disparador a la vez**: el `REVOKE` para el
  operador honesto, el disparador para el distraído con superusuario.
- **Denegar por omisión en límites** (`R-LIM-01`), con el comentario que explica la
  trampa del `NULL` y la evita explícitamente.
- **`EXCLUDE USING gist` para vigencias sin solape**: la herramienta correcta,
  poco usada.
- **Cero `ON DELETE CASCADE`** en 568 claves foráneas.
- **Plazos guardados y nunca recalculados** (`R-CON-01`).
- Los casos de uso con contrato, códigos de error, Gherkin y descomposición
  atómica.

## Cómo reproducir

```bash
python3 scripts/generar_ddl.py && python3 scripts/extraer_sql.py
python3 scripts/verificar_boveda.py

docker exec -i <contenedor> psql -U postgres -c 'CREATE DATABASE aportaya_v;'
docker cp sql <contenedor>:/tmp/sql
docker exec -i <contenedor> psql -U postgres -d aportaya_v -v ON_ERROR_STOP=1 \
  -f /tmp/sql/aplicar.sql
docker exec -i <contenedor> psql -U postgres -d aportaya_v \
  -f /tmp/sql/50_verificacion/prueba_humo.sql | grep -c FALLA   # debe dar 0

# la de concurrencia necesita semillas (tipo de cambio) y su fixture
scripts/probar_concurrencia.sh <contenedor> aportaya_v
```
