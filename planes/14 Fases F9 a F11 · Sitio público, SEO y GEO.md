---
tags:
  - plan
  - fase
  - frontend
  - seo
  - geo
titulo: "Fases F9 a F11 — Sitio público, SEO y GEO"
fases: [F9, F10, F11]
depende_de: [F0, F1]
habilita: [F12]
---

# Fases F9 a F11 — Sitio público, SEO y GEO

> **Se ejecuta en:** Ola F1 · carril W (F9) y Ola F2 · carriles W1 (F10) y W2 (F11).
> Ver [[16 Carriles de frontend]].

> [!important] Antes de escribir la primera línea
> [[10b Estándar de ejecución del frontend]] aplica en las tres fases. Y acá pesa más
> que en ningún lado la **regla cero**: en un sitio público de finanzas, una
> afirmación inventada no es un bug — es publicidad engañosa.

**Producto:** `apps/web`, Astro 5 + islas React (ADR-018).
**Es la única superficie indexable de AportaYa.**

---

## Las dos reglas que gobiernan estas tres fases

### 1 · El SEO nunca gana sobre la protección de datos

`/verificar/:codigo`, `/publico/grupos/:codigo` y `/publico/sorteos/:id` muestran
información **de una persona o de un grupo concreto**. Son públicas por diseño
—CU-61, CU-73, CU-75 lo exigen— pero **no son indexables**:

- `noindex, nofollow` en la meta **y** en la cabecera `X-Robots-Tag`.
- Excluidas del `sitemap.xml`.
- `Disallow` para **todos** los rastreadores, incluidos los de IA.
- **Sin JSON-LD** con datos de la persona.
- El código no se puede enumerar: `VerificadorPublico` responde igual para un código
  inexistente y uno revocado (CU-75), así que la página **no es un oráculo** para
  descubrir usuarios.

> Indexar un certificado de reputación sería exponer el historial financiero de una
> persona en Google. Viola `R-SEG-03` y la protección de datos. **No se negocia por
> tráfico.**

### 2 · No se publica lo que no es cierto hoy

La licencia está sembrada **`EN_TRAMITE`** a propósito, porque ese es el estado real
mientras no haya resolución de ASFI.

| Nunca, hasta que haya resolución | Sí, siempre |
| --- | --- |
| «Regulados por ASFI» | «Solicitud de licencia en trámite ante ASFI» |
| «Entidad financiera autorizada» | Qué somos hoy, con la fecha de la última actualización |
| Rendimientos, ganancias o promesas de retorno | Cómo funciona el pasanaku, con sus riesgos |
| Reseñas o calificaciones que no existen | Nada. Si no hay reseñas reales, **no hay `AggregateRating`** |

Un `AggregateRating` inventado en JSON-LD es una reseña falsa estructurada y
legible por máquina. Es exactamente lo que este proyecto prohíbe.

---

# FASE F9 — Sitio público: estructura y contenido

> **Objetivo.** Que existan las páginas que la bóveda ya obliga a publicar, con el
> contenido correcto y las rutas de verificación funcionando.

## F9.1 · Mapa del sitio

| Ruta | Qué es | Render | Indexación | Obligación |
| --- | --- | --- | :-: | --- |
| `/` | Qué es AportaYa, para quién, cómo empieza | estático | index | — |
| `/como-funciona` | El pasanaku digital paso a paso | estático | index | — |
| `/seguridad` | Custodia, encaje, qué pasa con tu plata | estático | index | Transparencia |
| `/tarifas` | **Tarifario vigente, con impuestos** | ISR | index | **CU-34**, `R-TAR-08` |
| `/contrato-de-adhesion` | Versión vigente y anteriores, con hash | ISR | index | **CU-05**, `R-CON-06` |
| `/reclamos` | Puntos de reclamo, plazos, segunda instancia ASFI | estático | index | **CU-52/53**, ASFI L4 T1 |
| `/privacidad` | Tratamiento de datos y cómo ejercer derechos | estático | index | **CU-07** |
| `/transparencia` | Cómo se sella y verifica la cadena | estático | index | CU-72/73 |
| `/preguntas` | FAQ | estático | index | — |
| `/legal/estado-regulatorio` | **Estado real de la licencia** | ISR | index | Regla 2 |
| `/descargar` | Enlaces a tiendas | estático | index | — |
| `/verificar/[codigo]` | Certificado de reputación | SSR | **noindex** | CU-75 |
| `/publico/grupos/[codigo]` | Cadena de transparencia | SSR | **noindex** | CU-72/73 |
| `/publico/sorteos/[id]` | Verificación del sorteo, con paquete JSON | SSR | **noindex** | CU-61 |
| `/catalogo` | Catálogo del sistema de diseño | estático | **noindex** | interno |

