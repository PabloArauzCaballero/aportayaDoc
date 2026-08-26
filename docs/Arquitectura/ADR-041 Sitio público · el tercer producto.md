---
tags:
  - arquitectura
  - adr
  - frontend
  - producto
titulo: "ADR-041 — Sitio público: el tercer producto"
estado: aceptada
fecha: 2026-08-26
---

# ADR-041 — Sitio público: el tercer producto

> Reconoce que AportaYa tiene **tres** superficies y no dos, y elige con qué se
> construye la tercera. Enmienda [[ADR-004 Frontend]], que descartó Next.js **como
> billetera** — no como sitio público, que entonces no existía.

## Contexto

El producto nació con dos superficies: la app del participante y el backoffice. Las
dos van detrás de login, las dos son aplicaciones y ninguna necesita ser encontrada
por un buscador.

Después aparecieron seis casos de uso que **no encajan en ninguna de las dos**, porque
su requisito es exactamente el contrario: que funcionen **sin cuenta**.

| Caso de uso | Por qué no puede vivir detrás de login |
| --- | --- |
| CU-61 verificar públicamente el sorteo | Lo verifica un tercero que desconfía. Pedirle cuenta es pedirle que confíe primero |
| CU-72 y CU-73 bloque y cadena de transparencia | Auditar el grupo no puede depender de que nosotros abramos la base |
| CU-75 certificado de reputación verificable | Se presenta **afuera**: a otro grupo, a quien pide un aval |
| CU-34 tarifario público | Un precio que hay que instalar una app para conocer es un precio escondido |
| Punto de reclamo y contrato de adhesión | La normativa de defensa del consumidor exige que sean accesibles sin ser cliente |

Servirlos desde la app o desde el backoffice significaría cargar un paquete de
JavaScript de megabytes para mostrar una página que no cambia, y renunciar a que
alguien encuentre el producto buscándolo.

## Decisión

**Existe un tercer producto, `apps/web`, y se construye con Astro con islas de React y
adaptador de Node.**

1. **Estático por omisión.** `output: 'static'`. Cada página que necesite servidor lo
   declara con `export const prerender = false`, una por una, y hay una prueba que
   enumera cuáles son.
2. **SSR solo donde el contenido depende de un servicio en vivo**: las rutas de
   verificación (CU-61, CU-73, CU-75) y las consultas al gateway.
3. **Islas de React, no páginas de React.** Lo interactivo se marca con `client:*`; el
   resto viaja como HTML sin JavaScript.
4. **Los documentos regulatorios van en *content collections*** —contrato de adhesión,
   política de privacidad, tarifario—: son contenido versionado con el repositorio, no
   filas de una tabla.
5. **Comparte el monorepo**: mismo `tsconfig.base.json`, mismo `@aportaya/simulado`,
   mismo cliente generado. Lo único propio es su `astro.config`.

## Motivo

**Porque el requisito de este producto es medible y es distinto.** Las otras dos
superficies se miden por lo que dejan hacer; esta se mide por cuánto tarda en pintar
y por si aparece cuando alguien la busca. Astro estático manda menos bytes que
cualquier aplicación de una sola página, y eso se traduce en métricas de carga, que se
traducen en posicionamiento.

**Porque las cuatro rutas de verificación tienen que sobrevivir a la caída de lo
demás.** Una página que responde `503` porque un servicio interno está caído no cumple
la promesa de CU-61: quien desconfía va a leer la caída como conveniente. Estático se
sirve igual; y lo dinámico se apoya en la proyección local de
[[ADR-040 Fronteras de transparencia, reputación y riesgo]], no en cuatro servicios.

**Porque ADR-004 respondió otra pregunta.** Descartó Next.js para la billetera, donde
el SSR no aporta —todo va detrás de login y nada se indexa—. Acá el argumento se da
vuelta, y por eso esto es una enmienda y no una contradicción.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Servir el sitio desde `apps/backoffice`** (React + Vite) | Una aplicación de una sola página para mostrar un tarifario: megabytes de JavaScript, pintado tardío y contenido que un buscador ve vacío hasta que ejecuta el script |
| **Next.js** | Resuelve lo mismo trayendo un modelo de servidor entero. El sitio es casi todo estático: pagar SSR por omisión para apagarlo página por página es el problema al revés |
| **HTML a mano o un generador sin componentes** | Las islas de verificación son interactivas de verdad, y compartir átomos con las otras dos superficies deja de ser posible |
| **Poner el sitio entero en SSR «por las dudas»** | Tira a la basura la única ventaja del producto y obliga a tener el servidor arriba para responder una página que no cambia nunca |
| **Un solo producto con rutas públicas dentro de la app** | Mezcla dos perfiles de riesgo: la superficie sin sesión y la que maneja dinero comparten paquete, dependencias y despliegue |

## Consecuencias

**Lo que se gana.** Una superficie que se indexa, carga rápido en un teléfono de gama
baja con datos móviles, y sigue respondiendo cuando el resto no.

**Lo que cuesta.**

1. **Un tercer producto que mantener**: su despliegue, su Dockerfile, su CI y sus
   dependencias. Se acota compartiendo el monorepo y el servidor simulado.
2. **Dos modelos mentales conviviendo.** Una página estática y una isla se escriben
   distinto, y confundirlos produce o JavaScript de más o una página que no reacciona.
   Por eso `prerender = false` se declara por página y **hay una prueba que las
   enumera**: si mañana hay media docena, esta decisión dejó de cumplirse en silencio.
3. **Las rutas de verificación llevan datos de personas.** Son públicas y **no
   indexables**: `noindex` en la página y `Disallow` en `robots.txt`
   ([[ADR-042 Política de rastreadores de IA]]). Es un punto de contacto entre este
   carril y el de SEO, y está escrito en las fichas de los dos.

## Cómo se verifica

- `paginas-por-archivo.spec.ts` enumera las páginas con `prerender = false` y hoy
  exige que sea **exactamente una**. Crece solo si alguien lo cambia a propósito.
- `astro build` separa lo prerenderizado de lo servido: la salida lo dice página por
  página, y es lo que se mira al revisar.
- El `robots.txt` publicado tiene su prueba (`robots.spec.ts`).
- Cuando existan las rutas de verificación: una prueba de que responden **sin sesión**
  y otra de que llevan `noindex`.

## Ver también

[[ADR-004 Frontend]] · [[ADR-040 Fronteras de transparencia, reputación y riesgo]] ·
[[ADR-042 Política de rastreadores de IA]] ·
[[CU-61 Verificar públicamente el sorteo]] ·
[[CU-75 Emitir un certificado de reputación verificable]] ·
`planes/10 Plan maestro del frontend.md`
