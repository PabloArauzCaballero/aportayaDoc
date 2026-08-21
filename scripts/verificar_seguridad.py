#!/usr/bin/env python3
"""
Verifica el estándar de ciberseguridad de docs/Seguridad.md sobre el repositorio.

    python3 scripts/verificar_seguridad.py            (desde la raíz del repositorio)
    python3 scripts/verificar_seguridad.py --estricto (los avisos también fallan)

Devuelve 1 si algo falla, para usarlo como puerta del CI. No toca ningún archivo.

Por qué existe: un documento de seguridad con todos los casilleros marcados y ningún
comando que los compruebe da confianza sin sustento, que es peor que no tener el
documento. Acá se comprueba lo que se puede comprobar hoy, y lo que todavía no
—pruebas de intrusión, gestión de llaves, DLP— está declarado como brecha en
docs/Seguridad.md §7 en vez de figurar como control cumplido.

Seis bloques:

  1 · EL ESTÁNDAR ES COHERENTE   toda fila de control declara cómo se verifica, y
                                 toda restricción que cita existe de verdad
  2 · ACCESO ADMINISTRATIVO      ADR-038 vigente, R-SEG-10/11/12 en la bóveda y en el
                                 SQL generado, y las semillas coherentes con ellas
  3 · PATRONES PROHIBIDOS        las dieciséis prohibiciones que se pueden barrer
  4 · SECRETOS                   nada que parezca una credencial, versionado
  5 · EL CICLO ESTÁ CABLEADO     el contrato, la skill y el CI se conocen entre sí
  6 · CATÁLOGO vs ESPECIFICACIÓN permisos y canales que un CU exige y el catálogo no
                                 tiene todavía (AVISO: completarlos es decisión de
                                 seguridad, no de implementación)
"""
import argparse
import pathlib
import re
import sys

RAIZ = pathlib.Path(__file__).resolve().parent.parent
SEGURIDAD = RAIZ / "docs" / "Seguridad.md"
RESTRICCIONES = RAIZ / "docs" / "Restricciones.md"
CONTRATO = RAIZ / "docs" / "Contrato de implementación para IA.md"
SKILL = RAIZ / ".claude" / "skills" / "seguridad-aplicacion" / "SKILL.md"
WORKFLOWS = RAIZ / ".github" / "workflows"
SQL_REGLAS = RAIZ / "sql" / "40_reglas" / "restricciones.sql"

errores: list[str] = []
avisos: list[str] = []


def ok(msg):
    print(f"  OK    · {msg}")


def falla(msg):
    print(f"  FALLA · {msg}")
    errores.append(msg)


def aviso(msg):
    print(f"  AVISO · {msg}")
    avisos.append(msg)


def check(cond, bien, mal):
    ok(bien) if cond else falla(mal)


# Archivos de código que se barren en busca de patrones prohibidos. La bóveda queda
# fuera a propósito: un documento que EXPLICA por qué `Math.random` está prohibido
# tiene que poder escribir `Math.random`.
EXT_CODIGO = {".java", ".kt", ".kts", ".ts", ".tsx", ".js", ".jsx", ".py", ".sh", ".sql"}
EXCLUIDOS = {".git", "node_modules", "build", "dist", "__pycache__", ".gradle",
             "docs", "planes", ".claude", "landing"}


def archivos(extensiones=None, incluir_todo=False):
    for p in RAIZ.rglob("*"):
        if not p.is_file():
            continue
        partes = set(p.relative_to(RAIZ).parts)
        if partes & EXCLUIDOS:
            continue
        if incluir_todo or (extensiones and p.suffix in extensiones):
            yield p


