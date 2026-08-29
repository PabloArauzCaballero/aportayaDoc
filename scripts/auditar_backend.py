#!/usr/bin/env python3
"""Auditoria integral del backend. Mide, no opina.

Siete dimensiones, cada una con hallazgos concretos —archivo y linea— y una nota. La
nota global es el promedio ponderado. **Una auditoria que no se puede volver a correr
no es una auditoria: es una foto**, y por eso esto es un script y no un documento.

Cada regla dice que busca y por que importa. Las que no se pueden verificar desde el
codigo se declaran como tales en vez de puntuarse a ojo.

Uso:  python3 scripts/auditar_backend.py [--json]
"""
from __future__ import annotations

import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

import yaml

RAIZ = Path(__file__).resolve().parent.parent
SERVICIOS = sorted(p.name for p in (RAIZ / "servicios").iterdir() if p.is_dir())

ESQUEMAS = {
    "identidad", "grupos", "aportes", "entregas", "notificaciones", "transparencia",
    "organizador", "garantia", "auditoria", "nucleo_financiero", "tarifas",
    "cumplimiento", "erp", "publicidad",
}


@dataclass
class Dimension:
    nombre: str
    peso: int
    porque: str
    revisadas: int = 0
    hallazgos: list[str] = field(default_factory=list)

    @property
    def nota(self) -> float:
        if self.revisadas == 0:
            return 0.0
        limpio = max(0, self.revisadas - len(self.hallazgos))
        return round(10 * limpio / self.revisadas, 2)


def java_de(servicio: str):
    return list((RAIZ / "servicios" / servicio / "src/main/java").rglob("*.java"))


def contrato_de(servicio: str):
    rutas = list((RAIZ / "servicios" / servicio / "src/main/resources/openapi").glob("*.yaml"))
    if not rutas:
        return None, ""
    texto = rutas[0].read_text(encoding="utf-8")
    return yaml.safe_load(texto), texto


def operaciones(contrato) -> list[tuple[str, str, dict]]:
    return [
        (ruta, metodo, op)
        for ruta, metodos in (contrato or {}).get("paths", {}).items()
        for metodo, op in metodos.items()
        if metodo in ("get", "post", "put", "patch", "delete")
    ]


def rel(p: Path) -> str:
    return str(p.relative_to(RAIZ))


def sin_comentarios(texto: str) -> str:
    """Quita comentarios y javadoc.

    Sin esto la auditoria acusa a los comentarios que **explican** una regla de
    violarla: el javadoc que dice «los consentimientos viven en
    `identidad.consentimiento`» salia como lectura de esquema ajeno.
    """
    texto = re.sub(r"/\*[\s\S]*?\*/", "", texto)
    return re.sub(r"//[^\n]*", "", texto)


def literales(texto: str):
    """Las cadenas del codigo, con la linea en que empiezan."""
    for m in re.finditer(r'"""([\s\S]*?)"""|"((?:[^"\\\n]|\\.)*)"', texto):
        yield (m.group(1) or m.group(2) or ""), texto[: m.start()].count("\n") + 1


PALABRAS_SQL = ("SELECT ", "FROM ", "JOIN ", "INSERT INTO", "UPDATE ", "DELETE FROM")


def es_sql(cadena: str) -> bool:
    arriba = cadena.upper()
    return any(p in arriba for p in PALABRAS_SQL)


# ------------------------------------------------------------------ 1 alcance --
def alcance() -> Dimension:
    d = Dimension(
        "Alcance",
        3,
        "Una operacion declarada y no implementada es una promesa rota: el cliente la "
        "genera, programa contra ella y recibe un 501.",
    )
    for servicio in SERVICIOS:
        contrato, _ = contrato_de(servicio)
        if contrato is None:
            d.hallazgos.append(f"{servicio}: sin contrato OpenAPI")
            d.revisadas += 1
            continue
        codigo = " ".join(
            f.read_text(encoding="utf-8") for f in java_de(servicio) if f.name.endswith("Controller.java")
        )
        for _, _, op in operaciones(contrato):
            d.revisadas += 1
            if not re.search(r"\b" + re.escape(op["operationId"]) + r"\s*\(", codigo):
                d.hallazgos.append(f"{servicio}: `{op['operationId']}` declarada sin implementar")
    return d


