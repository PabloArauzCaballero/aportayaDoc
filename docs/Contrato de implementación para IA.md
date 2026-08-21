---
tags:
  - producto
  - contrato
  - guardas
titulo: "Contrato de implementación para IA"
fecha: 2026-08-19
alcance: reglas duras que cualquier IA (o persona) debe obedecer al implementar los flujos de docs/Flujo*.md
---

# Contrato de implementación para IA

> **Leé esto ANTES de implementar cualquier cosa de los flujos.** Son reglas duras, no
> sugerencias. Están para que no inventes, no alucines y no gastes tokens explorando lo que ya
> está fijado. Si algo no está acá ni en una fuente de verdad (§1), **no existe**: es un hueco
> (§4), no algo que completás.

## 0 · Regla cero — no inventes

Tabla, columna, endpoint, CU, rol, permiso, código de error, restricción o pantalla que **no
esté** en una fuente de verdad de §1 **no existe**. No lo completás con una suposición: lo
marcás como hueco (§4) y parás. "Parecería que…" no es una fuente.

## 1 · Fuentes de verdad y precedencia (mayor → menor)

Si dos se contradicen, **gana la de más arriba**. Un flujo que cite una columna que el `.puml`
no tiene está **equivocado**: gana el `.puml`, y reportás el error del flujo.

1. **`docs/entidades/*.puml` + `sql/` generado** — la forma de los datos: nombres exactos de
   tabla y columna, claves, tipos, `CHECK`, append-only. **No hay tabla ni columna fuera de acá.**
2. **`docs/CasosDeUso/CU-*.md`** — el contrato de cada flujo: entradas, salidas, **códigos de
   error `AP-CU<NN>-<nn>`**, reglas, transaccionalidad, idempotencia y `gherkin`.
3. **`docs/Restricciones.md` + `sql/40_reglas/`** — lo que la base hace cumplir (RLS incluida).
4. **`openapi/*.yaml`** de cada servicio (cuando exista) — la forma exacta de cada endpoint.
5. **`scripts/modelo.py` → `PREFIJOS`** — a qué servicio pertenece cada ruta.
6. **`docs/Arquitectura/ADR-*.md`** — las decisiones técnicas (**vigente supera a
   superada**: si el frontmatter dice `estado: superada por ADR-0XX`, ese documento
   **no manda**; manda el que lo superó).
7. **`docs/Flujo*.md`** (estos flujos) — el **recorrido** y el mapa pantalla↔CU↔servicio. **No**
   son la fuente de la forma de los datos; para columnas y errores, subí a §1.1–§1.2.

## 2 · Checklist obligatorio antes de escribir una línea

- [ ] Identificá el **CU por número** y leélo **entero**.
- [ ] Resolvé **cada tabla** contra su `.puml` (nombre y columnas exactos, copiados, no de memoria).
- [ ] Resolvé el **endpoint** contra `PREFIJOS` (el primer segmento debe ser un prefijo real del
      servicio dueño) y su OpenAPI.
- [ ] Dinero: tipo **`BigDecimal`** (back) / átomo **`Monto`** (front). Nunca `float`, nunca
      recalcular importes en el cliente (`dinero-decimal`, `contabilidad-partida-doble`).
- [ ] Pantalla con datos o dinero: **los cuatro estados** (cargando · vacío · error · éxito).
- [ ] Borrar = **soft delete** (baja lógica: estado + fecha), salvo lo **financiero/append-only**
      (libro, asientos, movimientos, eventos, bitácora), que **no se borra** — se reversa.
- [ ] Escritura con efecto: **idempotente** (clave de idempotencia) y **transaccional** según el
      CU; lo que cruza servicios es **saga** ([[ADR-028 Mecánica de saga]]), no transacción local.
- [ ] ¿Toca algo **fuera del proceso** (red, disco, correo, plata, reloj, azar)? Entonces es
      **puerto** en `dominio/puertos/` + **adaptador local primero**
      ([[ADR-033 Puertos y adaptadores]]). El nombre del proveedor no sale de su adaptador.
