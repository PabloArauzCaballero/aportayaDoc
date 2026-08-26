#!/usr/bin/env python3
"""
Genera la bóveda Obsidian del modelo de datos a partir de los diagramas PlantUML.

    python3 scripts/generar_boveda.py        (desde la raíz del repositorio)

Lee   docs/entidades/*.puml   (bloque relacional de cada módulo)
Escribe
    docs/Index.md
    docs/Modelos/Entidades/*.md     una nota por tabla
    docs/Modelos/Relaciones/*.md    una nota por clave foránea

Las carpetas Entidades/ y Relaciones/ se borran y regeneran completas en cada
corrida: no edite esas notas a mano, edite el .puml y vuelva a ejecutar.
Las fichas de negocio de docs/entidades/*.md sí están escritas a mano y no se
tocan.
"""

import re, pathlib, shutil, json
from collections import defaultdict
import sys

# Estos informes se imprimen con acentos y flechas. En Windows la consola entrega
# stdout en cp1252 y el generador muere con UnicodeEncodeError despues de haber
# escrito los archivos — en tres de las cinco maquinas del parque.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")


ROOT = pathlib.Path("docs")
SRC = ROOT / "entidades"
OUT = ROOT / "Modelos"
ENT = OUT / "Entidades"
REL = OUT / "Relaciones"

MODULOS = {
    "01": ("Identidad, Usuarios y Seguridad", "01_identidad_usuarios"),
    "02": ("Grupos, Cupos, Turnos y Gobernanza", "02_grupos_turnos"),
    "03": ("Aportes, Pagos QR y Conciliación", "03_aportes_pagos_qr"),
    "04": ("Entregas de Fondo", "04_entregas_fondo"),
    "05": ("Notificaciones y Comunicaciones", "05_notificaciones"),
    "06": ("Transparencia y Reputación", "06_transparencia_reputacion"),
    "07": ("Organizador y Automatización", "07_organizador_automatizacion"),
    "08": ("Garantía, Incumplimiento, Cobranza y Sanciones", "08_garantia_incumplimiento"),
    "09": ("Auditoría, Reportes y Cumplimiento", "09_auditoria_reportes"),
    "10": ("Billetera, Custodia y Dinero Electrónico", "10_billetera_custodia"),
    "11": ("Tarifas, Comisiones, Impuestos y Facturación", "11_tarifas_comisiones"),
    "12": ("Cumplimiento Regulatorio y Consumidor Financiero", "12_cumplimiento_asfi"),
    "13": ("Contabilidad Financiera y ERP", "13_contabilidad_erp"),
    "14": ("Publicidad y Campañas", "14_publicidad_campanas"),
}

APPEND_ONLY = {
    "evento_reputacion", "registro_sellado", "bitacora_evento",
    "registro_acceso_datos", "movimiento_fondo", "abono_recuperacion",
    "historial_estado_incumplimiento", "registro_incumplimiento",
    "asiento_contable", "movimiento_contable",
    "transaccion_billetera", "movimiento_billetera", "movimiento_custodia",
    "saldo_diario_billetera", "devengo_comision",
    "registro_operacion_relevante", "evento_riesgo_operativo",
    "acta_comite",
    "factura_proveedor", "pago_a_proveedor", "cuenta_por_cobrar",
    "cobro_cuenta_por_cobrar", "depreciacion_activo", "estado_financiero_generado",
    "cierre_periodo_contable",
    "impresion_anuncio", "clic_anuncio", "conversion_anuncio", "factura_publicidad",
}

