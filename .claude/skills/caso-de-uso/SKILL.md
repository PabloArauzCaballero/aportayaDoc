---
name: caso-de-uso
description: "Escribir o modificar un caso de uso de AportaYa en docs/CasosDeUso/. Úsala cuando haya que especificar un flujo nuevo (registro, pago, entrega, reporte, reclamo), cambiar uno existente, o cuando alguien pida 'cómo debería funcionar X' antes de programarlo. Incluye la plantilla obligatoria, la numeración, las reglas de transaccionalidad e idempotencia y los criterios de aceptación."
---

# Escribir un caso de uso

Los casos de uso son **la especificación ejecutable**: un programador debe poder
implementar el flujo sin volver a preguntar, y un auditor debe poder verificar que
cumple la norma que lo obliga.

```
Norma (docs/Cumplimiento.md) → Caso de uso (docs/CasosDeUso/) → Restricción (docs/Restricciones.md)
      qué obliga                     cómo se ejecuta                 qué impide violarlo
```

## Numeración

| Rango | Área |
| --- | --- |
| CU-01..09 | Identidad, debida diligencia y contratos |
| CU-10..19 | Billetera, custodia y saldo |
| CU-20..29 | Circuito de dinero del pasanaku |
| CU-30..39 | Comisiones, impuestos y facturación |
| CU-40..49 | Cumplimiento UIF y ASFI |
| CU-50..59 | Operación, control y consumidor financiero |
| CU-60..69 | Gobernanza del grupo: sorteo, acuerdos, cupos y disolución |
| CU-70..79 | Transparencia verificable y reputación |
| CU-80..89 | Notificaciones y comunicación con el usuario |
| CU-90..99 | Organizador, automatización y proveedores de plataforma |
| CU-100..109 | Contabilidad financiera y ERP |
| CU-110..119 | Publicidad y campañas |

Los códigos de tres dígitos son válidos: `scripts/verificar_boveda.py` extrae el
número de `CU-NN` con una expresión regular, no asume dos dígitos fijos.

**Los códigos no se reutilizan ni se renumeran.** Un caso retirado se marca como
obsoleto y conserva su número.

Nombre de archivo: `CU-NN Título en minúsculas.md` — el título es exactamente el
texto con el que se lo enlaza desde `_CasosDeUso.md`.

## Plantilla obligatoria

```markdown
---
tags:
  - caso-uso
  - modulo/NN-slug-del-modulo
codigo: CU-NN
criticidad: alta | media | baja
actores: [..]
normas: [..]
---

# CU-NN — Título

> **Objetivo.** Una o dos líneas: qué logra el actor y por qué importa.

## Actores y disparador
## Precondiciones          ← numeradas, verificables
## Flujo principal         ← pasos numerados, con tabla.columna concretas
## Flujos alternativos     ← tabla: # | Situación | Resultado
## Postcondiciones
## Contrato · `openapi/<servicio>.yaml`  ← operación OpenAPI: entrada, salida y códigos de error
## Descomposición atómica  ← tabla: Nivel | Pieza | Responsabilidad
## Eventos, trabajos y permisos ← tabla: Emite | Dispara | Exige
## Interfaz                ← una línea para la app y una para el backoffice
## Restricciones aplicables ← códigos R-XXX-nn de [[Restricciones]]
## Evidencia que deja      ← qué filas quedan escritas
## Criterios de aceptación ← bloque ```gherkin
## Ver también
```

Las cuatro secciones intermedias son las que convierten la especificación en algo
programable sin volver a preguntar. Se escriben así:

| Sección | Qué contiene | Regla |
| --- | --- | --- |
| **Contrato** | La operación del OpenAPI del servicio —entrada, salida y respuestas de error— más una tabla que explica cuándo se devuelve cada una | Códigos `AP-CU<NN>-<nn>`; importes como **cadena decimal**; `additionalProperties: false`. Ver `contratos-api` |
| **Descomposición atómica** | Átomos, moléculas, el organismo y la página | El organismo es **el único** que abre transacción; si no hay endpoint, la fila Página dice qué lo dispara. Ver `arquitectura-atomica` |
| **Eventos, trabajos y permisos** | Qué evento de dominio emite, qué trabajo dispara y qué permiso exige | El evento se escribe en la misma transacción (*outbox*). Ver `trabajos-outbox` |
| **Interfaz** | Qué ve el usuario en la app y qué ve el operador en el backoffice | Una línea cada uno. "Sin pantalla en la app" es una respuesta válida y frecuente |

## Reglas de escritura

1. **Nombrar tablas y columnas reales**, enlazadas con `[[wikilinks]]`. "Se guarda
   el pago" no sirve; "se crea [[pago]] y se enlaza a la orden" sí.
2. **Marcar la transaccionalidad.** Cuando varias escrituras deben ser atómicas,
   escribir literalmente *"en la misma transacción"*. Es la instrucción más
   importante del documento.
3. **Idempotencia explícita** en todo flujo con dinero: qué `clave_idempotencia`
   se usa y qué pasa con el reintento.
4. **Los plazos se calculan al inicio y se guardan.** Si el flujo tiene un plazo
   legal, decir en qué columna queda.
5. **Los flujos alternativos son la mitad del valor.** Webhook duplicado, timeout
   del proveedor, saldo insuficiente, plazo vencido, autoridad de por medio.
   Un caso sin alternativos es una lista de deseos.
6. **Evento de dominio**: todo caso relevante escribe en [[evento_dominio]] dentro
   de la misma transacción (patrón *outbox*), nunca por fuera.
7. **Criterios de aceptación en Gherkin**, incluyendo al menos un caso feliz, uno
   de rechazo por restricción y uno de reintento o borde.

## Checklist antes de dar por terminado

Casi todo esto lo verifica una corrida, y **es la que decide**, no la lectura:

```bash
python3 scripts/verificar_boveda.py   # secciones, errores, gherkin, alternativos,
                                      # reciprocidad, cobertura de entidades e índices
```

- [ ] `verificar_boveda.py` termina en `TODO OK`.
- [ ] Está en el índice `docs/CasosDeUso/_CasosDeUso.md` (tabla del área correcta).
- [ ] Todos los `[[enlaces]]` resuelven (correr la verificación de la skill `boveda-modelo`).
- [ ] Cada restricción citada existe en `docs/Restricciones.md`; si no, se agrega
      con la skill `restriccion`.
- [ ] Si el caso nace de una norma, la fila correspondiente de
      `docs/Cumplimiento.md` lo referencia.
- [ ] Las entidades que menciona existen en el modelo; si falta alguna, se agrega
      con la skill `boveda-modelo` **antes** de terminar el caso.
- [ ] Están las cuatro secciones de implementación: contrato, descomposición,
      eventos y interfaz.
- [ ] Cada error del contrato corresponde a un criterio de aceptación o a una
      restricción citada. Un código de error sin prueba es decorativo.
- [ ] `_CasosDeUso.md` refleja el total y el caso aparece en su rango.

## Errores frecuentes

- Describir la interfaz en vez del flujo de datos.
- Omitir qué pasa si el proveedor externo no responde.
- Decir "se valida que…" sin decir **quién** valida: aplicación o base de datos.
  Si la regla protege dinero o cumplimiento, va también en la base.
- Inventar nombres de tabla que no existen en `docs/Modelos/Entidades/`.
