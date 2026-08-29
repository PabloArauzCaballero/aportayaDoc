---
tags: [plan, informe, auditoria]
titulo: "Auditoría integral del backend"
estado: en curso
---

# Auditoría integral del backend

**Se corre, no se lee.** Una auditoría que no se puede volver a ejecutar es una foto, y
la foto envejece sin avisar:

```bash
python3 scripts/auditar_backend.py          # el informe
python3 scripts/auditar_backend.py --json   # para comparar dos corridas
```

## Resultado

| Dimensión | Peso | Antes | Después | Hallazgos |
| --- | :-: | :-: | :-: | --- |
| Alcance | 3 | 10.00 | 10.00 | 151 operaciones declaradas, 151 implementadas |
| Seguridad | 5 | 10.00 | 10.00 | + dos comprobaciones nuevas (ver «el punto ciego») |
| Invariantes | 5 | 9.78 | 10.00 | 10 → 0; las 10 pasaron a divergencias **declaradas** |
| Contratos | 3 | 10.00 | 10.00 | ningún `$ref` roto, ninguna operación sin respuestas |
| Pruebas | 4 | 8.88 | 9.65 | 20 → 7 · **escritas, no ejecutadas** |
| Cableado | 4 | 8.62 | 10.00 | 26 → 0 |
| Prohibiciones | 6 | 10.00 | 10.00 | 3.136 comprobaciones, ninguna violación |
| **Global** | | **9.63** | **9.95** | con **166/194** pruebas verificadas |

> La nota «antes» sale del script tal como estaba al guardar la línea base. El propio
> script se volvió más preciso durante el trabajo —quitó falsos positivos y separó
> secretos de umbrales—, así que parte de la diferencia es de la medición y no del
> código. Se dice en vez de mostrar sólo el número que conviene.

## El punto ciego que tuvo esta auditoría

Daba **10/10 en seguridad mientras el sistema entero era inalcanzable desde afuera**.

`@Publico` se comprobaba sólo al arrancar; la cadena de filtros abría cuatro patrones
escritos a mano. El registro (CU-01) y el ingreso (CU-04) estaban anotados y devolvían
`401`: no había forma de conseguir un token, y por lo tanto ninguna de las 151
operaciones se podía usar. Todo compilaba y todas las pruebas de caso de uso pasaban.

Lo encontró la colección de Postman, que fue lo primero que ejercitó el sistema por
HTTP de punta a punta. La lección quedó incorporada: **comprobar que la anotación
existe no comprueba que se respete**, y ahora la dimensión de seguridad exige que
existan las pruebas que lo ejercitan por HTTP (`ArranquePuertaDeEntradaTest`,
`ArranqueEntradaMalFormadaTest`).

## Lo corregido

| Qué | Dónde | Por qué importaba |
| --- | --- | --- |
| `@Publico` no se respetaba | `LoQueEstaAbierto` | El sistema no se podía usar desde afuera |
| Entrada malformada daba `500` | `ManejadorGlobalDeErrores` | Mentía sobre de quién es la culpa, llenaba la bitácora de ERROR hasta perder las alertas reales, y dejaba simular una caída mandando basura. **Verificado en vivo**: fecha inválida, JSON roto y UUID inválido dan `400` con `AP-VAL-02`, sin filtrar la traza |
| 11 umbrales eran constantes disfrazadas | `application.yml` de auditoría, cumplimiento y núcleo | Un umbral que sólo existe como valor por omisión no se mueve sin desplegar (invariante 10) |
| 14 URLs de servicio faltaban en el compose | `generar_compose.py` | Los adaptadores HTTP no tenían a quién preguntar en el stack desplegado |
| La clave de firma se generaba en memoria sin avisar | `EmisorDeAcceso` | Con dos réplicas cada una firma distinto y los tokens de una los rechaza la otra (ADR-037) |
| `grupos` resolvía un alias con un `JOIN` contra `nucleo_financiero` | `ConsultasRepositorio` | Invariante 11, y lo había escrito yo |

## Divergencias declaradas — diez, y se revisan igual