# --- FK -> tabla destino: overrides donde el nombre de columna no coincide ---
OVERRIDES = {
    "obligacion_id": "obligacion_aporte", "obligacion_origen_id": "obligacion_aporte",
    "registro_id": "registro_incumplimiento", "registro_origen_id": "registro_incumplimiento",
    "fondo_id": "fondo_garantia", "deuda_id": "deuda_participante",
    "cobertura_id": "cobertura_incumplimiento", "aval_id": "aval_participante",
    "gestion_id": "gestion_cobranza", "estrategia_id": "estrategia_cobranza",
    "matriz_id": "matriz_sancion", "politica_cobertura_id": "politica_cobertura",
    "politica_mora_id": "politica_mora", "politica_sancion_id": "politica_sancion",
    "acuerdo_grupo_id": "acuerdo", "entrega_id": "entrega_fondo",
    "entrega_afectada_id": "entrega_fondo", "asiento_id": "asiento_contable",
    "asiento_contable_id": "asiento_contable", "asiento_reversa_id": "asiento_contable",
    "cuenta_id": "cuenta_contable", "cuenta_contable_id": "cuenta_contable",
    "cuenta_destino_id": "cuenta_bancaria_beneficiario",
    "extracto_id": "extracto_bancario", "documento_id": "documento_identidad",
    "kyc_reforzado_id": "verificacion_kyc", "rotado_de_id": "token_verificacion",
    "token_id": "token_verificacion", "token_firma_id": "token_verificacion",
    "token_aceptacion_id": "token_verificacion", "token_confirmacion_id": "token_verificacion",
    "politica_id": "politica_sancion", "plantilla_id": "plantilla_mensaje",
    "version_plantilla_id": "version_plantilla", "envio_id": "envio_notificacion",
    "notificacion_id": "notificacion", "notificacion_relacionada_id": "notificacion",
    "modelo_id": "modelo_scoring", "puntaje_id": "puntaje_reputacion",
    "snapshot_id": "snapshot_reputacion", "insignia_id": "insignia_logro",
    "bloque_id": "bloque_transparencia", "revertido_por_id": "evento_reputacion",
    "evaluacion_id": "evaluacion_desempeno", "tarea_id": "tarea_automatizada",
    "definicion_id": "definicion_reporte", "ejecucion_id": "ejecucion_reporte",
    "lista_id": "lista_restrictiva_externa", "solicitud_id": "solicitud_datos_personales",
    "reporte_sospechoso_id": "reporte_operacion_sospechosa",
    "sancion_organizador_id": "sancion_organizador", "sancion_id": "sancion",
    "reemplazo_id": "reemplazo_participante", "disolucion_id": "disolucion_anticipada",
    "postulacion_id": "postulacion_emparejamiento", "propuesta_id": "propuesta_grupo",
    "criterio_id": "criterio_emparejamiento", "reglamento_id": "reglamento_grupo",
    "orden_desembolso_id": "orden_desembolso", "orden_cobro_id": "orden_cobro",
    "intento_pago_id": "intento_pago", "movimiento_bancario_id": "movimiento_bancario",
    "movimiento_fondo_id": "movimiento_fondo", "conciliacion_id": "conciliacion",
    "canal_vinculado_id": "canal_vinculado", "grupo_materializado_id": "grupo",
    "permutado_con_turno_id": "turno", "turno_origen_id": "turno", "turno_destino_id": "turno",
    "invitado_por_id": "participante",
    "plan_regularizacion_id": "plan_regularizacion",
    "actor_usuario_id": "usuario", "suplantando_a_usuario_id": "usuario",
    "aprobado_por_acuerdo_id": "acuerdo",
    # --- M10: billetera y custodia ---
    "transaccion_id": "transaccion_billetera",
    "transaccion_origen_id": "transaccion_billetera",
    "transaccion_original_id": "transaccion_billetera",
    "transaccion_reverso_id": "transaccion_billetera",
    "cuenta_billetera_origen_id": "cuenta_billetera",
    "cuenta_billetera_destino_id": "cuenta_billetera",
    "instrumento_destino_id": "instrumento_fondeo",
    "retencion_id": "retencion_saldo",
    "limite_id": "limite_operativo_billetera",
    # --- M11: tarifas y comisiones ---
    "hecho_generador_id": "catalogo_hecho_generador",
    "tarifario_anterior_id": "tarifario", "tarifario_nuevo_id": "tarifario",
    "segmento_id": "segmento_comercial",
    "cotizacion_id": "cotizacion_comision",
    "devengo_id": "devengo_comision",
    "campana_id": "campana_promocional",
    "cuenta_ingreso_id": "cuenta_contable",
    "factura_id": "factura_electronica",
    "evento_significativo_id": "evento_significativo_sin",
    # --- M12: cumplimiento ---
    "matriz_riesgo_id": "matriz_riesgo_lft",
    "calificacion_riesgo_id": "calificacion_riesgo_cliente",
    "regla_monitoreo_id": "regla_monitoreo_lft",
    "alerta_monitoreo_id": "alerta_monitoreo_lft",
    "caso_id": "caso_investigacion_lft",
    "catalogo_reporte_id": "catalogo_reporte_regulatorio",
    "reclamo_id": "reclamo_cliente",
    "evento_riesgo_id": "evento_riesgo_operativo",
    "hallazgo_id": "hallazgo_auditoria",
    "control_id": "control_interno",
    "umbral_reporte_id": "umbral_reporte_uif",
    "operacion_inicio_ventana_id": "registro_operacion_relevante",
    "declaracion_origen_fondos_id": "declaracion_origen_fondos",
    "propietario_id": "usuario", "custodio_id": "usuario",
    "responsable_id": "usuario", "analista_id": "usuario",
    "usuario_obligado_id": "usuario", "responsable_usuario_id": "usuario",
    # --- M13: contabilidad financiera y ERP ---
    "cuenta_padre_id": "cuenta_contable",
    "cuenta_activo_id": "cuenta_contable",
    "cuenta_depreciacion_id": "cuenta_contable",
    "cuenta_gasto_depreciacion_id": "cuenta_contable",
    # --- M14: publicidad y campañas ---
    "impresion_id": "impresion_anuncio",
    "clic_id": "clic_anuncio",
}
# FK por modulo cuando el nombre es ambiguo
POR_MODULO = {
    ("03", "proveedor_id"): "proveedor_pago", ("04", "proveedor_id"): "proveedor_pago",
    ("05", "proveedor_id"): "proveedor_mensajeria",
    ("10", "proveedor_id"): "proveedor_pago", ("11", "proveedor_id"): "proveedor_pago",
    ("04", "regla_id"): "regla_entrega", ("07", "regla_id"): "regla_automatizacion",
    ("09", "regla_id"): "regla_cumplimiento",
    ("05", "evento_id"): "evento_notificable",
    ("08", "politica_id"): "politica_sancion",
    ("13", "plantilla_id"): "asiento_plantilla",
}
# columnas de actor -> usuario
ACTOR_A_USUARIO = re.compile(
    r"^(.*_por|.*_por_id|asignad[ao]_a|asignada_a|gestor_asignado_id|responsable_gestion|"
    r"analista_id|apelante_id|reclamante_id|emisor_id|usuario_consultor_id|usuario_afectado_id|"
    r"evaluado_usuario_id|avalista_usuario_id|suplantando_a_usuario_id|liberada_por|levantada_por|"
    r"revisada_por|revisado_por|segunda_revision_por|moderada_por|resuelta_por|resuelto_por)$")