# --------------------------------------------------------------- 2 seguridad --
def seguridad() -> Dimension:
    d = Dimension(
        "Seguridad",
        5,
        "Denegar por omision (invariante 9). Un endpoint sin decision de acceso, un "
        "permiso que el catalogo no tiene o un secreto versionado son fallos de una "
        "sola linea con consecuencia total.",
    )
    catalogo = set()
    semilla = RAIZ / "sql/60_semillas/10-roles-y-permisos.sql"
    if semilla.exists():
        catalogo = set(re.findall(r"'([A-Z][A-Z_]{3,})'", semilla.read_text(encoding="utf-8")))

    for servicio in SERVICIOS:
        for archivo in java_de(servicio):
            if not archivo.name.endswith("Controller.java"):
                continue
            texto = archivo.read_text(encoding="utf-8")
            cabecera = texto.split("public class", 1)[0]
            de_clase = "@Permiso" in cabecera or "@Publico" in cabecera
            for bloque in re.finditer(
                r"((?:@\w+(?:\([^)]*\))?\s*)+)public\s+ResponseEntity<.+?>\s+(\w+)\s*\(", texto, re.DOTALL
            ):
                d.revisadas += 1
                anotaciones, operacion = bloque.group(1), bloque.group(2)
                if not ("@Permiso" in anotaciones or "@Publico" in anotaciones or de_clase):
                    d.hallazgos.append(f"{rel(archivo)}: `{operacion}` sin @Permiso ni @Publico")
                for permiso in re.findall(r'@Permiso\("([A-Z_]+)"\)', anotaciones):
                    if catalogo and permiso not in catalogo:
                        d.hallazgos.append(
                            f"{rel(archivo)}: `{operacion}` exige `{permiso}`, que el catalogo no tiene"
                        )

    # Secretos versionados: una clave en un yaml es una clave publicada.
    for yml in (RAIZ / "servicios").rglob("application.yml"):
        d.revisadas += 1
        for numero, linea in enumerate(yml.read_text(encoding="utf-8").splitlines(), 1):
            if re.search(r"(clave|secreto|password|token)\s*:\s*(?!\$\{)[\"']?\S{8,}", linea, re.I):
                if "${" not in linea and not linea.strip().startswith("#"):
                    d.hallazgos.append(f"{rel(yml)}:{numero}: posible secreto escrito")
    return d


# ------------------------------------------------------------- 3 invariantes --
# Divergencias que el modelo obliga y que estan escritas en el codigo con su razon.
# Se listan siempre, aunque no bajen la nota: una divergencia aceptada que nadie
# vuelve a mirar deja de ser una decision y pasa a ser una costumbre.
DECLARADAS: list[str] = []


