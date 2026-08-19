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
6. **`docs/Arquitectura/ADR-*.md`** — las decisiones técnicas (vigente supera a superada).
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

## 4 · Cuando falta algo (hueco)

Los huecos **ya declarados** viven en la sección "Notas de modelo" de cada flujo y en
`planes/20`. Si encontrás uno **nuevo**: parás, lo escribís como hueco (qué falta, en qué
`.puml`/CU, y por qué), y **no lo completás con una suposición**. Un hueco declarado es correcto;
una suposición silenciosa es un defecto.

## 5 · Verificación mínima (antes de decir "listo")

- [ ] `python3 scripts/verificar_boveda.py` → **TODO OK**.
- [ ] Cada CU citado **existe**; cada tabla citada **existe** en su `.puml`.
- [ ] Cada endpoint cae en un **prefijo real**.
- [ ] No afirmás "compila / pasa / es seguro / está listo" sin **haberlo ejecutado**
      (`definicion-de-terminado`).

## Ver también

[[Flujo funcional · recorrido del usuario]] · [[Flujo de pantallas · app del participante]] ·
[[Flujo funcional · usuario administrador]] · [[Flujo de pantallas · backoffice administrador]] ·
`planes/20 · Saneamiento del plan` · `errores-api` · `dinero-decimal` · `definicion-de-terminado`
