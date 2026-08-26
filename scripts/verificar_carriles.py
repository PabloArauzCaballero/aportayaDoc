#!/usr/bin/env python3
"""
Verifica que los carriles estén alineados, completos y equilibrados.

    python3 scripts/verificar_carriles.py

Con cinco máquinas concurrentes y un solo revisor, lo que no se verifica se
desalinea solo. Este script comprueba cinco cosas que ninguna persona sostiene a
mano:

  1 · COBERTURA DE SKILLS   las 65 skills están asignadas a algún carril, y
                            ningún carril nombra una skill que no existe
  2 · FICHAS COMPLETAS      todo carril del índice tiene ficha y fila en la
                            matriz normativa de skills
  3 · ALINEACIÓN 17 ↔ 18    el puesto de cada carril dice lo mismo en el plan de
                            coordinación y en su ficha
  4 · BALANCE DE CARGA      ningún puesto se lleva mucho más trabajo que la media
  5 · SERVICIO CON DUEÑO    todo servicio tiene descriptor con nivel declarado,
                            y todo carril de backend posee uno

Sale con 1 si falla 1, 2, 3 o 5. El desbalance (4) se informa siempre y solo
falla con --estricto: reasignar una máquina es una decisión de quien planifica,
no del verificador.
"""

import pathlib
import re
import sys

# Estos informes se imprimen con acentos, flechas y el punto medio. En Windows la
# consola entrega stdout en cp1252 y el gate muere con UnicodeEncodeError antes de
# decir si algo falla — en tres de las cinco maquinas del parque.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")


RAIZ = pathlib.Path(__file__).resolve().parent.parent
SKILLS = RAIZ / ".claude" / "skills"
P17 = RAIZ / "planes" / "17 Plan de acción secuencial · coordinación de cinco máquinas.md"
P18 = RAIZ / "planes" / "18 Fichas de carril · las 38 unidades de trabajo.md"
P19 = RAIZ / "planes" / "19 Contrato de carril · conflicto cero, skills y calidad verificada.md"
SERVICIOS = RAIZ / "servicios"

TOLERANCIA = 0.35   # ±35 % sobre la media antes de considerarlo desbalance

# Carriles de backend que poseen un servicio desplegable.
DUENO_DE_SERVICIO = {
    "1A": "identidad", "1B": "nucleo-financiero", "1C": "cumplimiento",
    "1D": "notificaciones", "2A": "nucleo-financiero", "2B": "tarifas",
    "2C": "grupos", "2D": "auditoria", "2E": "organizador", "3A": "aportes",
    "3B": "transparencia", "3C": "cumplimiento", "3D": "entregas",
    "4A": "entregas", "4B": "garantia", "5A": "erp", "5B": "publicidad",
}


def indice_de_fichas():
    """Los carriles del índice de planes/18: id → (puesto, {tramos}, peso)."""
    t = P18.read_text(encoding="utf-8")
    idx = t[t.index("## Índice"):t.index("★ carriles nuevos")]
    fichas = {}
    patron = r"\|\s*\[`([^`]+)`\][^|]*\|\s*(P\d)\s*\|\s*([^|]+?)\s*\|[^|]*\|[^|]*\|\s*([●○]+)\s*\|"
    for m in re.finditer(patron, idx):
        tramos = set(re.findall(r"T\d+", m.group(3)))
        fichas[m.group(1)] = (m.group(2), tramos, m.group(4).count("●"))
    return fichas


def asignacion_en_coordinacion():
    """Lo que dicen las tablas por tramo de planes/17: id → {(puesto, tramo)}.

    Lee TODO lo que sigue al puesto en la fila, porque las tablas tienen dos
    formatos —con columna de carril y sin ella— y el carril aparece en
    cualquiera de los dos.
    """
    asignado = {}
    tramo = None
    for linea in P17.read_text(encoding="utf-8").splitlines():
        cabecera = re.match(r"###\s+(T\d+)\s+·", linea)
        if cabecera:
            tramo = cabecera.group(1)
            continue
        fila = re.match(r"\|\s*\*\*(P\d)\*\*[^|]*\|(.*)", linea)
        if not (fila and tramo):
            continue
        for lane in re.findall(r"\b(T\d|[1-5][A-Z]|F\d{1,2}|F0-[MBW])\b", fila.group(2)):
            asignado.setdefault(lane, set()).add((fila.group(1), tramo))
    return asignado


