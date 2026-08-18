---
tags:
  - arquitectura
  - adr
titulo: "ADR-017 — Propiedad de datos: un clúster, un esquema por servicio"
estado: aceptada
fecha: 2026-08-16
---

# ADR-017 — Propiedad de datos por servicio

## Contexto

[[ADR-014 Arquitectura de servicios]] parte el backend en catorce desplegables. La
pregunta que sigue es la que decide si la partición sobrevive al contacto con este
dominio: **¿se parte también la base?**

Lo que hay que preservar está escrito y verificado:

| Propiedad | Dónde vive | Qué la rompería |
| --- | --- | --- |
| **633 claves foráneas** con integridad referencial | `sql/20_claves/` | Bases separadas: la FK deja de existir y pasa a ser una validación de aplicación |
| **Partida doble que cuadra al centavo** | `asiento_contable` + `movimiento_billetera` | Que el débito y el crédito ocurran en transacciones distintas |
| **138 restricciones**, incluidas `EXCLUDE` con `btree_gist` | `sql/40_reglas/` | Bases separadas: una `EXCLUDE` no cruza instancias |
| **Append-only por `REVOKE`** | `sql/35_append_only/` | Nada, si los permisos siguen siendo por rol |
| **RLS por `app.usuario_id`** | políticas de fila | Nada, si el contexto se sigue fijando por sesión |

La doctrina de microservicios dice «una base por servicio». Aplicada acá, esa
doctrina cuesta las tres primeras filas de esa tabla. **No se aplica.**

## Decisión

**Un único clúster PostgreSQL 16. Un esquema y un rol por servicio. La partida
doble, entera, dentro de un solo servicio.**

### El esquema se deriva del módulo

El nombre del esquema sale del `.puml` del módulo, sin decisión humana:
`docs/entidades/01_identidad_usuarios.puml` → esquema `identidad`. Lo calcula
`scripts/generar_ddl.py` y lo verifica el gate de la Fase 1.

**Hay exactamente una excepción, y está enumerada.** Las cuatro tablas del libro
contable que el `.puml` del módulo 03 aloja —`cuenta_contable`,
`asiento_contable`, `movimiento_contable` y `cierre_diario`— viven en el esquema
`nucleo_financiero`, junto a las 26 tablas del módulo 10. El resto del módulo 03
—obligación, orden de cobro, QR, pago, conciliación— queda en el esquema `aportes`.

La razón es la de [[ADR-014 Arquitectura de servicios]]: el débito de la billetera y
el asiento que lo respalda tienen que confirmar juntos o no confirmar. Una excepción
enumerada y justificada es preferible a una regla que se cumple a medias.

### Los dos esquemas que no son de ningún servicio

**`catalogo`** — los datos sembrados que más de un servicio lee y ninguno escribe en
caliente: tipo de cambio, calendario de días no hábiles, tarifario vigente, umbrales
regulatorios. Todos los roles tienen `SELECT`; solo `rol_migracion` tiene `INSERT`, y
solo durante el despliegue de semillas.

**`comun`** — las tres tablas transversales en las que **todo servicio inserta y
ninguno edita**: `evento_dominio` (el outbox), `bitacora_evento` y
`registro_acceso_datos`. No son datos de negocio de nadie: son el rastro que deja
cualquiera.

> **Es la segunda excepción a la regla, y también está enumerada.** El outbox tiene
> que escribirse en la **misma transacción** que el hecho que lo origina; si viviera
> en el esquema de `auditoria`, cada servicio necesitaría escribir en el esquema de
> otro — exactamente el acceso cruzado que este ADR prohíbe. La alternativa era
> duplicar las tres tablas trece veces, y con ello perder la posibilidad de consultar
> el outbox completo de una sola vez.
>
> **Se sostiene porque las tres son append-only.** Un servicio recibe `INSERT` y nada
> más: no puede leer el rastro de otro ni modificar el propio. El `SELECT` se otorga
> únicamente a `rol_auditor`, contra la réplica.

### Los permisos son la frontera, no la convención

```sql
-- por cada servicio
CREATE ROLE svc_identidad LOGIN;
GRANT USAGE ON SCHEMA identidad TO svc_identidad;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA identidad TO svc_identidad;
GRANT USAGE ON SCHEMA catalogo  TO svc_identidad;
GRANT SELECT ON ALL TABLES IN SCHEMA catalogo TO svc_identidad;
-- y nada más. Sin GRANT sobre los otros doce esquemas.
```

**Un servicio no puede leer las tablas de otro aunque quiera**: no tiene permiso, y
además jOOQ no le generó las clases ([[ADR-016 Acceso a datos con jOOQ]]). El
aislamiento no depende de que nadie escriba el `JOIN`; depende de que el `JOIN`
falle.

`REVOKE UPDATE, DELETE` sobre las tablas append-only se conserva exactamente como
está, ahora aplicado al rol del servicio dueño.

### Las claves foráneas cruzadas se conservan

PostgreSQL soporta claves foráneas entre esquemas de la misma base. Las 633
relaciones **siguen existiendo y siguen siendo verificadas por el motor**. Una FK
cruzada es de solo lectura para el servicio que no es dueño: puede apuntar a
`identidad.usuario`, no puede escribirlo.

> Esta es la línea entera del argumento a favor de un clúster: se compra el
> aislamiento de despliegue sin vender la integridad referencial.

