#!/usr/bin/env python3
"""
Verifica que el codigo cubra la especificacion, caso de uso por caso de uso.

    python3 scripts/verificar_criterios.py            (desde la raiz)
    python3 scripts/verificar_criterios.py --servicio tarifas

Convierte en mecanica la definicion que hasta ahora era prosa: "un CU cuenta como
implementado cuando todos sus criterios de aceptacion tienen su prueba con el mismo
nombre". Deja de ser una afirmacion de quien escribe el informe.

Compara, para cada caso de uso YA IMPLEMENTADO (existe su clase en servicios/):

  1. cada bloque `gherkin` de la boveda        -> una prueba con el mismo nombre
  2. cada R-XXX-nn citado por el caso de uso   -> una prueba de RECHAZO
  3. cada codigo AP-CU<NN>-<nn> del caso       -> declarado en el OpenAPI del servicio

Un caso de uso sin clase en servicios/ se reporta como PENDIENTE y no falla: el
gate es sobre lo que se declara terminado, no sobre lo que falta por hacer.

Devuelve 1 si algo falla, para usarlo como paso del CI.
"""
import argparse
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from modelo import escenarios_gherkin  # noqa: E402  — UNA sola definicion

R = pathlib.Path(__file__).resolve().parent.parent
CU_DIR = R / "docs/CasosDeUso"
SERVICIOS = R / "servicios"

# Un caso de uso vive en el servicio que lo expone. El mapa es el mismo que usa
# la cabecera "## Contrato · `openapi/<servicio>.yaml`" de cada CU, asi que se
# LEE de ahi en vez de duplicarlo: si el CU cambia de servicio, esto lo sigue.
RE_CONTRATO = re.compile(r"^## Contrato · `openapi/([\w-]+)\.yaml`", re.M)


def cu_num(p):
    return re.match(r"CU-(\d+)", p.stem).group(1)





def normalizar(s):
    """Compara nombres ignorando lo que no cambia el significado."""
    s = s.lower().strip()
    s = re.sub(r"[^\w\s]", " ", s)
    return re.sub(r"\s+", " ", s)


def pruebas_de(servicio, nn):
    """Nombres declarados en @DisplayName dentro de las pruebas del CU."""
    d = SERVICIOS / servicio / "src/test/java"
    if not d.exists():
        return None, [], ""
    archivos = [f for f in d.rglob(f"CU{nn}*Test.java")]
    if not archivos:
        return None, [], ""
    texto = "\n".join(f.read_text(encoding="utf-8") for f in archivos)
    return archivos, nombres_de_display(texto), texto


# El formateador parte `@DisplayName("texto muy largo")` en varias lineas y a veces
# concatena literales. Sin tolerarlo, el gate de formato y el de criterios se
# contradicen: nuevo_cu.py genera la prueba, spotless la reformatea, y este
# verificador la da por ausente. Le habria pasado a los cinco carriles el primer dia.
RE_DISPLAY = re.compile(r'@DisplayName\(\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)\)')
RE_LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')


def nombres_de_display(texto):
    """Los @DisplayName del archivo, ya rearmados si venian partidos."""
    return ["".join(RE_LITERAL.findall(bloque)) for bloque in RE_DISPLAY.findall(texto)]


def clase_de_aplicacion(servicio, nn):
    d = SERVICIOS / servicio / "src/main/java"
    if not d.exists():
        return None
    hits = list(d.rglob(f"CU{nn}*.java"))
    return hits[0] if hits else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--servicio", help="verificar solo este servicio")
    args = ap.parse_args()

    fallas, pendientes, verificados = [], [], 0

    for p in sorted(CU_DIR.glob("CU-*.md")):
        texto = p.read_text(encoding="utf-8")
        m = RE_CONTRATO.search(texto)
        if not m:
            fallas.append(f"{p.stem}: sin cabecera '## Contrato · `openapi/<servicio>.yaml`'")
            continue
        servicio, nn = m.group(1), cu_num(p)
        if args.servicio and servicio != args.servicio:
            continue

        if clase_de_aplicacion(servicio, nn) is None:
            pendientes.append(f"CU-{nn} ({servicio})")
            continue

        verificados += 1
        etiqueta = f"CU-{nn} · {servicio}"
        archivos, nombres, cuerpo = pruebas_de(servicio, nn)
        if archivos is None:
            fallas.append(f"{etiqueta}: implementado, sin archivo CU{nn}*Test.java")
            continue

        vistos = {normalizar(n) for n in nombres}

        # 1 · un criterio de aceptacion, una prueba con el mismo nombre
        for esc in escenarios_gherkin(texto):
            if normalizar(esc) not in vistos:
                fallas.append(f"{etiqueta}: criterio sin prueba — «{esc}»")
        # ... y ninguna prueba que no corresponda a un criterio: o sobra, o el
        # criterio se cambio en el codigo y no en la boveda, que es peor.
        esperados = {normalizar(e) for e in escenarios_gherkin(texto)}
        for n in nombres:
            if normalizar(n) not in esperados and not n.lower().startswith(
                    ("rechaza", "reintento", "concurrencia", "cuadre", "compensa",
                     "evento duplicado", "fuera de orden", "rls")):
                fallas.append(f"{etiqueta}: prueba sin criterio en la boveda — «{n}»")

        # 2 · cada restriccion citada, con su prueba de rechazo
        for r in sorted(set(re.findall(r"R-[A-Z]{2,4}-\d{2}", texto))):
            if r not in cuerpo:
                fallas.append(f"{etiqueta}: {r} citado sin prueba de rechazo")

        # 3 · cada codigo de error, declarado en el contrato
        yaml = SERVICIOS / servicio / f"src/main/resources/openapi/{servicio}.yaml"
        if yaml.exists():
            spec = yaml.read_text(encoding="utf-8")
            for c in sorted(set(re.findall(rf"AP-CU{int(nn)}-\d+", texto))):
                if c not in spec:
                    fallas.append(f"{etiqueta}: {c} no declarado en openapi/{servicio}.yaml")

    print(f"casos de uso implementados y verificados: {verificados}")
    if pendientes:
        print(f"pendientes de implementar ({len(pendientes)}): "
              + ", ".join(pendientes[:8]) + (" …" if len(pendientes) > 8 else ""))
    if fallas:
        print(f"\n{len(fallas)} FALLAS:")
        for f in fallas:
            print(f"  - {f}")
        return 1
    print("Sin divergencias entre la boveda y el codigo.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
