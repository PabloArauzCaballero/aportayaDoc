#!/usr/bin/env python3
"""
Convierte los seeders JSON de seeders/ en SQL aplicable.

    python3 scripts/generar_semillas.py

Lee
    seeders/minimos/*.json   catálogos que también van a producción
    seeders/dev/*.json       datos de desarrollo y QA — NUNCA a producción
Escribe
    sql/60_semillas/         SQL de los seeders mínimos + sembrar.sql
    sql/61_dev/              SQL de los datos de dev + sembrar_dev.sql

La separación entre los dos conjuntos es dura y la verifica este mismo script:
ninguna tabla escrita por `minimos/` puede escribirse desde `dev/`, `minimos/` no
toca datos de personas, y el orquestador de dev arranca con una guarda que aborta
si la base no está marcada como entorno de desarrollo.

Los JSON son la fuente de verdad. El SQL es un derivado: no lo edite.

Formato de cada archivo:

    {
      "descripcion": "…",
      "bloques": [
        {
          "tabla": "cuenta_contable",
          "conflicto": ["codigo"],       // ON CONFLICT (…) DO NOTHING
          "filas": [ { "codigo": "1.1.01", … } ]
        }
      ]
    }

Valores especiales dentro de una fila:

    {"$ref": "tarifario", "codigo": "GENERAL", "version": 1}
        → subconsulta (SELECT id FROM tarifario WHERE codigo=… AND version=…)
    {"$sql": "now()"}          → se emite tal cual
    {"$fecha": "+30 days"}     → current_date + intervalo
    listas y objetos comunes   → literal JSONB
"""

import json
import pathlib
import shutil


import sys

# Estos informes se imprimen con acentos y flechas. En Windows la consola entrega
# stdout en cp1252 y el generador muere con UnicodeEncodeError despues de haber
# escrito los archivos — en tres de las cinco maquinas del parque.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from modelo import cargar, clase_de  # noqa: E402

ORIGEN = pathlib.Path("seeders")
DESTINOS = {
    "minimos": (pathlib.Path("sql/60_semillas"), "sembrar.sql",
                "Catálogos mínimos — también se aplican en producción"),
    "dev": (pathlib.Path("sql/61_dev"), "sembrar_dev.sql",
            "Datos de desarrollo — NO aplicar en producción"),
}

# Lo que debe decir el campo "entorno" de cada archivo de la carpeta.
ETIQUETA = {"minimos": "minimo", "dev": "dev"}

# Tablas con datos de personas: `minimos/` no las toca nunca. Un catálogo que
# necesita una persona está mal modelado, o es un dato de dev disfrazado.
PROHIBIDAS_EN_MINIMOS = {
    "usuario", "credencial_acceso", "historial_credencial", "sesion",
    "documento_identidad", "verificacion_kyc", "declaracion_pep",
    "debida_diligencia", "expediente_cliente", "perfil_financiero",
    "perfil_transaccional", "direccion_usuario", "canal_vinculado",
    "factor_mfa", "dispositivo", "cuenta_billetera", "movimiento_billetera",
    "asiento_contable", "linea_asiento", "intento_autenticacion",
    "aceptacion_contrato", "consentimiento", "referencia_personal",
}

# Guarda que encabeza el orquestador de dev: sin esta marca, no siembra.
GUARDA_DEV = "\n".join([
    "-- GUARDA — sin esto, estas semillas no entran a ninguna base.",
    "-- La marca la pone el arranque de desarrollo, nunca un despliegue:",
    "--   ALTER DATABASE pasanaku SET app.entorno = 'dev';",
    "DO $$",
    "BEGIN",
    "  IF current_setting('app.entorno', true) IS DISTINCT FROM 'dev' THEN",
    "    RAISE EXCEPTION",
    "      'SEMILLAS DE DEV BLOQUEADAS: app.entorno = %, se exige ''dev''',",
    "      coalesce(nullif(current_setting('app.entorno', true), ''), '<sin definir>');",
    "  END IF;",
    "END $$;",
    "",
])


def lit(valor):
    if valor is None:
        return "NULL"
    if isinstance(valor, bool):
        return "TRUE" if valor else "FALSE"
    if isinstance(valor, (int, float)):
        return str(valor)
    if isinstance(valor, dict):
        if "$sql" in valor:
            return valor["$sql"]
        if "$fecha" in valor:
            return f"(current_date + interval '{valor['$fecha']}')"
        if "$ref" in valor:
            tabla = valor["$ref"]
            cond = " AND ".join(f"{k} = {lit(v)}" for k, v in valor.items() if k != "$ref")
            return f"(SELECT id FROM {tabla} WHERE {cond})"
        return "'" + json.dumps(valor, ensure_ascii=False).replace("'", "''") + "'::jsonb"
    if isinstance(valor, list):
        return "'" + json.dumps(valor, ensure_ascii=False).replace("'", "''") + "'::jsonb"
    return "'" + str(valor).replace("'", "''") + "'"


