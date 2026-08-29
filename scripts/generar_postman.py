#!/usr/bin/env python3
"""Emite las colecciones de Postman desde los catorce contratos OpenAPI.

**No se escriben a mano.** Una coleccion escrita a mano se desincroniza del contrato
en la primera semana, y entonces prueba una API que ya no existe. Aca hay una sola
fuente —`servicios/*/src/main/resources/openapi/*.yaml`— y la coleccion es su salida,
igual que el cliente de TypeScript y las clases de jOOQ.

Emite tres cosas en `postman/`:

* `coleccion/<servicio>.postman_collection.json` — las 151 operaciones, una por una,
  con su cuerpo de ejemplo derivado del esquema, su clave de idempotencia, el permiso
  que exige (leido de los `@Permiso` del codigo, no supuesto) y los codigos de error
  que declara el contrato.
* `entornos/<entorno>.postman_environment.json` — local, ensayo y produccion.
* `humo/<servicio>.humo.postman_collection.json` — por operacion: **caso valido,
  caso limite y caso de error**, cada uno con su asercion.

Uso:  python3 scripts/generar_postman.py
"""
from __future__ import annotations

import json
import re
from pathlib import Path

import yaml

RAIZ = Path(__file__).resolve().parent.parent
SALIDA = RAIZ / "postman"

# El orden importa: es el que se ve en la barra lateral de Postman, y sigue el
# recorrido real —primero se es alguien, despues se tiene plata, despues un grupo—.
ORDEN = [
    "identidad", "cumplimiento", "nucleo-financiero", "aportes", "grupos",
    "entregas", "garantia", "tarifas", "transparencia", "organizador",
    "notificaciones", "auditoria", "erp", "publicidad",
]


# --------------------------------------------------------------- el contrato --
def contratos() -> dict[str, tuple[dict, str]]:
    """Cada contrato, parseado y en crudo. El crudo hace falta: los codigos de error
    viven en comentarios YAML, que el parser tira."""
    salida = {}
    for ruta in sorted(RAIZ.glob("servicios/*/src/main/resources/openapi/*.yaml")):
        servicio = ruta.parts[len(RAIZ.parts) + 1]
        texto = ruta.read_text(encoding="utf-8")
        salida[servicio] = (yaml.safe_load(texto), texto)
    return salida


def permisos_del_codigo(servicio: str) -> dict[str, str]:
    """`operationId -> permiso`, leido de los `@Permiso` de los controladores.

    No se supone ni se copia de una tabla aparte: si el codigo cambia el permiso, la
    coleccion lo dice en la siguiente generacion. Un `@Permiso` de clase se aplica a
    lo que no tenga uno propio, igual que en Spring.
    """
    permisos: dict[str, str] = {}
    for java in (RAIZ / "servicios" / servicio / "src/main/java").rglob("*Controller.java"):
        texto = java.read_text(encoding="utf-8")
        de_clase = None
        cabecera = texto.split("public class", 1)[0]
        if (m := re.search(r'@Permiso\("([A-Z_]+)"\)', cabecera)):
            de_clase = m.group(1)
        if "@Publico" in cabecera:
            de_clase = "PUBLICO"
        # Cada metodo publico, con las anotaciones que lo preceden.
        for bloque in re.finditer(
            r"((?:@\w+(?:\([^)]*\))?\s*)+)public\s+ResponseEntity<.+?>\s+(\w+)\s*\(", texto, re.DOTALL
        ):
            anotaciones, operacion = bloque.group(1), bloque.group(2)
            if (m := re.search(r'@Permiso\("([A-Z_]+)"\)', anotaciones)):
                permisos[operacion] = m.group(1)
            elif "@Publico" in anotaciones:
                permisos[operacion] = "PUBLICO"
            elif de_clase:
                permisos[operacion] = de_clase
    return permisos


def codigos_de_error(crudo: str, operacion: str) -> list[str]:
    """Los `AP-CUnn-nn` que el contrato declara para esta operacion.

    Estan en comentarios, al lado del `422`. Se leen del texto crudo porque el parser
    de YAML los descarta, y son justamente lo que hace falta para probar el rechazo.
    """
    inicio = crudo.find(f"operationId: {operacion}")
    if inicio == -1:
        return []
    # Hasta el siguiente operationId, o el final de `paths`.
    fin = crudo.find("operationId:", inicio + 20)
    tramo = crudo[inicio : fin if fin != -1 else len(crudo)]
    return re.findall(r"(AP-CU\d+-\d+)\s+([A-Z_]+)", tramo)


# ------------------------------------------------------- ejemplos por esquema --
def resolver(nodo, esquemas: dict, visto: frozenset = frozenset()):
    """Sigue un `$ref`. `visto` corta la recursion: un esquema que se referencia a si
    mismo colgaria el generador en vez de fallar."""
    while isinstance(nodo, dict) and "$ref" in nodo:
        nombre = nodo["$ref"].rsplit("/", 1)[-1]
        if nombre in visto:
            return {"type": "object"}, visto
        visto = visto | {nombre}
        nodo = esquemas.get(nombre, {})
    return nodo, visto