## F9.2 · Contenido como datos, no como marcado

Los documentos regulatorios van en **content collections** de Astro
(`src/content/legal/`, `src/content/faq/`, `src/content/tarifas/`), en Markdown con
frontmatter validado por esquema:

```yaml
---
titulo: "Contrato de adhesión — cuenta de billetera"
version: "v3"
vigente_desde: 2026-07-01
hash_documento: "sha256:…"      # el mismo que registró CU-05
fuente: "documento_publicado"
actualizado: 2026-08-14
---
```

> **El tarifario y el contrato no se escriben a mano en el sitio.** Se traen del
> backend (`documento_publicado`, `tarifario`), con su hash y su vigencia. Si el sitio
> muestra un tarifario distinto del que cobra la API, eso es un incumplimiento de
> transparencia, no una inconsistencia de contenido.

## F9.3 · Las islas React

Solo cuatro fragmentos necesitan JavaScript. Todo lo demás es HTML estático:

| Isla | Dónde | Por qué |
| --- | --- | --- |
| `VerificadorDeCertificado` | `/verificar/[codigo]` | Consulta la API |
| `VerificadorDeCadena` | `/publico/grupos/[codigo]` | Recomputa hashes en el navegador |
| `VerificadorDeSorteo` | `/publico/sorteos/[id]` | Recomputa el orden con `barajarDeterminista` |
| `SimuladorDeCostos` | `/tarifas` | Cotiza contra CU-30 |

> **Los verificadores recomputan en el cliente, no le creen al servidor.** Ese es el
> punto entero de CU-61 y CU-73: un tercero tiene que poder auditar *sin depender de
> nosotros*. Los átomos `barajarDeterminista`, `verificarCompromiso`,
> `serializarCanonico` y `hashDeBloque` se importan de `plataforma/comun-dominio` — **los
> mismos** que usa el backend.

## Gate de salida F9

- [ ] Gate común de §10 del plan maestro del frontend
- [ ] Las 15 rutas existen con contenido real
- [ ] El tarifario y el contrato se traen del backend, con hash y vigencia visibles
- [ ] Los tres verificadores **recomputan en el cliente** y coinciden con el servidor
- [ ] `/verificar/*` y `/publico/*` responden `noindex` en meta **y** en `X-Robots-Tag`
- [ ] Un código inexistente y uno revocado responden **igual**
- [ ] Ninguna afirmación sobre licencia que no sea cierta hoy

---

# FASE F10 — SEO

> **Objetivo.** Que las páginas indexables tengan metadatos completos, datos
> estructurados válidos y Core Web Vitals en verde — y que las no indexables estén
> selladas.

## F10.1 · Metadatos por página

Un componente `<Meta>` en `src/seo/` recibe los datos y emite todo. **Ninguna página
escribe una etiqueta `<meta>` a mano.**

```html
<title>Tarifas y comisiones · AportaYa</title>          <!-- ≤ 60 caracteres, única -->
<meta name="description" content="El tarifario vigente de AportaYa, con impuestos
  incluidos y la fecha desde la que rige. Consultá cuánto cuesta cada operación
  antes de usarla.">                                     <!-- 140–160, única -->
<link rel="canonical" href="https://aportaya.bo/tarifas">
<link rel="alternate" hreflang="es-BO" href="https://aportaya.bo/tarifas">
<link rel="alternate" hreflang="es"    href="https://aportaya.bo/tarifas">
<link rel="alternate" hreflang="x-default" href="https://aportaya.bo/tarifas">
<meta name="robots" content="index, follow, max-snippet:-1, max-image-preview:large">

<meta property="og:type"        content="website">
<meta property="og:site_name"   content="AportaYa">
<meta property="og:locale"      content="es_BO">
<meta property="og:title"       content="Tarifas y comisiones · AportaYa">
<meta property="og:description" content="…">
<meta property="og:url"         content="https://aportaya.bo/tarifas">
<meta property="og:image"       content="https://aportaya.bo/og/tarifas.png"><!-- 1200×630 -->
<meta property="og:image:alt"   content="Tabla de tarifas de AportaYa">
<meta name="twitter:card"       content="summary_large_image">

<meta name="theme-color" content="#1C5A3A">
```