def invariantes() -> Dimension:
    d = Dimension(
        "Invariantes",
        5,
        "Los doce de `planes/00`. Si el codigo viola uno esta mal aunque pase las "
        "pruebas: son las reglas que hacen que el dinero cuadre.",
    )
    for servicio in SERVICIOS:
        for archivo in java_de(servicio):
            texto = archivo.read_text(encoding="utf-8")
            limpio = sin_comentarios(texto)
            partes = archivo.parts
            d.revisadas += 1

            # Invariante 2: @Transactional solo en aplicacion/.
            if "@Transactional" in limpio and "aplicacion" not in partes:
                d.hallazgos.append(f"{rel(archivo)}: @Transactional fuera de aplicacion/ (invariante 2)")

            # Invariante 3: SET plano se filtra a la siguiente transaccion.
            if re.search(r'"\s*SET\s+(?!LOCAL)', limpio, re.I):
                d.hallazgos.append(f"{rel(archivo)}: `SET` sin `LOCAL` (invariante 3)")

            # Invariante 11: nadie lee el esquema de otro.
            #
            # Solo dentro de una cadena que **es** SQL, o de un `DSL.name("esquema",…)`.
            # Buscar el nombre suelto acusaba a una variable local llamada
            # `organizador` y al javadoc que explica donde vive un dato.
            propio = servicio.replace("-", "_")
            ajenos = ESQUEMAS - {propio}
            # Una divergencia ESCRITA no es lo mismo que una silenciosa. Estas son las
            # que el modelo obliga —la clave foranea esta del otro lado, o el bloqueo
            # tiene que pasar dentro de la transaccion— y cerrarlas exige tocar `sql/`
            # o romper el invariante 6. Se cuentan aparte, con su razon al lado, en vez
            # de fingir que no estan o de taparlas con un puerto que empeora otra cosa.
            declarado = "INVARIANTE-11 DECLARADO" in texto
            for cadena, linea in literales(limpio):
                if not es_sql(cadena):
                    continue
                for ajeno in ajenos:
                    if re.search(rf"\b{ajeno}\.[a-z_]+", cadena):
                        (DECLARADAS if declarado else d.hallazgos).append(
                            f"{rel(archivo)}:{linea}: SQL contra el esquema `{ajeno}` (invariante 11)"
                        )
            for m in re.finditer(r'DSL\.name\(\s*"(\w+)"', limpio):
                if m.group(1) in ajenos:
                    linea = limpio[: m.start()].count("\n") + 1
                    (DECLARADAS if declarado else d.hallazgos).append(
                        f"{rel(archivo)}:{linea}: tabla del esquema `{m.group(1)}` (invariante 11)"
                    )

            # Invariante 4: un importe en double es un importe que pierde centavos.
            if re.search(r"\b(double|float)\s+\w*(monto|importe|saldo|precio)", limpio, re.I):
                d.hallazgos.append(f"{rel(archivo)}: importe en coma flotante (invariante 4)")

            # Invariante 6: red dentro de la transaccion.
            if "@Transactional" in limpio and re.search(r"\b(RestClient|RestTemplate|HttpClient)\b", limpio):
                d.hallazgos.append(f"{rel(archivo)}: cliente HTTP en una clase transaccional (invariante 6)")
    return d


# ---------------------------------------------------------------- 4 contratos --
def contratos() -> Dimension:
    d = Dimension(
        "Contratos",
        3,
        "El contrato se escribe primero (ADR-020). Una operacion sin cuerpo declarado, "
        "sin respuestas o con un `$ref` roto es un contrato que no se puede generar.",
    )
    for servicio in SERVICIOS:
        contrato, crudo = contrato_de(servicio)
        if contrato is None:
            continue
        esquemas = set(contrato.get("components", {}).get("schemas", {}))
        respuestas = set(contrato.get("components", {}).get("responses", {}))
        parametros = set(contrato.get("components", {}).get("parameters", {}))
        for ruta, metodo, op in operaciones(contrato):
            d.revisadas += 1
            fallos = []
            if not op.get("responses"):
                fallos.append("sin respuestas")
            # Un POST sobre un identificador —aprobar, ejecutar, cerrar— no necesita
            # cuerpo: la ruta ya dice sobre que actua. Solo falta cuerpo cuando la
            # operacion no recibe NADA, ni por camino ni por consulta.
            sin_parametros = not re.search(r"\{\w+\}", ruta) and not op.get("parameters")
            if metodo in ("post", "put", "patch") and "requestBody" not in op and sin_parametros:
                fallos.append("sin cuerpo ni parametros: no recibe nada")
            if not op.get("summary"):
                fallos.append("sin resumen")
            if fallos:
                d.hallazgos.append(f"{servicio}: `{op['operationId']}` {', '.join(fallos)}")
        # `$ref` que apunta a algo que no existe: el generador falla o, peor, lo ignora.
        for referencia in set(re.findall(r"#/components/(\w+)/(\w+)", crudo)):
            grupo, nombre = referencia
            conocidos = {"schemas": esquemas, "responses": respuestas, "parameters": parametros}.get(grupo, set())
            d.revisadas += 1
            if nombre not in conocidos:
                d.hallazgos.append(f"{servicio}: `$ref` a `{grupo}/{nombre}`, que no existe")
    return d