def bloque_sql(b):
    # Bloque de SQL suelto (por ejemplo, ajustes de entorno de prueba)
    if "sql" in b and "tabla" not in b:
        prefijo = f"-- {b['comentario']}\n" if b.get("comentario") else ""
        return prefijo + b["sql"].rstrip() + "\n"
    tabla = b["tabla"]
    filas = b["filas"]
    if not filas:
        return ""
    columnas = list(dict.fromkeys(c for f in filas for c in f))
    conflicto = b.get("conflicto", [])

    def cola():
        if conflicto == "ninguno":
            return ";"
        if conflicto:
            return f"\nON CONFLICT ({', '.join(conflicto)}) DO NOTHING;"
        return "\nON CONFLICT DO NOTHING;"

    # Una jerarquía dentro de la misma tabla (cuenta_contable.cuenta_padre_id)
    # no se puede sembrar con un único INSERT multi-fila: los subselects de
    # $ref se evalúan contra el estado previo a la sentencia, así que ninguna
    # fila ve a las anteriores y el padre queda en NULL sin que nada falle.
    # En ese caso se emite una sentencia por fila, respetando el orden del
    # archivo (los padres van primero).
    autorreferente = any(
        isinstance(v, dict) and v.get("$ref") == tabla
        for f in filas for v in f.values())

    L = []
    if b.get("comentario"):
        L.append(f"-- {b['comentario']}")
    if autorreferente:
        L.append(f"-- Jerarquía en la propia tabla: una sentencia por fila para"
                 f" que cada\n-- hija vea a su madre ya insertada.")
        for f in filas:
            L.append(f"INSERT INTO {tabla} ({', '.join(columnas)}) VALUES\n  ("
                     + ", ".join(lit(f.get(c)) for c in columnas) + ")" + cola())
        sql = "\n".join(L) + "\n"
    else:
        L.append(f"INSERT INTO {tabla} ({', '.join(columnas)}) VALUES")
        L.append(",\n".join(
            "  (" + ", ".join(lit(f.get(c)) for c in columnas) + ")" for f in filas))
        L[-1] += cola()
        sql = "\n".join(L) + "\n"

    # Tablas sin clave natural: se cargan solo si están vacías, para que
    # volver a sembrar no duplique.
    if b.get("solo_si_vacia"):
        cuerpo = "\n".join("  " + l for l in sql.splitlines())
        sql = (f"DO $$\nBEGIN\n  IF NOT EXISTS (SELECT 1 FROM {tabla}) THEN\n"
               f"{cuerpo}\n  END IF;\nEND $$;\n")
    return sql


def validar(entorno, mods):
    """Contrasta cada seeder contra el modelo: tablas, columnas y referencias.

    Es la verificación que atrapa el error más común al escribir semillas: una
    columna que no existe. No sustituye a aplicar el SQL contra PostgreSQL, pero
    no depende de tener una base a mano.
    """
    columnas = {}
    for d in mods.values():
        for e in d["entidades"].values():
            columnas[e["tabla"]] = {c["nombre"] for c in e["cols"]}

    errores = []
    carpeta = ORIGEN / entorno
    manifiesto = json.loads((carpeta / "manifiesto.json").read_text(encoding="utf-8"))

    def revisar_ref(valor, origen):
        if not isinstance(valor, dict):
            return
        if "$ref" in valor:
            tabla = valor["$ref"]
            if tabla not in columnas:
                errores.append(f"{origen}: $ref a tabla inexistente '{tabla}'")
                return
            for k, v in valor.items():
                if k == "$ref":
                    continue
                if k not in columnas[tabla]:
                    errores.append(f"{origen}: $ref {tabla}.{k} no existe")
                revisar_ref(v, origen)

    for nombre in manifiesto["orden"]:
        ruta = carpeta / nombre
        if not ruta.exists():
            errores.append(f"{nombre}: declarado en el manifiesto pero no existe")
            continue
        datos = json.loads(ruta.read_text(encoding="utf-8"))
        for b in datos.get("bloques", []):
            if "tabla" not in b:
                continue
            tabla = b["tabla"]
            if tabla not in columnas:
                errores.append(f"{nombre}: tabla inexistente '{tabla}'")
                continue
            for i, fila in enumerate(b.get("filas", []), start=1):
                for col, valor in fila.items():
                    if col not in columnas[tabla]:
                        errores.append(f"{nombre}: {tabla}.{col} no existe (fila {i})")
                    revisar_ref(valor, f"{nombre}: {tabla} fila {i}")

    sueltos = {p.name for p in carpeta.glob("*.json")} - {"manifiesto.json"}
    for extra in sorted(sueltos - set(manifiesto["orden"])):
        errores.append(f"{extra}: existe pero no está en el manifiesto")
    return errores


