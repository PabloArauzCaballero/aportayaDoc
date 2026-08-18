# AportaYa — Landing pública

Sitio de marca de una sola página. **Sin build**: se abre con doble clic, funciona sin
conexión y no hay `node_modules` que se pudra en seis meses.

```bash
open index.html                 # así, tal cual
python3 -m http.server 8899     # o servido, si querés medir con el navegador
```

## Dónde está publicado

> **https://pabloarauzcaballero.github.io/aportayaDoc/**

Esa es la única URL válida. Anotala: el repositorio se renombró de `aportaya` a
`aportayaDoc`, y **GitHub Pages no redirige las URLs viejas de *project page***. La
anterior devuelve 404 para siempre:

| URL | Resultado |
| --- | :-: |
| `https://pabloarauzcaballero.github.io/aportayaDoc/` | **200** ✅ |
| `https://pabloarauzcaballero.github.io/aportaya/` | 404 — nombre viejo |
| `https://pabloarauzcaballero.github.io/` | 404 — es la página de *usuario*, que no existe |

Como este repo se sirve bajo un subdirectorio (`/aportayaDoc/`), **todas las rutas del
HTML son relativas** (`assets/…`, `verificar/…`). Una ruta absoluta tipo `/assets/…`
apuntaría a la raíz del dominio y daría 404. La excepción son `canonical`, `og:url`,
`og:image` y `twitter:image`, que **deben** ser absolutas porque los rastreadores
sociales no resuelven rutas relativas.

> **Si el repositorio se vuelve a renombrar**, hay que actualizar esas cuatro etiquetas
> en `index.html` y esta sección. Es el único lugar donde el nombre está cableado.

## Despliegue en GitHub Pages

`.github/workflows/pages-landing.yml` publica esta carpeta (y nada más — es el artefacto
completo, no un subdirectorio del sitio) en cada push a `main` **o `dev`** que toque
`landing/`, y también se puede disparar a mano desde la pestaña **Actions**.

Escucha las dos ramas a propósito: `dev` es donde se integra el trabajo de este repo y
`main` va varios merges atrás, así que un workflow que solo mirara `main` no correría nunca.

El paso `configure-pages` corre con `enablement: true`, así que **activa Pages por su
cuenta** usando el token del propio workflow (permiso `pages: write`). No hace falta que
nadie con permiso de administrador entre a Settings a hacer el toggle a mano.

Si por política del repo ese auto-activado fallara, el camino manual es una sola vez:

> Settings → Pages → Build and deployment → Source → **GitHub Actions**

Los enlaces del pie que apuntaban a `../docs/...` y `../README.md` se cambiaron a URLs de
GitHub, porque el artefacto que sube el workflow es solo `landing/` — esos archivos no
existen en el sitio publicado.

## Qué hay acá

```
index.html            ← toda la página
assets/
  css/tokens.css      ← tokens de marca (única fuente de color, espacio, tipo y curvas)
  css/fuentes.css     ← @font-face locales
  css/style.css       ← el sistema: base → átomos → moléculas → organismos
  js/main.js          ← tema, entradas, teléfono ligado al scroll, sección activa
  fonts/*.woff2       ← Poppins e Inter vendorizadas
  img/                ← símbolo, logotipo horizontal, favicon
verificar/            ← las mediciones, no la página (ver § Cómo se verifica)
```

## De dónde sale el diseño

Nada de esto se inventó acá. Todo viene de la bóveda:

| Qué | Fuente de verdad |
| --- | --- |
| Paleta, tipografía, voz, uso del logo | [`docs/Views/AportaYa-Identidad.md`](../docs/Views/AportaYa-Identidad.md) |
| Tokens y componentes | [`docs/Views/Sistema-Diseno/`](../docs/Views/Sistema-Diseno/README.md) |
| Reglas de construcción | skill [`disenar-frontend`](../.claude/skills/disenar-frontend/SKILL.md) |
| Vocabulario (grupo, cupo, turno, período, aporte, entrega) | skill `glosario-dominio` |
| Afirmaciones sobre sorteo y cadena de bloques | skill `sorteo-transparencia` · [CU-73](../docs/CasosDeUso/CU-73%20Verificar%20la%20cadena%20de%20transparencia.md) |

**Regla de oro:** verde `#1C5A3A` = estructura, naranja `#E5852B` = acción. Un solo botón
naranja por pantalla: el del hero y el del cierre, que nunca se ven juntos.

### Un hallazgo que ya se devolvió a la bóveda

Midiendo el contraste sobre el DOM renderizado aparecieron **cuatro pares de la paleta que
no llegan a AA como texto chico**. La landing agregó roles de texto derivados del mismo
matiz (`--accent-texto`, `--ok-texto`, `--brand-texto`, `--aviso-texto`, un `--text-3` más
oscuro), cada uno con su ratio medido anotado en `tokens.css`. El color de marca se sigue
usando en superficies, iconos y logotipo, donde el requisito es otro (3:1) o no aplica.