# ---------------------------------------------------------------- 1 · el estándar
def bloque_1():
    print("=== 1 · EL ESTÁNDAR ES COHERENTE ===")
    if not SEGURIDAD.exists():
        falla("no existe docs/Seguridad.md: el estándar es la fuente de este gate")
        return ""
    t = SEGURIDAD.read_text(encoding="utf-8")

    secciones = ["## 0 · Qué es esto", "## 1 · El principio", "## 2 · Contra qué se defiende",
                 "## 3 · Los controles", "## 4 · Correspondencia con las normas ISO",
                 "## 5 · Prohibiciones absolutas", "## 6 · Cómo se verifica",
                 "## 7 · Lo que este documento NO da", "## 8 · Cómo se mantiene vivo"]
    faltan = [s for s in secciones if s not in t]
    check(not faltan, "el estándar tiene sus nueve secciones",
          f"faltan secciones en docs/Seguridad.md: {faltan}")

    # Toda fila de control de §3 declara su nivel de confianza (ISO/IEC 27034).
    cuerpo3 = t[t.index("## 3 · Los controles"):t.index("## 4 · Correspondencia")]
    filas = re.findall(r"^\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*$",
                       cuerpo3, re.M)
    filas = [f for f in filas
             if not f[0].startswith("---") and f[0] not in ("Regla", ":-:")]
    sin_nivel = [f[0][:48] for f in filas
                 if not re.search(r"motor|gate|revisión|prueba", f[2], re.I)]
    check(not sin_nivel, f"los {len(filas)} controles declaran cómo se verifican",
          f"controles sin nivel de confianza declarado: {sin_nivel[:5]}")

    # Toda restricción citada por el estándar existe en el catálogo.
    citadas = set(re.findall(r"\bR-[A-Z]{2,4}-\d{2}[a-z]?\b", t))
    catalogo = set(re.findall(r"\bR-[A-Z]{2,4}-\d{2}[a-z]?\b",
                              RESTRICCIONES.read_text(encoding="utf-8")))
    inventadas = sorted(citadas - catalogo)
    check(not inventadas, f"las {len(citadas)} restricciones citadas existen en el catálogo",
          f"el estándar cita restricciones inexistentes: {inventadas}")

    # Las brechas se numeran correlativas: una S-4 sin S-3 es una brecha que se perdió.
    brechas = [int(n) for n in re.findall(r"\bS-(\d+)\b", t)]
    esperado = list(range(1, max(brechas) + 1)) if brechas else []
    check(sorted(set(brechas)) == esperado,
          f"las {len(set(brechas))} brechas declaradas están numeradas sin huecos",
          f"numeración de brechas con huecos: {sorted(set(brechas))}")

    # Una cifra escrita a mano en el estándar envejece igual que en un plan. Se
    # cuenta como la cuenta verificar_boveda.py —sin sufijo de letra— para que las
    # dos puertas no digan números distintos de lo mismo.
    reales = len(set(re.findall(r"\bR-[A-Z]{2,4}-\d{2}\b",
                                RESTRICCIONES.read_text(encoding="utf-8"))))
    for n in re.findall(r"\b(\d{2,4})\s+restricciones", t):
        if int(n) != reales:
            falla(f"docs/Seguridad.md dice {n} restricciones y son {reales}")
            break
    else:
        ok(f"la cifra de restricciones del estándar está al día ({reales})")
    return t