### Cómo se leen los datos ajenos

| Necesidad | Cómo se resuelve | Cómo **no** |
| --- | --- | --- |
| Un dato de otro servicio, ahora | Llamada HTTP a su API, por el cliente generado de su OpenAPI | `SELECT` cruzado de esquema |
| Un dato de otro servicio, muchas veces | Copia local mantenida por evento del outbox, marcada como réplica y nunca autoritativa | Vista sobre el esquema ajeno |
| Un informe que cruza todo el sistema | `auditoria` sobre la **réplica de solo lectura**, con `rol_auditor`, que sí ve todos los esquemas | Que cada servicio exporte su parte |
| Mover dinero | Llamada a `nucleo-financiero` con clave de idempotencia | Escribir el libro desde otro servicio |

`auditoria` es la única excepción de lectura, es de **solo lectura**, va contra la
**réplica** y no contra la primaria, y está justificada porque reportar al supervisor
exige una vista consistente del sistema entero ([[ADR-011 Lecturas y réplica]]).

## Motivo

**La partida doble es el motivo entero.** Todo lo demás de este ADR se sigue de no
querer que el cuadre contable dependa de una saga. Un asiento que puede quedar a
medias no es un asiento: es un descuadre esperando a ser encontrado por el cierre
diario.

**Las restricciones ya escritas son un activo, no un legado.** 138 reglas que la
base hace cumplir valen más que catorce servicios perfectamente puros. Partir la
base convierte cada `EXCLUDE` y cada FK en código de aplicación que hay que escribir,
probar y mantener catorce veces.

**El aislamiento que sí importa se consigue igual.** Lo que un carril necesita para
no chocar con otro es que su código, su despliegue y su ciclo de vida sean suyos.
Eso lo da el servicio. Compartir un clúster no crea conflictos de merge.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Una base por servicio** | La doctrina. Cuesta las 633 FK, las `EXCLUDE` y la atomicidad de la partida doble; obligaría a rediseñar el modelo contable alrededor de sagas. Se paga un rediseño completo para comprar una pureza que este sistema no necesita. |
| **Base compartida sin esquemas** | Lo más simple hoy y lo que garantiza que en tres meses nadie sepa quién escribe qué tabla. Sin frontera de permisos, la partición de servicios es decorativa. |
| **Esquema por servicio pero un solo rol** | Los esquemas separan nombres y no separan poder: cualquier servicio podría escribir cualquier tabla. La frontera es el `GRANT`, no el `CREATE SCHEMA`. |
| **Un servicio de datos que todos consultan** | Reintroduce el acoplamiento en un solo punto y lo vuelve además un cuello de botella y un fallo único. |
| **Partir también el libro contable** (billetera y asiento en servicios distintos) | Es la decisión que este ADR existe para no tomar. |

## Consecuencias

**A favor**

- Integridad referencial, restricciones y partida doble intactas: `sql/` no cambia
  de naturaleza, solo gana la asignación de esquema.
- La frontera entre servicios la hace cumplir PostgreSQL, no la revisión de código.
- Una sola estrategia de respaldo, PITR y réplica ([[ADR-013 Respaldo y continuidad]]).

**En contra, y hay que asumirlo**

- **El clúster es un punto único de fallo compartido.** Se mitiga con réplica y
  PITR, que ya estaban decididos; no es un riesgo nuevo, pero ahora afecta a
  catorce servicios a la vez.
- **Un cambio de esquema sigue siendo global.** Para todo, se hace en troncal, se
  regenera y recién ahí los carriles rebasan. La partición de servicios no compra
  independencia de modelo, y decirlo claro evita que alguien lo suponga.
- **El pool de conexiones se multiplica por catorce.** Cada servicio trae el suyo;
  el dimensionamiento total está en [[ADR-021 Sesión, RLS y pooling]].
- Un servicio que necesita muchos datos ajenos va a sentir la latencia HTTP. Si
  eso pasa de forma sostenida, la frontera está mal puesta y se revisa la
  frontera, no el permiso.

## Cómo se verifica

- [ ] `\dn` lista catorce esquemas de servicio más `catalogo` y `comun`.
- [ ] Por cada rol `svc_*`: `SELECT` sobre una tabla de otro esquema devuelve
      **permiso denegado**. Hay una prueba por par de servicios.
- [ ] Por cada rol `svc_*`: `INSERT` en `comun.evento_dominio` funciona, y `SELECT`
      sobre esa misma tabla **es denegado**.
- [ ] `rol_auditor` no tiene `INSERT` en ningún esquema.
- [ ] `UPDATE` sobre cualquier tabla de `sql/35_append_only/` es rechazado para el
      rol del servicio dueño.
- [ ] Solo `svc_nucleo_financiero` tiene escritura sobre `asiento_contable`,
      `movimiento_contable` y `movimiento_billetera`.
- [ ] El esquema de cada tabla coincide con el `.puml` de su módulo, salvo las
      cuatro tablas enumeradas arriba. Lo comprueba `verificar_boveda.py`.

## Ver también

[[ADR-014 Arquitectura de servicios]] · [[ADR-016 Acceso a datos con jOOQ]] · [[ADR-021 Sesión, RLS y pooling]] · [[ADR-011 Lecturas y réplica]] · [[ADR-013 Respaldo y continuidad]] · [[Restricciones]] · [[_Arquitectura]]