PARTICIPANTE = re.compile(
    r"^(participante_origen_id|participante_destino_id|participante_avalado_id|"
    r"participante_saliente_id|participante_entrante_id|beneficiario_participante_id|"
    r"reportado_por_participante_id|autor_participante_id|solicitante_id|contraparte_id)$")


def slug(s):
    return re.sub(r"[^\w\- ]", "", s).strip()


def parse_puml(path):
    txt = path.read_text(encoding="utf-8")
    i = txt.index("@enduml")
    rel_block = txt[txt.index("@startuml", i):]
    cls_block = txt[: i]

    # clases + estereotipos
    clases = {}
    for m in re.finditer(r"^\s*(?:abstract\s+)?class\s+(\w+)\s*(<<(\w+)>>)?", cls_block, re.M):
        clases[m.group(1)] = m.group(3) or ""

    entidades, orden = {}, []
    for m in re.finditer(r'^entity\s+"([^"]+)"\s+as\s+(\w+)\s*\{(.*?)^\}', rel_block, re.M | re.S):
        tabla, alias, body = m.group(1), m.group(2), m.group(3)
        cols = []
        for line in body.splitlines():
            line = line.strip()
            if not line or line == "--":
                continue
            pk = line.startswith("*")
            line_s = line.lstrip("* ").strip()
            fk = line_s.startswith("#")
            line_s = line_s.lstrip("# ").strip()
            if " : " not in line_s:
                continue
            nombre, resto = line_s.split(" : ", 1)
            anot = re.findall(r"<<(.*?)>>", resto)
            tipo = re.sub(r"<<.*?>>", "", resto).strip()
            flat = ", ".join(anot)
            cols.append({
                "nombre": nombre.strip(), "tipo": tipo, "pk": pk or "PK" in flat,
                "fk": fk or re.search(r"\bFK\b", flat) is not None,
                "uq": "UQ" in flat, "idx": "IDX" in flat,
                "nulo": "NULL" in flat, "ck": "CK" in flat, "anot": flat,
            })
        entidades[alias] = {"tabla": tabla, "cols": cols}
        orden.append(alias)

    relaciones = []
    for line in rel_block.splitlines():
        m = re.match(r'^(\w+)\s+(\S*--\S*)\s+(\w+)\s*:\s*"([^"]*)"\s*$', line.strip())
        if m:
            relaciones.append({"a": m.group(1), "card": m.group(2),
                               "b": m.group(3), "label": m.group(4)})

    notas = defaultdict(list)
    for m in re.finditer(r"^note\s+(?:top|bottom|left|right)\s+of\s+(\w+)\s*\n(.*?)^end note",
                         rel_block, re.M | re.S):
        notas[m.group(1)].append(m.group(2).rstrip())

    return {"entidades": entidades, "orden": orden, "relaciones": relaciones,
            "notas": notas, "clases": clases}


# ---------- carga ----------
mods = {}
for k, (nombre, fichero) in MODULOS.items():
    mods[k] = parse_puml(SRC / f"{fichero}.puml")
    mods[k]["nombre"], mods[k]["fichero"] = nombre, fichero

registro = {}        # tabla -> modulo
alias_de = {}        # (modulo, alias) -> tabla
for k, d in mods.items():
    for alias, e in d["entidades"].items():
        registro[e["tabla"]] = k
        alias_de[(k, alias)] = e["tabla"]


def clase_de(tabla, modulo=None):
    cand = "".join(p.capitalize() for p in tabla.split("_"))
    orden = ([modulo] if modulo else []) + [k for k in mods if k != modulo]
    for k in orden:
        for c, st in mods[k]["clases"].items():
            if c.lower() == cand.lower():
                return c, st
    return None, ""


def resolver_fk(col, modulo, tabla_origen):
    n = col["nombre"]
    if (modulo, n) in POR_MODULO:
        return POR_MODULO[(modulo, n)]
    if n in OVERRIDES:
        return OVERRIDES[n]
    if PARTICIPANTE.match(n):
        return "participante"
    if n.endswith("_id"):
        base = n[:-3]
        if base in registro:
            return base
    if ACTOR_A_USUARIO.match(n):
        return "usuario"
    if n in registro:
        return n
    return None


# ---------- construir grafo de FKs ----------
fks = []
for k, d in mods.items():
    for alias, e in d["entidades"].items():
        for c in e["cols"]:
            if not c["fk"]:
                continue
            destino = resolver_fk(c, k, e["tabla"])
            fks.append({
                "origen": e["tabla"], "modulo_origen": k, "columna": c["nombre"],
                "destino": destino, "modulo_destino": registro.get(destino),
                "opcional": c["nulo"], "unica": c["uq"], "col": c,
            })