# ------------------------------------------------- 2 · acceso administrativo (ADR-038)
def bloque_2():
    print("\n=== 2 · ACCESO ADMINISTRATIVO ===")
    adr = list((RAIZ / "docs" / "Arquitectura").glob("ADR-038*.md"))
    check(len(adr) == 1, "ADR-038 registrado",
          "falta ADR-038: el acceso administrativo no puede quedar en un documento de recorrido")
    if adr:
        check("estado: aceptada" in adr[0].read_text(encoding="utf-8"),
              "ADR-038 vigente", "ADR-038 no está en estado aceptada")

    texto_r = RESTRICCIONES.read_text(encoding="utf-8")
    sql = SQL_REGLAS.read_text(encoding="utf-8") if SQL_REGLAS.exists() else ""
    for codigo, marca in (("R-SEG-10", "fn_seg_sesion_operador_exige_mfa"),
                          ("R-SEG-11", "fn_seg_credencial_operador_corta_sesiones"),
                          ("R-SEG-12", "ck_permiso_decision_exige_mfa")):
        en_boveda = codigo in texto_r
        en_sql = marca in sql
        check(en_boveda and en_sql, f"{codigo} está en la bóveda y llegó al SQL generado",
              f"{codigo}: bóveda={en_boveda} sql={en_sql} — regenerá con generar_ddl.py")

    # El disparador no puede depender de una función auxiliar: se crearía en el primer
    # esquema del search_path de quien aplica, y el servicio que escribe no la vería.
    check("fn_seg_es_operador" not in sql,
          "los disparadores de operador no dependen de una función que el search_path puede no ver",
          "hay una función auxiliar en los disparadores de R-SEG-10/11: inlineá la condición")

    # Las semillas tienen que cumplir la misma regla que el CHECK, en la fuente.
    import json
    roles = RAIZ / "seeders" / "minimos" / "10-roles-y-permisos.json"
    if roles.exists():
        d = json.loads(roles.read_text(encoding="utf-8"))
        permisos = [f for b in d["bloques"] if b["tabla"] == "permiso" for f in b["filas"]]
        irreversibles = {"AUTORIZAR", "APROBAR", "EJECUTAR", "REVERSAR",
                         "PUBLICAR", "ENVIAR", "CERRAR", "LEER_TERCEROS"}
        malos = [p["codigo"] for p in permisos
                 if p["accion"] in irreversibles and not p.get("requiere_mfa")]
        check(not malos, f"los {len(permisos)} permisos sembrados respetan R-SEG-12",
              f"permisos de decisión irreversible sin segundo factor: {malos}")
        for necesario in ("SEGURIDAD_ACCESO_RESTABLECER", "SEGURIDAD_FACTOR_REINSCRIBIR"):
            check(any(p["codigo"] == necesario for p in permisos),
                  f"el permiso {necesario} está sembrado",
                  f"falta el permiso {necesario}: AF-01b/01c no tendrían con qué aprobar")

    # Un operador de dev sin TOTP no puede entrar (R-SEG-10). Eso es correcto en
    # produccion y deja el entorno de desarrollo inservible si nadie lo previó.
    dev = RAIZ / "seeders" / "dev"
    if dev.exists() and roles.exists():
        globales = {r["codigo"] for b in json.loads(roles.read_text(encoding="utf-8"))["bloques"]
                    if b["tabla"] == "rol" for r in b["filas"] if r["ambito"] == "GLOBAL"}
        operadores, con_totp = set(), set()
        for archivo in sorted(dev.glob("*.json")):
            datos = json.loads(archivo.read_text(encoding="utf-8"))
            for b in datos.get("bloques", []):
                if b.get("tabla") == "asignacion_rol":
                    for f in b["filas"]:
                        if f.get("rol_id", {}).get("codigo") in globales:
                            operadores.add(f["usuario_id"]["codigo_publico"])
                if b.get("tabla") == "factor_mfa":
                    for f in b["filas"]:
                        if f.get("tipo") == "TOTP" and f.get("activo"):
                            con_totp.add(f["usuario_id"]["codigo_publico"])
        sin_factor = sorted(operadores - con_totp)
        check(not sin_factor,
              f"los {len(operadores)} operadores de dev tienen TOTP y pueden entrar",
              f"operadores de dev sin TOTP: {sin_factor} — R-SEG-10 les cierra la sesión")


# --------------------------------------------------------- 3 · patrones prohibidos
# Cada patrón declara en qué extensiones aplica. El alcance no es comodidad: la
# prohibición de SQL concatenado protege una consulta que corre en el camino de una
# petición con datos de afuera; un generador en Python que ESCRIBE un archivo .sql no
# es eso, y perseguirlo ahí solo enseña a apagar el gate.
CODIGO_APP = {".java", ".kt", ".ts", ".tsx", ".js", ".jsx"}
CUALQUIERA = EXT_CODIGO

PROHIBIDOS = [
    (r"\bprintStackTrace\s*\(", "traza al log o a la respuesta (prohibición 10)"),
    (r"\bMath\.random\s*\(", "aleatoriedad no criptográfica (prohibición 6)"),
    (r"\bnew\s+Random\s*\(", "aleatoriedad no criptográfica (prohibición 6)"),
    (r"MessageDigest\.getInstance\(\s*\"(MD5|SHA-1)\"", "hash roto (prohibición 7)"),
    (r"dangerouslySetInnerHTML", "inyección en el cliente (prohibición 13)"),
    (r"curl[^\n|]*\|\s*(sudo\s+)?(ba)?sh", "descarga no verificada (§3.6)"),
    (r"\beval\s*\(", "ejecución dinámica (prohibición 11)", CODIGO_APP),
    (r"enableDefaultTyping|@JsonTypeInfo\(\s*use\s*=\s*Id\.CLASS",
     "deserialización polimórfica (prohibición 11)"),
    (r"\"\s*(SELECT|INSERT|UPDATE|DELETE)[^\"]*\"\s*\+", "SQL concatenado (prohibición 2)",
     CODIGO_APP),
    (r"@PermitAll|permitAll\(\)", "endpoint abierto sin marca declarada (prohibición 3)"),
    (r"\.setAllowedOrigins\(\s*\"\*\"|Access-Control-Allow-Origin:\s*\*",
     "CORS abierto (§3.1)"),
    (r"verify\s*=\s*False|rejectUnauthorized:\s*false|InsecureSkipVerify",
     "verificación de TLS desactivada (§3.4)"),
]