| Elemento | Regla |
| --- | --- |
| `title` | Único, ≤ 60 caracteres, patrón `<Página> · AportaYa` |
| `description` | Única, 140–160, **describe la página**, no la empresa |
| `canonical` | **Siempre**, absoluta, incluso en la home |
| `hreflang` | `es-BO` primario, `es` de respaldo, `x-default` |
| `og:image` | 1200×630, generada por página, con `alt` |
| `theme-color` | `--g600 #1C5A3A` |
| Idioma | `<html lang="es-BO">` |

## F10.2 · Datos estructurados (JSON-LD)

### Organización — en todas las páginas

```jsonc
{
  "@context": "https://schema.org",
  "@type": "Organization",
  "@id": "https://aportaya.bo/#organizacion",
  "name": "AportaYa",
  "url": "https://aportaya.bo",
  "logo": "https://aportaya.bo/logo.png",
  "description": "Billetera móvil que digitaliza el pasanaku boliviano: ahorro rotativo comunitario con custodia y trazabilidad.",
  "address": { "@type": "PostalAddress", "addressCountry": "BO", "addressLocality": "…" },
  "areaServed": { "@type": "Country", "name": "Bolivia" },
  "contactPoint": [{
    "@type": "ContactPoint",
    "contactType": "customer support",
    "availableLanguage": "es",
    "areaServed": "BO",
    "url": "https://aportaya.bo/reclamos"
  }],
  "sameAs": ["…"]
}
```

> **`FinancialService` en vez de `Organization` recién cuando haya licencia.** El tipo
> declara una actividad regulada; usarlo con la licencia `EN_TRAMITE` es afirmar algo
> que no es cierto (regla 2). El cambio de tipo es parte del checklist del día que
> ASFI resuelva.

### `WebSite` con búsqueda — solo en la home

```jsonc
{ "@type": "WebSite", "@id": "https://aportaya.bo/#sitio",
  "name": "AportaYa", "inLanguage": "es-BO",
  "publisher": { "@id": "https://aportaya.bo/#organizacion" },
  "potentialAction": { "@type": "SearchAction",
    "target": "https://aportaya.bo/buscar?q={search_term_string}",
    "query-input": "required name=search_term_string" } }
```

### Tarifas — el que más rinde

```jsonc
{ "@type": "FinancialProduct",
  "name": "Cuenta de billetera AportaYa",
  "provider": { "@id": "https://aportaya.bo/#organizacion" },
  "feesAndCommissionsSpecification": "https://aportaya.bo/tarifas",
  "areaServed": { "@type": "Country", "name": "Bolivia" },
  "dateModified": "2026-08-14" }
```

Cumple transparencia ASFI y posiciona la página de tarifas a la vez.

### Otros, donde correspondan

| Tipo | Página | Nota |
| --- | --- | --- |
| `FAQPage` | `/preguntas` | Preguntas **reales**, con la respuesta completa en el HTML visible |
| `HowTo` | `/como-funciona` | Pasos del pasanaku, con `HowToStep` |
| `BreadcrumbList` | todas menos la home | Coincide con la navegación visible |
| `WebPage` + `speakable` | `/como-funciona`, `/preguntas` | `cssSelector` de los párrafos de respuesta |
| `Article` | contenido educativo | `author`, `publisher`, `datePublished`, `dateModified` |

**Prohibido:** `Review`, `AggregateRating`, `Offer` con precios inventados, `HowTo`
con pasos que la app no hace. Todo JSON-LD tiene que describir **lo que la página
muestra**; si no coincide, es *structured data spam* y se penaliza.

