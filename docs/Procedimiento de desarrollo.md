---
tags:
  - producto
  - procedimiento
  - calidad
titulo: "Procedimiento de desarrollo — de un flujo a humo verde"
fecha: 2026-08-19
alcance: cómo se construye un flujo completo (back, front y humo) y qué lo deja cerrado
---

# Procedimiento de desarrollo

> **Qué agrega este documento.** `planes/00c Recetario` dice cómo se construye **un
> caso de uso dentro de un servicio**. Este dice cómo se construye **un flujo entero**
> —contrato, backend, app, humo— y en qué orden, con qué evidencia y qué lo bloquea.
> [[Contrato de implementación para IA]] dice qué **no** se puede inventar mientras
> tanto.

## 0 · La unidad de trabajo es el flujo, no la capa

Un flujo terminado es: **contrato + backend + pantalla Android + humo en verde**. No
se abre un flujo nuevo con el anterior a medias, y no existe "el backend está listo,
falta el front": eso es un flujo sin terminar.

La razón es de riesgo, no de gusto. Un backend sin pantalla no demostró nada: la
pregunta que descubre los errores de diseño —¿qué ve el usuario cuando esto falla?—
solo aparece cuando hay pantalla.

## 1 · Los diez pasos de un flujo

**No se saltea ninguno y no se cambia el orden.** Los pasos 4 a 7 son el recetario de
`planes/00c`; los demás son los que lo rodean.

| # | Paso | Termina cuando | Skill |
| :-: | --- | --- | --- |
| **0** | **Frontera transaccional**: las seis preguntas, respondidas por escrito | Está decidido qué va todo-junto-o-nada, y si cruza servicios, que es saga | `frontera-transaccional` |
| **1** | **Leer el CU entero** y sus restricciones. Resolver **cada** tabla y columna contra su `.puml` | No queda ni un nombre "de memoria" | `implementar-desde-boveda` |
| **2** | **Restricciones y semillas**: la barrera en la base, y el catálogo que el flujo necesita | El flujo no puede operar por falta de dato — y eso es lo correcto | `restriccion`, `semillas-catalogos` |
| **3** | **Contrato**: la operación en `openapi/<servicio>.yaml`, con sus errores `AP-CU<NN>-<nn>` e idempotencia | Servidor y clientes se generan sin diff | `contratos-api` |
| **3b** | **Puertos**: ¿toca algo de afuera? Entonces interfaz en `dominio/puertos/` y **adaptador local** primero | El flujo corre sin salir a internet | `proveedores-externos`, [[ADR-033 Puertos y adaptadores]] |
| **4** | **Átomos** puros | Pruebas en milisegundos, sin contexto | `arquitectura-atomica` |
| **5** | **Moléculas**: repositorios, clientes de pares, adaptadores | Ninguna abre transacción | `datos-jooq` |
| **6** | **Organismo**: el caso de uso, único `@Transactional` | Los criterios de aceptación pasan | `back-spring`, `pruebas-cu` |
| **7** | **Página**: el controlador que implementa lo generado | Sin una sola condición de negocio | `back-spring` |
| **8** | **Pantalla Android**: los cuatro estados, con el cliente generado | Humo de pantalla en verde en emulador | `disenar-frontend`, `movil-expo` |
| **9** | **Humo del flujo**: la escalera completa, H0 a H4 | Evidencia adjunta al PR | `definicion-de-terminado` |
| **10** | **Descriptor**: si el carril cierra un servicio, su `descriptor.yml` declara **nivel** y por qué | `python3 scripts/generar_k8s.py` pasa | `despliegue-contenedores` |

El **pase de iOS** no es un paso del flujo: es una fase por bloque de pantallas
([[ADR-036 Android primero]]).

> [!important] La seguridad no es un paso once
> No hay una casilla «asegurar» al final porque lo que se agrega al final no protege
> nada: atraviesa los diez. En el **2** la barrera va en la base y no en el servicio;
> en el **3** la entrada es `strict()` y los errores no filtran la causa; en el **5**
> ninguna consulta sale de `conContexto`; en el **7** el permiso se verifica contra el
> recurso y no contra el rol; en el **8** la pantalla **refleja** permisos, no los
> decide. El estándar completo con su correspondencia ISO está en [[Seguridad]], y la
> versión corta para escribir código, en `seguridad-aplicacion`.
>
> El paso **9** no cierra sin `python3 scripts/verificar_seguridad.py` en **TODO OK**,
> ejecutado y con la salida adjunta —no supuesto (`definicion-de-terminado`).