def tablas_de(entorno):
    """Las tablas que escribe un conjunto de seeders."""
    escritas = set()
    for ruta in sorted((ORIGEN / entorno).glob("*.json")):
        if ruta.name == "manifiesto.json":
            continue
        datos = json.loads(ruta.read_text(encoding="utf-8"))
        for b in datos.get("bloques", []):
            if "tabla" in b:
                escritas.add(b["tabla"])
    return escritas


def validar_separacion():
    """La frontera dura entre `minimos/` y `dev/`.

    No es una convención de carpetas: es lo que impide que un dato de demostración
    viaje a producción dentro de un catálogo, y que un umbral regulatorio se
    modifique desde un archivo que nadie revisa como si fuera regulatorio.
    """
    errores = []

    for entorno, etiqueta in ETIQUETA.items():
        carpeta = ORIGEN / entorno
        manifiesto = json.loads((carpeta / "manifiesto.json").read_text(encoding="utf-8"))
        if manifiesto.get("entorno") != etiqueta:
            errores.append(f"{entorno}/manifiesto.json: entorno debe ser '{etiqueta}'")
        for nombre in manifiesto["orden"]:
            ruta = carpeta / nombre
            if not ruta.exists():
                continue
            datos = json.loads(ruta.read_text(encoding="utf-8"))
            if datos.get("entorno") != etiqueta:
                errores.append(
                    f"{entorno}/{nombre}: declara entorno "
                    f"'{datos.get('entorno')}' y está en la carpeta de '{etiqueta}'")

    de_minimos, de_dev = tablas_de("minimos"), tablas_de("dev")

    for tabla in sorted(de_minimos & PROHIBIDAS_EN_MINIMOS):
        errores.append(f"minimos/: escribe '{tabla}', que es dato de personas")

    for tabla in sorted(de_minimos & de_dev):
        errores.append(
            f"colisión: '{tabla}' la escriben mínimos y dev. Un catálogo tiene un"
            f" solo dueño: si es de producción va en minimos/, si no, en dev/")

    return errores


def procesar(entorno):
    carpeta = ORIGEN / entorno
    destino, orquestador, titulo = DESTINOS[entorno]
    if destino.exists():
        shutil.rmtree(destino)
    destino.mkdir(parents=True, exist_ok=True)

    manifiesto = json.loads((carpeta / "manifiesto.json").read_text(encoding="utf-8"))
    archivos, filas_totales = [], 0

    for nombre in manifiesto["orden"]:
        datos = json.loads((carpeta / nombre).read_text(encoding="utf-8"))
        salida = nombre.replace(".json", ".sql")
        L = [f"-- {datos.get('descripcion', nombre)}",
             f"-- GENERADO desde seeders/{entorno}/{nombre} — no editar a mano.", ""]
        for b in datos["bloques"]:
            L.append(bloque_sql(b))
            filas_totales += len(b.get("filas", []))
        (destino / salida).write_text("\n".join(L), encoding="utf-8")
        archivos.append(salida)

    # as_posix(): `destino` es un Path, y en Windows se renderiza con contrabarras.
    # Sin esto el comentario cambia segun la maquina que corre el generador y el
    # archivo rebota en cada merge entre el Mac y las Windows.
    L = [f"-- {titulo}",
         f"--   psql -d pasanaku -v ON_ERROR_STOP=1 -f {destino.as_posix()}/{orquestador}",
         "-- GENERADO desde seeders/ — no editar a mano.", "",
         "\\set ON_ERROR_STOP on", "BEGIN;", ""]
    if entorno == "dev":
        L += [GUARDA_DEV]
    L += [f"\\ir {a}" for a in archivos]
    L += ["", "COMMIT;", ""]
    (destino / orquestador).write_text("\n".join(L), encoding="utf-8")
    return len(archivos), filas_totales


def main():
    mods, _, _ = cargar()
    problemas = validar_separacion()
    for entorno in DESTINOS:
        problemas += validar(entorno, mods)
    if problemas:
        print(f"SEMILLAS INVÁLIDAS ({len(problemas)}):")
        for p in problemas:
            print(f"  - {p}")
        return 1

    total = 0
    for entorno in DESTINOS:
        archivos, filas = procesar(entorno)
        print(f"{entorno:8} → {DESTINOS[entorno][0]}: {archivos} archivos, {filas} filas")
        total += filas
    print(f"total: {total} filas de semilla · validadas contra el modelo")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