# cardinalidades declaradas: (tabla_a, tabla_b) -> (card, label)
declaradas = {}
for k, d in mods.items():
    for r in d["relaciones"]:
        a = alias_de.get((k, r["a"]))
        b = alias_de.get((k, r["b"]))
        if a and b:
            declaradas[(a, b)] = (r["card"], r["label"])

CARD_TXT = {
    "||--o{": "uno a muchos (0..N)", "||--|{": "uno a muchos (1..N)",
    "||--o|": "uno a uno opcional", "||--||": "uno a uno",
    "}o--o|": "muchos a uno opcional", "}o--||": "muchos a uno",
    "}o--o{": "muchos a muchos", "|o--o|": "uno a uno, ambos opcionales",
    "|o--o{": "uno a muchos, origen opcional",
}

entrantes = defaultdict(list)
salientes = defaultdict(list)
for f in fks:
    salientes[f["origen"]].append(f)
    if f["destino"]:
        entrantes[f["destino"]].append(f)


def rel_nombre(f):
    return f'{f["origen"]}.{f["columna"]} → {f["destino"] or "SIN_RESOLVER"}'


def carpeta_modulo(k):
    """Subcarpeta por módulo dentro de Entidades/ y Relaciones/."""
    return f"{k} - {MODULOS[k][0]}"


def _sin_acentos(s):
    for a, b in zip("áéíóúüñÁÉÍÓÚÜÑ", "aeiouunAEIOUUN"):
        s = s.replace(a, b)
    return s


def tag_modulo(k):
    base = _sin_acentos(MODULOS[k][0].lower())
    return f"modulo/{k}-" + re.sub(r"[^a-z0-9]+", "-", base).strip("-")


# ---------- emitir ----------
for p in (ENT, REL):
    if p.exists():
        shutil.rmtree(p)
    p.mkdir(parents=True)
    for k in MODULOS:
        (p / carpeta_modulo(k)).mkdir()

# --- Entidades ---
for k, d in mods.items():
    for alias in d["orden"]:
        e = d["entidades"][alias]
        t = e["tabla"]
        clase, ster = clase_de(t, k)
        ster_txt = {"AR": "Raíz de agregado", "VO": "Objeto de valor",
                    "Svc": "Servicio de dominio", "Pol": "Política configurable"}.get(ster, "")
        pks = [c["nombre"] for c in e["cols"] if c["pk"]]
        L = []
        L.append("---")
        L.append(f"tags:\n  - entidad\n  - {tag_modulo(k)}" +
                 ("\n  - append-only" if t in APPEND_ONLY else ""))
        L.append(f'tabla: {t}')
        if clase:
            L.append(f'clase: {clase}')
        L.append(f'modulo: "{k} — {MODULOS[k][0]}"')
        if ster_txt:
            L.append(f'estereotipo: {ster_txt}')
        L.append(f'clave_primaria: [{", ".join(pks) if pks else ""}]')
        L.append(f'columnas: {len(e["cols"])}')
        L.append(f'fk_salientes: {len(salientes[t])}')
        L.append(f'fk_entrantes: {len(entrantes[t])}')
        L.append(f'append_only: {"true" if t in APPEND_ONLY else "false"}')
        L.append("---\n")
        L.append(f"# `{t}`\n")
        cab = [f"Módulo [[{MODULOS[k][1]}|{k} — {MODULOS[k][0]}]]"]
        if clase:
            cab.append(f"clase `{clase}`" + (f" · {ster_txt}" if ster_txt else ""))
        if t in APPEND_ONLY:
            cab.append("**append-only** (sin `UPDATE`/`DELETE`)")
        L.append("> " + " · ".join(cab) + "\n")

        L.append("## Columnas\n")
        L.append("| Columna | Tipo | Clave | Nulo | Anotaciones |")
        L.append("| --- | --- | --- | :-: | --- |")
        for c in e["cols"]:
            clave = " ".join(x for x in
                             ("PK" if c["pk"] else "", "FK" if c["fk"] else "",
                              "UQ" if c["uq"] else "", "IDX" if c["idx"] else "") if x)
            L.append(f'| `{c["nombre"]}` | {c["tipo"]} | {clave or "—"} | '
                     f'{"sí" if c["nulo"] else "no"} | {c["anot"] or "—"} |')
        L.append("")

        if salientes[t]:
            L.append("## Claves foráneas salientes\n")
            L.append("| Columna | Referencia a | Módulo | Opcional | Relación |")
            L.append("| --- | --- | :-: | :-: | --- |")
            for f in sorted(salientes[t], key=lambda x: x["columna"]):
                dst = f["destino"]
                md = f["modulo_destino"]
                cruz = "↗ " if md and md != k else ""
                ref = f"[[{dst}]]" if dst else "*sin resolver*"
                L.append(f'| `{f["columna"]}` | {ref} | {cruz}{md or "—"} | '
                         f'{"sí" if f["opcional"] else "no"} | [[{rel_nombre(f)}]] |')
            L.append("")

        if entrantes[t]:
            L.append("## Referenciada por\n")
            L.append("| Entidad | Columna | Módulo | Relación |")
            L.append("| --- | --- | :-: | --- |")
            for f in sorted(entrantes[t], key=lambda x: (x["origen"], x["columna"])):
                md = f["modulo_origen"]
                cruz = "↗ " if md != k else ""
                L.append(f'| [[{f["origen"]}]] | `{f["columna"]}` | {cruz}{md} | '
                         f'[[{rel_nombre(f)}]] |')
            L.append("")

        vecinos = sorted({f["destino"] for f in salientes[t] if f["destino"]} |
                         {f["origen"] for f in entrantes[t]} - {t})
        if vecinos:
            L.append("## Entidades vecinas\n")
            L.append(" · ".join(f"[[{v}]]" for v in vecinos) + "\n")

        if d["notas"].get(alias):
            L.append("## Notas del modelo\n")
            for n in d["notas"][alias]:
                limpio = re.sub(r"</?b>", "**", n)
                L.append("> " + "\n> ".join(l.strip() for l in limpio.splitlines()) + "\n")

        L.append("## Ver también\n")
        L.append(f"- Justificación de negocio: [[{MODULOS[k][1]}]]")
        L.append(f"- Diagramas: `docs/entidades/{MODULOS[k][1]}.puml`")
        L.append(f"- Índice: [[_Entidades]] · [[Index]]")
        (ENT / carpeta_modulo(k) / f"{t}.md").write_text("\n".join(L) + "\n")

