---
tags:
  - caso-uso
  - modulo/02-grupos-cupos-turnos-y-gobernanza
  - modulo/06-transparencia-y-reputacion
codigo: CU-61
criticidad: media
actores: [Participante, Cualquier tercero]
normas: [Transparencia con el consumidor]
---

# CU-61 — Verificar públicamente el sorteo

> **Objetivo.** Que la frase *"el sorteo fue limpio"* deje de ser una afirmación de
> la plataforma y pase a ser algo que **cualquiera comprueba por su cuenta**, sin
> pedirnos permiso y sin confiar en nosotros.

## Actores y disparador

- **Actor principal:** un participante, o cualquier tercero con el enlace.
- **Disparador:** el sorteo pasó a `REVELADO` y se publicó el resultado.

## Precondiciones

1. Existe [[sorteo_turnos]] en estado `REVELADO` con `semilla_revelada` publicada.

## Flujo principal

1. El verificador obtiene el paquete público: `hash_semilla`, `semilla_revelada`,
   los aportes de entropía, el `metodo` y la lista de cupos en su orden original.
2. Recomputa `SHA256(semilla || entropías)` y lo compara con el hash comprometido.
3. Recomputa el barajado determinista con la semilla y compara el orden resultante
   con los [[turno]] publicados.
4. Se registra la consulta en [[verificacion_publica]] con el resultado y el
   `hash_verificado` — **también cuando falla**.
5. Se devuelve un veredicto legible: coincide o no, y en qué paso se rompió.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | El hash no coincide | Veredicto negativo con el detalle. Se abre [[incidente_operativo]] automáticamente: si la verificación pública falla, es un problema nuestro, no del verificador |
| 3a | El orden recomputado difiere | Igual que arriba, señalando el primer cupo que difiere |
| 1a | El sorteo aún está `COMPROMETIDO` | Se devuelve solo el hash y la fecha de revelado: **antes del revelado no hay nada que verificar**, y ese es justamente el punto |
| — | Verificación desde fuera de la plataforma | El algoritmo está publicado; el paquete se descarga en JSON y se puede verificar con veinte líneas de cualquier lenguaje |

## Postcondiciones

- Queda registro de cada verificación, con quién y cuándo.
- Un resultado negativo nunca queda silencioso.

## Contrato · `openapi/transparencia.yaml`

```ts
export const EntradaCU61 = z.object({
  sorteoId: z.string().uuid(),
}).strict()

export const SalidaCU61 = z.object({
  verifica:      z.boolean(),
  hashEsperado:  z.string().length(64),
  hashRecomputado: z.string().length(64),
  ordenCoincide: z.boolean(),
  primerCupoDiscrepante: z.number().int().nullable(),
  paquete: z.object({
    semilla:    z.string().length(64),
    entropias:  z.array(z.string()),
    metodo:     z.string(),
    cupos:      z.array(z.number().int()),
  }),
}).strict()

export const ErroresCU61 = {
  SORTEO_NO_REVELADO: 'AP-CU61-01',
  SORTEO_INEXISTENTE: 'AP-CU61-02',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SORTEO_NO_REVELADO` | El sorteo está `COMPROMETIDO`: se devuelve el hash y la fecha, y nada más |
| `SORTEO_INEXISTENTE` | El grupo no llegó a sortear turnos |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `verificarCompromiso` | El mismo átomo que usa [[CU-60 Sortear los turnos]]: una sola implementación para generar y para verificar |
| Átomo | `barajarDeterminista` | Idem |
| Molécula | `SorteoRepositorio` · `VerificacionRepositorio` | Lectura del sorteo, escritura de la verificación |
| Organismo | `CU61VerificarSorteo` | Compara y registra |
| Página | `GET /publico/sorteos/:id/verificacion` | Ruta pública, sin sesión |

> **El átomo se comparte a propósito.** Si la verificación usara otra
> implementación, estaríamos comprobando que dos códigos distintos coinciden, no
> que el sorteo es correcto.

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `sorteo.verificado` | Nada si coincide | Ninguno: **ruta pública** |
| `sorteo.verificacion_fallida` | Incidente operativo + aviso al equipo | — |

## Interfaz

- **App:** botón **Verificar** en la pantalla del turno; muestra el veredicto en
  lenguaje llano y deja copiar el paquete.
- **Público:** página sin sesión con el paquete descargable en JSON y el
  pseudocódigo del algoritmo.

## Restricciones aplicables

`R-AUD-01` · `R-AUD-02` · `R-GRP-06`

## Evidencia que deja

[[verificacion_publica]] · [[incidente_operativo]] (si falla)

## Criterios de aceptación

```gherkin
Dado un sorteo revelado y correcto
Cuando un tercero verifica
Entonces verifica es true y ordenCoincide es true
Y queda una fila en verificacion_publica

Dado un sorteo cuyo orden publicado fue alterado en la base
Cuando alguien verifica
Entonces verifica es false, se indica el primer cupo discrepante
Y se abre un incidente_operativo

Dado un sorteo aún comprometido
Cuando alguien intenta verificar
Entonces se responde SORTEO_NO_REVELADO con la fecha de revelado
```

## Ver también

[[CU-60 Sortear los turnos]] · [[CU-73 Verificar la cadena de transparencia]]