## F10.3 · `sitemap.xml` y `robots.txt`

- Generados por Astro. **Solo las páginas `index`.**
- `lastmod` real, tomado del `actualizado` del contenido, no de la fecha del build.
- Nunca `/verificar/`, `/publico/`, `/catalogo`.
- `robots.txt` con la política de IA de la Fase F11 y la referencia al sitemap.

## F10.4 · Core Web Vitals — señal directa de posicionamiento

| Métrica | Objetivo | Cómo se logra en Astro |
| --- | :-: | --- |
| **LCP** | < 2.0 s | HTML estático, sin JS bloqueante, imagen del héroe con `priority` |
| **INP** | < 200 ms | Solo cuatro islas hidratadas; el resto es HTML |
| **CLS** | < 0.05 | Dimensiones explícitas en imágenes y fuentes con `size-adjust` |
| Peso de JS | < 50 KB en páginas de contenido | `client:visible`, nunca `client:load` sin motivo |

Medido con Lighthouse CI en cada PR, **bloqueante**.

## F10.5 · YMYL y E-E-A-T

Un sitio de dinero está en la categoría de máximo escrutinio (*Your Money, Your
Life*). Lo que hay que exhibir, y que además es obligación regulatoria:

- Identidad legal completa, NIT y **dirección física** en el pie.
- **Estado regulatorio real**, con fecha (`/legal/estado-regulatorio`).
- Punto de reclamo con canal, plazo y segunda instancia.
- Política de privacidad y contrato de adhesión, **con versión y hash**.
- `dateModified` **visible** en cada página de contenido, no solo en el JSON-LD.
- Cada afirmación regulatoria **con su norma citada** — la bóveda ya lo hace en
  `docs/Cumplimiento.md`; se trae al sitio.

## Gate de salida F10

```bash
yarn build:web && yarn seo:validar     # metadatos, canonical, hreflang, JSON-LD
npx lighthouse-ci autorun              # CWV bloqueante
```

- [ ] Gate común
- [ ] `title`, `description` y `canonical` **únicos** en cada página indexable
- [ ] JSON-LD válido en el validador de Schema.org, **sin errores ni advertencias**
- [ ] Cero `Review`/`AggregateRating`; cero `FinancialService` mientras no haya licencia
- [ ] `sitemap.xml` **sin** rutas `noindex`; `lastmod` real
- [ ] CWV en verde en las páginas de contenido
- [ ] JS < 50 KB en páginas de contenido
- [ ] Pie con identidad legal, NIT, dirección y estado regulatorio
- [ ] `X-Robots-Tag: noindex` verificado con `curl` en `/verificar/`, `/publico/`, `/catalogo`

---

# FASE F11 — GEO · optimización para motores generativos

> **Objetivo.** Que cuando alguien le pregunte a ChatGPT, Claude, Perplexity o Gemini
> *«cómo funciona un pasanaku digital en Bolivia»* o *«qué cobra AportaYa»*, la
> respuesta salga **de nuestro contenido, citada y correcta**.

El SEO optimiza para que **la persona haga clic**. El GEO optimiza para que **la
máquina entienda, extraiga y cite**. Se parecen en la base y difieren en lo que
importa: al motor generativo no le sirve una página bonita — le sirve una afirmación
clara, fechada, atribuible y fácil de extraer.

## F11.1 · Política de rastreadores (ADR-019)

**Decisión: búsqueda sí, entrenamiento no.**

`public/robots.txt`:

```
# Buscadores clásicos
User-agent: Googlebot
User-agent: Bingbot
Allow: /

# Motores generativos con cita — SÍ: queremos aparecer en sus respuestas
User-agent: OAI-SearchBot
User-agent: ChatGPT-User
User-agent: ClaudeBot
User-agent: Claude-User
User-agent: PerplexityBot
User-agent: Google-CloudVertexBot
Allow: /

# Entrenamiento de modelos — NO
User-agent: GPTBot
User-agent: Google-Extended
User-agent: Applebot-Extended
User-agent: CCBot
User-agent: Bytespider
User-agent: meta-externalagent
Disallow: /

# Datos de terceros: bloqueados para TODOS, sin excepción
User-agent: *
Disallow: /verificar/
Disallow: /publico/
Disallow: /catalogo
Disallow: /api/

Sitemap: https://aportaya.bo/sitemap.xml
```

