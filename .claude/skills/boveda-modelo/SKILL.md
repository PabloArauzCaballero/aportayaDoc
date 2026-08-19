---
name: boveda-modelo
description: "Modificar el modelo de datos de AportaYa (agregar, cambiar o quitar tablas, columnas y claves foráneas) y regenerar la bóveda de Obsidian. Úsala siempre que el trabajo toque docs/entidades/*.puml, entidades nuevas, columnas nuevas, relaciones o el generador scripts/generar_boveda.py. Incluye las convenciones de notación, la resolución de FK por nombre y la verificación obligatoria posterior."
---

# Modificar el modelo de datos

## Regla de oro

**Las notas de `docs/Modelos/` son generadas: nunca se editan a mano.** Se borran y
se reescriben completas en cada corrida del generador. La fuente de verdad son los
`.puml` de `docs/entidades/`.

```
docs/entidades/NN_modulo.puml   ← fuente de verdad (2 diagramas por archivo)
docs/entidades/NN_modulo.md     ← justificación de negocio, escrita a mano
        ↓ scripts/generar_boveda.py
docs/Index.md · docs/Modelos/Entidades/** · docs/Modelos/Relaciones/**
```

## Anatomía de un `.puml`

Cada archivo tiene **dos** bloques `@startuml … @enduml`:

1. **Clases** (`NN_..._clases`): diseño orientado a objetos con estereotipos DDD
   `<<AR>>` raíz de agregado, `<<VO>>` objeto de valor, `<<Svc>>` servicio,
   `<<Pol>>` política configurable. Enums para los estados.
2. **Relacional** (`NN_..._relacional`): lo que el generador parsea. Solo este
   bloque produce notas.

El parser lee del bloque relacional: `entity "tabla" as alias { … }`, las líneas
de cardinalidad (`a ||--o{ b : "etiqueta"`) y los `note ... of alias`.

## Notación obligatoria

```
* id : UUID <<PK>>
--
# grupo_id : UUID <<FK, IDX, M2>>
monto : DECIMAL(14,2) <<CK: > 0>>
moneda : CHAR(3)
estado : VARCHAR(20) <<CK, IDX>>
creado_en : TIMESTAMPTZ
opcional_id : UUID <<FK, NULL>>
```

- `*` PK · `#` FK · `<<UQ>>` único · `<<IDX>>` indexado · `<<CK>>` CHECK ·
  `<<NULL>>` admite nulos · `<<GENERATED>>` columna derivada.
- Importes en `DECIMAL(14,2)` (o `16,2`/`18,2` para acumulados) **siempre** con
  `moneda CHAR(3)` ISO-4217. Fechas en `TIMESTAMPTZ`.
- El sufijo `M3`, `M10`… en las anotaciones indica que la FK cruza a otro módulo.
- Nombres de tabla y columna en `snake_case`, sin acentos. **El texto dentro de los
  `.puml` va sin acentos** (el resto de la bóveda sí los usa).

## Resolución de claves foráneas

El generador resuelve el destino de cada `#` columna así, en orden:

1. `POR_MODULO[(modulo, columna)]` — para nombres ambiguos entre módulos.
2. `OVERRIDES[columna]` — mapeo explícito.
3. Patrón `participante` / actor (`*_por`, `asignado_a`, …) → `usuario`.
4. `columna` termina en `_id` y el prefijo es una tabla existente.

**Si agregás una columna FK cuyo nombre no coincide con la tabla destino, tenés que
agregar el override en `scripts/modelo.py`** (lo comparten el generador de
la bóveda y el de SQL). No hacerlo deja la FK sin
resolver y el generador lo reporta.

## Procedimiento

1. Editar el `.puml`: **los dos bloques**, clases y relacional. Un cambio en uno
   solo deja el modelo inconsistente.
2. Si la tabla es de dinero, auditoría o reportes, agregarla a `APPEND_ONLY` en
   `scripts/modelo.py`: eso basta para que quede sellada contra `UPDATE`/`DELETE`.
3. Si hay FK con nombre no obvio, agregar `OVERRIDES` (o `POR_MODULO`).
4. Actualizar la ficha de negocio `docs/entidades/NN_modulo.md`: **qué es, para qué
   sirve, por qué debe existir, a nivel de sistema**. Una entidad sin ficha es una
   entidad que nadie va a entender en seis meses.
5. Regenerar y verificar:

```bash
python3 scripts/generar_boveda.py    # notas de Obsidian
python3 scripts/generar_ddl.py       # esquema SQL en sql/ + extraer_sql + semillas
python3 scripts/verificar_boveda.py  # coherencia: casos, restricciones e índices
```