def ejemplo(nodo, esquemas: dict, nombre: str = "", visto: frozenset = frozenset()):
    """Un valor de ejemplo **que respeta el esquema**.

    No es decoracion: un cuerpo de ejemplo que no valida convierte cada prueba en una
    sesion de arreglar el JSON a mano, y entonces nadie usa la coleccion. Los importes
    salen como CADENA con dos decimales porque asi los declara el contrato —un
    `number` JSON es un doble— y los UUID salen como variables de Postman, para que se
    encadenen entre peticiones.
    """
    nodo, visto = resolver(nodo, esquemas, visto)
    if not isinstance(nodo, dict):
        return None
    if "enum" in nodo:
        return nodo["enum"][0]
    if "const" in nodo:
        return nodo["const"]

    tipo = nodo.get("type")
    if isinstance(tipo, list):  # `[string, 'null']`
        tipo = next((t for t in tipo if t != "null"), "string")

    if tipo == "object" or "properties" in nodo:
        propiedades = nodo.get("properties", {})
        requeridas = set(nodo.get("required", []))
        # Se emiten TODAS, no solo las requeridas: quien prueba tiene que ver el
        # cuerpo completo y borrar lo que no quiera, no adivinar que campos existen.
        return {
            clave: ejemplo(valor, esquemas, clave, visto)
            for clave, valor in propiedades.items()
        } or {}
    if tipo == "array":
        return [ejemplo(nodo.get("items", {}), esquemas, nombre, visto)]
    if tipo in ("number", "integer"):
        if "minimum" in nodo:
            return nodo["minimum"]
        return 1
    if tipo == "boolean":
        return True

    # cadenas
    formato = nodo.get("format")
    if formato == "uuid":
        return f"{{{{{nombre or 'id'}}}}}"
    if formato == "date":
        return "{{fecha_hoy}}"
    if formato in ("date-time",):
        return "{{momento_ahora}}"
    if formato == "uri":
        return "https://ejemplo.aportaya.bo/documento.pdf"
    if (patron := nodo.get("pattern")):
        return desdeElPatron(patron, nombre)
    if nodo.get("maxLength") == 64 and "hash" in nombre.lower():
        return "0" * 64
    if (largo := nodo.get("minLength")) and largo > len(f"{nombre or 'texto'}-de-prueba"):
        return "x" * largo
    return f"{nombre or 'texto'}-de-prueba"


# Los patrones que el contrato usa de verdad, con un valor que los satisface. Un
# ejemplo que no valida contra su propio esquema convierte cada prueba en una sesion
# de arreglar el JSON a mano, y entonces la coleccion no la usa nadie — que fue
# exactamente lo que paso la primera vez que se corrio el humo: `telefonoE164` salia
# como `"telefonoE164-de-prueba"` y el registro contestaba 400 por eso, no por el
# sistema.
PATRONES = {
    r"^-?\d+\.\d{2}$": "100.00",
    r"^\+591\d{8}$": "+59178123456",
    r"^[0-9a-f]{64}$": "0" * 64,
    r"^[A-Z]{3}$": "BOB",
}


def desdeElPatron(patron: str, nombre: str) -> str:
    """Un valor que satisface el patron, o uno construido a partir de el."""
    if patron in PATRONES:
        return PATRONES[patron]
    # Los que no estan en la tabla se resuelven leyendo el patron: se le saca el
    # ancla, se expanden los cuantificadores fijos y se deja lo literal.
    cuerpo = patron.lstrip("^").rstrip("$")
    salida = []
    indice = 0
    while indice < len(cuerpo):
        letra = cuerpo[indice]
        if letra == "\\" and indice + 1 < len(cuerpo):
            clase, indice = cuerpo[indice + 1], indice + 2
            muestra = "1" if clase == "d" else ("a" if clase in "wsS" else clase)
        elif letra == "[":
            cierre = cuerpo.find("]", indice)
            if cierre == -1:
                break
            dentro, indice = cuerpo[indice + 1 : cierre], cierre + 1
            muestra = "1" if "0-9" in dentro else ("A" if "A-Z" in dentro else "a")
        elif letra in "()?*+|.":
            indice += 1
            continue
        else:
            muestra, indice = letra, indice + 1
        # Un cuantificador fijo detras: {8}, {2,}
        veces = 1
        if indice < len(cuerpo) and cuerpo[indice] == "{":
            cierre = cuerpo.find("}", indice)
            if cierre != -1:
                crudo = cuerpo[indice + 1 : cierre].split(",")[0]
                veces = int(crudo) if crudo.isdigit() else 1
                indice = cierre + 1
        elif indice < len(cuerpo) and cuerpo[indice] in "+*":
            indice += 1
        salida.append(muestra * veces)
    resultado = "".join(salida)
    return resultado if resultado else f"{nombre or 'texto'}-de-prueba"


# ------------------------------------------------------------ armar peticion --
def url_de(ruta: str, servidor: str, servicio: str) -> dict:
    """La URL, con los parametros de camino como variables de Postman.

    El prefijo va en `{{prefijo_api}}` y no fijo: **el contrato declara
    `servers: /api/v1` pero el servicio no lo sirve**. Hablandole derecho a un proceso,
    las rutas estan en la raiz (`POST /usuarios`); el prefijo lo pone la entrada
    publica. Escribirlo fijo haria que la coleccion funcionara contra el gateway y
    diera 401 contra un servicio suelto —que es exactamente lo que paso la primera vez
    que se corrio esto—, asi que lo decide el entorno.
    """
    partes = [p for p in ruta.split("/") if p]
    return {
        "raw": "{{base_url}}{{prefijo_api}}" + ruta.replace("{", ":").replace("}", ""),
        "host": ["{{base_url}}{{prefijo_api}}"],
        "path": [p.replace("{", ":").replace("}", "") for p in partes],
        "variable": [
            {"key": p.strip("{}"), "value": f"{{{{{p.strip('{}')}}}}}", "description": "Identificador; se encadena desde una peticion anterior."}
            for p in ruta.split("/")
            if p.startswith("{")
        ],
    }


def descripcion(servicio: str, ruta: str, metodo: str, op: dict, permiso: str, errores: list) -> str:
    """La ficha de la operacion. Es lo que convierte la coleccion en documentacion."""
    lineas = [
        f"## {op.get('summary', op['operationId'])}",
        "",
        (op.get("description") or "").strip(),
        "",
        "| | |",
        "| --- | --- |",
        f"| **Servicio** | `{servicio}` |",
        f"| **Operacion** | `{op['operationId']}` |",
        f"| **Ruta** | `{metodo.upper()} /api/v1{ruta}` |",
        f"| **Permiso** | {'**ruta publica, sin sesion**' if permiso == 'PUBLICO' else f'`{permiso}`' if permiso else '_no declarado en el codigo_'} |",
    ]
    if any(p.get("name") == "Idempotency-Key" or "ClaveIdempotencia" in str(p.get("$ref", "")) for p in op.get("parameters", [])):
        lineas.append("| **Idempotencia** | exige `Idempotency-Key`; se manda un `{{$guid}}` nuevo por intento |")
    respuestas = ", ".join(f"`{c}`" for c in op.get("responses", {}))
    lineas.append(f"| **Respuestas** | {respuestas} |")
    lineas += ["", "### Rechazos que declara el contrato", ""]
    if errores:
        lineas += ["| Codigo | Motivo |", "| --- | --- |"]
        lineas += [f"| `{codigo}` | {motivo.replace('_', ' ').capitalize()} |" for codigo, motivo in errores]
    else:
        lineas.append("_El contrato no declara rechazos de negocio para esta operacion._")
    lineas += [
        "",
        "> Los importes viajan como **cadena decimal** (`\"100.00\"`), nunca como numero:",
        "> un `number` de JSON es un doble, y un doble pierde centavos.",
    ]
    return "\n".join(lineas)