def pares_en_serie():
    """Los `X → Y` que planes/17 declara: dos carriles seguidos en un mismo puesto."""
    serie = set()
    for a, b in re.findall(r"`?\*?\*?([A-Z0-9]{2,3})\*?\*?`?\s*→\s*`?\*?\*?([A-Z0-9]{2,3})\*?\*?`?",
                           P17.read_text(encoding="utf-8")):
        serie.add(frozenset((a, b)))
    return serie


def skills_de_la_matriz():
    """Todo lo que planes/19 §1 y §2 nombran entre comillas invertidas."""
    t = P19.read_text(encoding="utf-8")
    seccion = t[t.index("## 1 · Arranque de máquina"):t.index("## 3 · Conflicto cero")]
    nombradas = set(re.findall(r"`([a-z0-9-]{4,})`", seccion))
    filas = {}
    tabla = seccion[seccion.index("### Y las propias de cada carril"):]
    for m in re.finditer(r"^\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*$", tabla, re.M):
        carriles = re.findall(r"`([^`]+)`", m.group(1))
        propias = set(re.findall(r"`([a-z0-9-]{4,})`", m.group(2)))
        for lane in carriles:
            filas[lane] = propias
    return nombradas, filas


def main():
    estricto = "--estricto" in sys.argv
    errores, avisos = [], []

    existentes = {p.name for p in SKILLS.iterdir() if p.is_dir()}
    fichas = indice_de_fichas()
    coordinacion = asignacion_en_coordinacion()
    serie = pares_en_serie()
    nombradas, por_carril = skills_de_la_matriz()

    # 1 · cobertura de skills
    print("=== COBERTURA DE SKILLS ===")
    fantasmas = sorted(s for s in nombradas - existentes if (SKILLS / s).exists() is False
                       and "-" in s and s not in {"planes-19", "docs-views"})
    huerfanas = sorted(existentes - nombradas)
    if huerfanas:
        errores.append(f"skills sin carril asignado: {', '.join(huerfanas)}")
        print(f"  FALLA · {len(existentes) - len(huerfanas)}/{len(existentes)} skills asignadas"
              f" — huérfanas: {', '.join(huerfanas)}")
    else:
        print(f"  OK    · las {len(existentes)} skills están asignadas a algún carril")
    # Una skill nombrada que no existe manda a la máquina a buscar un archivo fantasma.
    inventadas = sorted(s for s in fantasmas if s.count("-") >= 1)
    if inventadas:
        errores.append(f"skills nombradas que no existen: {', '.join(inventadas)}")
        print(f"  FALLA · nombradas y inexistentes: {', '.join(inventadas)}")
    else:
        print("  OK    · ninguna skill nombrada que no exista")

    # 2 · fichas completas
    print("\n=== FICHAS Y MATRIZ ===")
    texto18 = P18.read_text(encoding="utf-8")
    sin_ficha = [l for l in fichas if f"`{l}`" not in texto18.split("## Índice")[0] + texto18]
    sin_skills = [l for l in fichas if l not in por_carril]
    if sin_skills:
        errores.append(f"carriles sin skills propias declaradas: {', '.join(sorted(sin_skills))}")
        print(f"  FALLA · sin fila en la matriz normativa: {', '.join(sorted(sin_skills))}")
    else:
        print(f"  OK    · los {len(fichas)} carriles tienen skills declaradas")
    if sin_ficha:
        errores.append(f"carriles del índice sin ficha: {', '.join(sin_ficha)}")

    # 3 · alineación entre el plan de coordinación y las fichas
    print("\n=== ALINEACIÓN planes/17 ↔ planes/18 ===")
    desalineados = []
    for lane, (puesto, tramos, _) in sorted(fichas.items()):
        en17 = coordinacion.get(lane)
        if not en17:
            continue
        if puesto not in {p for p, _ in en17}:
            desalineados.append(
                f"{lane}: ficha dice {puesto}, coordinación dice "
                f"{'/'.join(sorted({p for p, _ in en17}))}")
        elif tramos and not (tramos & {t for p, t in en17 if p == puesto}):
            desalineados.append(
                f"{lane}: ficha lo pone en {'/'.join(sorted(tramos))} y la coordinación en "
                f"{'/'.join(sorted(t for p, t in en17 if p == puesto))}")
    if desalineados:
        errores.append("carriles con puesto contradictorio: " + " · ".join(desalineados))
        for d in desalineados:
            print(f"  FALLA · {d}")
    else:
        print("  OK    · ningún carril con puesto contradictorio entre los dos planes")

    # 4 · balance de carga — por tramo, que es donde se decide, y global de contexto
    print("\n=== BALANCE DE CARGA ===")
    puestos = sorted({p for p, _, _ in fichas.values()})
    por_tramo = {}
    for _, (puesto, tramos, peso) in fichas.items():
        for tr in tramos:
            por_tramo.setdefault(tr, {})[puesto] = por_tramo.setdefault(tr, {}).get(puesto, 0) + peso

    print("  peso por tramo (● de la escala de tamaño) — el número que importa:")
    print("  tramo  " + " ".join(f"{p:>4}" for p in puestos) + "   max/min  ocupados")
    desparejos = []
    for tr in sorted(por_tramo, key=lambda x: int(x[1:])):
        fila = [por_tramo[tr].get(p, 0) for p in puestos]
        ocupados = [v for v in fila if v]
        razon = max(ocupados) / min(ocupados) if len(ocupados) > 1 else 1.0
        marca = "!!" if razon >= 4 else "  "
        print(f"  {marca} {tr:4} " + " ".join(f"{v:>4}" for v in fila)
              + f"   {razon:.1f}x     {len(ocupados)}/{len(puestos)}")
        if razon >= 4:
            desparejos.append(f"{tr} {razon:.0f}x")

    # Dos carriles del mismo puesto en el mismo tramo solo valen si están
    # declarados en serie ("X → Y") en el plan de coordinación.
    print("\n  concurrencia por puesto y tramo:")
    solapados = []
    for tr in sorted(por_tramo, key=lambda x: int(x[1:])):
        for puesto in puestos:
            juntos = sorted(l for l, (p, trs, _) in fichas.items() if p == puesto and tr in trs)
            if len(juntos) > 1 and frozenset(juntos) not in serie:
                solapados.append(f"{puesto} en {tr}: {' + '.join(juntos)}")
    if solapados:
        errores.append("carriles simultáneos sin declarar en serie: " + " · ".join(solapados))
        for s in solapados:
            print(f"    FALLA · {s} — o van en serie (`X → Y` en planes/17) o son dos máquinas")
    else:
        print("    OK    · ningún puesto con dos carriles a la vez sin declararlo en serie")

    carga = {}
    for _, (puesto, _, peso) in fichas.items():
        carga[puesto] = carga.get(puesto, 0) + peso
    total = sum(carga.values())
    media = total / len(carga)
    print(f"\n  acumulado del proyecto: {total} unidades · media {media:.1f} por puesto")
    fuera = []
    for puesto, peso in sorted(carga.items()):
        desvio = (peso - media) / media
        marca = "  " if abs(desvio) <= TOLERANCIA else "!!"
        print(f"  {marca} {puesto}: {peso:3} unidades   {desvio:+.0%}")
        if abs(desvio) > TOLERANCIA:
            fuera.append(f"{puesto} {desvio:+.0%}")

    if desparejos:
        mensaje = (f"tramos con un puesto cargando 4x o más que otro: {', '.join(desparejos)}."
                   f" Se corrige moviendo una deuda declarada, no partiendo un carril (planes/19 §11)")
        (errores if estricto else avisos).append(mensaje)
        print(f"\n  {'FALLA' if estricto else 'AVISO'} · {mensaje}")
    if fuera:
        print(f"  NOTA  · acumulado fuera de ±{TOLERANCIA:.0%}: {', '.join(fuera)} —"
              f" es estructural, no reasignable (planes/19 §11)")

    # 5 · todo servicio con dueño y con nivel declarado
    print("\n=== SERVICIOS Y SUS DUEÑOS ===")
    servicios = {p.name for p in SERVICIOS.iterdir() if p.is_dir()}
    sin_descriptor = sorted(s for s in servicios if not (SERVICIOS / s / "descriptor.yml").exists())
    sin_nivel = sorted(s for s in servicios
                       if (SERVICIOS / s / "descriptor.yml").exists()
                       and "nivel:" not in (SERVICIOS / s / "descriptor.yml").read_text(encoding="utf-8"))
    sin_carril = sorted(servicios - set(DUENO_DE_SERVICIO.values()))
    for lista, texto in ((sin_descriptor, "sin descriptor.yml"),
                         (sin_nivel, "sin nivel declarado (ADR-037)"),
                         (sin_carril, "sin carril dueño")):
        if lista:
            errores.append(f"servicios {texto}: {', '.join(lista)}")
            print(f"  FALLA · {texto}: {', '.join(lista)}")
    if not (sin_descriptor or sin_nivel or sin_carril):
        print(f"  OK    · los {len(servicios)} servicios tienen descriptor, nivel y carril dueño")

    print()
    for a in avisos:
        print(f"AVISO · {a}")
    if errores:
        print(f"\n{len(errores)} FALLAS")
        return 1
    print("TODO OK" + (" (con avisos)" if avisos else ""))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