`generar_ddl.py` encadena `extraer_sql.py` (restricciones del catálogo) y
`generar_semillas.py` (`seeders/*.json` → `sql/60_semillas/` y `sql/61_dev/`).
Una columna nueva en el modelo puede romper una semilla: el generador de semillas
valida cada columna contra el modelo y falla si no existe. Eso es deseable.

## Verificación obligatoria

`generar_boveda.py` **debe** terminar con `"sin_resolver": []`; si aparece alguna
columna, falta un override.

`verificar_boveda.py` **debe** terminar con `TODO OK` y devuelve 1 si algo falla. Es
el que detecta la entidad nueva que nadie especificó en un caso de uso, la
restricción que se cita y no existe, y el índice que quedó desactualizado.

`generar_ddl.py` **debe** terminar con `Sin pendientes a nivel de datos.` Reporta
como pendiente toda columna `<<CK>>` cuyos valores no pueda derivar del diagrama de
clases y todo `UNIQUE` compuesto que apunte a una columna inexistente. Si hay
pendientes: se agregan los valores a `VALORES` en el generador, o se corrige el
`.puml`.

Y después, contra una base real:

```bash
docker run --rm -d --name pg-aportaya -e POSTGRES_PASSWORD=x -e POSTGRES_DB=aportaya \
  -v "$PWD/sql:/sql:ro" postgres:16 && sleep 10
docker exec pg-aportaya psql -U postgres -d aportaya -v ON_ERROR_STOP=1 -f /sql/aplicar.sql
docker exec pg-aportaya psql -U postgres -d aportaya -f /sql/50_verificacion/prueba_humo.sql
```

La prueba de humo tiene que dar **todo OK**. Requiere base recién creada.

```bash
# 1) Estructura de los diagramas: notas balanceadas y entidades detectadas
python3 - <<'EOF'
import re, pathlib
for p in sorted(pathlib.Path('docs/entidades').glob('*.puml')):
    t = p.read_text()
    op = len(re.findall(r'^\s*note\b', t, re.M)); cl = len(re.findall(r'^\s*end ?note\b', t, re.M))
    print(f'{p.name:34} notes {op}/{cl} entities {len(re.findall(r"^entity ", t, re.M))} {"OK" if op==cl else "DESBALANCE"}')
EOF

# 2) Enlaces rotos en la bóveda escrita a mano
python3 - <<'EOF'
import re, pathlib
notas = {p.stem for p in pathlib.Path('docs').rglob('*.md')}
for f in list(pathlib.Path('docs').glob('*.md')) + list(pathlib.Path('docs/CasosDeUso').glob('*.md')) + list(pathlib.Path('docs/entidades').glob('*.md')):
    for m in re.findall(r'\[\[([^\]|#]+)', f.read_text()):
        if m.strip().rstrip('\\') not in notas and not m.startswith('docs/'):
            print('ROTO', f.name, '->', m)
EOF
```

## Al agregar un módulo nuevo

1. Crear `docs/entidades/NN_nombre.puml` y `NN_nombre.md`.
2. Registrarlo en `MODULOS` y en `FOCO` dentro de `scripts/modelo.py`.
3. **El nombre del módulo no puede contener `/`**: se usa como nombre de carpeta.
4. Actualizar `README.md` (tabla de módulos) y las decisiones de diseño si aplica.

## Qué no hacer

- No editar `docs/Modelos/**`: se pierde en la siguiente corrida.
- No cablear cifras regulatorias (umbrales, límites, alícuotas, plazos) como
  `CHECK` con números: van en tablas con vigencia. Ver la skill `norma-nueva`.
- No agregar una tabla de dinero sin decidir si es *append-only*.
- No dejar una FK sin resolver "porque después se arregla".
- No definir dos enumeraciones con el mismo nombre en módulos distintos sin
  verificar el resultado: el generador resuelve primero en el módulo propio, pero
  la ambigüedad se lee mal (ya pasó con `EstadoTransaccion` en M03 y M10).

## Tamaño actual

307 tablas · 633 claves foráneas · 701 índices · 425 `CHECK`, en 14 módulos.
Si una corrida devuelve menos tablas de las que había, algo se rompió al parsear:
se revisa el `.puml` tocado antes de commitear.

## Ver también

- Skills relacionadas: `caso-de-uso`, `restriccion`, `norma-nueva`,
  `semillas-catalogos`, `datos-jooq`, `glosario-dominio`.
- `docs/Index.md` · `docs/entidades/README.md` · `docs/Cumplimiento.md`
