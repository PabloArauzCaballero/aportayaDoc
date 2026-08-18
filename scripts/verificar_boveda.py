#!/usr/bin/env python3
"""
Verifica la coherencia de la boveda: casos de uso, modelo, restricciones e indices.

    python3 scripts/verificar_boveda.py     (desde la raiz del repositorio)

Devuelve 1 si algo falla, para poder usarlo como gate del CI. No toca ningun
archivo: solo lee y compara. Complementa a generar_boveda.py (que valida el
modelo contra los .puml) y a la prueba de humo (que valida la base real).
"""
import re, pathlib, collections, sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from modelo import (ESQUEMA, ESQUEMA_CATALOGO, ESQUEMA_COMUN,  # noqa: E402
                    LIBRO_CONTABLE, COMPARTIDAS_ESCRITURA, esquema_de, rol_de, cargar)

R = pathlib.Path(__file__).resolve().parent.parent
CU = R / 'docs/CasosDeUso'
fallas = []

def check(cond, msg):
    print(('  OK    · ' if cond else '  FALLA · ') + msg)
    if not cond:
        fallas.append(msg)

SEC = ['## Actores y disparador', '## Precondiciones', '## Flujo principal',
       '## Flujos alternativos', '## Postcondiciones', '## Contrato',
       '## Descomposición atómica', '## Eventos, trabajos y permisos', '## Interfaz',
       '## Restricciones aplicables', '## Evidencia que deja',
       '## Criterios de aceptación', '## Ver también']

casos = sorted(CU.glob('CU-*.md'))
print('\n=== CASOS DE USO ===')
check(len(casos) > 0, f'{len(casos)} casos de uso encontrados')


def cu_num(p):
    """Número de CU como string, sin asumir ancho fijo (soporta CU-99 y CU-100)."""
    return re.match(r'CU-(\d+)', p.stem).group(1)


def cu_label(p):
    return 'CU-' + cu_num(p)


sin_seccion = [cu_label(p) for p in casos if any(s not in p.read_text() for s in SEC)]
check(not sin_seccion, f'todos con las 13 secciones de la plantilla {sin_seccion or ""}')

sin_tabla, mal_num, sin_fila = [], [], []
for p in casos:
    t = p.read_text(); nn = cu_num(p)
    zod = re.findall(r"(\w+):\s*'AP-CU(\d+)-(\d+)'", t)
    filas = set(re.findall(r'^\|\s*`([A-Z0-9_]+)`\s*\|', t, re.M))
    if zod and '| Error | Cuándo se devuelve |' not in t:
        sin_tabla.append(cu_label(p))
    if [int(c) for _, _, c in zod] != list(range(1, len(zod) + 1)):
        mal_num.append(cu_label(p))
    for n, cc, _ in zod:
        if n not in filas or cc != nn:
            sin_fila.append(f'{cu_label(p)}:{n}')
check(not sin_tabla, f'todos con tabla de errores {sin_tabla or ""}')
check(not mal_num, f'códigos de error correlativos {mal_num or ""}')
check(not sin_fila, f'cada código con su fila y su CU {sin_fila[:5] or ""}')

pocos_g = [cu_label(p) for p in casos
           if len(re.findall(r'^\s*Dad[oa]s?\b', re.search(r'```gherkin(.*?)```', p.read_text(), re.S).group(1), re.M)) < 3]
check(not pocos_g, f'≥3 escenarios Gherkin por caso {pocos_g or ""}')

pocos_a = []
for p in casos:
    sec = p.read_text().split('## Flujos alternativos')[-1].split('## Postcondiciones')[0]
    if len([l for l in sec.splitlines() if l.startswith('|') and '---' not in l]) - 1 < 4:
        pocos_a.append(cu_label(p))
check(not pocos_a, f'≥4 flujos alternativos por caso {pocos_a or ""}')

ref = {}
for p in casos:
    ref[cu_label(p)] = set(re.findall(r'\[\[(CU-\d+)[^\]]*\]\]', p.read_text().split('## Ver también')[-1]))
no_rec = [f'{a}→{b}' for a, s in ref.items() for b in s if b in ref and a not in ref[b]]
check(not no_rec, f'"Ver también" recíproco {no_rec[:5] or ""}')

print('\n=== COBERTURA DEL MODELO ===')
ents = {p.stem for p in (R / 'docs/Modelos/Entidades').rglob('*.md') if not p.stem.startswith('_')}
usadas = set()
for p in casos:
    usadas |= {m.strip().rstrip('\\') for m in re.findall(r'\[\[([^\]|#]+)', p.read_text())}