# ------------------------------------------------------------------ 5 pruebas --
def pruebas() -> Dimension:
    d = Dimension(
        "Pruebas",
        4,
        "Un caso de uso sin prueba de rechazo prueba que el camino feliz funciona, que "
        "es lo unico que nunca falla en produccion.",
    )
    for servicio in SERVICIOS:
        raiz_pruebas = RAIZ / "servicios" / servicio / "src/test/java"
        nombres = {p.stem for p in raiz_pruebas.rglob("*.java")} if raiz_pruebas.exists() else set()
        casos = {
            p.stem[:4]
            for p in (RAIZ / "servicios" / servicio / "src/main/java").rglob("CU*.java")
            if re.match(r"CU\d\d", p.stem)
        }
        for caso in sorted(casos):
            d.revisadas += 2
            if f"{caso}Test" not in nombres:
                d.hallazgos.append(f"{servicio}: {caso} sin prueba de caso de uso")
            if f"{caso}RechazosTest" not in nombres:
                d.hallazgos.append(f"{servicio}: {caso} sin prueba de rechazos")
    return d


# ----------------------------------------------------------------- 6 cableado --
def cableado() -> Dimension:
    d = Dimension(
        "Cableado",
        4,
        "Lo que esta escrito pero no conectado no existe: un puerto sin adaptador, una "
        "clave `@Value` que ningun `application.yml` define, un cliente sin URL.",
    )
    for servicio in SERVICIOS:
        archivos = java_de(servicio)
        texto_todo = {f: f.read_text(encoding="utf-8") for f in archivos}
        yml = RAIZ / "servicios" / servicio / "src/main/resources/application.yml"
        config = yml.read_text(encoding="utf-8") if yml.exists() else ""
        # Se resuelve la clave ENTERA sobre el YAML cargado. Buscar solo la ultima
        # hoja daba falsos positivos y, peor, falsos negativos: dos bloques distintos
        # pueden tener una hoja con el mismo nombre.
        arbol = yaml.safe_load(config) if config else {}

        # Cada puerto de dominio tiene al menos una implementacion.
        for archivo, texto in texto_todo.items():
            if "dominio/puertos" not in str(archivo) and "dominio\\puertos" not in str(archivo):
                continue
            d.revisadas += 1
            interfaz = archivo.stem
            if not any(f"implements {interfaz}" in t for t in texto_todo.values()):
                d.hallazgos.append(f"{servicio}: el puerto `{interfaz}` no tiene adaptador")

        # Cada `@Value` apunta a una clave que existe.
        for archivo, texto in texto_todo.items():
            # Dos cosas distintas, y la diferencia importa:
            #
            #   sin valor por omision y sin clave -> el proceso NO levanta;
            #   con valor por omision y sin clave -> levanta, pero el umbral es una
            #   constante disfrazada: nadie puede moverlo sin desplegar, que es
            #   exactamente lo que el invariante 10 prohibe.
            for clave, omision in re.findall(r'@Value\("\$\{([a-zA-Z0-9._-]+)(:[^}]*)?\}', texto):
                d.revisadas += 1
                nodo = arbol
                for tramo in clave.split("."):
                    nodo = nodo.get(tramo) if isinstance(nodo, dict) else None
                    if nodo is None:
                        break
                if nodo is not None:
                    continue
                if omision:
                    # Un secreto con valor por omision no es un umbral mal puesto: es
                    # otra cosa, y se cuenta aparte. Confundirlos hace que arreglar
                    # once umbrales tape que hay una clave sin inyectar.
                    if re.search(r"clave|secreto|password|token|pimienta", clave, re.I):
                        d.hallazgos.append(
                            f"{rel(archivo)}: `{clave}` es un secreto y solo tiene valor por omision"
                        )
                    else:
                        d.hallazgos.append(
                            f"{rel(archivo)}: `{clave}` solo existe como valor por omision "
                            f"(`{omision[1:]}`): es una constante, no configuracion (invariante 10)"
                        )
                else:
                    d.hallazgos.append(f"{rel(archivo)}: `{clave}` no esta en application.yml — no levanta")

        # Cada variable de entorno del yaml esta en el compose.
        compose = RAIZ / "despliegue/compose/servicios.yml"
        if compose.exists() and config:
            texto_compose = compose.read_text(encoding="utf-8")
            for variable in set(re.findall(r"\$\{([A-Z_]+)[:}]", config)):
                d.revisadas += 1
                if f"{variable}:" not in texto_compose:
                    d.hallazgos.append(f"{servicio}: `{variable}` no la define el compose")
    return d


