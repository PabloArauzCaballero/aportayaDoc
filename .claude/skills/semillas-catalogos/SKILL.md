---
name: semillas-catalogos
description: "Agregar o cambiar datos de catálogo de AportaYa en seeders/ (umbrales, límites, tarifario, impuestos, roles, licencia, políticas) y regenerar el SQL de semillas. Úsala cuando cambie un valor regulatorio o comercial, cuando falte un catálogo para que un flujo funcione, o cuando alguien esté por escribir una cifra dentro del código."
---

# Sembrar catálogos

```
seeders/minimos/   catálogos sin los cuales el sistema NO opera → van también a producción
seeders/dev/       datos de desarrollo y QA                   → nunca a producción
        ↓ python3 scripts/generar_semillas.py   (lo encadena generar_ddl.py)
sql/60_semillas/   sql/61_dev/
```

## La frontera dev / mínimos es dura, y la verifica el generador

`generar_semillas.py` **no genera** si se rompe alguna de estas tres:

| Regla | Qué impide |
| --- | --- |
| Cada archivo declara `"entorno"` igual al de su carpeta (`minimo` / `dev`) | Que un archivo de demostración se cuele en la carpeta que va a producción |
| `minimos/` no escribe tablas de personas (`usuario`, `credencial_acceso`, `cuenta_billetera`, `sesion`, …) | Que una persona de demostración exista en producción |
| Una tabla la escribe **un solo** conjunto, nunca los dos | Que un umbral regulatorio se cambie desde un archivo que nadie revisa como regulatorio |

Y `sql/61_dev/sembrar_dev.sql` arranca con una guarda que aborta si la base no
tiene `app.entorno = 'dev'`. La marca la pone el arranque de desarrollo:

```bash
psql -d aportaya -c "ALTER DATABASE aportaya SET app.entorno = 'dev'"
```

**Dónde va cada cosa, sin dudar:** ¿lo necesita producción para operar? → `minimos/`.
¿Es una persona, un grupo, un movimiento o una credencial? → `dev/`. Si dudás, es
`dev/`: un mínimo que falta se nota al primer arranque; un dato de demostración en
producción se nota tarde y mal.

**Los JSON son la fuente de verdad.** El SQL es derivado y se regenera; editarlo a
mano se pierde en la siguiente corrida.

## Por qué los mínimos no son opcionales

La regla de *denegar por omisión* (`R-LIM-01`, `R-LIC-01`) rechaza toda operación
sin límite configurado, sin tarifario vigente y sin licencia. Con los catálogos
vacíos **el sistema no hace nada, y está bien**. Por eso los mínimos son parte del
despliegue, igual que el esquema: no son un extra de desarrollo.

Corolario para las pruebas: una suite que no siembra los mínimos falla entera, y
eso es la confirmación de que la regla funciona.

## Formato

```json
{
  "descripcion": "Para qué sirve este archivo",
  "entorno": "minimo",
  "bloques": [
    {
      "tabla": "cuenta_contable",
      "conflicto": ["codigo"],
      "filas": [ { "codigo": "1.1.01", "nombre": "…", "tipo": "ACTIVO" } ]
    }
  ]
}
```

| Clave del bloque | Efecto |
| --- | --- |
| `conflicto: ["col"]` | `ON CONFLICT (col) DO NOTHING` |
| `conflicto: []` | `ON CONFLICT DO NOTHING` |
| `conflicto: "ninguno"` | Sin cláusula |
| `solo_si_vacia: true` | Inserta solo si la tabla está vacía (tablas sin clave natural) |
| `sql: "UPDATE …"` | Bloque de SQL suelto, sin `tabla` |

| Valor especial en una fila | Se traduce a |
| --- | --- |
| `{"$ref": "tarifario", "codigo": "GENERAL", "version": 1}` | `(SELECT id FROM tarifario WHERE …)` — anidable |
| `{"$sql": "now()"}` | Se emite tal cual |
| `{"$fecha": "30 days"}` | `(current_date + interval '30 days')` |
| Objetos y listas | Literal `jsonb` |