# --- Relaciones ---
for f in fks:
    o, dst = f["origen"], f["destino"]
    card, label = declaradas.get((dst, o), declaradas.get((o, dst), ("", "")))
    nombre = rel_nombre(f)
    cruz = f["modulo_destino"] and f["modulo_destino"] != f["modulo_origen"]
    L = ["---",
         "tags:\n  - relacion\n  - fk\n  - " + tag_modulo(f["modulo_origen"]) +
         ("\n  - cross-modulo" if cruz else ""),
         f'origen: {o}', f'columna: {f["columna"]}',
         f'destino: {dst or "sin-resolver"}',
         f'modulo_origen: "{f["modulo_origen"]}"',
         f'modulo_destino: "{f["modulo_destino"] or "—"}"',
         f'cross_modulo: {"true" if cruz else "false"}',
         f'opcional: {"true" if f["opcional"] else "false"}',
         f'cardinalidad: "{CARD_TXT.get(card, card or "no declarada en el diagrama")}"',
         "---\n",
         f"# {nombre}\n"]
    L.append(f"> **[[{o}]]** `.{f['columna']}` → **" +
             (f"[[{dst}]]" if dst else "sin resolver") + "**\n")
    L.append("| | |")
    L.append("| --- | --- |")
    L.append(f"| Entidad origen | [[{o}]] (módulo {f['modulo_origen']}) |")
    L.append(f"| Entidad destino | " +
             (f"[[{dst}]] (módulo {f['modulo_destino']}) |" if dst else "*sin resolver* |"))
    L.append(f"| Columna | `{f['columna']}` — {f['col']['tipo']} |")
    L.append(f"| Cardinalidad | {CARD_TXT.get(card, card or 'no declarada en el diagrama')} |")
    L.append(f"| Obligatoria | {'no (admite NULL)' if f['opcional'] else 'sí'} |")
    L.append(f"| Uno a uno | {'sí (columna UNIQUE)' if f['unica'] else 'no'} |")
    L.append(f"| Cruza módulos | {'sí ↗' if cruz else 'no'} |")
    if label:
        L.append(f'| Semántica | "{label}" |')
    L.append("")
    if cruz:
        L.append("> [!info] Referencia entre módulos\n"
                 f"> Esta FK conecta el módulo {f['modulo_origen']} con el "
                 f"{f['modulo_destino']}. En los diagramas se documenta en notas al pie "
                 "y, cuando es polimórfica, se valida por aplicación o trigger en lugar "
                 "de con una FK física.\n")
    if f["opcional"]:
        L.append("> [!note] Opcional\n"
                 "> La columna admite `NULL`: la relación puede no existir. "
                 "Conviene revisar en la ficha de negocio qué significa su ausencia.\n")
    L.append("## Ver también\n")
    L.append(f"- [[{MODULOS[f['modulo_origen']][1]}]] — justificación de negocio del origen")
    if dst and f["modulo_destino"]:
        L.append(f"- [[{MODULOS[f['modulo_destino']][1]}]] — justificación de negocio del destino")
    L.append("- [[_Relaciones]] · [[Index]]")
    (REL / carpeta_modulo(f["modulo_origen"]) / f"{nombre}.md").write_text("\n".join(L) + "\n")

resumen = {
    "entidades": sum(len(d["entidades"]) for d in mods.values()),
    "relaciones_fk": len(fks),
    "sin_resolver": sorted({f'{f["origen"]}.{f["columna"]}' for f in fks if not f["destino"]}),
    "cross_modulo": sum(1 for f in fks if f["modulo_destino"] and f["modulo_destino"] != f["modulo_origen"]),
}