huerfanas = sorted(ents - usadas)
check(len(ents) > 0, f'{len(ents)} entidades en el modelo')
check(not huerfanas, f'toda entidad tiene al menos un caso de uso {huerfanas[:5] or ""}')

print('\n=== RESTRICCIONES ===')
rdef = set(re.findall(r'R-[A-Z]{3}-\d{2}', (R / 'docs/Restricciones.md').read_text()))
rcit = set()
for p in casos:
    rcit |= set(re.findall(r'R-[A-Z]{3}-\d{2}', p.read_text()))
check(len(rdef) > 0, f'{len(rdef)} restricciones definidas')
check(not (rcit - rdef), f'toda restricción citada existe {sorted(rcit - rdef) or ""}')
check(not (rdef - rcit), f'toda restricción está citada por un caso {sorted(rdef - rcit) or ""}')

print('\n=== ÍNDICES ===')
idx = (CU / '_CasosDeUso.md').read_text()
enl = set(re.findall(r'\[\[(CU-\d+[^\]]*)\]\]', idx))
arch = {p.stem for p in casos}
check(enl == arch, f'índice de casos completo (sobran {sorted(enl - arch)[:3]}, faltan {sorted(arch - enl)[:3]})')
check(f'total_casos: {len(casos)}' in idx, f'total_casos: {len(casos)} en el frontmatter')

sk = (R / '.claude/skills/README.md').read_text()
listadas = set(re.findall(r'^\| `([a-z0-9-]+)`', sk, re.M))
carpetas = {p.name for p in (R / '.claude/skills').iterdir() if p.is_dir()}
check(listadas == carpetas, f'índice de skills completo ({len(carpetas)} skills)')

nombres_ok = all(re.match(r'---\nname: ' + re.escape(p.parent.name) + r'\n', (p).read_text())
                 for p in (R / '.claude/skills').glob('*/SKILL.md'))
check(nombres_ok, 'frontmatter de cada skill coincide con su carpeta')

print('\n=== ARQUITECTURA ===')
SEC_ADR = ['## Contexto', '## Decisión', '## Motivo', '## Alternativas descartadas',
           '## Consecuencias', '## Cómo se verifica']
adrs = sorted((R / 'docs/Arquitectura').glob('ADR-*.md'))
arq = (R / 'docs/Arquitectura/_Arquitectura.md').read_text()
check(len(adrs) > 0, f'{len(adrs)} decisiones registradas')

sin_idx = [p.stem[:7] for p in adrs if p.stem not in arq]
check(not sin_idx, f'toda decisión está en el índice {sin_idx or ""}')

sin_sec = [p.stem[:7] for p in adrs if any(s not in p.read_text() for s in SEC_ADR)]
check(not sin_sec, f'toda decisión tiene las 6 secciones obligatorias {sin_sec or ""}')

sin_estado = [p.stem[:7] for p in adrs
              if not re.search(r'^estado: (aceptada|rechazada|superada por ADR-\d+)$',
                               p.read_text(), re.M)]
check(not sin_estado, f'toda decisión declara estado válido {sin_estado or ""}')

nums = [p.stem[4:7] for p in adrs]
check(len(nums) == len(set(nums)), f'sin números de ADR repetidos {sorted({n for n in nums if nums.count(n) > 1}) or ""}')