# ----------------------------------------------------------- 7 prohibiciones --
def prohibiciones() -> Dimension:
    d = Dimension(
        "Prohibiciones",
        6,
        "Las dieciocho del contrato. No son estilo: cada una es un incidente que ya le "
        "paso a alguien.",
    )
    reglas = [
        (r"\bMath\.random\b", "Math.random para algo que no puede ser adivinable", None),
        (r"printStackTrace\(", "traza impresa a la salida estandar", None),
        (r"\bMD5\b|\bSHA1\b|\bDigestUtils\.md5", "hash debil", None),
        (r"dangerouslySetInnerHTML", "HTML sin escapar", None),
        (r"@Disabled|@Ignore", "prueba desactivada", None),
        (r"TODO|FIXME|XXX", "trabajo sin terminar marcado en el codigo", None),
    ]
    for servicio in SERVICIOS:
        for archivo in java_de(servicio):
            texto = archivo.read_text(encoding="utf-8")
            limpio = sin_comentarios(texto)

            # SQL concatenado: **solo** cuando lo que se concatena es SQL. Buscar
            # `" + x + "` a secas acusaba a los mensajes de error, que son la mitad
            # de las cadenas del proyecto y no tocan la base.
            d.revisadas += 1
            for m in re.finditer(r'"([^"\n]*)"\s*\+\s*\w+', limpio):
                if es_sql(m.group(1)):
                    linea = limpio[: m.start()].count("\n") + 1
                    d.hallazgos.append(f"{rel(archivo)}:{linea}: SQL concatenado")
                    break

            for patron, motivo, guarda in reglas:
                d.revisadas += 1
                for m in re.finditer(patron, limpio):
                    if guarda and not guarda(limpio):
                        continue
                    linea = limpio[: m.start()].count("\n") + 1
                    d.hallazgos.append(f"{rel(archivo)}:{linea}: {motivo}")
                    break
    return d


DIMENSIONES = [alcance, seguridad, invariantes, contratos, pruebas, cableado, prohibiciones]


def main() -> None:
    resultados = [f() for f in DIMENSIONES]
    peso_total = sum(r.peso for r in resultados)
    global_ = round(sum(r.nota * r.peso for r in resultados) / peso_total, 2)

    if "--json" in sys.argv:
        print(json.dumps(
            {
                "global": global_,
                "declaradas": DECLARADAS,
                "dimensiones": [
                    {"nombre": r.nombre, "nota": r.nota, "peso": r.peso,
                     "revisadas": r.revisadas, "hallazgos": r.hallazgos}
                    for r in resultados
                ],
            },
            indent=2, ensure_ascii=False,
        ))
        return

    print("=" * 78)
    print(f"AUDITORIA DEL BACKEND · nota global {global_}/10")
    print("=" * 78)
    for r in resultados:
        estado = "OK" if not r.hallazgos else f"{len(r.hallazgos)} hallazgo(s)"
        print(f"\n{r.nombre:16} {r.nota:5.2f}/10  peso {r.peso}  ·  {r.revisadas} revisadas  ·  {estado}")
        print(f"  {r.porque}")
        for hallazgo in r.hallazgos[:12]:
            print(f"    - {hallazgo}")
        if len(r.hallazgos) > 12:
            print(f"    … y {len(r.hallazgos) - 12} mas")
    if DECLARADAS:
        print(f"\nDIVERGENCIAS DECLARADAS ({len(DECLARADAS)})  ·  no bajan la nota, y se revisan igual")
        print("  El modelo pone la clave del otro lado, o el bloqueo tiene que pasar dentro")
        print("  de la transaccion. Cerrarlas exige tocar `sql/` o romper el invariante 6.")
        for entrada in DECLARADAS:
            print(f"    - {entrada}")

    print("\n" + "=" * 78)
    print(f"NOTA GLOBAL: {global_}/10")


if __name__ == "__main__":
    main()