# ---------------- indices por modulo ----------------
for k, d in sorted(mods.items()):
    tablas_mod = [d["entidades"][a]["tabla"] for a in d["orden"]]
    fks_mod = sorted([f for f in fks if f["modulo_origen"] == k],
                     key=lambda x: (x["origen"], x["columna"]))

    L = ["---", f"tags:\n  - moc\n  - {tag_modulo(k)}",
         f'modulo: "{k} — {MODULOS[k][0]}"', f"entidades: {len(tablas_mod)}", "---\n",
         f"# {k} — {MODULOS[k][0]} · entidades\n",
         f"Las **{len(tablas_mod)} tablas** de este módulo. "
         f"Justificación de negocio en [[{MODULOS[k][1]}]].\n",
         "[[_Entidades|← Todas las entidades]] · [[Index]]\n",
         "| Tabla | Columnas | FK sal. | FK ent. |", "| --- | --: | --: | --: |"]
    for a in d["orden"]:
        e = d["entidades"][a]
        tb = e["tabla"]
        L.append(f'| [[{tb}]] | {len(e["cols"])} | {len(salientes[tb])} | {len(entrantes[tb])} |')
    (ENT / carpeta_modulo(k) / f"_Entidades {k}.md").write_text("\n".join(L) + "\n")

    L = ["---", f"tags:\n  - moc\n  - {tag_modulo(k)}",
         f'modulo: "{k} — {MODULOS[k][0]}"', f"relaciones_fk: {len(fks_mod)}", "---\n",
         f"# {k} — {MODULOS[k][0]} · relaciones\n",
         f"Las **{len(fks_mod)} claves foráneas** que salen de las tablas de este módulo.\n",
         "[[_Relaciones|← Todas las relaciones]] · [[Index]]\n",
         "| Relación | Destino | Cruza | Opcional |", "| --- | --- | :-: | :-: |"]
    for f in fks_mod:
        cruza = ("↗ " + f["modulo_destino"]
                 if f["modulo_destino"] and f["modulo_destino"] != k else "—")
        L.append(f'| [[{f["origen"]}.{f["columna"]} → {f["destino"]}]] | [[{f["destino"]}]] | '
                 f'{cruza} | {"sí" if f["opcional"] else "no"} |')
    (REL / carpeta_modulo(k) / f"_Relaciones {k}.md").write_text("\n".join(L) + "\n")


# ---------------- contexto para los indices ----------------
mods = {k: {"entidades": d["entidades"], "orden": d["orden"],
            "nombre": d["nombre"], "fichero": d["fichero"]} for k, d in mods.items()}
fks = [{kk: vv for kk, vv in f.items() if kk != "col"} for f in fks]
APPEND_ONLY = set(APPEND_ONLY)
ROOT, OUT = pathlib.Path("docs"), pathlib.Path("docs") / "Modelos"
sal, ent = defaultdict(list), defaultdict(list)
for f in fks:
    sal[f["origen"]].append(f)
    ent[f["destino"]].append(f)

FOCO = {
    "01": "Saber con certeza a quién le estás confiando plata ajena",
    "02": "Reglas del juego, orden de cobro y decisiones colectivas",
    "03": 'Que "pagué" signifique "el banco lo confirmó"',
    "04": "Que la bolsa llegue completa, a la persona correcta, una sola vez",
    "05": "WhatsApp como canal real de cobro, sin spam ni doble aviso",
    "06": 'Que nadie tenga que "creerle" al organizador',
    "07": "Administrar es un rol, no un negocio: el organizador no cobra ni custodia",
    "08": "El grupo no se detiene, pero la deuda no se perdona sola",
    "09": "Poder demostrar todo lo anterior ante un reclamo o un regulador",
    "10": "El saldo no se guarda: se deriva, y todos los días cuadra contra el banco",
    "11": "La política de cobro es dato, no código: se cambia con un seeder",
    "12": "Que una inspección se responda con consultas, no armando carpetas",
    "13": "Que cerrar un mes no dependa de un Excel armado a mano",
    "14": "Que un partner se anuncie dentro de la app sin inventar un segundo cobro",
}

tablas = []
for k, d in sorted(mods.items()):
    for alias in d["orden"]:
        e = d["entidades"][alias]
        tablas.append((e["tabla"], k, len(e["cols"]), len(sal[e["tabla"]]), len(ent[e["tabla"]])))

grado = {t: o + i for t, _, _, o, i in tablas}
hubs = sorted(tablas, key=lambda r: -(r[3] + r[4]))[:12]


def fichero(k):
    return mods[k]["fichero"]

