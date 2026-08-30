#!/usr/bin/env python3
"""
Hace re-ejecutable el SQL que sale de `docs/Restricciones.md`.

    from idempotencia import idempotente, NoSeComoHacerloIdempotente

`sql/aplicar.sql` no es una migración: es el esquema completo, y se aplica
tanto sobre una base virgen como sobre una que ya lo tiene. PostgreSQL trae
`IF NOT EXISTS` para las tablas y los índices, pero NO para `ADD CONSTRAINT`,
`CREATE TRIGGER` ni `CREATE POLICY`: sobre una base ya aplicada, la primera
repetida aborta el archivo entero. Sin esto, `./gradlew bd:aplicar` solo
funciona una vez y la única salida es `bd:reset`, que borra el volumen.

La regla es una sola, y por eso se puede explicar: **el archivo generado es la
verdad**. Cada objeto se borra si existe y se vuelve a crear. Un `ON DELETE`
que cambió en el modelo queda corregido al reaplicar, cosa que una guarda por
nombre —«si ya existe, no toques»— dejaría desactualizada en silencio.

El costo es que volver a crear una restricción revalida la tabla. `aplicar.sql`
construye el esquema, no atiende peticiones: se paga al desplegar, no en línea.
Y corre dentro de una transacción, así que en ningún momento hay una tabla sin
su restricción visible para otra sesión.

Lo que este módulo NO sabe hacer idempotente lo dice y falla. Es deliberado:
un generador que emite en silencio un archivo que se va a romper al aplicarlo
es peor que uno que no emite nada.
"""

import re

# Un identificador SQL: entrecomillado o desnudo, con o sin esquema.
IDENT = r'(?:"[^"]+"|[A-Za-z_][A-Za-z0-9_$]*)'
CALIF = rf'{IDENT}(?:\.{IDENT})*'


class NoSeComoHacerloIdempotente(Exception):
    """Una sentencia que se rompería al aplicarla dos veces, sin regla que la cubra."""


def _enmascarar(sql: str) -> str:
    """Devuelve `sql` con los comentarios y los literales vueltos espacios.

    Mantiene la longitud exacta para que los desplazamientos de una coincidencia
    sirvan sobre el texto original. Sin esto, un `RAISE EXCEPTION 'ADD CONSTRAINT
    ...'` o un `-- CREATE POLICY` comentado dispararían reglas que no
    corresponden.
    """
    salida = []
    i, n = 0, len(sql)
    while i < n:
        c = sql[i]
        if c == "-" and sql.startswith("--", i):
            j = sql.find("\n", i)
            j = n if j < 0 else j
            salida.append(" " * (j - i))
            i = j
        elif c == "/" and sql.startswith("/*", i):
            j = sql.find("*/", i + 2)
            j = n if j < 0 else j + 2
            salida.append(" " * (j - i))
            i = j
        elif c == "'":
            j = i + 1
            while j < n:
                if sql[j] == "'":
                    if j + 1 < n and sql[j + 1] == "'":
                        j += 2
                        continue
                    j += 1
                    break
                j += 1
            salida.append(" " * (j - i))
            i = j
        elif c == "$":
            m = re.compile(r"\$[A-Za-z_][A-Za-z0-9_]*\$|\$\$").match(sql, i)
            if m:
                cierre = sql.find(m.group(0), m.end())
                j = n if cierre < 0 else cierre + len(m.group(0))
                salida.append(" " * (j - i))
                i = j
            else:
                salida.append(c)
                i += 1
        else:
            salida.append(c)
            i += 1
    return "".join(salida)


def dividir(sql: str) -> list[str]:
    """Parte el SQL en sentencias por el `;` que está fuera de literal y comentario.

    Cada trozo conserva los comentarios que lo preceden: el archivo generado se
    lee, y una restricción sin su `-- R-XXX-nn` al lado no se puede rastrear
    hasta el documento que la ordenó.
    """
    mascara = _enmascarar(sql)
    trozos, inicio = [], 0
    for i, c in enumerate(mascara):
        if c == ";":
            trozos.append(sql[inicio:i + 1])
            inicio = i + 1
    resto = sql[inicio:]
    if resto.strip():
        trozos.append(resto)
    return trozos


def _cuerpo(trozo: str) -> int:
    """Desplazamiento donde empieza el SQL de verdad, salteando comentarios y blancos.

    La guarda se inserta ahí y no al principio del trozo: si fuera antes, el
    `DROP` quedaría separado de la sentencia por el comentario que la explica.
    """
    mascara = _enmascarar(trozo)
    m = re.search(r"\S", mascara)
    return m.start() if m else len(trozo)


# --- las reglas -------------------------------------------------------------
# Cada una recibe la sentencia enmascarada y devuelve las guardas que hay que
# anteponerle, o una sentencia reescrita.