def cabeceras(op: dict, tiene_cuerpo: bool, permiso: str) -> list[dict]:
    salida = []
    if tiene_cuerpo:
        salida.append({"key": "Content-Type", "value": "application/json"})
    for parametro in op.get("parameters", []):
        if "ClaveIdempotencia" in str(parametro.get("$ref", "")) or parametro.get("name") == "Idempotency-Key":
            salida.append({
                "key": "Idempotency-Key",
                "value": "{{$guid}}",
                "description": "Uno nuevo por intento. Repetir el mismo debe devolver la MISMA respuesta y un solo efecto (invariante 7).",
            })
    if permiso != "PUBLICO":
        salida.append({"key": "Authorization", "value": "Bearer {{token}}"})
    return salida


def consulta(op: dict, esquemas: dict) -> list[dict]:
    salida = []
    for parametro in op.get("parameters", []):
        parametro, _ = resolver(parametro, esquemas)
        if parametro.get("in") != "query":
            continue
        salida.append({
            "key": parametro["name"],
            "value": str(ejemplo(parametro.get("schema", {}), esquemas, parametro["name"])),
            "description": (parametro.get("description") or "").strip(),
            "disabled": not parametro.get("required", False),
        })
    return salida


PRUEBA_COMUN = """// Lo que se le exige a TODA respuesta del sistema, sin excepcion.
const esperadas = %s;

pm.test("responde con un codigo que el contrato declara", () => {
    pm.expect(esperadas).to.include(pm.response.code);
});

pm.test("no filtra la traza al cliente", () => {
    const cuerpo = pm.response.text();
    // Un stack trace en la respuesta le cuenta al atacante como esta hecho el
    // servidor. Es una de las dieciocho prohibiciones del contrato.
    pm.expect(cuerpo).to.not.match(/at bo\\.aportaya\\./);
    pm.expect(cuerpo).to.not.include("java.lang.");
});

if (pm.response.code === 422) {
    pm.test("el rechazo trae su codigo AP-CU", () => {
        pm.expect(pm.response.json()).to.have.property("codigo").that.matches(/^AP-CU\\d+-\\d+$/);
    });
}
%s"""

GUARDAR = """
// Encadena: lo que devuelve esta peticion queda disponible para la siguiente.
if (pm.response.code < 300) {
    const cuerpo = pm.response.json();
    for (const [clave, valor] of Object.entries(cuerpo || {})) {
        if (typeof valor === "string" && /^[0-9a-f]{8}-[0-9a-f]{4}-/i.test(valor)) {
            pm.collectionVariables.set(clave, valor);
        }
    }
}
"""


def peticion(servicio: str, ruta: str, metodo: str, op: dict, esquemas: dict,
             servidor: str, permisos: dict, crudo: str) -> dict:
    permiso = permisos.get(op["operationId"], "")
    errores = codigos_de_error(crudo, op["operationId"])
    cuerpo_esquema = (
        op.get("requestBody", {}).get("content", {}).get("application/json", {}).get("schema")
    )
    esperadas = sorted(int(c) for c in op.get("responses", {}) if c.isdigit())

    item = {
        "name": f"{op['operationId']} · {op.get('summary', '')}".strip(" ·"),
        "request": {
            "method": metodo.upper(),
            "header": cabeceras(op, cuerpo_esquema is not None, permiso),
            "url": url_de(ruta, servidor, servicio),
            "description": descripcion(servicio, ruta, metodo, op, permiso, errores),
        },
        "response": [],
        "event": [{
            "listen": "test",
            "script": {"type": "text/javascript", "exec": (PRUEBA_COMUN % (json.dumps(esperadas), GUARDAR)).split("\n")},
        }],
    }
    if cuerpo_esquema is not None:
        item["request"]["body"] = {
            "mode": "raw",
            "raw": json.dumps(ejemplo(cuerpo_esquema, esquemas), indent=2, ensure_ascii=False),
            "options": {"raw": {"language": "json"}},
        }
    if (parametros := consulta(op, esquemas)):
        item["request"]["url"]["query"] = parametros
    return item


# ------------------------------------------------------------------- el humo --
# Las tres preguntas que se le hacen a cada operacion. No son tres variantes de la
# misma: son tres cosas distintas, y un endpoint puede pasar una y fallar las otras.
#
#   valido  — ¿hace lo que promete cuando todo esta bien?
#   limite  — ¿que hace justo en el borde? El borde es donde vive el defecto: el
#             importe exacto del umbral, la cadena de largo maximo, el minimo del
#             rango. Un `>` que debia ser `>=` solo se nota ahi.
#   error   — ¿rechaza lo que tiene que rechazar, y lo dice con su codigo? Un sistema
#             que acepta lo invalido en silencio es peor que uno que se cae.