> [!warning] Los tres pasos que más se saltean, y qué pasa cuando se saltean
> - **Saltear el 0** → se descubre a mitad del código que la operación cruza dos
>   servicios, y lo que estaba escrito como transacción hay que rehacerlo como saga.
> - **Saltear el 2** → el flujo "funciona" en la máquina de quien lo escribió porque
>   su base tiene datos que nadie sembró, y falla en CI.
> - **Saltear el 3b** → el nombre del proveedor termina dentro del caso de uso, y la
>   prueba de aceptación pasa a depender de un contrato comercial.

## 2 · La escalera de humo

"Humo" hasta ahora quería decir una sola cosa: `prueba_humo.sql`. A partir de acá son
**cinco peldaños**, cada uno con su dueño y su costo. Un flujo cerrado los tiene todos
en verde; un flujo en curso sube de a uno.

| # | Peldaño | Qué comprueba | Cuánto tarda | Cuándo corre |
| :-: | --- | --- | --- | --- |
| **H0** | **Base** — `sql/50_verificacion/prueba_humo.sql` | El esquema aplica en limpio y las restricciones rechazan lo que tienen que rechazar | segundos | Cada cambio de modelo o de `sql/` |
| **H1** | **Servicio arriba** — el servicio levanta y responde | Arranca con configuración por defecto, `/health` responde, **todos los puertos resolvieron a su adaptador local** y no hubo una sola llamada saliente | ~30 s | Cada PR del servicio |
| **H2** | **Contrato** — petición real contra el servicio levantado | El camino feliz del CU y **al menos un rechazo** con su código `AP-CU<NN>-<nn>` exacto | ~1 min | Cada PR del servicio |
| **H3** | **Pantalla** — la app sobre emulador Android | La pantalla renderiza los cuatro estados contra el servicio real y hace la operación de punta a punta | ~3 min | Cada PR de la app |
| **H4** | **Flujo** — el recorrido completo entre servicios | El flujo cruza los servicios que tenga que cruzar, la saga compensa si se corta, y el dinero cuadra al final | ~10 min | Antes de fusionar a `main` |

**Las tres reglas de la escalera**

1. **Un peldaño no reemplaza al de abajo.** H4 en verde con H0 en rojo no significa
   nada: significa que el flujo pasó por casualidad.
2. **El humo prueba que algo *anda*, no que esté *bien*.** Los casos límite son de
   `pruebas-cu`. El humo existe para que un error tonto no llegue a la revisión humana.
3. **Un humo que falla intermitente se arregla o se borra.** Un peldaño en el que
   nadie confía es peor que no tenerlo: enseña a ignorar el rojo.

**H1 tiene una comprobación que los demás no**: que ningún puerto haya resuelto a un
adaptador externo. Es lo que hace verificable la promesa de
[[ADR-033 Puertos y adaptadores]] de que la omisión nunca elige un tercero.

## 3 · Qué cambia en el backend

Tres cosas, todas consecuencia de [[ADR-033 Puertos y adaptadores]]:

1. **Antes de escribir el caso de uso se decide qué es puerto.** La pregunta es: *¿esto
   sale del proceso?* Si sale —red, disco, reloj, azar, correo, plata—, es puerto.
2. **El adaptador local se escribe primero**, y es el que corre en dev, en las pruebas
   y en la primera demostración. El real se escribe cuando exista el contrato con el
   proveedor.
3. **El nombre del proveedor no aparece fuera de su adaptador.** Se verifica con
   `grep`, y está en la lista de rechazo automático de revisión.

Lo demás del backend no cambia: `planes/00c` sigue siendo la receta, y el
`@Transactional` sigue viviendo solo en `aplicacion/`.

## 4 · Qué cambia en el frontend

1. **Android primero, iOS por pase** ([[ADR-036 Android primero]]). La ficha de
   paridad se escribe cuando la pantalla se termina en Android, no cuando llega el pase.