**Esto ya se corrigió también en `docs/Views/Sistema-Diseno/estilos.css`**, la fuente de
verdad: ver ahí § «Texto: usar los roles `-texto`». La auditoría completa sobre las 5
páginas del catálogo (no solo la landing) encontró además dos bugs de página más serios,
también corregidos ahí — `--brand` usado como fondo sólido detrás de texto blanco
(`.btn-secondary`, `.navbar`, paginador — caía a 3.59:1 en oscuro), y las 5 páginas
renderizando en **quirks mode** por falta de `<!doctype html>`, lo que dejaba el texto de
cualquier tabla en tema oscuro casi invisible (1.08:1) porque `<table>` no hereda `color`
de sus ancestros sin doctype. Ver el README de Sistema-Diseno para el detalle completo.

La única excepción que queda, ahí y acá, es el logotipo: `Ya` en naranja sobre crema da
2.47:1, y WCAG exime explícitamente el texto que forma parte de una marca. Es deliberado.

Cambiar la marca es **cambiar `tokens.css`**, no buscar y reemplazar. Los alpha salen de los
tokens `-rgb` (`rgba(var(--brand-rgb),.12)`), nunca de un color escrito a mano.

## Reemplazar antes de publicar

Todo lo de esta tabla es **placeholder**. Nada de esto está confirmado por nadie.

| Dónde | Qué dice hoy | Qué necesita |
| --- | --- | --- |
| Hero · teléfono y tarjetas flotantes | «Marisol», grupo «Las Comadres», Bs 1.240,00 / 250,00 / 500,00 / 2.500,00. Las dos tarjetas flotantes repiten estos mismos datos de ejemplo («Aporte confirmado», «Cadena íntegra · 12 bloques»), no agregan cifras nuevas | Captura real de la app cuando exista, o consentimiento para usar nombres |
| Transparencia · cadena | Hashes `7f3c…a91d`, `c204…5be8`, `e8a0…31f7` y sus contenidos | Bloques reales de un grupo de prueba |
| Cierre y pie | `hola@aportaya.bo` | Casilla real de atención al cliente |
| Cierre | «te avisamos apenas abramos los primeros grupos» | Confirmar que hay lista de espera y dónde se guarda |
| Preguntas · costo | «comisión pequeña por juego», organizador humano sin comisión | Tarifario aprobado. Es decisión de negocio, no técnica |
| Todo el sitio | No hay analítica ni formulario: los CTA son `mailto:` | Definir formulario y su tratamiento de datos personales |

Las cifras de la sección **«Lo que hay debajo»** (87 casos de uso, 274 tablas, 119
restricciones, 12 módulos) **sí son reales**: salen del [README del
repositorio](../README.md). Si cambia el modelo, cambian acá.

## Decisiones

- **Sin framework.** Es una landing: HTML, CSS y un JS. Subir de nivel solo si aparece estado
  o rutas.
- **Fuentes vendorizadas.** Cero peticiones a `fonts.googleapis.com` en runtime. Se bajan solo
  los subconjuntos `latin` y `latin-ext`; en español la primera carga son ~63 KB de fuente.
  Inter es variable: un archivo cubre 400–600.
- **`backdrop-filter` solo en escritorio con puntero.** Es de lo más caro que hay en scroll:
  en móvil la barra va con fondo sólido y se ve casi igual.
- **Nav en móvil que se desliza, no que desaparece.** Con `display:none` el visitante de
  teléfono se queda sin navegación. Va en scroll horizontal con máscara de degradado, que es
  la pista de que hay más.
- **El teléfono rota con el scroll** con un lerp por cuadro. Se apaga bajo 940px, con la
  pestaña oculta y con `prefers-reduced-motion`.
- **Las entradas se ocultan solo si hay JS** (`html.js`, marcado en el `<head>` antes del
  primer pintado). Sin JS la página se ve entera.
- **`<details>` para las preguntas**, no un acordeón a mano: teclado y lector de pantalla
  vienen gratis.