def bloque_3():
    print("\n=== 3 · PATRONES PROHIBIDOS ===")
    hallazgos, revisados = [], 0
    for p in archivos(EXT_CODIGO):
        # Este archivo enumera los patrones: encontrarlos acá es su función.
        if p.name == "verificar_seguridad.py":
            continue
        texto = p.read_text(encoding="utf-8", errors="ignore")
        revisados += 1
        for regla in PROHIBIDOS:
            patron, motivo = regla[0], regla[1]
            alcance = regla[2] if len(regla) > 2 else CUALQUIERA
            if p.suffix not in alcance:
                continue
            for m in re.finditer(patron, texto):
                linea = texto[:m.start()].count("\n") + 1
                hallazgos.append(f"{p.relative_to(RAIZ)}:{linea} — {motivo}")
    check(not hallazgos, f"{revisados} archivos de código sin patrones prohibidos",
          f"patrones prohibidos: {hallazgos[:8]}")
    if revisados == 0:
        aviso("todavía no hay código de aplicación: este bloque empieza a morder cuando lo haya")


# ------------------------------------------------------------------- 4 · secretos
SECRETOS = [
    (r"-----BEGIN [A-Z ]*PRIVATE KEY-----", "clave privada"),
    (r"\bAKIA[0-9A-Z]{16}\b", "credencial de AWS"),
    (r"\bsk_live_[0-9a-zA-Z]{16,}", "clave viva de pasarela"),
    (r"\bghp_[A-Za-z0-9]{30,}", "token de GitHub"),
    (r"\bxox[baprs]-[0-9A-Za-z-]{10,}", "token de Slack"),
    (r"(?i)\b(password|passwd|contrasena|secret|api[_-]?key|token)\s*[:=]\s*"
     r"[\"'][^\"'\s$#{}<>]{12,}[\"']", "credencial en claro"),
]
# Literales que el repositorio usa a propósito y no son secretos: hashes de
# demostración, marcadores de plantilla y contraseñas del contenedor de CI.
INOCENTES = re.compile(
    r"enc:v1:|demo-|\$argon2|\$2[aby]\$|POSTGRES_PASSWORD|PGPASSWORD|"
    r"\{\{|\$\{|<[a-z-]+>|xxx|CAMBIAR|placeholder|ejemplo", re.I)


def bloque_4():
    print("\n=== 4 · SECRETOS ===")
    hallazgos = []
    for p in archivos(incluir_todo=True):
        if p.name == "verificar_seguridad.py" or p.suffix in {".png", ".jpg", ".woff2", ".ico"}:
            continue
        try:
            texto = p.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        for patron, que in SECRETOS:
            for m in re.finditer(patron, texto):
                linea_txt = texto[max(0, m.start() - 120):m.end() + 40]
                if INOCENTES.search(linea_txt):
                    continue
                linea = texto[:m.start()].count("\n") + 1
                hallazgos.append(f"{p.relative_to(RAIZ)}:{linea} — {que}")
    check(not hallazgos, "ningún secreto versionado", f"posibles secretos: {hallazgos[:8]}")

    versionado = [p.name for p in RAIZ.rglob(".env*")
                  if p.is_file() and ".git" not in p.parts and p.suffix != ".ejemplo"]
    check(not versionado, "ningún archivo .env en el árbol de trabajo",
          f"hay archivos de entorno en el repositorio: {versionado}")

    gi = (RAIZ / ".gitignore")
    check(gi.exists() and ".env" in gi.read_text(encoding="utf-8"),
          ".gitignore excluye los archivos de entorno",
          ".gitignore no excluye .env: un secreto entra por descuido, no por decisión")


