#!/usr/bin/env python3
"""
Lee las reglas nombradas de `docs/Restricciones.md` y dice a qué columna toca cada una.

    from catalogo_reglas import reglas_por_tabla

Existe por una razón concreta. El modelo (`docs/entidades/*.puml`) declaraba
`<<UQ>>` y `<<CK>>` en columnas que el catálogo YA cubría con una regla nombrada,
y `generar_ddl.py` emitía entonces un índice único además del que declaraba el
catálogo: 22 índices y 4 CHECK duplicados, cada escritura pagando dos veces por
la misma garantía. En c66bee1 se quitaron esas anotaciones del modelo —bien
quitadas: el nombre es el que manda—, pero con ellas la bóveda perdió la
información de que esas columnas son únicas.

Así que la bóveda deja de leerla del modelo y la lee de donde la regla vive de
verdad. La ventaja no es sólo recuperar el dato: ahora la ficha dice **el nombre**
de la regla —`uq_tarifa_congelada_grupo`— que es el que verifica la prueba de humo
y el que hay que buscar cuando algo la viola.

Se lee `docs/Restricciones.md` y no el `.sql` generado a propósito: fuente contra
fuente, sin obligar a generar el esquema antes de generar la bóveda.
"""

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

from idempotencia import _enmascarar, dividir  # noqa: E402

ORIGEN = pathlib.Path("docs/Restricciones.md")

IDENT = r'(?:"[^"]+"|[A-Za-z_][A-Za-z0-9_$]*)'
CALIF = rf'{IDENT}(?:\.{IDENT})*'

_ALTER = re.compile(rf"\bALTER\s+TABLE\s+(?:ONLY\s+)?({CALIF})", re.I)
_CONSTRAINT = re.compile(
    rf"\bADD\s+CONSTRAINT\s+({IDENT})\s+(UNIQUE|CHECK|EXCLUDE|PRIMARY|FOREIGN)", re.I)
_INDICE = re.compile(
    rf"\bCREATE\s+(UNIQUE\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?({IDENT})\s+ON\s+({CALIF})",
    re.I)


def bloques_sql(texto):
    """Los bloques ```sql del documento, en orden."""
    dentro, actual, salida = False, [], []
    for linea in texto.splitlines():
        if linea.strip().startswith("```sql"):
            dentro, actual = True, []
        elif dentro and linea.strip() == "```":
            salida.append("\n".join(actual))
            dentro = False
        elif dentro:
            actual.append(linea)
    return salida


def _tras(texto, desde):
    """El primer paréntesis balanceado a partir de `desde`, sin su envoltura."""
    inicio = texto.find("(", desde)
    if inicio < 0:
        return ""
    hondo = 0
    for i in range(inicio, len(texto)):
        if texto[i] == "(":
            hondo += 1
        elif texto[i] == ")":
            hondo -= 1
            if hondo == 0:
                return texto[inicio + 1:i]
    return ""


def _columnas_simples(lista):
    """Los nombres de columna de una lista `(a, b, c)`. Vacío si trae expresiones.

    Un índice sobre `lower(codigo)` o `(fecha, (datos->>'x'))` no se puede repartir
    entre columnas sin interpretar SQL, y adivinar sería peor que no decir nada.
    """
    partes = [p.strip().strip('"') for p in lista.split(",")]
    if not partes or not all(re.fullmatch(r"[A-Za-z_][A-Za-z0-9_$]*", p) for p in partes):
        return []
    return partes


def reglas_por_tabla(origen=ORIGEN):
    """{tabla: [{nombre, tipo, columnas, parcial}]} — sólo reglas nombradas.

    `columnas` puede venir vacío cuando la regla es una expresión que no se reparte
    entre columnas: la regla igual se lista en la ficha de la tabla, que es donde
    alguien la va a buscar.
    """
    if not origen.exists():
        raise SystemExit(f"No se encuentra {origen}: es la fuente de las reglas nombradas")

    reglas = {}
    for bloque in bloques_sql(origen.read_text(encoding="utf-8")):
        for sentencia in dividir(bloque):
            mascara = _enmascarar(sentencia)
            if not mascara.strip():
                continue

            # finditer y no search: un solo ALTER TABLE puede declarar varias
            # restricciones separadas por coma, y quedarse con la primera perdía
            # justamente las que nadie vuelve a mirar.
            tabla = _ALTER.search(mascara)
            hallada = False
            for m in _CONSTRAINT.finditer(mascara):
                hallada = True
                if not tabla:
                    continue
                tipo = m.group(2).upper()
                if tipo in ("PRIMARY", "FOREIGN"):
                    continue
                cuerpo = _tras(sentencia, m.end())
                cols = (_columnas_simples(cuerpo) if tipo == "UNIQUE"
                        else _columnas_de_expresion(cuerpo))
                reglas.setdefault(tabla.group(1).split(".")[-1].strip('"'), []).append({
                    "nombre": m.group(1).strip('"'), "tipo": tipo,
                    "columnas": cols, "parcial": False})
            if hallada:
                continue

            m = _INDICE.search(mascara)
            if m:
                cuerpo = _tras(sentencia, m.end())
                cols = _columnas_simples(cuerpo)
                donde = mascara[m.end():]
                w = re.search(r"\bWHERE\b(.*)", donde, re.I | re.S)
                reglas.setdefault(m.group(3).split(".")[-1].strip('"'), []).append({
                    "nombre": m.group(2).strip('"'),
                    "tipo": "UNIQUE" if m.group(1) else "INDEX",
                    "columnas": cols,
                    "parcial": bool(w) and not _solo_descarta_nulos(w.group(1), cols)})
    return reglas


def _solo_descarta_nulos(condicion, columnas):
    """¿El WHERE del índice es sólo `col IS NOT NULL` sobre las columnas indexadas?

    Es el modismo de una columna única ANULABLE. En PostgreSQL dos NULL no chocan
    entre sí, así que ese índice parcial vale exactamente lo mismo que un único
    normal: la relación sigue siendo uno a uno, opcional. Contarlo como parcial
    hacía que la bóveda dijera que `cargo_comision → deduccion_entrega` no es uno
    a uno, cuando R-TAR-06 dice justamente que una deducción respalda un solo cargo.

    Cualquier otra condición —`WHERE estado = 'ACTIVA'`— sí es parcial de verdad:
    la unicidad rige sólo para algunas filas y prometer uno a uno sería mentir.
    """
    if not columnas:
        return False
    nombradas = set(re.findall(r"\b([a-z_][a-z0-9_]*)\s+IS\s+NOT\s+NULL", condicion, re.I))
    resto = re.sub(r"\b[a-z_][a-z0-9_]*\s+IS\s+NOT\s+NULL", "", condicion, flags=re.I)
    resto = re.sub(r"\b(AND|OR)\b|[()\s;]", "", resto, flags=re.I)
    return not resto and nombradas and nombradas <= set(columnas)


def _columnas_de_expresion(cuerpo):
    """Las columnas que nombra un CHECK o un EXCLUDE, por aparición literal.

    No interpreta SQL: junta los identificadores y deja que quien llama los cruce
    contra las columnas que la tabla realmente tiene. Un CHECK que menciona una
    palabra reservada o el nombre de una función se descarta en ese cruce.
    """
    return sorted(set(re.findall(r"\b([a-z_][a-z0-9_]*)\b", _enmascarar(cuerpo))))