- [ ] Leé `seguridad-aplicacion` y [[Seguridad]] §5. Las **dieciocho prohibiciones** no se
      discuten en revisión: secreto versionado, SQL concatenado, endpoint sin decisión de
      autenticación, permiso sin comprobar el recurso, consulta fuera de `conContexto`,
      `Math.random` para algo que no se debe adivinar, hash rápido de contraseña, `digest()`
      desnudo, PII en el log, traza en la respuesta, deserialización polimórfica, SSRF,
      `dangerouslySetInnerHTML`, confiar en que la UI esconde el botón, desactivar una
      restricción para que pase una prueba, bajar un gate para desbloquear un merge,
      inventar un código de permiso que el catálogo no tiene, encender un canal apagado.

## 3 · Prohibiciones (las alucinaciones típicas)

- **No inventar endpoints.** Si el segmento no está en `PREFIJOS`, no existe.
- **No inventar tablas ni columnas.** Si no está en el `.puml`, no existe.
- **No inventar códigos de error.** Son `AP-CU<NN>-<nn>`, salen del CU (`errores-api`).
- **No inventar reglas de negocio.** Salen del CU y de `Restricciones.md`.
- **No inventar campos ni pantallas de UI.** Solo los organismos y campos que el flujo lista.
- **No “mejorar” el modelo sobre la marcha.** Cambiar el modelo es un **ADR + regeneración**
  (`boveda-modelo`), nunca una decisión de implementación.
- **No mezclar stacks.** Back = **Java 21 + Spring Boot** en microservicios; front = **React
  Native (Expo) + Turborepo** en monorepo yarn. Nada de TypeScript en el back ni Java en el front.
- **No leer el esquema ajeno.** Un servicio solo ve su esquema + `catalogo` (lectura) + su
  infraestructura ([[ADR-017 Propiedad de datos por servicio]]).
- **No elegir proveedor dentro de un caso de uso.** Ni un `if proveedor == …`, ni un
  `@Profile`, ni un SDK importado en `aplicacion/`. Se elige por configuración
  ([[ADR-033 Puertos y adaptadores]]).
- **No inventar canales de aviso.** Los de por defecto son `IN_APP`, `PUSH` y `CORREO`.
  WhatsApp, SMS y voz están **apagados**: no se usan en un flujo nuevo
  ([[ADR-035 Canales por defecto]]).
- **No poner una URL pública en una columna de archivo.** Va la **clave de objeto**
  (`local://…`), y el binario lo sirve el servicio dueño ([[ADR-034 Almacenamiento de archivos]]).
- **No mezclar semillas.** Un dato de personas jamás va a `seeders/minimos/`; un catálogo
  que producción necesita jamás va a `seeders/dev/`. El generador rechaza las dos cosas.
- **No escribir código de iOS antes que el de Android.** El orden es Android, ficha de
  paridad, pase de iOS ([[ADR-036 Android primero]]). Y `Platform.OS` no entra en una vista.
- **No editar `despliegue/k8s/generado/`.** Son derivados de `descriptor.yml` e
  `infra.yml`; se regeneran ([[ADR-037 Alta disponibilidad y balanceo]]).
- **No declarar una réplica**, ni un HPA sin tope: el generador lo rechaza y tiene razón.

## 3 bis · Seguridad — lo que no se negocia

Todo endpoint, consulta, adaptador, Dockerfile o pantalla se escribe contra
[[Seguridad]], que es donde está el estándar completo con su correspondencia ISO/IEC
27001 y 27034. Lo mínimo que hay que tener presente al escribir:

| Frontera | Por omisión | Si no lo cumplís |
| --- | --- | --- |
| HTTP | Guard global; público solo con marca explícita | El endpoint queda abierto y nadie lo nota |
| Autorización | Se verifica **contra el recurso**, no contra el rol | Cualquier participante opera sobre cualquier grupo |
| Fila | Toda consulta dentro de `conContexto` | La política de RLS no aplica y se ve todo |
| Entrada | Contrato `strict()`; lista blanca, no lista negra | Entra lo que nadie declaró |
| Salida | Ni traza, ni SQL, ni causa interna | El error enseña la arquitectura |
| Secretos | Fuera del repositorio, siempre | Un `git clone` es una filtración |
| Cripto | Argon2id, HMAC con pimienta, azar criptográfico | El cifrado de al lado queda decorativo |

