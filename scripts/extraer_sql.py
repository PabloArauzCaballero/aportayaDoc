#!/usr/bin/env python3
"""
Extrae el DDL de docs/Restricciones.md a un archivo .sql ejecutable.

    python3 scripts/extraer_sql.py        (desde la raíz del repositorio)

Lee    docs/Restricciones.md   (bloques ```sql)
Escribe sql/40_reglas/restricciones.sql

El documento es la fuente de verdad: no edite el .sql a mano, edite el .md y
vuelva a ejecutar. Cada bloque se emite precedido por el encabezado de la
sección en la que estaba, para que el archivo siga siendo legible.

El DDL se emite re-ejecutable (scripts/idempotencia.py): `sql/aplicar.sql` se
aplica igual sobre una base virgen que sobre una que ya lo tiene. Las consultas
de verificación no se tocan: son SELECT y ya se pueden repetir.
"""

import re
import pathlib
import sys

from idempotencia import NoSeComoHacerloIdempotente, idempotente

# Estos informes se imprimen con acentos y flechas. En Windows la consola entrega
# stdout en cp1252 y el generador muere con UnicodeEncodeError despues de haber
# escrito los archivos — en tres de las cinco maquinas del parque.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")


ORIGEN = pathlib.Path("docs/Restricciones.md")
DESTINO = pathlib.Path("sql/40_reglas/restricciones.sql")
DESTINO_VERIF = pathlib.Path("sql/50_verificacion/verificaciones.sql")
SECCION_VERIF = "Consultas de verificación"

CABECERA = """-- =====================================================================
--  AportaYa — restricciones normativas
--  GENERADO desde docs/Restricciones.md por scripts/extraer_sql.py
--  No edite este archivo a mano: edite el documento y regenere.
--
--  Requisitos del motor: PostgreSQL 15+, extensiones pgcrypto y btree_gist
-- =====================================================================
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS btree_gist;

"""


def main() -> int:
    if not ORIGEN.exists():
        print(f"No se encuentra {ORIGEN}")
        return 1

    texto = ORIGEN.read_text(encoding="utf-8")
    lineas = texto.splitlines()

    seccion = "General"
    bloques: list[tuple[str, str]] = []
    dentro = False
    actual: list[str] = []

    for linea in lineas:
        if not dentro and linea.startswith("## "):
            seccion = linea[3:].strip()
        if linea.strip().startswith("```sql"):
            dentro, actual = True, []
            continue
        if dentro and linea.strip() == "```":
            bloques.append((seccion, "\n".join(actual).rstrip()))
            dentro = False
            continue
        if dentro:
            actual.append(linea)

    verif = [(s, q) for s, q in bloques if s.startswith(SECCION_VERIF)]
    bloques = [(s, q) for s, q in bloques if not s.startswith(SECCION_VERIF)]

    DESTINO_VERIF.parent.mkdir(parents=True, exist_ok=True)
    DESTINO_VERIF.write_text(
        "-- Consultas de control: TODAS deben devolver cero filas.\n"
        "-- GENERADO desde docs/Restricciones.md — no editar a mano.\n"
        "-- Se ejecutan en cada despliegue y en el control diario,\n"
        "-- no forman parte de sql/aplicar.sql.\n\n"
        + "\n\n".join(q for _, q in verif) + "\n", encoding="utf-8")

    DESTINO.parent.mkdir(parents=True, exist_ok=True)
    partes = [CABECERA]
    seccion_previa = None
    for seccion, sql in bloques:
        if seccion != seccion_previa:
            partes.append(f"\n-- ---------------------------------------------------------------------\n"
                          f"-- {seccion}\n"
                          f"-- ---------------------------------------------------------------------\n")
            seccion_previa = seccion
        try:
            partes.append(idempotente(sql) + "\n")
        except NoSeComoHacerloIdempotente as e:
            print(f"{ORIGEN} · sección «{seccion}»: {e}")
            return 1

    DESTINO.write_text("\n".join(partes) + "\n", encoding="utf-8")

    codigos = sorted(set(re.findall(r"\bR-[A-Z]{3}-\d{2}\b", texto)))
    print(f"{DESTINO}: {len(bloques)} bloques SQL, {len(codigos)} restricciones · "
          f"{DESTINO_VERIF}: {len(verif)} bloque(s) de control")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