HUMO_VALIDO = """// CASO VALIDO — todo bien formado, sesion valida, cuerpo completo.
pm.test("acepta el caso valido", () => {
    pm.expect(pm.response.code, "esperaba " + %(esperadas)s + ", vino " + pm.response.code
        + " · " + pm.response.text().slice(0, 300)).to.be.oneOf(%(esperadas)s);
});

pm.test("responde JSON", () => {
    if (pm.response.code !== 204) {
        pm.response.to.have.header("Content-Type");
        pm.expect(pm.response.headers.get("Content-Type")).to.include("json");
    }
});

pm.test("trae los campos que el contrato declara obligatorios", () => {
    if (pm.response.code >= 300 || pm.response.code === 204) return;
    const cuerpo = pm.response.json();
    for (const campo of %(requeridas)s) {
        pm.expect(cuerpo, "falta " + campo).to.have.property(campo);
    }
});

pm.test("los importes son cadena, no numero", () => {
    if (pm.response.code >= 300) return;
    const revisar = (nodo) => {
        if (nodo === null || typeof nodo !== "object") return;
        if ("monto" in nodo && "moneda" in nodo) {
            // Un `number` de JSON es un doble. Dos decimales exactos, como cadena.
            pm.expect(nodo.monto, "el importe viajo como numero").to.be.a("string");
            pm.expect(nodo.monto).to.match(/^-?\\d+\\.\\d{2}$/);
        }
        Object.values(nodo).forEach(revisar);
    };
    revisar(pm.response.json());
});
""" + GUARDAR

HUMO_LIMITE = """// CASO LIMITE — el borde exacto, que es donde vive el defecto.
// %(nota)s
pm.test("el borde no revienta: contesta con un codigo declarado", () => {
    pm.expect(pm.response.code, pm.response.text().slice(0, 300)).to.be.oneOf(%(declarados)s);
});

pm.test("si rechaza el borde, lo hace con un codigo de negocio y no con un 500", () => {
    pm.expect(pm.response.code, "un borde nunca es un error del servidor").to.be.below(500);
});

pm.test("no devuelve un importe con mas de dos decimales", () => {
    const cuerpo = pm.response.text();
    pm.expect(cuerpo).to.not.match(/"monto"\\s*:\\s*"-?\\d+\\.\\d{3,}"/);
});
"""

HUMO_ERROR_SIN_SESION = """// CASO DE ERROR — la misma peticion, SIN token.
// Denegar por omision (invariante 9): una ruta que contesta sin sesion es una ruta
// abierta, y no hay ninguna abierta fuera de /publico y /verificar de transparencia.
pm.test("sin sesion no se pasa", () => {
    pm.expect(pm.response.code, "una ruta con permiso contesto sin token")
        .to.be.oneOf([401, 403]);
});

pm.test("el 401 no cuenta de mas", () => {
    const cuerpo = pm.response.text();
    // Ni el nombre de la clase, ni la consulta, ni si el usuario existe.
    pm.expect(cuerpo).to.not.match(/at bo\\.aportaya\\./);
    pm.expect(cuerpo.toLowerCase()).to.not.include("select ");
});
"""

HUMO_ERROR_CUERPO = """// CASO DE ERROR — cuerpo invalido: falta un campo obligatorio y sobra uno que el
// esquema prohibe (`additionalProperties: false`).
pm.test("rechaza el cuerpo invalido", () => {
    pm.expect(pm.response.code, "acepto un cuerpo que no valida contra su esquema")
        .to.be.oneOf([400, 422]);
});

pm.test("dice que esta mal sin exponer como esta hecho por dentro", () => {
    const cuerpo = pm.response.text();
    pm.expect(cuerpo).to.not.match(/at bo\\.aportaya\\./);
    pm.expect(cuerpo).to.not.include("java.lang.");
    pm.expect(cuerpo).to.not.include("org.jooq");
});
"""


def valores_limite(esquema, esquemas: dict, visto: frozenset = frozenset()):
    """El mismo cuerpo, empujado al borde.

    Cada regla dice por que ese borde importa:
    * un importe se lleva al umbral de doble aprobacion —5000.00— porque ahi esta el
      `>=` que decide si hace falta una segunda firma;
    * una cadena se lleva a su `maxLength` exacto, que es donde la base rechaza;
    * un numero se lleva a su `minimum`, que es el valor que mas veces esta mal.
    """
    esquema, visto = resolver(esquema, esquemas, visto)
    if not isinstance(esquema, dict):
        return None
    if "enum" in esquema:
        return esquema["enum"][-1]  # el ultimo, no el primero: el que nadie prueba
    tipo = esquema.get("type")
    if isinstance(tipo, list):
        tipo = next((t for t in tipo if t != "null"), "string")
    if tipo == "object" or "properties" in esquema:
        return {
            clave: valores_limite(valor, esquemas, visto)
            for clave, valor in esquema.get("properties", {}).items()
        }
    if tipo == "array":
        return [valores_limite(esquema.get("items", {}), esquemas, visto)]
    if tipo in ("number", "integer"):
        return esquema.get("minimum", esquema.get("maximum", 0))
    if tipo == "boolean":
        return False
    if (patron := esquema.get("pattern")):
        if patron == r"^-?\d+\.\d{2}$":
            # El umbral de doble aprobacion de `aportaya.retiro.doble-aprobacion-desde`.
            return "5000.00"
        # Con patron, el borde sigue siendo el patron: una cadena de 60 letras en un
        # campo de telefono no prueba el limite del negocio, prueba el parser — y lo
        # que devuelve es un 400 que no dice nada sobre la regla.
        return desdeElPatron(patron, "")
    if (largo := esquema.get("maxLength")):
        return "L" * int(largo)
    if esquema.get("format") in ("uuid", "date", "date-time", "uri"):
        return ejemplo(esquema, esquemas, "")
    return "x"


def cuerpo_invalido(esquema, esquemas: dict):
    """Le saca un campo obligatorio y le agrega uno que el esquema prohibe."""
    esquema, _ = resolver(esquema, esquemas)
    base = ejemplo(esquema, esquemas)
    if not isinstance(base, dict):
        return {"campoQueNoExiste": "esto no esta en el esquema"}
    for requerido in esquema.get("required", []):
        base.pop(requerido, None)
        break
    base["campoQueNoExiste"] = "additionalProperties: false tiene que rechazarlo"
    return base


