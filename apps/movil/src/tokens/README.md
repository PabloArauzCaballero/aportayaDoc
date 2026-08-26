# `tokens/` — no es de este carril

El sistema de diseño (tokens, átomos y moléculas compartidos) es de la fase **F1**,
carril `F1 · Sistema de diseño`, que posee `packages/ui/**` y `apps/*/src/tokens/**`
([[18 Fichas de carril · las 38 unidades de trabajo]]).

Lo que hay acá es **andamiaje**: los valores mínimos para que la pantalla de la
fase F0 se vea, no un sistema de diseño. `andamiaje.ts` lo dice en su cabecera y
existe por una razón concreta: si el andamiaje no tuviera dónde poner un color, los
literales se esparcirían por los componentes y F1 tendría que ir a buscarlos uno
por uno.

**Al cerrar F1 este archivo se borra** y los componentes importan de `@aportaya/ui`.
