#!/usr/bin/env python3
"""
Corre la prueba de humo del esquema sobre una base DESECHABLE.

    python3 scripts/probar_humo.py        (o `./gradlew bd:humo`)

Por qué no se corre sobre `pasanaku`, que es lo que hacía antes:

1. `prueba_humo.sql` siembra sus propias filas con identificadores fijos
   —`PLT-0001`, `ZZAPROB01`, `ZZ-MOV`— y no las borra. La segunda corrida
   chocaba contra las suyas de la primera: 27 líneas FALLA que no eran defectos
   del sistema sino basura de la corrida anterior. La única salida era
   `bd:reset`, que borra el volumen entero.
2. Y aunque no volviera a correr, esas filas quedaban para siempre en la base
   de trabajo. Una cuenta de plataforma de mentira conviviendo con los datos de
   desarrollo es una trampa esperando a alguien.

La base de la prueba se arma acá y se tira al terminar, pase lo que pase. Se
configura leyendo la configuración de la base de trabajo en vez de repetirla:
si mañana cambia el `search_path`, la prueba lo hereda sin que nadie se acuerde
de tocar este archivo.

Lo que se verifica es el ESQUEMA —que las restricciones rechacen lo que tienen
que rechazar—, y el esquema sale de `sql/`, el mismo para las dos bases. Probarlo
en una copia limpia no prueba menos: prueba lo mismo sin ensuciar nada.
"""

import pathlib
import re
import subprocess
import sys

CONTENEDOR = "aportaya-postgres"
ADMIN = "pasanaku"
BASE_TRABAJO = "pasanaku"
BASE_PRUEBA = "pasanaku_humo"

# El orden es el de aplicar.sql y no se negocia: sin esquemas no hay tablas, sin
# tablas no hay semillas, y sin semillas la prueba de humo no prueba nada.
PASOS = [
    ("sql/aplicar.sql", "esquema, claves, índices, reglas y permisos"),
    ("sql/60_semillas/sembrar.sql", "los catálogos mínimos"),
    ("sql/61_dev/sembrar_dev.sql", "los datos de desarrollo"),
]
HUMO = "sql/50_verificacion/prueba_humo.sql"
# La última línea que imprime el archivo. Sin ella, se cortó a mitad de camino
# y un «0 FALLA» no significa nada: significa que no llegó a preguntar.
TERMINO = "Prueba de humo terminada"

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")


def psql(base, *args, archivo=None, entrada=None):
    orden = ["docker", "exec", "-i", CONTENEDOR,
             "psql", "-U", ADMIN, "-d", base, "-q", *args]
    if archivo:
        orden += ["-v", "ON_ERROR_STOP=1", "-f", f"/repo/{archivo}"]
    return subprocess.run(orden, input=entrada, capture_output=True, text=True)


def leer_configuracion(base):
    """Los ajustes que la base lleva pegados (`ALTER DATABASE ... SET`)."""
    r = psql("postgres", "-tAc",
             "SELECT unnest(coalesce(setconfig, '{}')) "
             "FROM pg_database d LEFT JOIN pg_db_role_setting s "
             "  ON s.setdatabase = d.oid AND s.setrole = 0 "
             f"WHERE d.datname = '{base}'")
    if r.returncode != 0:
        raise SystemExit(
            f"No se pudo leer la configuración de {base}:\n{r.stderr}\n"
            "¿Está levantado el contenedor? docker compose "
            "-f despliegue/compose/base.yml --profile base up -d --wait")
    return [l for l in r.stdout.splitlines() if "=" in l]


def crear_base_de_prueba():
    """Base nueva, con la MISMA configuración que la de trabajo.

    `search_path` y `app.entorno` viven en la base (los pone el arranque del
    contenedor). Se copian en vez de repetirse: una lista duplicada acá se
    desincroniza el día que alguien agregue un esquema, y el síntoma sería una
    prueba que falla por una razón que no tiene nada que ver.
    """
    ajustes = leer_configuracion(BASE_TRABAJO)

    tirar_base_de_prueba()
    r = psql("postgres", "-c", f'CREATE DATABASE "{BASE_PRUEBA}"')
    if r.returncode != 0:
        raise SystemExit(f"No se pudo crear {BASE_PRUEBA}:\n{r.stderr}")

    for ajuste in ajustes:
        clave, _, valor = ajuste.partition("=")
        # El valor va SIN comillas, tal como lo devuelve el catálogo. Entrecomillado
        # entero, `search_path = 'aportes, catalogo, public'` se guarda como UN
        # esquema llamado «aportes, catalogo, public»: la base queda sin ruta de
        # búsqueda y las semillas fallan con «relation does not exist».
        r = psql("postgres", "-c", f'ALTER DATABASE "{BASE_PRUEBA}" SET {clave} = {valor}')
        if r.returncode != 0:
            raise SystemExit(f"No se pudo heredar {clave} en {BASE_PRUEBA}:\n{r.stderr}")

    copiados = leer_configuracion(BASE_PRUEBA)
    if set(copiados) != set(ajustes):
        raise SystemExit(
            "La base de prueba no quedó configurada como la de trabajo.\n"
            f"  esperado: {sorted(ajustes)}\n  quedó:    {sorted(copiados)}")
    return ajustes


def tirar_base_de_prueba():
    psql("postgres", "-c", f'DROP DATABASE IF EXISTS "{BASE_PRUEBA}" WITH (FORCE)')


def main() -> int:
    if not pathlib.Path(HUMO).exists():
        raise SystemExit(f"Corré esto desde la raíz del repositorio: no encuentro {HUMO}")

    ajustes = crear_base_de_prueba()
    print(f"Base desechable {BASE_PRUEBA} · configuración heredada: "
          f"{', '.join(a.split('=')[0] for a in ajustes) or 'ninguna'}")
    try:
        for archivo, que in PASOS:
            r = psql(BASE_PRUEBA, archivo=archivo)
            errores = [l for l in r.stderr.splitlines() if "ERROR:" in l]
            if r.returncode != 0 or errores:
                print(f"  {archivo} · {que} → FALLÓ")
                print("\n".join(errores[:5]) or r.stderr[:2000])
                return 1
            print(f"  {archivo} · {que} → ok")

        r = psql(BASE_PRUEBA, archivo=HUMO)
        print()
        print(r.stdout.rstrip())

        ok = len(re.findall(r"^OK\b", r.stdout, re.M))
        fallas = re.findall(r"^FALLA.*$", r.stdout, re.M)
        # Los ERROR de psql NO son fallas acá: el archivo apaga ON_ERROR_STOP a
        # propósito y varios casos se prueban justamente provocando el rechazo
        # —R-BIL-01 descuadra una transacción para que el COMMIT la tire—. El
        # veredicto es el que declara el propio archivo: toda línea empieza con OK.
        errores = [l for l in r.stderr.splitlines() if "ERROR:" in l]
        completa = TERMINO in r.stdout

        print(f"\n{ok} OK · {len(fallas)} FALLA · "
              f"{len(errores)} rechazos del motor (esperados en los casos negativos)")
        for f in fallas:
            print(f"  {f}")

        if not completa:
            print(f"\nLa prueba no llegó al final: falta «{TERMINO}». "
                  "Se cortó antes, y lo que no corrió no probó nada.")
            for e in errores[-5:]:
                print(f"  {e}")
            return 1
        # Una prueba que informa lo que salió mal y devuelve 0 no es una prueba:
        # el tablero queda verde y nadie mira la salida.
        if fallas or ok == 0:
            return 1
        return 0
    finally:
        tirar_base_de_prueba()


if __name__ == "__main__":
    raise SystemExit(main())