PRELUDIO = """// Consigue el token con el que corre todo lo demas.
//
// Si no lo consigue, **lo dice una vez aca** en vez de dejar que las cincuenta
// pruebas siguientes fallen con un 401 que no explica nada. Un tablero rojo por el
// motivo equivocado es peor que uno rojo por el motivo correcto.
pm.test("hay sesion para correr el humo", () => {
    if (pm.response.code >= 200 && pm.response.code < 300) {
        const cuerpo = pm.response.json();
        const token = cuerpo.tokenAcceso || cuerpo.token;
        pm.expect(token, "el ingreso no devolvio token: revisa si pide segundo factor").to.be.a("string");
        pm.collectionVariables.set("token", token);
        return;
    }
    // Sin credenciales sembradas no se puede iniciar sesion sola. Se avisa y se sigue
    // con el token que haya en el entorno, si alguien lo puso a mano.
    const yaHay = pm.environment.get("token") || pm.collectionVariables.get("token");
    pm.expect(yaHay,
        "no se pudo iniciar sesion (" + pm.response.code + ") y el entorno no trae `token`. "
        + "Carga uno a mano en el entorno, o siembra credenciales de prueba."
    ).to.be.a("string").and.not.empty;
});
"""


def preludio_de_sesion(todos: dict) -> dict:
    """La carpeta `00` que abre sesion antes que nada.

    Va en TODAS las colecciones de humo, no solo en la de identidad: cada una se corre
    sola, y una coleccion que asume que alguien corrio otra antes no se corre nunca.
    Apunta a `{{base_identidad}}`, que por omision es el mismo servidor.
    """
    contrato, _ = todos["identidad"]
    esquemas = contrato.get("components", {}).get("schemas", {})
    entrada = ejemplo(esquemas.get("EntradaAutenticacion", {}), esquemas)
    return {
        "name": "00 · sesion",
        "description": (
            "Consigue el token. Se ejecuta primero y **una sola vez por corrida**.\n\n"
            "Si tu entorno no tiene credenciales sembradas, carga `token` a mano en el "
            "entorno: esta carpeta lo detecta y sigue en vez de tirar cincuenta 401."
        ),
        "item": [{
            "name": "autenticar · conseguir el token",
            "request": {
                "method": "POST",
                "header": [
                    {"key": "Content-Type", "value": "application/json"},
                    {"key": "Idempotency-Key", "value": "{{$guid}}"},
                ],
                "url": {
                    "raw": "{{base_identidad}}{{prefijo_api}}/sesiones",
                    "host": ["{{base_identidad}}{{prefijo_api}}"],
                    "path": ["sesiones"],
                },
                "body": {
                    "mode": "raw",
                    "raw": json.dumps(entrada, indent=2, ensure_ascii=False),
                    "options": {"raw": {"language": "json"}},
                },
                "description": (
                    "CU-04. Ruta **publica**: es el momento en que todavia no hay sesion.\n\n"
                    "El token que devuelve es un JWT RS256 firmado por `identidad`, y los otros "
                    "trece servicios lo validan contra su JWKS sin preguntarle a nadie (ADR-024)."
                ),
            },
            "response": [],
            "event": [{"listen": "test", "script": {"type": "text/javascript", "exec": PRELUDIO.split("\n")}}],
        }],
    }


def humo_de(servicio: str, ruta: str, metodo: str, op: dict, esquemas: dict,
            servidor: str, permisos: dict, crudo: str) -> dict:
    """Las tres pruebas de una operacion, en su carpeta."""
    permiso = permisos.get(op["operationId"], "")
    esquema_cuerpo = (
        op.get("requestBody", {}).get("content", {}).get("application/json", {}).get("schema")
    )
    exitosas = sorted(int(c) for c in op.get("responses", {}) if c.isdigit() and c.startswith("2"))
    declaradas = sorted(int(c) for c in op.get("responses", {}) if c.isdigit()) + [400, 404, 409, 422]
    esquema_ok = None
    for codigo in map(str, exitosas):
        esquema_ok = (
            op["responses"][codigo].get("content", {}).get("application/json", {}).get("schema")
        )
        if esquema_ok:
            break
    resuelto, _ = resolver(esquema_ok or {}, esquemas)
    requeridas = resuelto.get("required", []) if isinstance(resuelto, dict) else []

    def armar(nombre, guion, cuerpo, con_token=True):
        item = {
            "name": nombre,
            "request": {
                "method": metodo.upper(),
                "header": [h for h in cabeceras(op, cuerpo is not None, permiso)
                           if con_token or h["key"] != "Authorization"],
                "url": url_de(ruta, servidor, servicio),
            },
            "response": [],
            "event": [{"listen": "test", "script": {"type": "text/javascript", "exec": guion.split("\n")}}],
        }
        if cuerpo is not None:
            item["request"]["body"] = {
                "mode": "raw",
                "raw": json.dumps(cuerpo, indent=2, ensure_ascii=False),
                "options": {"raw": {"language": "json"}},
            }
        if (parametros := consulta(op, esquemas)):
            item["request"]["url"]["query"] = [dict(p, disabled=False) for p in parametros]
        return item

    casos = [
        armar(
            "1 · valido",
            HUMO_VALIDO % {"esperadas": json.dumps(exitosas or [200]), "requeridas": json.dumps(requeridas)},
            ejemplo(esquema_cuerpo, esquemas) if esquema_cuerpo else None,
        ),
        armar(
            "2 · limite",
            HUMO_LIMITE % {
                "declarados": json.dumps(sorted(set(declaradas))),
                "nota": "Importes en el umbral de doble aprobacion, cadenas en su largo maximo, numeros en su minimo.",
            },
            valores_limite(esquema_cuerpo, esquemas) if esquema_cuerpo else None,
        ),
    ]
    if permiso and permiso != "PUBLICO":
        casos.append(armar("3 · error · sin sesion", HUMO_ERROR_SIN_SESION,
                           ejemplo(esquema_cuerpo, esquemas) if esquema_cuerpo else None,
                           con_token=False))
    if esquema_cuerpo is not None:
        casos.append(armar("4 · error · cuerpo invalido", HUMO_ERROR_CUERPO,
                           cuerpo_invalido(esquema_cuerpo, esquemas)))

    return {
        "name": f"{op['operationId']} · {op.get('summary', '')}".strip(" ·"),
        "item": casos,
        "description": (
            f"Humo de `{op['operationId']}`. **Valido**, **limite** y **error** son tres "
            f"preguntas distintas: una operacion puede contestar bien la primera y fallar "
            f"las otras dos, y es justo ahi donde estan los defectos que llegan a produccion."
        ),
    }