- **Cada sección de texto tiene un ancla visual propia**, no solo tarjetas con párrafos:
  el flujo conectado de "Cómo funciona" (línea + ícono + insignia de número), el diagrama
  de "Cuentas de participantes / Cuenta de la plataforma" dentro de la tarjeta de custodia,
  el mini-flujo de "Aviso → Descargo → Decisión → Apelación", y las dos tarjetas flotantes
  del hero (que repiten afirmaciones ya dichas en el texto — "cadena íntegra", "aporte
  confirmado" — nunca inventan una cifra nueva). El criterio: si una sección se explica
  solo con prosa, algo se está pidiendo que el lector arme mentalmente y no debería.
- **Los orbes de fondo son degradados radiales estáticos, no `filter:blur()` en vivo.**
  Es la diferencia entre gratis y lo más caro que hay en una página con scroll (ver
  playbook de rendimiento). El grano (`--grano` en `tokens.css`) es la misma idea: una
  textura de una sola vez vía `feTurbulence`, no algo que se recalcula por cuadro.
- **Una franja oscura fija rompe el ritmo crema/blanco a propósito** ("Lo que hay debajo"):
  usa `--tinta-900/800/700`, que no se redefinen por tema — se ve igual en claro y en
  oscuro, como una decisión de composición, no una variante de color.
- **La pantalla del teléfono mide 644px** porque es lo que ocupan sus tres movimientos
  completos. Con 568px la última fila quedaba cortada bajo la tab bar.

## Breakpoints (medidos, no redondos)

| Corte | Por qué |
| --- | --- |
| `940px` | Ancho al que el teléfono del hero (292px + aire) deja de convivir con el texto, y al que una tarjeta de tres columnas baja de ~240px |
| `900px` | Ancho real de la barra con las cinco secciones, el logo, el botón de tema y el CTA |
| `1000px` + `hover:hover` | Umbral a partir del cual se enciende el desenfoque de la barra |
| `600px` / `520px` / `820px` | Colapso a una columna de rejillas y del pie |

## Cómo se verifica

Las páginas de `verificar/` cargan la landing dentro de un iframe: **dentro de un
iframe las media queries evalúan el ancho del iframe**, así que 360px es 360px de verdad
aunque Chrome headless no baje de ~500px de ventana. Ninguna se versiona como parte del
sitio publicado; son herramientas de desarrollo, no páginas de la landing.

| Página | Qué contesta |
| --- | --- |
| `verificar/medir.html` | Bandas vacías >150px, desborde horizontal real y elementos fuera de cuadro, por ancho (`?w=360,390,…`) |
| `verificar/contraste.html` | Contraste de cada nodo con texto, en claro y oscuro, con el mínimo que le toca por tamaño y peso |
| `verificar/red.html` | Peso y número de peticiones de la primera carga |
| `verificar/toma.html` | Captura a un ancho, alto, desplazamiento y tema dados (`?w=&h=&off=&tema=light\|dark`) — para revisar una sección puntual |
| `verificar/altura.html` | Offset en píxeles de cada `<section>`, para saber qué `off=` pasarle a `toma.html` sin adivinar |
| `verificar/og.html` | Fuente única de `assets/img/og.png` (la imagen para redes) — nunca se edita el PNG a mano, se regenera desde acá |

```bash
python3 -m http.server 8899 --directory landing
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
  --headless=new --force-prefers-reduced-motion --virtual-time-budget=8000 \
  --window-size=1500,900 --user-data-dir=/tmp/cr \
  --dump-dom "http://localhost:8899/verificar/medir.html"
```

### Lo medido hoy

| Comprobación | Resultado |
| --- | --- |
| Desborde horizontal a 360 / 390 / 414 / 768 / 1024 / 1440 | Ninguno; `scrollX` sigue en 0 tras `scrollTo(400,0)` |
| Bandas vacías | 0px en todos los anchos medidos |
| Primera carga a 390px | **142.9 KB en 8 peticiones**, contra un presupuesto de 200 KB |
| Contraste, 183 nodos con texto | Todos pasan en claro y en oscuro, salvo el logotipo (exento) |

Dos avisos sobre el método, por si alguien repite las mediciones:

- El medidor de bandas contaba el **alto del iframe**, no el del contenido, y reportaba 3.000
  a 8.000px muertos que no existían. Se mide `body.getBoundingClientRect().height`.
- El medidor de contraste no entendía `color()` ni los degradados: daba 13 falsos positivos.
  Ahora convierte bien `color()` y **saltea lo que va sobre degradado**, que se verifica aparte
  contra los dos extremos (el peor caso de la banda de cierre da 4.55:1).

## Lo que queda abierto

- Sin OG image rasterizada: hoy apunta al SVG del logotipo, y varias redes no lo renderizan.
  Hace falta un PNG de 1200×630 **generado** desde el SVG, no dibujado a mano.
- El logotipo horizontal usa texto vivo: para uso final hay que convertirlo a trazos.
- No se probó en un teléfono real, solo a anchos simulados. Gestos, rendimiento táctil y el
  scroll horizontal del nav en móvil están **sin verificar en hardware**.
- El peso medido es sin compresión (`http.server` no la aplica). En un servidor con gzip, HTML,
  CSS y JS bajan bastante; las fuentes ya están comprimidas.
- Falta la versión en la que el CTA sea un formulario real; hoy es `mailto:`.