print('\n=== ESQUEMAS POR SERVICIO ===')
# ADR-017: un esquema y un rol por servicio. La frontera es el GRANT, y el SQL
# generado tiene que reflejarla: una tabla sin esquema es una tabla que cualquiera
# escribe.
SQL_T = R / 'sql/10_tablas'
if SQL_T.exists():
    ubic = {}
    for f in SQL_T.rglob('*.sql'):
        for esq, tab in re.findall(r'CREATE TABLE IF NOT EXISTS (\w+)\.(\w+) \(', f.read_text()):
            ubic[tab] = esq
    validos = set(ESQUEMA.values()) | {ESQUEMA_CATALOGO, ESQUEMA_COMUN}

    total_modelo = sum(len(re.findall(r'^entity ', q.read_text(), re.M))
                       for q in (R / 'docs/entidades').glob('*.puml'))
    check(len(ubic) == total_modelo,
          f'{len(ubic)} tablas con esquema asignado (modelo: {total_modelo})')
    malos = sorted(t for t, e in ubic.items() if e not in validos)
    check(not malos, f'todo esquema es uno de los declarados {malos[:5] or ""}')

    # el libro contable no se parte: es lo que sostiene la partida doble
    libro = sorted(t for t in LIBRO_CONTABLE if ubic.get(t) != ESQUEMA['10'])
    check(not libro, f'el libro contable entero en {ESQUEMA["10"]} {libro or ""}')
    check(ubic.get('movimiento_billetera') == ESQUEMA['10'],
          'movimiento_billetera con el libro: partida doble en una transaccion')

    # las bitacoras transversales van al esquema comun, no al de un servicio
    rastro = sorted(t for t in COMPARTIDAS_ESCRITURA if ubic.get(t) != ESQUEMA_COMUN)
    check(not rastro, f'bitacoras en el esquema {ESQUEMA_COMUN} {rastro or ""}')

    # ADR-027: el outbox NO esta en comun; es infraestructura por esquema
    check('evento_dominio' not in COMPARTIDAS_ESCRITURA,
          'el outbox salio de comun (ADR-027)')
    infra = (R / 'sql/15_infra/mensajeria.sql')
    infra_txt = infra.read_text() if infra.exists() else ''
    from modelo import esquemas_de_servicio, ESQUEMAS_ORQUESTADORES
    faltan_outbox = [e for e in esquemas_de_servicio()
                     if f'{e}.evento_dominio' not in infra_txt]
    check(not faltan_outbox,
          f'outbox por esquema en 15_infra {faltan_outbox[:3] or ""}')
    faltan_saga = [e for e in ESQUEMAS_ORQUESTADORES
                   if f'{e}.estado_saga' not in infra_txt]
    check(not faltan_saga,
          f'estado_saga en los esquemas orquestadores {faltan_saga or ""}')

    # el modelo manda: cada tabla donde su .puml dice, salvo las excepciones
    mods_, _, _ = cargar()
    desviadas = []
    for k, d in mods_.items():
        for alias in d['orden']:
            tab = d['entidades'][alias]['tabla']
            esperado = esquema_de(tab, k)
            if ubic.get(tab) != esperado:
                desviadas.append(f'{tab}: {ubic.get(tab)} != {esperado}')
    check(not desviadas, f'el esquema de cada tabla coincide con su modulo {desviadas[:3] or ""}')

    # claves foraneas cruzadas: son las que se habrian perdido con base por servicio
    cruz = 0
    for f in (R / 'sql/20_claves').glob('*.sql'):
        for m in re.finditer(r'ALTER TABLE (\w+)\.\w+\n\s+ADD CONSTRAINT \S+\n'
                             r'\s+FOREIGN KEY \(\w+\) REFERENCES (\w+)\.', f.read_text()):
            if m.group(1) != m.group(2):
                cruz += 1
    check(cruz > 0, f'{cruz} claves foraneas cruzan esquemas y las verifica el motor')

    # permisos
    perm = (R / 'sql/00_base/03_permisos.sql')
    esqf = (R / 'sql/00_base/02_esquemas.sql')
    if perm.exists() and esqf.exists():
        pt, et = perm.read_text(), esqf.read_text()
        check(len(re.findall(r'CREATE ROLE svc_', et)) == len(set(ESQUEMA.values())),
              f'{len(set(ESQUEMA.values()))} roles de servicio creados')
        sin_grant = [e for e in sorted(set(ESQUEMA.values()))
                     if f'GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA {e} TO {rol_de(e)};' not in pt]
        check(not sin_grant, f'cada rol con permiso sobre su esquema {sin_grant or ""}')
        fuga = [e for e in sorted(set(ESQUEMA.values()))
                if f'SELECT ON ALL TABLES IN SCHEMA {ESQUEMA_COMUN} TO {rol_de(e)}' in pt]
        check(not fuga, f'ningun servicio LEE el rastro ajeno {fuga or ""}')
    else:
        check(False, 'sql/00_base/02_esquemas.sql y 03_permisos.sql existen')
else:
    check(False, 'sql/10_tablas existe (correr generar_ddl.py)')

print('\n=== ENLACES ===')
# La boveda de Obsidian tiene su raiz en docs/. Un wikilink resuelve por nombre de
# nota o por ruta parcial desde esa raiz. Los que van dentro de `backticks` son
# ejemplos citados, no enlaces, y no se cuentan.
notas = {p.stem for p in (R / 'docs').rglob('*.md')}
rutas = {str(p.relative_to(R / 'docs').with_suffix('')) for p in (R / 'docs').rglob('*.md')}