# --------------------------------------------------------------- los entornos --
# Un entorno por destino. El token NO se escribe aca: lo pone la peticion de sesion
# al ejecutarse, y el archivo se versiona. Un secreto en un archivo versionado es una
# de las dieciocho prohibiciones, y da igual que sea "solo de desarrollo".
ENTORNOS = {
    "local": {
        "base_url": "http://localhost:8080",
        "prefijo_api": "",
        "nota": ("Un solo proceso, el que tengas levantado con `bootRun`. Sin prefijo: hablandole "
                 "derecho a un servicio, las rutas estan en la raiz (`POST /usuarios`)."),
    },
    "ensayo-local": {
        "base_url": "http://localhost",
        "prefijo_api": "/api/v1",
        "nota": ("Los catorce servicios detras de NGINX (`docker compose --profile todo up`). "
                 "Con prefijo: es la unica entrada publica, y es la que el contrato declara."),
    },
    "ensayo": {
        "base_url": "https://ensayo.aportaya.bo",
        "prefijo_api": "/api/v1",
        "nota": "El entorno compartido. Datos de prueba, nunca datos de personas reales.",
    },
    "produccion": {
        "base_url": "https://api.aportaya.bo",
        "prefijo_api": "/api/v1",
        "nota": "SOLO LECTURA. Ninguna coleccion de humo se corre aca: la de humo escribe.",
    },
}


def entorno(nombre: str, datos: dict) -> dict:
    variables = [
        {"key": "base_url", "value": datos["base_url"], "type": "default", "enabled": True},
        {"key": "prefijo_api", "value": datos["prefijo_api"], "type": "default", "enabled": True,
         "description": ("El contrato declara `/api/v1` pero el servicio sirve en la raiz: el prefijo "
                         "lo pone la entrada publica. Vacio cuando le hablas derecho a un proceso.")},
        # El token es `secret`: Postman no lo muestra ni lo exporta con el entorno.
        {"key": "token", "value": "", "type": "secret", "enabled": True,
         "description": "Lo escribe `iniciarSesion` al ejecutarse. NO se versiona."},
        {"key": "refresco", "value": "", "type": "secret", "enabled": True},
        {"key": "base_identidad", "value": datos["base_url"], "type": "default", "enabled": True,
         "description": "Donde vive `identidad`. Todas las colecciones de humo abren sesion contra el."},
        {"key": "usuario_prueba", "value": "prueba@aportaya.bo", "type": "default", "enabled": True},
        {"key": "clave_prueba", "value": "", "type": "secret", "enabled": True,
         "description": "Se carga a mano por entorno. Nunca se comitea."},
    ]
    return {
        "id": f"aportaya-{nombre}",
        "name": f"AportaYa · {nombre}",
        "values": variables,
        "_postman_variable_scope": "environment",
        "_description": datos["nota"],
    }


PREVIO_COLECCION = """// Fechas y momentos, calculados al vuelo: una fecha fija en el archivo caduca y
// entonces la coleccion falla por algo que no es el sistema.
const ahora = new Date();
pm.collectionVariables.set("fecha_hoy", ahora.toISOString().slice(0, 10));
pm.collectionVariables.set("momento_ahora", ahora.toISOString());
pm.collectionVariables.set("fecha_manana", new Date(ahora.getTime() + 86400000).toISOString().slice(0, 10));

// Una sola clave para el par acreditar/reacreditar del recorrido: se fija una vez por
// corrida, porque probar el reintento con dos claves distintas no prueba nada.
if (!pm.collectionVariables.get("claveDeLaAcreditacion")) {
    pm.collectionVariables.set("claveDeLaAcreditacion", pm.variables.replaceIn("{{$guid}}"));
}
"""


def coleccion(nombre: str, descripcion_md: str, items: list, variables: list[str]) -> dict:
    return {
        "info": {
            "name": nombre,
            "description": descripcion_md,
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
        },
        "item": items,
        "event": [{"listen": "prerequest", "script": {"type": "text/javascript", "exec": PREVIO_COLECCION.split("\n")}}],
        "variable": [{"key": v, "value": ""} for v in sorted(variables)],
    }


def variables_de(items) -> set[str]:
    """Todo `{{loQueSea}}` que aparezca, para declararlo en la coleccion."""
    return set(re.findall(r"\{\{([a-zA-Z_][\w]*)\}\}", json.dumps(items)))


CABECERA = """# {titulo}

Generado por `scripts/generar_postman.py` desde `servicios/*/src/main/resources/openapi/`.
**No se edita a mano**: la proxima generacion lo pisa, y una coleccion editada a mano
prueba una API que ya no existe.

{cuerpo}
"""