| Bot | Para qué | Decisión |
| --- | --- | :-: |
| `OAI-SearchBot` | Indexa para respuestas de ChatGPT **con enlace** | ✅ |
| `GPTBot` | **Entrenamiento** de modelos de OpenAI | ⛔ |
| `ChatGPT-User` | Visita en vivo cuando un usuario pide una URL | ✅ |
| `ClaudeBot` | Rastreo para respuestas de Claude | ✅ |
| `PerplexityBot` | Índice de Perplexity, cita con fuente | ✅ |
| `Google-Extended` | Entrenamiento y *grounding* de Gemini | ⛔ |
| `Applebot-Extended` | Entrenamiento de Apple Intelligence | ⛔ |
| `CCBot` | Common Crawl → corpus de entrenamiento de terceros | ⛔ |

> **Bloquear `Google-Extended` no afecta el posicionamiento en Google.** Controla
> entrenamiento y *grounding* de Gemini, no la indexación de la Búsqueda — esa la
> maneja `Googlebot`, que sigue permitido. Es la confusión más común al escribir este
> archivo.

## F11.2 · `llms.txt` — el índice para modelos

En la raíz. Markdown, corto, con enlaces y una línea de qué es cada cosa:

```markdown
# AportaYa

> Billetera móvil boliviana que digitaliza el pasanaku: ahorro rotativo comunitario
> donde un grupo aporta por períodos y cada participante recibe el fondo en su turno.
> Opera en Bolivia, en bolivianos (Bs). Estado regulatorio: solicitud de licencia en
> trámite ante ASFI (actualizado 2026-08-14).

## Qué es

- [Cómo funciona](https://aportaya.bo/como-funciona.md): el pasanaku digital paso a paso, con el rol del organizador y el fondo de garantía.
- [Preguntas frecuentes](https://aportaya.bo/preguntas.md): respuestas directas sobre turnos, aportes, retiros y qué pasa si alguien no paga.

## Dinero y costos

- [Tarifas](https://aportaya.bo/tarifas.md): tarifario vigente con impuestos incluidos y fecha desde la que rige.
- [Seguridad y custodia](https://aportaya.bo/seguridad.md): dónde está el dinero, qué es el encaje y cómo se concilia.

## Derechos y reclamos

- [Reclamos](https://aportaya.bo/reclamos.md): canales, plazo de 5 días hábiles, prórroga y segunda instancia ante ASFI.
- [Privacidad](https://aportaya.bo/privacidad.md): tratamiento de datos y cómo ejercer derechos.
- [Contrato de adhesión](https://aportaya.bo/contrato-de-adhesion.md): versión vigente con su hash.

## Estado regulatorio

- [Estado regulatorio](https://aportaya.bo/legal/estado-regulatorio.md): qué autorización tenemos hoy y cuál está en trámite.

## Opcional

- [Transparencia](https://aportaya.bo/transparencia.md): cómo se sella y verifica la cadena de bloques del grupo.
```

Y `llms-full.txt`: el contenido completo de las páginas indexables concatenado en
Markdown, para el modelo que quiere todo de una.

**Ambos se generan en el build**, desde las mismas *content collections*. Escritos a
mano se desactualizan en la segunda semana.

## F11.3 · Espejo Markdown de cada página

Cada página indexable se publica también como `.md`:

```
/tarifas        → text/html
/tarifas.md     → text/markdown   ← el mismo contenido, sin navegación ni ruido
```

Y se declara en el `<head>`:

```html
<link rel="alternate" type="text/markdown" href="https://aportaya.bo/tarifas.md">
```

> **Un modelo que recibe Markdown limpio extrae mejor que uno que tiene que atravesar
> `<div>` anidados, menús y banners.** En Astro sale gratis: el contenido ya vive en
> Markdown; el `.md` es la fuente sin la capa de presentación.

## F11.4 · Cómo se escribe para que una IA cite bien