El generador **valida cada columna contra el modelo** y falla si no existe. Si una
semilla rompe después de tocar un `.puml`, el modelo cambió: se corrige la semilla,
no el generador.

## Idempotencia

Los mínimos se aplican muchas veces —cada despliegue— y no deben duplicar nada. Por
eso cada bloque declara su clave natural en `conflicto`. Una tabla de catálogo sin
clave natural es un problema de diseño: se le agrega un `codigo` único.

## Vigencias: nunca se borra una fila

Cambiar un valor regulatorio es **cerrar la vigencia de la fila anterior y agregar
una nueva**, no editar. Las operaciones pasadas guardan el valor que se les aplicó
y deben poder explicarse con la fila que regía ese día.

```json
{ "codigo": "PCC01_CARGA_ACUM", "umbral_usd": 1000, "vigente_desde": "2026-01-01",
  "vigente_hasta": "2026-06-30", "base_normativa": "art. 52 inc. b" }
```

Toda fila de catálogo regulatorio lleva su cita: `base_normativa`,
`fuente_normativa` o `base_legal`. Una fila sin cita es un número que nadie va a
poder defender.

## Honestidad sobre lo provisional

Cada archivo declara su estado (`estado`, `advertencia`, `revisar_con`). Lo marcado
**PROVISIONAL** no va a producción sin confirmación legal. En particular:

> La licencia se siembra `EN_TRAMITE` **a propósito**: es el estado real mientras
> no exista resolución. Con ese estado ningún servicio financiero se habilita. El
> archivo trae, en `al_otorgarse_la_licencia`, el `UPDATE` que corresponde el día
> que se otorgue.

Sembrar la licencia como otorgada para "poder probar" convierte un dato falso en
la base de un reporte. Si hace falta, se usa el entorno de prueba.

## Datos de prueba: verosímiles y con intención

El set de `prueba/` está armado para poder ejercitar reglas concretas, no para
llenar tablas: hay una persona expuesta políticamente con debida diligencia
reforzada y segunda revisión (`R-UIF-10`), y la cuenta del grupo tiene al grupo
como titular y no al organizador (`R-GRP-04`). Al agregar datos de prueba, se
agrega también **qué regla permiten probar**.

## Procedimiento

1. Editar o agregar el JSON en `seeders/minimos/` o `seeders/dev/`.
2. Registrarlo en el `manifiesto.json` correspondiente, **en el orden correcto**:
   las FK exigen que el destino exista antes.
3. Actualizar la tabla de `seeders/README.md` con el archivo y su estado.
4. Regenerar y verificar:

```bash
python3 scripts/generar_semillas.py
psql -d aportaya -v ON_ERROR_STOP=1 -f sql/aplicar.sql
psql -d aportaya -v ON_ERROR_STOP=1 -f sql/60_semillas/sembrar.sql
psql -d aportaya -c "ALTER DATABASE aportaya SET app.entorno = 'dev'"
psql -d aportaya -v ON_ERROR_STOP=1 -f sql/61_dev/sembrar_dev.sql
psql -d aportaya -f sql/50_verificacion/prueba_humo.sql     # todo OK
```

5. Volver a correr el paso 2 de semillas: **no debe duplicar nada**.

## Checklist

- [ ] La fila lleva su cita normativa o su justificación comercial.
- [ ] Tiene vigencia, y la fila anterior quedó cerrada, no borrada.
- [ ] El bloque declara `conflicto` y es idempotente.
- [ ] Está en el `manifiesto.json`, después de sus dependencias.
- [ ] `seeders/README.md` refleja el archivo y su estado.
- [ ] Ningún dato de prueba se coló en `minimos/`.
- [ ] La prueba de humo pasa sobre base recién creada.

## Ver también

`norma-nueva` · `boveda-modelo` · `restriccion` · `cumplimiento-uif` ·
`pruebas-cu` · `seeders/README.md`
