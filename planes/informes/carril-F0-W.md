---
tags:
  - plan
  - informe
  - carril
titulo: "Carril F0-W — andamiaje del sitio público"
ola: F0
fase: F0
modulo: apps/web
rama: dev
estado: en curso
---

# Carril F0-W — andamiaje del sitio público

**Fase** F0 · **Casos de uso** ninguno de negocio · **Puesto** P5 · Dell B
*(ejecutado desde la Legion: ver «Quién lo hizo» en [[carril-F0-B]], vale igual acá)*

> Tramo **T1** de [[17 Plan de acción secuencial · coordinación de cinco máquinas]].
> Ficha en [[18 Fichas de carril · las 38 unidades de trabajo]] · `F0-W`.
> Posee `apps/web/**` y `astro.config`. El andamiaje de MSW es de `F0-M` y acá **se
> consume**, no se reimplementa.

## Qué está hecho, con la salida que lo prueba

| Entregable | Evidencia | Estado |
| --- | --- | :-: |
| Astro 5 con islas de React y adaptador de Node | `astro build` separa lo prerenderizado de lo servido, página por página | ✅ |
| **Estático por omisión, SSR declarado por página** | `index.astro` prerenderizado · solo `plazos.astro` lleva `prerender = false`, **y hay una prueba que lo enumera** | ✅ |
| Una pantalla real contra MSW con sus **cuatro estados** | `CalculadoraDePlazo` · 5 pruebas: cargando, éxito, vacío, error y sin red | ✅ |
| **ADR-041** sitio público y **ADR-042** rastreadores de IA | Escritos, indexados, con sus 6 secciones · `verificar_boveda.py` en verde con **42 ADR** | ✅ |
| `robots.txt` **generado** y con prueba | 4 pruebas · emitido en la salida publicada · verificado dentro de la imagen | ✅ |
| `docker build -f apps/web/docker/Dockerfile.web .` | Imagen construida, **corre como `node`** y sirve portada, `robots.txt` y la ruta SSR | ✅ |
| `yarn --cwd apps/web dev` | Cubierto por el build y por la imagen corriendo; el servidor de desarrollo no se abrió a mano | 🟡 |

### Evidencia, con el comando y su resultado

```
yarn workspace @aportaya/web lint         sin hallazgos
yarn workspace @aportaya/web typecheck    sin errores
yarn workspace @aportaya/web test:front   4 archivos · 15 pruebas · 0 falladas
yarn workspace @aportaya/web test:a11y    1 archivo  ·  3 pruebas · 0 falladas
yarn workspace @aportaya/web build        /index.html prerenderizado · /robots.txt emitido
docker build -f apps/web/docker/Dockerfile.web .    imagen construida
docker run … && whoami                    node   (no root)
curl /                                    HTTP 200 · 1.783 bytes
curl /robots.txt                          la política, generada
curl /plazos                              HTTP 200  (SSR)
```

## Decisiones tomadas, y por qué

| Decisión | Por qué |
| --- | --- |
| **Astro 5**, no la mayor actual | El plan dice Astro 5 y las cinco máquinas se mantienen alineadas con lo escrito. **Existe Astro 7**: queda anotado como deuda de actualización, no como desviación silenciosa |
| La imagen sirve con **Node**, no con NGINX a secas | `/plazos` —y mañana las rutas de verificación— son SSR. NGINX sigue siendo la única entrada pública, por delante de este proceso |
| El contexto del `docker build` es la **raíz** del monorepo | El sitio es un espacio de trabajo de yarn: depende de `@aportaya/simulado` y del `tsconfig.base.json`. Construirlo desde su carpeta obligaría a copiar esas dos cosas adentro, y una copia se desactualiza |
| El `robots.txt` se **genera** de `src/seo/robots.ts` | Es una decisión de negocio (ADR-042). A mano, cambia en un commit de una línea que nadie revisa |
| El comodín `User-agent: *` **permite** el sitio | Bloquearlo lo saca de todo buscador que no esté nombrado. El costo —un recolector nuevo pasa hasta que alguien lo agregue— está declarado en el ADR |
| La página de plazos usa `client:load` y no `client:visible` | Es el contenido principal y está arriba de todo: diferirlo solo agrega un salto visible |
| Sin traza en el estado «sin red» | Un fallo de red no llegó al backend. Mostrar un código mandaría a soporte a buscar en el log una petición que nunca existió |

## Supuestos declarados

1. **La pantalla real consulta `calcularPlazoHabil` de `grupos`.** Las cuatro rutas que
   justifican este producto —CU-61, CU-72, CU-73, CU-75— **todavía no tienen
   contrato**: `transparencia.yaml` sigue con `paths: {}`. Construirlas ahora sería
   inventar su forma. `calcularPlazoHabil` es la única operación de lectura pública que
   hoy existe, y sirve para lo que el gate pide: una pantalla real, contra MSW, con sus
   cuatro estados.
2. **El `Dockerfile.web` fija los manifiestos de los tres productos** aunque solo
   construya uno: `yarn install --immutable` los exige porque el lockfile los nombra.
3. **Los estilos de `src/estilos/` son andamiaje**, no sistema de diseño: eso es de
   `F1`. Se borran al cerrar F1.
4. **Astro 7 existe.** Se eligió 5 por el plan. Migrar es trabajo declarado, no un
   descubrimiento futuro.

## Lo que queda abierto, y de quién es

| Qué | De quién | Cuándo |
| --- | --- | --- |
| Las cuatro rutas de verificación (`/verificar/**`, `/publico/**`) | **`F9`** (P5, T5) — y antes, el contrato de `transparencia` | Cuando `3B` escriba su OpenAPI |
| *Content collections* de los documentos regulatorios | `F9` | T5 |
| `sitemap.xml` (el `robots.txt` ya lo referencia) | `F10` SEO | T5 |
| Playwright + Chromium para E2E del sitio | `F0-W`/`F9` | Cuando exista un flujo real |
| `Dockerfile.backoffice` | `F0-B` | Junto con el primer despliegue del backoffice |
| Pasos del sitio en el CI | **P1**, por micro-PR | Antes de cerrar el tramo |
| Migrar a Astro 7 | este carril | Después de F9, no antes: migrar un andamiaje es barato, migrar un sitio hecho no |

## Frases prohibidas sin evidencia

No se dice «está listo». Lo que hay es: **18 pruebas en verde repartidas en tres
corredores, una imagen que arranca sin root y responde 200 en la portada, en el
`robots.txt` y en la única ruta SSR, dos decisiones de arquitectura escritas con sus
seis secciones, y una prueba que falla si mañana media docena de páginas se vuelven
dinámicas sin que nadie lo decida.**

## Ver también

[[11 Fases F0 y F1 · Cimientos y sistema de diseño]] · [[16 Carriles de frontend]] ·
[[carril-F0-M]] · [[carril-F0-B]] · [[ADR-041 Sitio público · el tercer producto]] ·
[[ADR-042 Política de rastreadores de IA]]