# ---------------- Index.md ----------------
n_casos = len(list((ROOT / "CasosDeUso").glob("CU-*.md")))
L = ["---",
     "tags:\n  - moc\n  - indice",
     'titulo: "AportaYa — modelo de datos"',
     f"entidades: {len(tablas)}",
     f"relaciones_fk: {len(fks)}",
     f"modulos: {len(mods)}",
     "---\n",
     "# AportaYa — Índice\n",
     "> [!abstract] Qué es esta bóveda",
     "> El modelo de datos completo del sistema, navegable como grafo. Cada tabla y",
     "> cada clave foránea es una nota, enlazada con las demás, para poder recorrer",
     "> el modelo por relaciones en vez de leer nueve diagramas sueltos.\n",
     "## Cómo está organizada\n",
     "```",
     "docs/",
     "├── Index.md                 ← estás acá",
     "├── Modelos/",
     f"│   ├── Entidades/           ← una nota por tabla ({len(tablas)}), en {len(mods)} carpetas",
     "│   │   ├── 01 - Identidad, Usuarios y Seguridad/",
     "│   │   ├── 02 - Grupos, Cupos, Turnos y Gobernanza/",
     "│   │   └── ...",
     f"│   └── Relaciones/          ← una nota por clave foránea ({len(fks)}), en {len(mods)} carpetas",
     "│       ├── 01 - Identidad, Usuarios y Seguridad/",
     "│       └── ...",
     f"├── CasosDeUso/              ← un caso de uso por flujo ({n_casos}), con criterios de aceptación",
     "├── Cumplimiento.md          ← matriz normativa ASFI · UIF · BCB · SIN · ISO",
     "├── Restricciones.md         ← catálogo de restricciones con DDL",
     "└── entidades/               ← justificación de negocio + diagramas .puml",
     "```\n",
     "| Carpeta | Qué contiene | Índice |",
     "| --- | --- | --- |",
     "| **Entidades** | Una nota por tabla, agrupadas en una carpeta por módulo: columnas, claves, FK salientes y entrantes, entidades vecinas y las notas del diagrama. | [[_Entidades]] |",
     "| **Relaciones** | Una nota por FK, agrupadas por el módulo de la tabla de origen: destino, cardinalidad, si es opcional y si cruza módulos. | [[_Relaciones]] |",
     "| **entidades/** | Por qué existe cada entidad, a nivel de negocio y de sistema. Un documento por módulo. | [[entidades/README\\|Fichas de negocio]] |",
     "| **Cumplimiento** | Contraste requisito por requisito contra ASFI, UIF, BCB, SIN e ISO, con estado y brechas abiertas. | [[Cumplimiento]] |",
     "| **Casos de uso** | Cómo se ejecuta cada flujo: pasos, tablas, validaciones, evidencia y criterios de aceptación. | [[_CasosDeUso]] |",
     "| **Restricciones** | Las reglas que la base de datos hace cumplir, con su DDL y la norma que las obliga. | [[Restricciones]] |",
     "\n## Los cinco registros que conviene entender primero\n",
     "Casi todo el modelo se explica con cinco ideas. Si vas a leer solo cinco notas, que sean estas:\n",
     "1. **[[obligacion_aporte]]** — el eje del dinero. La cubre el fondo, la deduce la entrega y la puntúa la reputación.",
     "2. **[[registro_incumplimiento]]** — el incumplimiento como expediente, no como bandera.",
     "3. **[[asiento_contable]]** — doble partida: nada se edita, todo se reversa.",
     "4. **[[transaccion_billetera]]** — el saldo no se guarda: se deriva de un libro append-only con partida doble interna.",
     "5. **[[concepto_tarifa]]** — la política de cobro completa en seis columnas: se cambia con un seeder, no con un despliegue.\n",
     "## Módulos\n",
     "| # | Módulo | Foco de negocio | Tablas | FK | Fichas |",
     "| :-: | --- | --- | --: | --: | --- |"]

for k, d in sorted(mods.items()):
    nt = len(d["entidades"])
    nf = sum(1 for f in fks if f["modulo_origen"] == k)
    L.append(f'| {k} | {d["nombre"]} | {FOCO[k]} | {nt} | {nf} | [[{fichero(k)}\\|negocio]] |')

L += ["", "## Entidades más conectadas\n",
      "El grado (FK entrantes + salientes) es un buen proxy de importancia estructural:\n",
      "| Entidad | Módulo | FK salientes | FK entrantes | Grado |",
      "| --- | :-: | --: | --: | --: |"]
for t, k, _, o, i in hubs:
    L.append(f"| [[{t}]] | {k} | {o} | {i} | **{o+i}** |")

cross = sum(1 for f in fks if f["modulo_destino"] and f["modulo_destino"] != f["modulo_origen"])
L += ["", "## Acoplamiento entre módulos\n",
      f"De las {len(fks)} claves foráneas, **{cross} cruzan módulos**. La matriz muestra",
      "cuántas FK van de un módulo (fila) a otro (columna):\n"]
mat = defaultdict(int)
for f in fks:
    if f["modulo_destino"] and f["modulo_destino"] != f["modulo_origen"]:
        mat[(f["modulo_origen"], f["modulo_destino"])] += 1
ks = sorted(mods)
L.append("| desde \\ hacia | " + " | ".join(ks) + " |")
L.append("| :-: |" + " --: |" * len(ks))
for a in ks:
    L.append(f"| **{a}** | " + " | ".join(str(mat.get((a, b), "") or "·") for b in ks) + " |")

