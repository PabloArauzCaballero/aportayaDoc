#!/usr/bin/env python3
"""
Genera el esqueleto de un caso de uso DESDE la boveda, con las pruebas en rojo.

    python3 scripts/nuevo_cu.py 31
    python3 scripts/nuevo_cu.py 31 --forzar

Las pruebas nacen fallando, y ese es el punto: un criterio de aceptacion olvidado
no es una prueba ausente que nadie nota, es el build en rojo. El carril no decide
que probar — decide como hacer pasar lo que ya esta escrito.

Lee `docs/CasosDeUso/CU-<NN> *.md` y genera:

  aplicacion/CU<NN><Verbo>.java   organismo con @Transactional y conContexto puestos
  CU<NN>Test.java                 una prueba por escenario gherkin, con su nombre
                                  + una prueba de RECHAZO por cada R-XXX-nn citado
                                  + las obligatorias segun lo que el CU toque
  README del servicio             fila del CU en la tabla

El contrato NO se genera entero: se imprime la plantilla de la operacion para
pegar en el openapi. Escribirlo es del carril, y a proposito (ADR-020).
"""
import argparse
import pathlib
import re
import sys
import unicodedata

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from modelo import escenarios_gherkin, paquete_de  # noqa: E402

# Estos informes se imprimen con acentos y flechas. En Windows la consola entrega
# stdout en cp1252 y el generador muere con UnicodeEncodeError despues de haber
# escrito los archivos — en tres de las cinco maquinas del parque.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")


R = pathlib.Path(__file__).resolve().parent.parent
CU_DIR = R / "docs/CasosDeUso"
SERVICIOS = R / "servicios"

RE_CONTRATO = re.compile(r"^## Contrato · `openapi/([\w-]+)\.yaml`", re.M)
# El nombre del organismo sale de la Descomposicion atomica del propio CU: la
# boveda ya decidio como se llama la pieza. Si no lo dice, se deriva del titulo.
RE_ORGANISMO = re.compile(r"\|\s*Organismo\s*\|\s*`(CU\d+\w+)`")


def sin_tildes(s):
    return "".join(c for c in unicodedata.normalize("NFD", s)
                   if unicodedata.category(c) != "Mn")


def camel(texto):
    limpio = re.sub(r"[^\w\s]", " ", sin_tildes(texto))
    return "".join(p.capitalize() for p in limpio.split())


def escapar(s):
    return s.replace("\\", "\\\\").replace('"', '\\"')


def obligatorias(texto):
    """Las pruebas que el CU exige por lo que toca, no por lo que uno recuerde."""
    pruebas = [
        ("reintento",
         "reintento: la misma clave de idempotencia dos veces devuelve la misma "
         "respuesta y un solo efecto"),
        ("concurrencia",
         "concurrencia: dos transacciones sobre el mismo agregado, una gana y "
         "nunca hay doble efecto"),
    ]
    t = texto.lower()
    if any(w in t for w in ("monto", "importe", "saldo", "comision", "asiento",
                            "movimiento", "dinero", "cobr", "pag")):
        pruebas.append(("cuadre",
                        "cuadre: la suma de debitos iguala la de creditos, al centavo"))
    if "evento" in t or "outbox" in t:
        pruebas.append(("evento duplicado",
                        "evento duplicado y fuera de orden: un solo efecto"))
    if "servicio" in t or "saga" in t:
        pruebas.append(("compensa",
                        "compensacion: se fuerza el fallo de cada paso y el sistema "
                        "queda cuadrado"))
    return pruebas


def organismo_java(pkg, clase, nn, titulo):
    return f"""package {pkg}.aplicacion;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-{nn} · {titulo}
 *
 * Generado por scripts/nuevo_cu.py desde docs/CasosDeUso/. La especificacion
 * manda: si algo de acá no coincide con el caso de uso, el error está acá.
 *
 * ANTES DE ESCRIBIR EL CUERPO, respondé por escrito las seis preguntas de
 * frontera transaccional (skill `frontera-transaccional`) y esperá el visto bueno.
 */
@Service
public class {clase} {{

    // Las moléculas entran por constructor. Nada de inyección en campos: esconde
    // la dependencia y hace imposible construir la clase en una prueba unitaria.

    @Transactional                       // la ÚNICA frontera transaccional del caso
    public Object ejecutar(Object entrada, Object ctx) {{
        // return datos.conContexto(ctx, dsl -> {{        ← SET LOCAL, misma conexión
        //     idempotencia.exigirNueva(dsl, entrada.clave());   ← ANTES de escribir
        //     ...                                               ← átomos puros
        //     outbox.emitir(dsl, "<modulo>.<evento>", carga);   ← misma transacción
        //     return ...;
        // }});
        throw new UnsupportedOperationException("CU-{nn} sin implementar");
    }}
}}
"""


