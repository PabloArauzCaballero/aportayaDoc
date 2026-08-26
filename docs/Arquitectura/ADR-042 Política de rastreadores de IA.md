---
tags:
  - arquitectura
  - adr
  - frontend
  - producto
titulo: "ADR-042 — Política de rastreadores de IA: búsqueda sí, entrenamiento no"
estado: aceptada
fecha: 2026-08-26
---

# ADR-042 — Política de rastreadores de IA

> Decide qué agentes automáticos pueden leer el sitio público y para qué. Es una
> **decisión de negocio**, no técnica, y por eso lleva ADR en vez de quedar en la
> cabeza de quien escribe el `robots.txt`.

## Contexto

El sitio público de [[ADR-041 Sitio público · el tercer producto]] existe para que la
gente encuentre AportaYa y para que un tercero pueda verificar sin cuenta. Los dos
objetivos empujan a dejar entrar a los rastreadores.

Pero «rastreador» dejó de ser una sola cosa. Hoy conviven dos familias con el mismo
mecanismo y propósitos distintos:

| Familia | Qué hace | Nos conviene |
| --- | --- | --- |
| **Búsqueda** — `Googlebot`, `Bingbot`, `OAI-SearchBot`, `ClaudeBot`, `PerplexityBot` | Indexa para responder una consulta y **enlaza de vuelta** | Sí: es cómo nos encuentran |
| **Entrenamiento** — `GPTBot`, `Google-Extended`, `Applebot-Extended`, `CCBot` | Recolecta texto para entrenar un modelo. No enlaza y no devuelve nada | No, sin acuerdo |

Y hay una tercera categoría que no es ninguna de las dos: `/verificar/` y `/publico/`
son rutas **sin sesión pero con datos de personas** —un certificado de reputación, el
bloque de un grupo—. Que se puedan consultar no significa que se puedan indexar.

## Decisión

**Búsqueda sí, entrenamiento no. Y las rutas con datos de personas, para nadie.**

1. Los agentes de **búsqueda** listados pasan (`Allow: /`).
2. Los agentes de **entrenamiento** listados no pasan (`Disallow: /`).
3. `/verificar/` y `/publico/` van con `Disallow` **para todos**, incluido el comodín,
   y además con `noindex` en la propia página.
4. El comodín `User-agent: *` **permite** el resto del sitio: bloquearlo sacaría a
   AportaYa de los buscadores que no están en la lista.
5. El `robots.txt` se **genera** de `src/seo/robots.ts` y tiene prueba. No se edita a
   mano.

## Motivo

**Porque las dos familias piden cosas distintas y merecen respuestas distintas.** El
buscador nos trae gente; el recolector de entrenamiento se lleva el contenido y no
devuelve nada. Tratarlos igual —abrir todo o cerrar todo— es contestar la pregunta
fácil en vez de la verdadera.

**Porque las rutas de verificación no son contenido.** Un certificado de reputación es
público en el sentido de que su titular decidió compartirlo con quien le pida el
enlace. Que aparezca en un buscador es otra cosa, y **nadie lo consintió**. Es el mismo
razonamiento que deja el backoffice fuera del índice, aplicado a una superficie que sí
es pública.

**Porque una lista se revisa y un comodín no.** El comodín permisivo es deliberado y
tiene su costo declarado: un recolector nuevo pasa hasta que alguien lo agrega. Se
acepta porque la alternativa —cerrar por omisión— nos saca de los buscadores que
aparezcan mañana, y ese daño es cierto mientras el otro es probable.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Abrir todo** | Regala el contenido al entrenamiento sin que nadie lo haya decidido, y expone al índice rutas con datos de personas |
| **Cerrar todo salvo Google y Bing** | Deja fuera a los buscadores con IA que **sí** enlazan de vuelta, que es de donde viene tráfico creciente. Y hay que mantener una lista blanca que envejece peor que la negra |
| **`Disallow: /` en el comodín** | Saca el sitio de todo buscador que no esté nombrado. El remedio peor que la enfermedad |
| **Bloqueo por servidor (user-agent en NGINX)** | Un agente que ignora `robots.txt` también miente el user-agent. Da sensación de control sin darlo, y bloquea de más |
| **No decidir y escribir el `robots.txt` a mano** | Es exactamente cómo una decisión de negocio termina cambiada en un commit de una línea que nadie revisa |

## Consecuencias

**Lo que se gana.** El sitio se indexa donde conviene, y el contenido no alimenta
modelos sin acuerdo. Las rutas con datos de personas quedan fuera del índice por dos
mecanismos independientes.

**Lo que cuesta, dicho sin adornos.**

1. **`robots.txt` es buena fe y no protege nada.** Un recolector que lo ignora entra
   igual. Lo que de verdad impide leer un expediente es la sesión; lo que limita el
   raspado masivo es el límite de tasa del gateway. Esta decisión declara una postura
   pública, no un control de seguridad — y confundir las dos cosas sería el error.
2. **La lista de entrenamiento envejece.** Se revisa cuando aparece un agente nuevo
   conocido. La prueba comprueba la forma, no la completitud: **ningún gate puede
   saber que la lista está completa**, y por eso está dicho acá.
3. **Si mañana se firma un acuerdo de licenciamiento**, este ADR se supera con otro:
   la postura cambia por decisión escrita, no editando un archivo.

## Cómo se verifica

- `robots.spec.ts`: los de búsqueda tienen `Allow`, los de entrenamiento tienen
  `Disallow: /` **y ningún `Allow`**, y `/verificar/` y `/publico/` están prohibidos
  bajo el comodín.
- El `robots.txt` se emite al construir y queda en la salida publicada: se revisa
  mirando el archivo, no el código que lo genera.
- Cuando existan las rutas de verificación: una prueba de que llevan `noindex`.

## Ver también

[[ADR-041 Sitio público · el tercer producto]] · [[ADR-004 Frontend]] ·
[[CU-75 Emitir un certificado de reputación verificable]] ·
`planes/10 Plan maestro del frontend.md` · [[Seguridad]]