_ALTER_ADD = re.compile(
    rf"\bALTER\s+TABLE\s+(?:IF\s+EXISTS\s+)?(?:ONLY\s+)?({CALIF})", re.I)
_ADD_CONSTRAINT = re.compile(rf"\bADD\s+CONSTRAINT\s+({IDENT})", re.I)
_ADD_COLUMN = re.compile(r"\bADD\s+COLUMN\s+(?!IF\s+NOT\s+EXISTS)", re.I)
_INDICE = re.compile(
    r"\bCREATE\s+(?:UNIQUE\s+)?INDEX\s+(?:CONCURRENTLY\s+)?(?!IF\s+NOT\s+EXISTS\b)", re.I)
_DISPARADOR = re.compile(
    rf"\bCREATE\s+(?:CONSTRAINT\s+)?TRIGGER\s+({IDENT})\b.*?\bON\s+({CALIF})", re.I | re.S)
_DISPARADOR_REEMPLAZO = re.compile(r"\bCREATE\s+OR\s+REPLACE\s+TRIGGER\b", re.I)
_POLITICA = re.compile(rf"\bCREATE\s+POLICY\s+({IDENT})\s+ON\s+({CALIF})", re.I)

# Lo que ya es re-ejecutable tal como está escrito. `ALTER` entra acá porque sus
# formas presentes (ENABLE ROW LEVEL SECURITY, FORCE, DEFAULT PRIVILEGES) se
# pueden repetir; sus dos formas peligrosas —ADD CONSTRAINT y ADD COLUMN— tienen
# regla propia y se resuelven antes de llegar a esta lista.
_SEGURAS = re.compile(
    r"\A(?:SELECT|WITH|DO|COMMENT|GRANT|REVOKE|SET|RESET|BEGIN|COMMIT|ROLLBACK"
    r"|ALTER|ANALYZE|VACUUM|REFRESH|TRUNCATE|CALL|EXECUTE"
    r"|DROP\s+.*?\bIF\s+EXISTS\b"
    r"|CREATE\s+(?:OR\s+REPLACE\s+)?(?:FUNCTION|PROCEDURE|VIEW|RULE|TRIGGER)"
    r"|CREATE\s+.*?\bIF\s+NOT\s+EXISTS\b"
    r"|INSERT\s+.*?\bON\s+CONFLICT\b)", re.I | re.S)


def _guardas(sentencia: str, mascara: str) -> tuple[list[str], str]:
    guardas: list[str] = []

    if _ADD_CONSTRAINT.search(mascara):
        tabla = _ALTER_ADD.search(mascara)
        if not tabla:
            raise NoSeComoHacerloIdempotente(
                f"ADD CONSTRAINT sin ALTER TABLE reconocible:\n{sentencia.strip()[:200]}")
        for m in _ADD_CONSTRAINT.finditer(mascara):
            guardas.append(
                f"ALTER TABLE {tabla.group(1)} DROP CONSTRAINT IF EXISTS {m.group(1)};")
        return guardas, sentencia

    if _ADD_COLUMN.search(mascara):
        m = _ADD_COLUMN.search(mascara)
        return [], sentencia[:m.end()] + "IF NOT EXISTS " + sentencia[m.end():]

    if m := _INDICE.search(mascara):
        return [], sentencia[:m.end()] + "IF NOT EXISTS " + sentencia[m.end():]

    if _DISPARADOR_REEMPLAZO.search(mascara):
        return [], sentencia

    if m := _DISPARADOR.search(mascara):
        return [f"DROP TRIGGER IF EXISTS {m.group(1)} ON {m.group(2)};"], sentencia

    if m := _POLITICA.search(mascara):
        return [f"DROP POLICY IF EXISTS {m.group(1)} ON {m.group(2)};"], sentencia

    if not _SEGURAS.match(mascara.strip()):
        raise NoSeComoHacerloIdempotente(
            "no hay regla que la haga re-ejecutable; agregala en "
            f"scripts/idempotencia.py:\n{sentencia.strip()[:200]}")
    return [], sentencia


def idempotente(sql: str) -> str:
    """Devuelve `sql` re-ejecutable. Falla si encuentra algo que no sabe tratar."""
    salida = []
    for trozo in dividir(sql):
        if not trozo.strip():
            salida.append(trozo)
            continue
        mascara = _enmascarar(trozo)
        guardas, trozo = _guardas(trozo, mascara)
        if guardas:
            corte = _cuerpo(trozo)
            sangria = re.search(r"[^\n]*\Z", trozo[:corte]).group(0)
            trozo = trozo[:corte] + ("\n" + sangria).join(guardas) + "\n" + sangria + trozo[corte:]
        salida.append(trozo)
    return "".join(salida)