L += ["", "> [!tip] Cómo leerla",
      "> La columna **01** llena de números confirma que identidad es el cimiento: casi",
      "> todo cuelga de `usuario`. La fila **08** muestra lo contrario: el incumplimiento",
      "> consume de todos lados porque necesita el contexto completo para armar el expediente.\n",
      "## Convenciones de la bóveda\n",
      "- **Tags**: `#entidad`, `#relacion`, `#fk`, `#cross-modulo`, `#append-only`, `#modulo/0X-...`",
      "- **Propiedades**: cada nota lleva frontmatter con módulo, tabla, clase, claves y conteos, para filtrar con búsqueda o Dataview.",
      "- **`↗`** en una tabla marca que la referencia cruza a otro módulo.",
      "- Las notas **append-only** no admiten `UPDATE` ni `DELETE`: se corrigen registrando el movimiento inverso.",
      "- Los nombres de nota son los **nombres de tabla** (`snake_case`), así que `[[pago]]` autocompleta desde cualquier nota.\n",
      "## Búsquedas útiles\n",
      "```",
      "tag:#append-only              → tablas que no se editan nunca",
      "tag:#cross-modulo             → FK que acoplan módulos",
      'tag:#entidad "clave_idempotencia"  → dónde se protege contra duplicados',
      "```\n",
      "> [!note] Cómo se generó",
      "> Las notas de `Modelos/` se derivan de los `.puml` de `docs/entidades/`: si cambia",
      "> un diagrama, hay que regenerarlas para que no se desincronicen. Las fichas de",
      "> negocio de `docs/entidades/*.md` sí están escritas a mano.\n"]
(ROOT / "Index.md").write_text("\n".join(L) + "\n")

# ---------------- _Entidades.md ----------------
L = ["---", "tags:\n  - moc\n  - indice", f"entidades: {len(tablas)}", "---\n",
     "# Índice de entidades\n",
     f"Las **{len(tablas)} tablas** del modelo, agrupadas por módulo. "
     "«Sal.» y «Ent.» son claves foráneas salientes y entrantes.\n",
     "[[Index|← Índice general]] · [[_Relaciones|Relaciones →]]\n"]
for k, d in sorted(mods.items()):
    L.append(f'## {k} — {d["nombre"]}\n')
    L.append(f'> {FOCO[k]} · [[{fichero(k)}|ficha de negocio]] · '
             f'[[_Entidades {k}|índice del módulo]]\n')
    L.append("| Tabla | Columnas | Sal. | Ent. | Notas |")
    L.append("| --- | --: | --: | --: | --- |")
    for alias in d["orden"]:
        t = d["entidades"][alias]["tabla"]
        nc = len(d["entidades"][alias]["cols"])
        flags = []
        if t in APPEND_ONLY:
            flags.append("append-only")
        if grado.get(t, 0) >= 8:
            flags.append("muy conectada")
        L.append(f'| [[{t}]] | {nc} | {len(sal[t])} | {len(ent[t])} | {", ".join(flags) or "—"} |')
    L.append("")
L.append("## Tablas append-only\n")
L.append("No admiten `UPDATE` ni `DELETE`; se corrigen con el movimiento inverso:\n")
L.append(" · ".join(f"[[{t}]]" for t in sorted(APPEND_ONLY) if t in registro) + "\n")
(OUT / "Entidades" / "_Entidades.md").write_text("\n".join(L) + "\n")

# ---------------- _Relaciones.md ----------------
def nom(f):
    return f'{f["origen"]}.{f["columna"]} → {f["destino"]}'

L = ["---", "tags:\n  - moc\n  - indice", f"relaciones_fk: {len(fks)}",
     f"cross_modulo: {cross}", "---\n",
     "# Índice de relaciones (claves foráneas)\n",
     f"Las **{len(fks)} claves foráneas** del modelo. **{cross}** cruzan módulos.\n",
     "[[Index|← Índice general]] · [[_Entidades|Entidades →]]\n",
     "## Referencias que cruzan módulos\n",
     "Son las que acoplan el sistema: conviene revisarlas antes de tocar un módulo.\n",
     "| Origen | Columna | Destino | Módulos | Opcional | Relación |",
     "| --- | --- | --- | :-: | :-: | --- |"]
for f in sorted([f for f in fks if f["modulo_destino"] and f["modulo_destino"] != f["modulo_origen"]],
                key=lambda x: (x["modulo_origen"], x["origen"], x["columna"])):
    L.append(f'| [[{f["origen"]}]] | `{f["columna"]}` | [[{f["destino"]}]] | '
             f'{f["modulo_origen"]} → {f["modulo_destino"]} | '
             f'{"sí" if f["opcional"] else "no"} | [[{nom(f)}\\|ver]] |')
L.append("")
for k, d in sorted(mods.items()):
    propias = sorted([f for f in fks if f["modulo_origen"] == k],
                     key=lambda x: (x["origen"], x["columna"]))
    if not propias:
        continue
    L.append(f'## {k} — {d["nombre"]}\n')
    L.append(f'> [[_Relaciones {k}|índice del módulo]]\n')
    L.append("| Relación | Destino | Cruza | Opcional |")
    L.append("| --- | --- | :-: | :-: |")
    for f in propias:
        cruza = "↗" if f["modulo_destino"] != k else "—"
        L.append(f'| [[{nom(f)}]] | [[{f["destino"]}]] | {cruza} | '
                 f'{"sí" if f["opcional"] else "no"} |')
    L.append("")
(OUT / "Relaciones" / "_Relaciones.md").write_text("\n".join(L) + "\n")

print(f"Index.md, _Entidades.md ({len(tablas)}), _Relaciones.md ({len(fks)}, {cross} cross)")

print(json.dumps(resumen, indent=2, ensure_ascii=False))