def prueba_java(pkg, nn, titulo, escenarios, restricciones, extra):
    cuerpo = []
    cuerpo.append("    // --- criterios de aceptación de la bóveda ---------------------------\n"
                  "    // Uno por escenario gherkin, con el MISMO nombre. Si borrás uno, el gate\n"
                  "    // scripts/verificar_criterios.py falla: el criterio quedaría sin cubrir.\n")
    for i, esc in enumerate(escenarios, 1):
        cuerpo.append(f"""    @Test
    @DisplayName("{escapar(esc)}")
    void criterio{i}() {{
        fail("CU-{nn} criterio {i} sin implementar");
    }}
""")
    if restricciones:
        cuerpo.append("\n    // --- prueba de RECHAZO por cada restricción citada -------------------\n"
                      "    // No basta con que la aplicación valide: hay que probar que la BASE\n"
                      "    // rechaza. Un doble siempre acepta; por eso van contra PostgreSQL real.\n")
    for r in restricciones:
        cuerpo.append(f"""    @Test
    @DisplayName("rechaza por {r}")
    void rechaza{r.replace('-', '')}() {{
        fail("{r}: falta la prueba de rechazo");
    }}
""")
    if extra:
        cuerpo.append("\n    // --- las obligatorias de este caso de uso ----------------------------\n")
    for clave, nombre in extra:
        cuerpo.append(f"""    @Test
    @DisplayName("{escapar(nombre)}")
    void {camel(clave)[0].lower() + camel(clave)[1:]}() {{
        fail("CU-{nn}: falta la prueba de {clave}");
    }}
""")
    return f"""package {pkg};

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * CU-{nn} · {titulo}
 *
 * Generado por scripts/nuevo_cu.py. NACE EN ROJO a propósito: el carril no decide
 * qué probar, decide cómo hacer pasar lo que la bóveda ya dejó escrito.
 *
 * Corre contra PostgreSQL 16 real (Testcontainers). Base en memoria NO: el modelo
 * usa EXCLUDE, btree_gist, RLS y numeric; una base que no los tiene prueba otro
 * sistema.
 */
class CU{nn}Test {{

{"".join(cuerpo)}}}
"""


def plantilla_openapi(nn, servicio, codigos, titulo):
    ops = "\n".join(f"    #         '422': {c}" for c in codigos) or "    #         '422': …"
    return f"""
# ── pegar en servicios/{servicio}/src/main/resources/openapi/{servicio}.yaml ──
#   CU-{nn} · {titulo}
#
#   /<prefijo-reservado>/…:
#     post:
#       operationId: <verboObjeto>
#       parameters:
#         - name: Idempotency-Key
#           in: header
#           required: true
#           schema: {{ type: string, format: uuid }}
#       requestBody: …          # additionalProperties: false
#       responses:
#         '200': …
{ops}
"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cu", help="numero del caso de uso, por ejemplo 31")
    ap.add_argument("--forzar", action="store_true")
    a = ap.parse_args()

    nn = a.cu.lstrip("0") or "0"
    hits = [p for p in CU_DIR.glob("CU-*.md")
            if re.match(rf"CU-0*{nn}\b", p.stem)]
    if not hits:
        print(f"ERROR: no existe docs/CasosDeUso/CU-{nn}")
        return 1
    cu = hits[0]
    texto = cu.read_text(encoding="utf-8")
    nn = re.match(r"CU-(\d+)", cu.stem).group(1)
    titulo = cu.stem.split(" ", 1)[1] if " " in cu.stem else cu.stem

    m = RE_CONTRATO.search(texto)
    if not m:
        print(f"ERROR: {cu.stem} sin cabecera de contrato")
        return 1
    servicio = m.group(1)
    base = SERVICIOS / servicio
    if not base.exists():
        print(f"ERROR: falta servicios/{servicio}. Corré primero:")
        print(f"       python3 scripts/nuevo_servicio.py {servicio}")
        return 1

    pkg = paquete_de(servicio)
    ruta = pkg.replace(".", "/")
    org = RE_ORGANISMO.search(texto)
    clase = org.group(1) if org else f"CU{nn}{camel(titulo)}"

    escenarios = escenarios_gherkin(texto)
    restricciones = sorted(set(re.findall(r"R-[A-Z]{2,4}-\d{2}", texto)))
    codigos = sorted(set(re.findall(rf"AP-CU{int(nn)}-\d+", texto)))
    extra = obligatorias(texto)

    destinos = {
        base / "src/main/java" / ruta / "aplicacion" / f"{clase}.java":
            organismo_java(pkg, clase, nn, titulo),
        base / "src/test/java" / ruta / f"CU{nn}Test.java":
            prueba_java(pkg, nn, titulo, escenarios, restricciones, extra),
    }
    for d, contenido in destinos.items():
        if d.exists() and not a.forzar:
            print(f"  omitido (ya existe): {d.relative_to(R)}")
            continue
        d.parent.mkdir(parents=True, exist_ok=True)
        d.write_text(contenido, encoding="utf-8")
        print(f"  creado: {d.relative_to(R)}")

    # fila del CU en el README del servicio
    rd = base / "README.md"
    if rd.exists():
        t = rd.read_text(encoding="utf-8")
        fila = f"| CU-{nn} | {titulo} | ⬜ sin implementar |"
        if f"| CU-{nn} |" not in t:
            t = t.replace("| | *(los llena `nuevo_cu.py`)* | |", fila)
            if fila not in t:
                t = t.replace("## Eventos que emite", fila + "\n\n## Eventos que emite")
            rd.write_text(t, encoding="utf-8")
            print(f"  README de {servicio}: fila de CU-{nn}")

    print(f"\n  {len(escenarios)} criterios · {len(restricciones)} restricciones · "
          f"{len(extra)} obligatorias  → {len(escenarios)+len(restricciones)+len(extra)} pruebas EN ROJO")
    print(plantilla_openapi(nn, servicio, codigos, titulo))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
