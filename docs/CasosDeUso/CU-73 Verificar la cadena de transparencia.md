---
tags:
  - caso-uso
  - modulo/06-transparencia-y-reputacion
codigo: CU-73
criticidad: media
actores: [Participante, Auditor, Cualquier tercero]
normas: [Transparencia, evidencia auditable]
---

# CU-73 — Verificar la cadena de transparencia

> **Objetivo.** Que auditar el grupo no dependa de que nosotros abramos la base.
> Con los bloques publicados, **cualquiera recorre la cadena y detecta si algo fue
> alterado y desde qué bloque**.

## Actores y disparador

- **Actor principal:** participante, auditor interno o externo, o un tercero.
- **Disparadores:** botón en la app; revisión de auditoría; control diario
  automático.

## Precondiciones

1. El grupo tiene al menos un [[bloque_transparencia]] sellado.

## Flujo principal

1. Se recorren los bloques del grupo en orden ascendente.
2. Para cada uno se recomputan `hash_contenido` desde el contenido publicado y
   `hash_bloque` desde sus tres componentes, y se comprueba que `hash_anterior`
   coincida con el `hash_bloque` del previo.
3. Se devuelve el veredicto: cadena íntegra, o **el número del primer bloque que
   falla** y qué componente no cuadra.
4. Se registra la verificación en [[verificacion_publica]], también cuando falla.
5. Si falla, se abre [[incidente_operativo]] de severidad alta y se notifica al
   equipo: una cadena rota es un evento de riesgo operativo, no una curiosidad.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | Un bloque intermedio fue alterado | Falla ese bloque y **todos los siguientes**: el reporte señala el primero, que es el que importa |
| 2b | Falta un bloque en la secuencia | Se reporta el salto de numeración; un hueco es tan grave como una alteración |
| 1a | El grupo no tiene bloques | Se informa que aún no hay historia sellada, no un error |
| — | Verificación fuera de la plataforma | El algoritmo y el formato canónico están publicados; el paquete se descarga en JSON |
| — | El control diario detecta una cadena rota | Se congelan los sellados nuevos del grupo hasta resolver el incidente |

## Postcondiciones

- Queda constancia de quién verificó, cuándo y con qué resultado.
- Ninguna cadena rota queda sin incidente.

## Contrato · `openapi/transparencia.yaml`

```ts
export const EntradaCU73 = z.object({
  grupoCodigoPublico: z.string().max(20),
  desdeBloque: z.number().int().min(1).optional(),
}).strict()

export const SalidaCU73 = z.object({
  integra: z.boolean(),
  bloquesVerificados: z.number().int(),
  primerBloqueFallido: z.number().int().nullable(),
  componenteFallido: z.enum(['HASH_CONTENIDO','HASH_ANTERIOR','HASH_BLOQUE','SECUENCIA']).nullable(),
  ultimoSellado: z.string().datetime().nullable(),
}).strict()

export const ErroresCU73 = {
  GRUPO_INEXISTENTE: 'AP-CU73-01',
  SIN_BLOQUES:       'AP-CU73-02',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `GRUPO_INEXISTENTE` | El identificador no corresponde a ningún grupo publicado |
| `SIN_BLOQUES` | El grupo todavía no selló historia. **No es un error de integridad**, y así se informa |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `serializarCanonico` · `hashDeBloque` | **Los mismos átomos de [[CU-72 Sellar el bloque de transparencia]]**: una sola implementación para sellar y para verificar |
| Átomo | `recorrerCadena(bloques)` | Devuelve el primer fallo; puro |
| Molécula | `BloqueRepositorio` · `VerificacionRepositorio` | |
| Organismo | `CU73VerificarCadena` | Recorre, dictamina y registra |
| Página | `GET /publico/grupos/:codigo/verificacion` | Ruta pública |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `cadena.verificada` | Nada si está íntegra | Ninguno: ruta pública |
| `cadena.rota` | Incidente operativo de severidad alta + congelamiento de sellados | — |
| — | Control diario que verifica todas las cadenas activas | — |

## Interfaz

- **App:** *Grupo → Historia → Verificar*, con el veredicto en una línea.
- **Backoffice:** tablero con el estado de todas las cadenas y la fecha de la
  última verificación por grupo.

## Restricciones aplicables

`R-REP-04` · `R-AUD-02` · `R-AUD-03` · `R-AUD-09` · `R-AUD-10` · `R-RIS-01`

## Evidencia que deja

[[verificacion_publica]] · [[incidente_operativo]] y
[[evento_riesgo_operativo]] si falla

## Criterios de aceptación

```gherkin
Dada una cadena de cinco bloques sellados correctamente
Cuando se verifica
Entonces integra es true y bloquesVerificados es 5

Dado un contenido alterado en el bloque 3
Cuando se verifica
Entonces integra es false y primerBloqueFallido es 3
Y se abre un incidente_operativo de severidad alta

Dada una secuencia con el bloque 4 ausente
Cuando se verifica
Entonces el componente fallido es SECUENCIA
```

## Ver también

[[CU-54 Registrar un evento de riesgo operativo]] · [[CU-61 Verificar públicamente el sorteo]] · [[CU-72 Sellar el bloque de transparencia]] · [[CU-75 Emitir un certificado de reputación verificable]]