2. **La app también tiene puertos**: biometría, push, cámara, almacén seguro, háptica.
   `Platform.OS` no aparece en una vista.
3. **Notificaciones**: la pantalla de avisos lee `bandeja_entrada`
   ([[ADR-035 Canales por defecto]]). El push es el aviso; la bandeja es la fuente. Una
   pantalla que muestre "notificaciones" leyendo otra cosa está mal.
4. **Archivos**: subir es `multipart` contra el endpoint del servicio dueño, y lo que
   vuelve es una **clave de objeto**, no una URL ([[ADR-034 Almacenamiento de archivos]]).
   La app nunca arma una URL de archivo por su cuenta.
5. **Los cuatro estados siguen siendo obligatorios** en toda pantalla con datos o con
   dinero, y ahora son parte de H3, no de la buena voluntad.

## 5 · La evidencia que cierra un flujo

Un flujo no se declara terminado con una afirmación: se declara con salidas pegadas.

| Paso | Evidencia |
| --- | --- |
| 2 · Semillas | Salida de `python3 scripts/generar_semillas.py` |
| 3 · Contrato | Que el cliente regenerado **no** produce diff |
| 4–7 · Backend | Salida de la suite del servicio, con los criterios del CU nombrados |
| 8 · Pantalla | Capturas de los cuatro estados en emulador Android |
| 9 · Humo | Las cinco líneas de H0 a H4, con sus conteos |

**Nadie escribe "compila", "pasa", "es seguro" o "está listo" sin haber ejecutado lo
que lo demuestra** (`definicion-de-terminado`). Es la regla que más se rompe y la
que más caro sale: una afirmación falsa de "listo" cuesta el doble que un "no pude".

## 5b · Disponibilidad — lo que cada carril entrega sin excepción

El nivel de un servicio no es cosa de infraestructura: lo decide quien conoce el
flujo. Por eso el `descriptor.yml` lo posee el carril
([[ADR-037 Alta disponibilidad y balanceo]]).

| Entrega | Qué es |
| --- | --- |
| `nivel` y `nivel_porque` | N1, N2 o N3, con la razón escrita. **La impone el peor dependiente sincrónico**, no la preferencia del dueño |
| `replicas.min` y `.max` | Nunca 1. El máximo tiene que caber en el pool: lo verifica el generador |
| `pool.hikari_por_replica` | Lo que cada réplica abre. Subir réplicas sin tocar esto agota PostgreSQL |
| Degradación | Si el servicio se apaga primero bajo presión, decirlo (§7 del ADR) |

Y una consecuencia para el paso 0: **si el caso de uso agrega una llamada sincrónica
de un servicio N1 a uno de nivel inferior, o ese servicio sube de nivel, o la llamada
deja de ser sincrónica.** Se decide al escribir el contrato, no en producción.

## 6 · Coordinación entre carriles

Con cinco máquinas (`planes/17 Plan de acción secuencial`)
lo que evita el conflicto es la propiedad exclusiva. Los archivos que **no** son de un
solo carril son cuatro, y cada uno tiene su regla:

| Archivo compartido | Regla |
| --- | --- |
| `docs/entidades/*.puml` y `sql/` | Solo el carril de modelo, y por regeneración; nunca a mano |
| `seeders/minimos/*.json` | Un archivo por tema; agregar bloque al final, nunca reordenar |
| `seeders/dev/*.json` | Un archivo por carril cuando haga falta; el manifiesto se edita al final |
| `openapi/<servicio>.yaml` | Es del carril dueño del servicio; nadie más lo toca |

Y un flujo que necesita un dato de otro servicio **no lee su esquema**: pide el
endpoint, o el dato viaja en el evento ([[ADR-017 Propiedad de datos por servicio]]).

## Ver también

[[Contrato de implementación para IA]] · [[ADR-033 Puertos y adaptadores]] ·
[[ADR-034 Almacenamiento de archivos]] · [[ADR-035 Canales por defecto]] ·
[[ADR-036 Android primero]] · [[Método de arquitectura]] ·
`planes/00c Recetario` · `planes/19 Contrato de carril` · `definicion-de-terminado`