def main() -> None:
    (SALIDA / "coleccion").mkdir(parents=True, exist_ok=True)
    (SALIDA / "entornos").mkdir(parents=True, exist_ok=True)
    (SALIDA / "humo").mkdir(parents=True, exist_ok=True)

    todos = contratos()
    orden = [s for s in ORDEN if s in todos] + [s for s in sorted(todos) if s not in ORDEN]
    resumen = []

    for servicio in orden:
        contrato, crudo = todos[servicio]
        esquemas = contrato.get("components", {}).get("schemas", {})
        servidor = (contrato.get("servers") or [{"url": "/api/v1"}])[0]["url"]
        permisos = permisos_del_codigo(servicio)

        peticiones, humos, operaciones = [], [], 0
        for ruta, metodos in contrato["paths"].items():
            for metodo, op in metodos.items():
                if metodo not in ("get", "post", "put", "patch", "delete"):
                    continue
                operaciones += 1
                peticiones.append(peticion(servicio, ruta, metodo, op, esquemas, servidor, permisos, crudo))
                humos.append(humo_de(servicio, ruta, metodo, op, esquemas, servidor, permisos, crudo))

        sin_permiso = [p["name"] for p, (r, m) in zip(peticiones, [(0, 0)] * len(peticiones))] if False else []
        cuerpo = CABECERA.format(
            titulo=f"AportaYa · {servicio}",
            cuerpo="\n".join([
                f"**{operaciones} operaciones.** Cada peticion trae su cuerpo de ejemplo derivado del",
                "esquema, la clave de idempotencia donde el contrato la exige, el permiso que pide",
                "—leido de los `@Permiso` del codigo— y los rechazos `AP-CU` que declara.",
                "",
                "Elegi un entorno de `postman/entornos/` antes de disparar nada.",
            ]),
        )
        archivo = SALIDA / "coleccion" / f"{servicio}.postman_collection.json"
        datos = coleccion(f"AportaYa · {servicio}", cuerpo, peticiones, variables_de(peticiones))
        archivo.write_text(json.dumps(datos, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

        cuerpo_humo = CABECERA.format(
            titulo=f"Humo · {servicio}",
            cuerpo="\n".join([
                f"**{operaciones} operaciones × tres preguntas.**",
                "",
                "| Caso | Que pregunta |",
                "| --- | --- |",
                "| `1 · valido` | Hace lo que promete cuando todo esta bien, y devuelve los campos obligatorios |",
                "| `2 · limite` | Que hace en el borde exacto: el umbral, el largo maximo, el minimo del rango |",
                "| `3 · error · sin sesion` | Deniega por omision: sin token no se pasa, y el 401 no cuenta de mas |",
                "| `4 · error · cuerpo invalido` | Rechaza lo que no valida, sin filtrar la traza |",
                "",
                "Se corre con `newman`:",
                "",
                "```",
                f"newman run postman/humo/{servicio}.humo.postman_collection.json \\",
                "  -e postman/entornos/ensayo-local.postman_environment.json",
                "```",
            ]),
        )
        archivo_humo = SALIDA / "humo" / f"{servicio}.humo.postman_collection.json"
        con_sesion = [preludio_de_sesion(todos)] + humos
        datos_humo = coleccion(f"Humo · {servicio}", cuerpo_humo, con_sesion, variables_de(con_sesion))
        archivo_humo.write_text(json.dumps(datos_humo, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

        faltan = [op for op in
                  (o["operationId"] for _, ms in contrato["paths"].items() for m, o in ms.items()
                   if m in ("get", "post", "put", "patch", "delete"))
                  if op not in permisos]
        resumen.append((servicio, operaciones, len(permisos), faltan))

    (SALIDA / "recorrido.postman_collection.json").write_text(
        json.dumps(recorrido(todos), indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )

    for nombre, datos in ENTORNOS.items():
        (SALIDA / "entornos" / f"{nombre}.postman_environment.json").write_text(
            json.dumps(entorno(nombre, datos), indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
        )

    total = sum(o for _, o, _, _ in resumen)
    print(f"postman/coleccion/  {len(resumen)} colecciones · {total} operaciones")
    print(f"postman/humo/       {len(resumen)} colecciones · {total * 3}–{total * 4} pruebas")
    print(f"postman/entornos/   {len(ENTORNOS)} entornos")
    print(f"postman/recorrido   {len(RECORRIDO)} pasos encadenados, de punta a punta")
    huerfanas = [(s, f) for s, _, _, f in resumen if f]
    if huerfanas:
        print("\nAVISO · operaciones sin @Permiso hallado en el codigo:")
        for servicio, faltan in huerfanas:
            print(f"  {servicio}: {', '.join(faltan)}")



# ------------------------------------------------- el recorrido, de punta a punta --
# Las 552 pruebas de humo miran una operacion cada una. Esto mira el sistema: una
# persona que se registra, entra, carga plata, arma un pasanaku, aporta y despues
# verifica publicamente que el sorteo fue limpio. **Encadenado**: cada paso usa lo
# que devolvio el anterior, y por eso encuentra lo que una prueba aislada no puede —
# que el identificador que un servicio emite no es el que el otro espera.
RECORRIDO = [
    ("identidad", "POST", "/usuarios", "registrarUsuario", None, """
pm.test("el alta se acepta y NO deja operar todavia", () => {
    pm.expect(pm.response.code).to.eql(202);
    // 202 y no 201 a proposito: la diligencia la hace `cumplimiento` y la billetera
    // la abre `nucleo-financiero` al consumir el evento. Nada de eso paso aun.
    pm.expect(pm.response.json().estado).to.eql("PENDIENTE_VERIFICACION");
});
pm.collectionVariables.set("usuarioId", pm.response.json().usuarioId);
"""),
    ("identidad", "POST", "/sesiones", "autenticar", None, """
pm.test("la sesion devuelve un token de acceso", () => {
    pm.expect(pm.response.code).to.be.oneOf([200, 201]);
    pm.expect(pm.response.json()).to.have.property("token");
});
pm.test("el token es RS256 firmado por identidad, no un opaco cualquiera", () => {
    const partes = (pm.response.json().token || "").split(".");
    pm.expect(partes.length, "no parece un JWT").to.eql(3);
    const cabecera = JSON.parse(atob(partes[0]));
    // ADR-024: con clave simetrica los catorce servicios podrian emitir tokens.
    pm.expect(cabecera.alg).to.eql("RS256");
});
pm.collectionVariables.set("token", pm.response.json().token);
"""),
    ("nucleo-financiero", "POST", "/billetera/recargas", "solicitarRecarga", None, """
pm.test("la recarga queda pendiente y con vencimiento", () => {
    pm.expect(pm.response.code).to.eql(201);
    const cuerpo = pm.response.json();
    // Invariante 8: el plazo se persiste al crear, no se recalcula al consultar.
    pm.expect(cuerpo, "sin vencimiento la orden vive para siempre").to.have.property("expiraEn");
});
pm.collectionVariables.set("ordenId", pm.response.json().ordenRecargaId);
"""),
    ("nucleo-financiero", "POST", "/billetera/recargas/{ordenId}/acreditacion", "acreditarRecarga", None, """
pm.test("acreditar mueve el saldo y devuelve el asiento", () => {
    pm.expect(pm.response.code).to.eql(200);
    pm.expect(pm.response.json()).to.have.property("transaccionId");
});
pm.collectionVariables.set("saldoTrasRecarga", pm.response.json().saldoDespues.monto);
"""),
    ("nucleo-financiero", "POST", "/billetera/recargas/{ordenId}/acreditacion", "acreditarRecarga · REPETIDA", None, """
// El mismo paso otra vez. La red duplica; reintentar tiene que ser seguro.
pm.test("el reintento no acredita dos veces", () => {
    pm.expect(pm.response.code, "un reintento nunca es un 500").to.be.below(500);
    if (pm.response.code < 300) {
        pm.expect(pm.response.json().saldoDespues.monto,
            "la segunda acreditacion movio el saldo: la idempotencia no esta")
            .to.eql(pm.collectionVariables.get("saldoTrasRecarga"));
    }
});
"""),
    ("grupos", "POST", "/grupos", "crearGrupo", None, """
pm.test("el grupo se acepta", () => {
    pm.expect(pm.response.code).to.eql(202);
});
pm.collectionVariables.set("grupoId", pm.response.json().grupoId);
"""),
    ("grupos", "POST", "/grupos/{grupoId}/sorteo", "comprometerSorteo", None, """
pm.test("el compromiso publica el HASH, nunca la semilla", () => {
    pm.expect(pm.response.code).to.eql(201);
    const cuerpo = pm.response.json();
    pm.expect(cuerpo).to.have.property("hashSemilla");
    // Si la semilla saliera aca, cualquiera podria calcular el orden antes del
    // revelado, y comprometer no serviria para nada.
    pm.expect(cuerpo, "la semilla no puede viajar en el compromiso").to.not.have.property("semilla");
});
pm.collectionVariables.set("sorteoId", pm.response.json().sorteoId);
"""),
    ("transparencia", "GET", "/publico/sorteos/{sorteoId}/verificacion", "verificarSorteo · SIN SESION", None, """
// La prueba que le da sentido a todo lo anterior: un tercero SIN cuenta comprobando
// que el sorteo fue limpio. Se dispara adrede sin `Authorization`.
pm.test("la ruta publica contesta sin token", () => {
    pm.expect(pm.response.code, "una ruta de /publico pidio sesion").to.eql(200);
});
pm.test("trae el paquete para rehacer el sorteo por fuera", () => {
    pm.expect(pm.response.json()).to.have.property("paquete");
});
"""),
]


def recorrido(todos: dict) -> dict:
    """El caso valido completo, encadenado."""
    items = []
    for indice, (servicio, metodo, ruta, nombre, _, guion) in enumerate(RECORRIDO, start=1):
        contrato, _ = todos[servicio]
        esquemas = contrato.get("components", {}).get("schemas", {})
        servidor = (contrato.get("servers") or [{"url": "/api/v1"}])[0]["url"]
        operacion = nombre.split(" · ")[0]
        op = next(
            (o for _, ms in contrato["paths"].items() for m, o in ms.items()
             if isinstance(o, dict) and o.get("operationId") == operacion),
            {},
        )
        permisos = permisos_del_codigo(servicio)
        permiso = permisos.get(operacion, "")
        if "SIN SESION" in nombre:
            permiso = "PUBLICO"
        esquema_cuerpo = (
            op.get("requestBody", {}).get("content", {}).get("application/json", {}).get("schema")
        )
        item = {
            "name": f"{indice:02d} · {servicio} · {nombre}",
            "request": {
                "method": metodo,
                "header": cabeceras(op, esquema_cuerpo is not None, permiso),
                "url": url_de(ruta, servidor, servicio),
                "description": f"Paso {indice} del recorrido. Servicio `{servicio}`, operacion `{operacion}`.",
            },
            "response": [],
            "event": [{"listen": "test", "script": {"type": "text/javascript", "exec": guion.strip().split("\n")}}],
        }
        # Los pasos 4 y 5 son la MISMA peticion: comparten la clave de idempotencia a
        # proposito. Un `{{$guid}}` nuevo probaria otra cosa —dos operaciones
        # distintas— y no que reintentar es seguro.
        for cabecera in item["request"]["header"]:
            if cabecera["key"] == "Idempotency-Key" and "acreditarRecarga" in nombre:
                cabecera["value"] = "{{claveDeLaAcreditacion}}"
                cabecera["description"] = "Fija en los dos pasos: es lo que se esta probando."
        if esquema_cuerpo is not None:
            item["request"]["body"] = {
                "mode": "raw",
                "raw": json.dumps(ejemplo(esquema_cuerpo, esquemas), indent=2, ensure_ascii=False),
                "options": {"raw": {"language": "json"}},
            }
        items.append(item)

    cuerpo = CABECERA.format(
        titulo="Recorrido completo",
        cuerpo="\n".join([
            "**El caso valido de punta a punta, encadenado.**",
            "",
            "Las pruebas de `postman/humo/` miran una operacion cada una. Esto mira el sistema:",
            "alguien se registra, entra, carga plata, arma un pasanaku, compromete el sorteo, y",
            "un tercero **sin cuenta** verifica que fue limpio.",
            "",
            "Cada paso usa lo que devolvio el anterior, y por eso encuentra lo que una prueba",
            "aislada no puede: que el identificador que un servicio emite no es el que el otro",
            "espera. Se corre **en orden**, de arriba abajo.",
            "",
            "El paso 5 repite el 4 a proposito, con la MISMA clave de idempotencia, para",
            "comprobar que acreditar dos veces no acredita dos veces.",
            "",
            "```",
            "newman run postman/recorrido.postman_collection.json \\",
            "  -e postman/entornos/ensayo-local.postman_environment.json",
            "```",
        ]),
    )
    return coleccion("AportaYa · recorrido completo", cuerpo, items, variables_de(items))

if __name__ == "__main__":
    main()
