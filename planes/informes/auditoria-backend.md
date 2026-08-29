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
| Pruebas | 4 | 8.88 | 9.24 | 20 → 15 · **queda abierto** |
| Cableado | 4 | 8.62 | 10.00 | 26 → 0 |
| Prohibiciones | 6 | 10.00 | 10.00 | 3.136 comprobaciones, ninguna violación |
| **Global** | | **9.63** | **9.90** | |

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
| Entrada malformada daba `500` | `ManejadorGlobalDeErrores` | Mentía sobre de quién es la culpa, llenaba la bitácora de ERROR hasta perder las alertas reales, y dejaba simular una caída mandando basura |
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

## Lo que queda abierto

**15 casos de uso sin prueba de rechazo** — cumplimiento CU-03/05/06/46, grupos
CU-20/59/62/63/64/65/68/69, identidad CU-01, notificaciones CU-81, núcleo CU-40.
`CU60RechazosTest` está escrito y compila; **no se pudo ejecutar**: el demonio de
Docker quedó colgado por un contenedor zombi y Testcontainers no arranca. No se
reinició porque en esa máquina hay 54 contenedores de otros proyectos.

No se dice que estén bien hasta que corran.