| Técnica | Cómo se ve | Por qué |
| --- | --- | --- |
| **Respuesta primero** | El primer párrafo responde la pregunta del encabezado. El contexto va después | El modelo extrae los primeros tokens de la sección |
| **Definición canónica en una frase** | «Un pasanaku es un sistema de ahorro rotativo donde un grupo aporta por períodos y cada integrante recibe el total en su turno.» | Es la frase que va a ser citada |
| **Encabezados que son preguntas reales** | `## ¿Qué pasa si alguien no paga su aporte?` | Coincide con cómo se pregunta |
| **Datos en tabla** | Tarifas, plazos, límites | Estructura extraíble sin ambigüedad |
| **Cifra con fuente y fecha** | «Bs 500 (tarifario v3, vigente desde 2026-07-01)» | Sin fecha, el modelo no sabe si sigue vigente |
| **Norma citada** | «5 días hábiles (ASFI, Libro 4, Título I)» | La bóveda ya lo hace: se trae tal cual |
| **Entidades consistentes** | Siempre «AportaYa», «pasanaku», «ASFI», «Bolivia» | La variación confunde la resolución de entidades |
| **Sin ambigüedad de sujeto** | «AportaYa cobra…», no «nosotros cobramos…» | Un fragmento extraído pierde el antecedente |
| **`dateModified` visible** | En la página, no solo en el JSON-LD | Los motores priorizan lo fresco |

**Lo que arruina el GEO:** contenido detrás de JavaScript (el rastreador no lo ve),
información clave solo en una imagen, respuestas que empiezan con tres párrafos de
preámbulo, y cifras sin fecha.

## F11.5 · Las diez preguntas objetivo

Se escriben antes de escribir el contenido, y cada una tiene su sección que la
responde en la primera oración:

1. ¿Qué es un pasanaku?
2. ¿Cómo funciona un pasanaku digital?
3. ¿Qué es AportaYa?
4. ¿Cuánto cobra AportaYa?
5. ¿Es seguro? ¿Dónde está mi dinero?
6. ¿Qué pasa si alguien del grupo no paga?
7. ¿Puedo retirar mi dinero cuando quiera?
8. ¿AportaYa está regulada en Bolivia?
9. ¿Cómo hago un reclamo?
10. ¿Cómo sé que el sorteo de turnos no está arreglado?

## F11.6 · Medición

El GEO no tiene *Search Console*. Se mide preguntando:

- Las **diez preguntas**, una vez por mes, en ChatGPT, Claude, Perplexity y Gemini.
- Se registra: ¿aparece AportaYa? ¿está citada con enlace? ¿la información es
  **correcta**?
- Resultado en `planes/informes/carril-W2.md`, con fecha.
- **Una respuesta incorrecta sobre nosotros es un defecto de contenido**, no mala
  suerte: significa que la página que debía responderla no es extraíble o no existe.

## Gate de salida F11

```bash
yarn build:web
curl -s https://…/robots.txt | grep -c "GPTBot"      # política aplicada
curl -sI https://…/verificar/x | grep X-Robots-Tag   # noindex
curl -s https://…/tarifas.md | head                  # espejo markdown
```

- [ ] Gate común
- [ ] `robots.txt` refleja ADR-019, con los ocho bots nombrados explícitamente
- [ ] `/verificar/`, `/publico/`, `/catalogo` y `/api/` bloqueados para **todos**
- [ ] `llms.txt` y `llms-full.txt` **generados en el build**, no escritos a mano
- [ ] Cada página indexable tiene su `.md` y su `<link rel="alternate">`
- [ ] Las diez preguntas tienen su sección, respondida **en la primera oración**
- [ ] Toda cifra publicada lleva **fuente y fecha**
- [ ] `dateModified` visible en la página, no solo en el JSON-LD
- [ ] Primera medición de las diez preguntas registrada, con fecha

## Ver también

[[00c Recetario · implementar un caso de uso]] · [[16 Carriles de frontend]] · [[10 Plan maestro del frontend]] · [[10b Estándar de ejecución del frontend]] · [[15 Fase F12 · Endurecimiento, E2E y publicación]] · [[Cumplimiento]] · [[AportaYa-Identidad]]