# ------------------------------------------------------- 5 · el ciclo está cableado
def bloque_5():
    print("\n=== 5 · EL CICLO ESTÁ CABLEADO ===")
    check(SKILL.exists(), "la skill seguridad-aplicacion existe",
          "falta .claude/skills/seguridad-aplicacion/SKILL.md")

    indice = (RAIZ / ".claude" / "skills" / "README.md")
    check(indice.exists() and "seguridad-aplicacion" in indice.read_text(encoding="utf-8"),
          "la skill está en el índice de skills",
          "la skill no figura en .claude/skills/README.md")

    if CONTRATO.exists():
        t = CONTRATO.read_text(encoding="utf-8")
        check("verificar_seguridad.py" in t,
              "el contrato de implementación exige este gate",
              "el contrato de implementación no cita verificar_seguridad.py")
        check("[[Seguridad]]" in t or "Seguridad.md" in t,
              "el contrato de implementación remite al estándar de seguridad",
              "el contrato de implementación no remite a docs/Seguridad.md")

    en_ci = any("verificar_seguridad.py" in w.read_text(encoding="utf-8")
                for w in WORKFLOWS.glob("*.yml")) if WORKFLOWS.exists() else False
    check(en_ci, "el CI ejecuta el gate de seguridad",
          "ningún workflow ejecuta verificar_seguridad.py: el gate no se ejecutaría solo")


# ------------------------------------- 6 · el catálogo dice lo que la bóveda exige
# Los dos huecos que la revisión de endurecimiento encontró y que nadie iba a notar
# solos: un caso de uso que exige un permiso inexistente, y un propósito de token
# cuyos canales están todos apagados. Son AVISO y no FALLA a propósito: completarlos
# es una decisión de seguridad de la información —qué recurso, qué acción, si exige
# segundo factor y de qué rol cuelga—, no algo que se resuelva inventando la fila.
# Se listan en cada corrida para que el hueco no se vuelva invisible por costumbre.
def bloque_6():
    print("\n=== 6 · CATÁLOGO COHERENTE CON LA ESPECIFICACIÓN ===")
    import json
    roles_json = RAIZ / "seeders" / "minimos" / "10-roles-y-permisos.json"
    cu_dir = RAIZ / "docs" / "CasosDeUso"
    if not roles_json.exists() or not cu_dir.exists():
        return

    d = json.loads(roles_json.read_text(encoding="utf-8"))
    permisos = {f["codigo"] for b in d["bloques"] if b["tabla"] == "permiso" for f in b["filas"]}
    roles = {f["codigo"] for b in d["bloques"] if b["tabla"] == "rol" for f in b["filas"]}

    citados = set()
    for cu in sorted(cu_dir.glob("CU-*.md")):
        m = re.search(r"## Eventos, trabajos y permisos(.*?)(\n## )", cu.read_text(encoding="utf-8"), re.S)
        if m:
            citados |= set(re.findall(r"`([A-Z][A-Z0-9_]{5,})`", m.group(1)))
    # Un rol en la columna «Exige» es uso legítimo: dice quién, no qué permiso.
    huerfanos = sorted(citados - permisos - roles)
    if huerfanos:
        aviso(f"S-8 · {len(huerfanos)} permisos que un CU exige y el catálogo no tiene: "
              f"{', '.join(huerfanos[:6])}…" if len(huerfanos) > 6 else
              f"S-8 · {len(huerfanos)} permisos que un CU exige y el catálogo no tiene: "
              f"{', '.join(huerfanos)}")
    else:
        ok(f"los {len(citados)} códigos que los CU exigen existen en el catálogo")

    # ADR-035: los únicos canales con adaptador encendido por defecto.
    activos = {"CORREO", "PUSH_APP", "APP_AUTENTICADORA"}
    tokens = RAIZ / "seeders" / "minimos" / "13-politicas-de-token.json"
    if tokens.exists():
        dt = json.loads(tokens.read_text(encoding="utf-8"))
        sin_canal = [f["proposito"] for b in dt["bloques"] for f in b["filas"]
                     if not (set(f.get("canales_permitidos", "").split(",")) & activos)]
        if sin_canal:
            aviso(f"S-9 · {len(sin_canal)} propósitos de token sin canal activo "
                  f"({', '.join(sin_canal)}): un flujo trabado invita a encender SMS")
        else:
            ok("todo propósito de token tiene al menos un canal con adaptador activo")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--estricto", action="store_true",
                        help="los avisos también hacen fallar")
    args = parser.parse_args()

    bloque_1()
    bloque_2()
    bloque_3()
    bloque_4()
    bloque_5()
    bloque_6()

    print()
    if errores:
        print(f"{len(errores)} FALLAS")
        return 1
    if avisos and args.estricto:
        print(f"{len(avisos)} AVISOS (modo estricto)")
        return 1
    print("TODO OK" + (f" · {len(avisos)} aviso(s)" if avisos else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
