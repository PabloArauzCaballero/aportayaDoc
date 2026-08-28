# `tokens/` — el sistema de diseño, ya conectado

Estos archivos **derivan** del sistema de diseño (`packages/ui`, carril `F1`), que a su
vez deriva de `docs/Views/Sistema-Diseno/estilos.css`. No hay ni un hex, ni un `px`
suelto, ni una familia tipográfica escrita acá: todo es un rol pedido a `@aportaya/ui`.

El andamiaje de la fase F0 —seis colores inventados para que la primera pantalla se
viera— **ya no existe**. Lo que queda son los alias con los nombres viejos
(`--color-fondo`, `tokens.color.acento`), que sostienen las pantallas que F0 escribió
mientras cada carril las migra al rol correspondiente. Cuando no quede ninguno, los
alias se borran.

**Si necesitás un valor que no está acá, no lo inventes**: está en `@aportaya/ui` o hay
que pedirlo por micro-PR al carril `F1`. Un color inventado se propaga a los tres
productos y ya no se saca.
