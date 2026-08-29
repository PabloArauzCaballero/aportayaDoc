# Postman — las 151 operaciones, sus entornos y su humo

Todo lo de esta carpeta lo **emite** `scripts/generar_postman.py` desde
`servicios/*/src/main/resources/openapi/*.yaml`. No se edita a mano: la próxima
generación lo pisa, y una colección editada a mano prueba una API que ya no existe.

```
python3 scripts/generar_postman.py
```

## Qué hay

| Carpeta | Qué | Cuánto |
| --- | --- | :-: |
| `coleccion/` | Una colección por servicio, con **todas** sus operaciones | 14 · 151 operaciones |
| `entornos/` | `local`, `ensayo-local`, `ensayo`, `produccion` | 4 |
| `humo/` | Por operación: **válido**, **límite** y **error** | 14 · 552 pruebas |
| `recorrido.postman_collection.json` | El caso válido **de punta a punta**, encadenado | 8 pasos |

## Cada petición trae

- **El cuerpo de ejemplo derivado del esquema**, y que *valida contra él*: los importes
  como cadena decimal (`"100.00"`), los teléfonos como `+591…`, los hashes de 64
  caracteres. Un ejemplo que no valida convierte cada prueba en una sesión de arreglar
  JSON a mano, y entonces la colección no la usa nadie.
- **El permiso que exige**, leído de los `@Permiso` del código —no de una tabla aparte
  que se desincroniza—. Las rutas públicas dicen que lo son.
- **La clave de idempotencia** donde el contrato la exige, con un `{{$guid}}` nuevo por
  intento.
- **Los rechazos `AP-CU` que el contrato declara**, con su motivo.
- Aserciones comunes a toda respuesta: que el código esté declarado, que un `422` traiga
  su código de negocio, y que **nunca** se filtre una traza al cliente.

## El prefijo de la ruta

`{{prefijo_api}}`, y no fijo. **El contrato declara `servers: /api/v1` pero el servicio
no lo sirve**: hablándole derecho a un proceso las rutas están en la raíz
(`POST /usuarios`), y el prefijo lo pone la entrada pública. Por eso `local` lo trae
vacío y los demás entornos lo traen en `/api/v1`.

Se descubrió corriendo esto por primera vez: con el prefijo escrito fijo, todo daba 401.

## Las tres preguntas del humo

No son tres variantes de la misma. Una operación puede contestar bien la primera y
fallar las otras dos, y ahí es donde están los defectos que llegan a producción.

| Caso | Qué pregunta |
| --- | --- |
| `1 · válido` | ¿Hace lo que promete? ¿Devuelve los campos obligatorios? ¿Los importes viajan como cadena? |
| `2 · límite` | ¿Qué hace en el borde exacto: el umbral de doble aprobación, el largo máximo, el mínimo del rango? Un `>` que debía ser `>=` solo se nota ahí |
| `3 · error · sin sesión` | ¿Deniega por omisión? ¿El 401 no cuenta de más? |
| `4 · error · cuerpo inválido` | ¿Rechaza lo que no valida, sin filtrar la traza? |

Cada colección de humo abre con `00 · sesión`, que consigue el token. Si no puede,
**lo dice una vez ahí** en vez de dejar que cincuenta pruebas fallen con un 401 que no
explica nada.

## Correrlo

```bash
yarn newman run postman/humo/identidad.humo.postman_collection.json \
  -e postman/entornos/local.postman_environment.json

# todo el humo, servicio por servicio
yarn humo

# el recorrido de punta a punta
yarn newman run postman/recorrido.postman_collection.json \
  -e postman/entornos/ensayo-local.postman_environment.json
```

## Lo que esto encontró la primera vez que corrió

No es anécdota: es la razón de que exista.

1. **`@Publico` no se cumplía.** La guardia abría cuatro patrones escritos a mano y la
   anotación sólo se comprobaba al arrancar. El registro (CU-01) y el ingreso (CU-04)
   estaban anotados y devolvían `401`: **no había forma de conseguir un token**, y por
   lo tanto ninguna de las 151 operaciones era alcanzable. Todo compilaba y todas las
   pruebas de caso de uso pasaban. Corregido en `LoQueEstaAbierto`, con
   `ArranquePuertaDeEntradaTest` de regresión.
2. **Una fecha inválida daba `500`.** El manejador global no cubría JSON malformado,
   tipos que no parsean, verbo no soportado ni parámetro faltante: todo eso caía en el
   de último recurso. Corregido, con `ArranqueEntradaMalFormadaTest`.

## Secretos

Los entornos **no traen el token ni las claves**. `token` y `clave_prueba` van como
`secret`, vacíos, y se cargan a mano o los escribe `00 · sesión` al ejecutarse. Un
secreto en un archivo versionado es una de las dieciocho prohibiciones, y da igual que
sea «sólo de desarrollo».
