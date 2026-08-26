#!/usr/bin/env python3
"""Catalogo de errores: nombre de restriccion -> R-XXX-nn.

Cuando PostgreSQL rechaza una escritura devuelve el NOMBRE de la restriccion, no
la regla de negocio. Sin esta tabla, el usuario ve
`uq_cuenta_billetera_titular_moneda` y el equipo tiene que ir al SQL para saber
que significa. Con ella, la capa web traduce a `R-BIL-04` y de ahi al
`AP-CU<NN>-<nn>` que el caso de uso declara (skill `errores-api`).

La fuente es sql/, no una lista escrita a mano: si una restriccion se renombra y
nadie actualiza el catalogo, el archivo generado cambia y el CI lo ve.

    python3 scripts/generar_errores.py <archivo de salida>
"""
import re
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
HUMO = RAIZ / "sql/50_verificacion/prueba_humo.sql"
REGLAS = RAIZ / "sql/40_reglas/restricciones.sql"

CODIGO = r"R-[A-Z]{3}-\d{2}"
# La prueba de humo ya lleva la pareja curada: ('R-XXX-nn nombre', 'nombre').
PAR_HUMO = re.compile(rf"\(\s*'({CODIGO})[^']*'\s*,\s*'([a-z0-9_]+)'\s*\)")
# En restricciones.sql, un comentario -- R-XXX-nn abre el bloque de su regla.
COMENTARIO = re.compile(rf"^\s*--\s*({CODIGO})\b")
# Solo los prefijos de la convencion del repositorio. Sin esto, un `CREATE INDEX
# ... AS` mete la palabra `as` en el catalogo como si fuera una restriccion.
NOMBRADO = re.compile(
    r"\b(?:CONSTRAINT|TRIGGER|(?:UNIQUE\s+)?INDEX(?:\s+IF\s+NOT\s+EXISTS)?)\s+"
    r"((?:ck|uq|fk|pk|tg|ex|ix)_[a-z0-9_]+)",
    re.IGNORECASE,
)


def desde_prueba_de_humo():
    if not HUMO.is_file():
        return {}
    return {nombre: codigo for codigo, nombre in PAR_HUMO.findall(HUMO.read_text(encoding="utf-8"))}


def desde_las_reglas():
    """Lo que restricciones.sql nombra bajo un comentario -- R-XXX-nn."""
    if not REGLAS.is_file():
        return {}
    encontrado = {}
    vigente = None
    for linea in REGLAS.read_text(encoding="utf-8").splitlines():
        marca = COMENTARIO.match(linea)
        if marca:
            vigente = marca.group(1)
            continue
        if not linea.strip():
            vigente = None
            continue
        if vigente:
            for nombre in NOMBRADO.findall(linea):
                encontrado.setdefault(nombre.lower(), vigente)
    return encontrado


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        return 2

    catalogo = desde_las_reglas()
    # La prueba de humo manda: es la pareja curada y la que el gate verifica.
    catalogo.update(desde_prueba_de_humo())

    salida = Path(sys.argv[1])
    salida.parent.mkdir(parents=True, exist_ok=True)
    lineas = [
        "# nombre de restriccion = R-XXX-nn",
        "# GENERADO por scripts/generar_errores.py desde sql/ — no editar a mano.",
        "",
    ]
    lineas += [f"{nombre}={codigo}" for nombre, codigo in sorted(catalogo.items())]
    salida.write_text("\n".join(lineas) + "\n", encoding="utf-8")

    reglas = len(set(catalogo.values()))
    print(f"catalogo de errores: {len(catalogo)} restricciones -> {reglas} reglas · {salida}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
