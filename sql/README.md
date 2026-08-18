# Esquema SQL

> Todo lo que hay acá, salvo `50_verificacion/prueba_humo.sql` y `60_semillas/`,
> **es generado**.
> La fuente de verdad son los diagramas de `docs/entidades/*.puml` y el catálogo
> de `docs/Restricciones.md`. No edite estos archivos a mano: se sobreescriben.

```bash
python3 scripts/generar_ddl.py     # regenera todo sql/ (incluye el catálogo)
```

## Estructura

```
sql/
├── 00_base/                 extensiones (pgcrypto, btree_gist) y roles
├── 10_tablas/               un archivo por tabla, agrupado por módulo
│   ├── 01_identidad_usuarios/usuario.sql …
│   └── 12_cumplimiento_asfi/…
├── 20_claves/               claves foráneas, un archivo por módulo
├── 30_indices/              índices y unicidad, un archivo por módulo
├── 35_append_only/          sella las tablas que no admiten UPDATE ni DELETE
├── 40_reglas/               catálogo de restricciones (extraído de la bóveda)
├── 50_verificacion/         controles y prueba de humo
├── 60_semillas/             catálogos: sin esto el sistema no opera
└── aplicar.sql              orquestador en orden
```

**Por qué las claves foráneas van aparte:** el modelo tiene referencias circulares
entre módulos (identidad ↔ incumplimiento, pagos ↔ billetera). Crear primero todas
las tablas y después las relaciones evita cualquier problema de orden, y es además
el orden que necesita la introspección de un ORM.

## Aplicar

```bash
psql -d aportaya -v ON_ERROR_STOP=1 -f sql/aplicar.sql
```

Con Docker, de cero:

```bash
docker run --rm -d --name pg-aportaya \
  -e POSTGRES_PASSWORD=aportaya -e POSTGRES_DB=aportaya \
  -p 5432:5432 -v "$PWD/sql:/sql:ro" postgres:16
sleep 10
docker exec pg-aportaya psql -U postgres -d aportaya -v ON_ERROR_STOP=1 -f /sql/aplicar.sql
```

## Sembrar los catálogos

Con los catálogos vacíos **el sistema no opera**: la regla de *denegar por omisión*
rechaza toda operación sin límite configurado, sin tarifario y sin licencia. Es
deliberado (`R-LIM-01`, `R-LIC-01`).

```bash
psql -d aportaya -v ON_ERROR_STOP=1 -f sql/60_semillas/sembrar.sql
psql -d aportaya -f sql/60_semillas/99_desarrollo.sql   # solo entorno local
```

| Archivo | Qué carga | Estado |
| --- | --- | --- |
| `01_plan_de_cuentas.sql` | 19 cuentas contables; el saldo de los usuarios es pasivo exigible | listo |
| `02_politicas_operativas.sql` | política de billetera y redondeo a Bs 0,10 | revisar con riesgos |
| `03_limites_operativos.sql` | 40 límites por nivel de debida diligencia | ⚠ **PROVISIONAL** |
| `04_tarifario_v1.sql` | hechos generadores + tarifario v1 (0,3 %, piso 10, techo 50) | decisión comercial |
| `05_impuestos.sql` | IVA e IT | ⚠ confirmar con tributaria |
| `06_umbrales_uif.sql` | 18 umbrales de los arts. 52 y 53 del instructivo vigente | ⚠ confirmar vigencia |
| `07_reportes_regulatorios.sql` | calendario de 10 reportes obligatorios | ⚠ confirmar plazos |
| `08_gobierno_y_licencia.sql` | licencia `EN_TRAMITE`, comités, puntos de reclamo, matriz de riesgo, 5 tipologías | listo |
| `09_reglas_operativas.sql` | 8 reglas de entrega y 5 antifraude | revisar con riesgos |
| `99_desarrollo.sql` | tipo de cambio, cuentas técnicas y cuenta de custodia de prueba | **no usar en producción** |

> [!warning] La licencia se siembra `EN_TRAMITE` a propósito
> Es el estado real mientras no exista resolución de ASFI. Con ese estado,
> `fn_lic_servicio_habilitado()` devuelve `false` y **ningún servicio financiero se
> habilita**. No es un defecto: es la regla funcionando. El archivo incluye, en
> comentario, el `UPDATE` que corresponde una vez otorgada.

## Verificar