No bajan la nota porque **no son descuidos**: el modelo pone la clave foránea del otro
lado, o el bloqueo tiene que ocurrir dentro de la transacción. Cerrarlas exige tocar
`sql/` o romper el invariante 6, y ninguna de las dos se decide en un carril.

| Dónde | Qué lee | Por qué no se cierra |
| --- | --- | --- |
| `cumplimiento/LimiteRepositorio` | `nucleo_financiero.consumo_limite` | CU-40 lo lee bloqueando **dentro** de la transacción del dinero: dos operaciones simultáneas que vean el mismo acumulado pasan las dos el tope. Por HTTP se pierde el bloqueo y se rompe el invariante 6 |
| `erp/PeriodoRepositorio` · `erp/PresupuestoRepositorio` | el libro contable | Los estados financieros suman el libro entero de un período: decenas de miles de filas por la red, dentro de la transacción del cierre. Es **sólo lectura**: el invariante 12 queda intacto |
| `publicidad/CreativaRepositorio` · `CU110AltaDeAnunciante` | `organizador.organizador` | La clave foránea la pone el modelo del lado de publicidad |

Cada una está marcada en el código con `INVARIANTE-11 DECLARADO` y su razón. La
auditoría las lista siempre: **una divergencia aceptada que nadie vuelve a mirar deja
de ser una decisión y pasa a ser una costumbre.**

## Existir no es pasar

La dimensión de «Pruebas» cuenta **archivos**, y eso premia a quien escribe el archivo.
Es la misma trampa que el contrato prohíbe cuando dice que nunca se afirma que algo
pasa sin haberlo ejecutado — y esta auditoría cayó en ella: escribir siete clases de
rechazo subió la nota de 8.88 a 9.65 sin haber ejecutado ninguna.

Por eso ahora reporta, aparte y siempre, **cuántas clases tienen evidencia de haber
corrido y pasado** en `build/test-results`. La nota y la evidencia son dos números
distintos, y el segundo es el que manda.

## Lo que queda abierto

**Siete casos de uso sin prueba de rechazo**: cumplimiento CU-03/05/06/46, identidad
CU-01, notificaciones CU-81, núcleo CU-40.

**Siete clases escritas y sin ejecutar**: las de grupos (CU-20/59/60/62/63/64/65/68/69).
Compilan; ninguna corrió. Testcontainers no puede levantar postgres —
«Could not find a valid Docker environment»— y `docker ps` cuelga varios minutos. El
demonio quedó degradado por un contenedor zombi que no acepta señales. **No se
reinició Docker Desktop porque en esa máquina hay 54 contenedores de otros proyectos**,
y eso no es una decisión de este carril.

No se dice que estén bien hasta que corran.

## Lo que sí quedó verificado, y cómo

| Qué | Cómo se comprobó |
| --- | --- |
| `@Publico` se respeta | `ArranquePuertaDeEntradaTest` **pasó** (`BUILD SUCCESSFUL`, 4m 58s) antes de que Docker se degradara · y en vivo: `/sesiones` llega al controlador y falla por credenciales de la base, **no** con el `401` de la guardia |
| Lo demás sigue cerrado | En vivo: `GET /usuarios/por-telefono` sin token → `401` |
| Entrada malformada da `400` | En vivo contra el proceso levantado: fecha inválida, JSON roto y UUID inválido → `400` con `AP-VAL-02` y sin nombres internos en el cuerpo |
| El JWKS se sirve sin sesión | En vivo → `200` |

### Un hallazgo del camino: a quien no tiene sesión no se le dice qué rutas existen

`GET /usuarios` no está mapeado —sólo POST— y responde **401, no 405**. La prueba
esperaba `405` y **la prueba estaba mal**: un `405` le confirma a quien tantea que la
ruta existe y que el verbo era otro; un `404`, que no existe. Las dos cosas son un mapa
gratis del sistema para un anónimo. El `401` uniforme no dice nada, y es lo correcto.
Los códigos `405` y `404` siguen valiendo para quien sí trae sesión, que es donde
informan sin regalar nada.