**Acceso administrativo** ([[ADR-038 Acceso administrativo · segundo factor y recuperación asistida]]):
segundo factor **en todo acceso** de un operador, **TOTP** y nunca SMS ni WhatsApp,
recuperación **aprobada por otra identidad**, y el cambio de credencial que **corta todas**
sus sesiones. Lo hacen cumplir `R-SEG-10`, `R-SEG-11` y `R-SEG-12`: no hay implementación
que lo saltee, y tampoco hace falta que la recuerdes — la base la rechaza.

## 4 · Cuando falta algo (hueco)

Los huecos **ya declarados** viven en la sección "Notas de modelo" de cada flujo y en
`planes/20`. Si encontrás uno **nuevo**: parás, lo escribís como hueco (qué falta, en qué
`.puml`/CU, y por qué), y **no lo completás con una suposición**. Un hueco declarado es correcto;
una suposición silenciosa es un defecto.

## 5 · Verificación mínima (antes de decir "listo")

- [ ] `python3 scripts/verificar_seguridad.py` → **TODO OK** (el estándar de
      [[Seguridad]] sobre el repositorio: patrones prohibidos, secretos, R-SEG-10/11/12).
- [ ] `python3 scripts/verificar_boveda.py` → **TODO OK**.
- [ ] `python3 scripts/generar_semillas.py` → sin errores (valida también la frontera
      dev / mínimos).
- [ ] Cada CU citado **existe**; cada tabla citada **existe** en su `.puml`.
- [ ] Cada endpoint cae en un **prefijo real**.
- [ ] La escalera de humo que corresponda al alcance, en verde
      ([[Procedimiento de desarrollo]] §2). Un peldaño no reemplaza al de abajo.
- [ ] No afirmás "compila / pasa / es seguro / está listo" sin **haberlo ejecutado**
      (`definicion-de-terminado`).

## 6 · Las seis preguntas que hay que responder antes de escribir

Si alguna no tiene respuesta **citando un archivo**, no se empieza: se pregunta.

| # | Pregunta | Dónde está la respuesta |
| :-: | --- | --- |
| 1 | ¿Qué CU es, por número? | `docs/CasosDeUso/CU-<NN> …` |
| 2 | ¿Qué servicio lo posee? | `scripts/modelo.py` → `PREFIJOS` |
| 3 | ¿Qué tablas toca, con qué columnas exactas? | `docs/entidades/*.puml` |
| 4 | ¿Va todo junto o no? ¿Es saga? | Las seis preguntas de `frontera-transaccional` |
| 5 | ¿Toca algo de afuera? ¿Cuál es el puerto y su adaptador local? | [[ADR-033 Puertos y adaptadores]] |
| 6 | ¿Qué peldaños de humo lo cierran? | [[Procedimiento de desarrollo]] §2 |

## 7 · Los defaults del proyecto — no se eligen, ya están elegidos

Estas son las respuestas por omisión. Apartarse de una es un ADR, no una decisión de
implementación:

| Tema | Por defecto | Lo opcional (apagado) |
| --- | --- | --- |
| Estructura del servicio | Cuatro capas + `dominio/puertos/` + `infraestructura/adaptadores/` | — |
| Mensajería | Bandeja interna (`IN_APP`) + `CORREO`, con `PUSH` como aviso | WhatsApp, SMS, voz |
| Archivos | Adaptador **local** en disco, clave `local://…` | S3 / MinIO |
| Pagos y facturación | Simulador de dev | Pasarelas y servicio de impuestos reales |
| Móvil | **Android**, después pase de iOS | — |
| Datos de arranque | `seeders/minimos/` (producción) + `seeders/dev/` (con guarda) | — |
| Cuentas de desarrollo | Las dos de `seeders/dev/15-usuarios-dev.json` | — |
| Disponibilidad | Nivel **N1/N2/N3** en `descriptor.yml`; **nunca 1 réplica** | Escalado por encima del tope del nivel |

## Ver también

[[Flujo funcional · recorrido del usuario]] · [[Flujo de pantallas · app del participante]] ·
[[Flujo funcional · usuario administrador]] · [[Flujo de pantallas · backoffice administrador]] ·
[[Seguridad]] · [[ADR-038 Acceso administrativo · segundo factor y recuperación asistida]] ·
`planes/20 · Saneamiento del plan` · `errores-api` · `dinero-decimal` ·
`seguridad-aplicacion` · `definicion-de-terminado`