```bash
# controles de integridad: TODAS las consultas deben devolver cero filas
psql -d aportaya -f sql/50_verificacion/verificaciones.sql

# prueba de humo de las restricciones: cada línea debe empezar con OK
psql -d aportaya -f sql/50_verificacion/prueba_humo.sql
```

> [!tip] Verificado de punta a punta sobre PostgreSQL 16
> Esquema + catálogos mínimos + datos de prueba cargan sin un solo error, la
> prueba de humo da **68 OK y ninguna falla**, y los controles de integridad
> devuelven cero filas. La prueba funciona igual con la base recién creada o ya
> sembrada: usa códigos y monedas propios (`XTS`) que no colisionan con los
> catálogos.

## Qué se crea

| Objeto | Cantidad |
| --- | --: |
| Tablas | 274 |
| Claves foráneas | 565 |
| Restricciones `CHECK` | 424 |
| `UNIQUE` | 67 |
| `EXCLUDE` (vigencias sin solape) | 4 |
| Índices | 947 |
| Disparadores | 18 + 19 de sellado append-only |
| Columnas generadas | 7 |
| Políticas de seguridad por fila | 2 |
| Comentarios de columna | 1.883 |

## Introspección con jOOQ

El acceso a datos es **jOOQ**, un constructor de consultas tipado, no un ORM
([[ADR-016 Acceso a datos con jOOQ]]). Las clases se generan por introspección del
esquema real, así que la base sigue siendo la fuente de verdad:

```bash
./gradlew :servicios:<x>:generateJooq   # solo las tablas del esquema de <x>
```

El código generado **no se versiona**: el gate es la compilación. Si el esquema
cambió y el código no, el build falla.

Lo que el esquema aporta a esa generación:

- **Claves foráneas explícitas y nombradas** (`fk_<tabla>_<columna>`): documentan la
  relación en la base, donde se verifica, y no en una anotación que puede mentir.
- **Comentarios en todas las columnas** con las anotaciones del modelo: sobreviven a
  la generación y quedan a la vista en el editor.
- **Tipos nativos**: `uuid`, `numeric`, `timestamptz`, `date`, `jsonb`, `inet`,
  `char(n)`. Sin tipos `enum` de PostgreSQL a propósito: los enumerados son
  `CHECK` sobre `varchar`, que jOOQ mapea a `String` con el `CHECK` como garantía.
- **`numeric` llega como cadena** y se opera con decimales exactos, nunca con
  `number` ([[ADR-005 Dinero y decimales]], skill `dinero-decimal`).

### Convenciones al consultar

1. **La regla de dinero vive abajo.** El código recibe el error de la restricción;
   la garantía no está en la capa de acceso a datos ni en un servicio.
2. **Las tablas *append-only* no admiten `update` ni `delete`.** El rol de
   aplicación no tiene ese permiso: la corrección es el movimiento inverso.
3. **Las columnas generadas** (`saldo_total`, `perdida_neta`, `ratio_cobertura`,
   `saldo_pendiente`, …) se leen, no se escriben.
4. **Los invariantes diferidos** (partida doble de `transaccion_billetera` y de
   `asiento_contable`) se validan al `COMMIT`: **una transacción por caso de uso**,
   nunca una por paso.
5. **Seguridad por fila**: cada transacción fija `app.usuario_id` y `app.rol` con
   `SET LOCAL`, o las políticas de `cuenta_billetera` y `movimiento_billetera` no
   protegen nada ([[ADR-007 Sesión, RLS y pooling]], skill `seguridad-sesion-rls`).

## Antes de operar con datos reales

El esquema y los catálogos están completos y cargables. Lo que falta **no es SQL**:

1. **Confirmación legal de los valores marcados PROVISIONAL** (límites, alícuotas,
   plazos, vigencia de los umbrales). Cada fila tiene su columna `base_normativa`
   esperando la cita exacta.
2. **La licencia de ASFI.** Mientras `licencia_regulatoria.estado` no sea
   `OTORGADA` con su alcance cargado, ningún servicio financiero se habilita.
3. **El tipo de cambio diario** desde la fuente oficial: `99_desarrollo.sql` carga
   uno de prueba, en producción lo alimenta un proceso.
4. **El formato exacto de los archivos** PCC-01, ROG y del módulo de reclamos, que
   hay que pedir a cada organismo.

Detalle completo en `docs/Cumplimiento.md` §8.