def enlaces_de(texto):
    sin_codigo = re.sub(r'```.*?```', '', texto, flags=re.S)   # bloques de codigo
    sin_codigo = re.sub(r'`[^`\n]*`', '', sin_codigo)          # codigo en linea
    return [m.strip().rstrip('\\') for m in re.findall(r'\[\[([^\]|#]+)', sin_codigo)]

rotos = []
for p in sorted((R / 'docs').rglob('*.md')):
    for enlace in enlaces_de(p.read_text()):
        if enlace in notas:
            continue
        if any(r == enlace or r.endswith('/' + enlace) for r in rutas):
            continue
        rotos.append(f'{p.relative_to(R)} → {enlace}')
check(not rotos, f'ningún wikilink roto en la bóveda {rotos[:5] or ""}')

# Las skills viven fuera de la boveda: solo pueden enlazar notas de docs/.
# Una skill se referencia con `backticks`, nunca con [[wikilink]].
rotos_sk = []
for p in sorted((R / '.claude/skills').glob('*/SKILL.md')):
    for enlace in enlaces_de(p.read_text()):
        if enlace in notas or any(r.endswith('/' + enlace) for r in rutas):
            continue
        rotos_sk.append(f'{p.parent.name} → {enlace}')
check(not rotos_sk, f'ningún wikilink roto en las skills {rotos_sk[:5] or ""}')

print('\n=== CIFRAS CITADAS ===')
# Un numero escrito a mano en un plan diverge del modelo en cuanto el modelo crece.
# Aca se recalcula la cifra real y se busca cualquier documento que cite otra.
# Regla: si una cifra del modelo aparece en prosa, tiene que ser la vigente.

pumls = sorted((R / 'docs/entidades').glob('*.puml'))
CIFRAS = {
    'casos de uso': len(casos),
    'tablas':       sum(len(re.findall(r'^entity ', p.read_text(), re.M)) for p in pumls),
    'restricciones': len(set(re.findall(r'\bR-[A-Z]{2,4}-\d{2}\b',
                                        (R / 'docs/Restricciones.md').read_text()))),
    'ADR':          len(list((R / 'docs/Arquitectura').glob('ADR-*.md'))),
}
# Sinonimos con los que cada cifra aparece en la prosa de los planes.
TERMINOS = {
    'casos de uso':  [r'casos de uso', r'CU\b'],
    'tablas':        [r'tablas', r'entidades'],
    'restricciones': [r'restricciones'],
    'ADR':           [r'ADR'],
}
# El plan 20 (saneamiento) queda fuera: es un meta-documento que CITA a proposito
# las cifras viejas como ejemplo de lo que hay que corregir (su §7.2). Escanearlo
# marcaria como desfasado justo el texto que documenta el desfase.
docs_prosa = ([p for p in sorted((R / 'planes').glob('*.md')) if not p.name.startswith('20 ')]
              + [R / 'README.md', R / 'docs/Index.md'])
desfasadas = []
for p in docs_prosa:
    if not p.exists():
        continue
    # Una cifra en formato de codigo es una cita literal, no una afirmacion:
    # asi se puede escribir "decia `87 casos de uso`" sin que el gate falle.
    texto = re.sub(r'```.*?```', '', p.read_text(), flags=re.S)
    texto = re.sub(r'`[^`\n]*`', '', texto)
    for cifra, real in CIFRAS.items():
        for termino in TERMINOS[cifra]:
            patron = r'\b(\d{2,4})\s+(?:\*\*)?' + termino + r'(?:\*\*)?(.{0,20})'
            for n, cola in re.findall(patron, texto):
                # Solo cifras que pretenden ser el TOTAL. Quedan fuera:
                #  - una cuenta por modulo ("47 entidades"): por debajo de la mitad;
                #  - un subconjunto declarado ("87 casos de uso del nucleo").
                if 'núcleo' in cola or 'nucleo' in cola:
                    continue
                if int(n) != real and int(n) > real / 2:
                    desfasadas.append(f'{p.relative_to(R)}: dice {n} {cifra}, son {real}')
check(not desfasadas,
      'ninguna cifra desfasada en los planes ' + str(sorted(set(desfasadas))[:6] or ''))
print('          cifras vigentes: ' +
      ' · '.join(f'{v} {k}' for k, v in CIFRAS.items()))

print(f'\n{"TODO OK" if not fallas else str(len(fallas)) + " FALLAS"}')
sys.exit(1 if fallas else 0)
