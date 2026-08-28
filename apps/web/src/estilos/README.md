# `estilos/` — el sistema de diseño, ya conectado

`sistema.css` importa los tokens y los átomos de `@aportaya/ui` (carril `F1`), que
derivan de `docs/Views/Sistema-Diseno/estilos.css` con una prueba que falla si alguno
diverge. **Ningún literal de diseño vive acá.**

Los alias `--color-*` y `--espacio-*` sostienen las páginas de la fase F0 y se borran
cuando `F9` migre cada una al rol correspondiente.
