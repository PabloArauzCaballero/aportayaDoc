---
tags:
  - caso-uso
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
codigo: CU-94
criticidad: alta
actores: [Comité de gobierno, Oficial de cumplimiento, Riesgos, Directorio]
normas: [ASFI gobierno corporativo, ASFI Res. 540/2025, UIF, Libro 3 Título V]
---

# CU-94 — Elevar una decisión al comité de gobierno

> **Objetivo.** Que las decisiones que no puede tomar una sola persona —aprobar una
> política, aceptar un riesgo, revocar una sanción— pasen por un órgano con
> composición, quórum y acta, y que la evidencia de que sesionó exista.

## Actores y disparador

- **Actor principal:** el comité que corresponda por materia.
- **Disparadores:** evaluación de riesgo de producto ([[CU-47 Evaluar el riesgo del producto antes de lanzarlo]]);
  apelación de sanción ([[CU-93 Sancionar al organizador y resolver su apelación]]);
  política interna nueva; observación del supervisor; sesión ordinaria por
  periodicidad.

## Precondiciones

1. Existe [[comite_gobierno]] del `tipo` requerido, activo, con
   `composicion_requerida`, `quorum_minimo` y `periodicidad_minima`.
2. Los miembros tienen roles vigentes y no son parte interesada en el asunto que se
   trata (`R-SEG-04`).
3. El asunto llega con expediente completo: antecedentes, opciones y recomendación.

## Flujo principal

1. Se convoca la sesión con el orden del día y el expediente de cada asunto,
   con la antelación que fija la política.
2. Se verifica quórum contra `quorum_minimo` y `composicion_requerida`: no alcanza
   con contar cabezas, tienen que estar los roles exigidos —cumplimiento, riesgos,
   negocio— según el tipo de comité.
3. Cada asunto se decide y se registra en [[acta_comite]] con lo tratado, la
   decisión, los fundamentos y **quién votó qué**, incluidas las abstenciones y sus
   motivos. Un acta sin disidencias registradas es un acta incompleta.
4. Quien tenga interés directo en un asunto **se abstiene y la abstención queda
   escrita**: es la única forma de demostrar después que no participó.
5. **En la misma transacción** que cierra el acta:
   - se aplican los efectos aprobados —política vigente, evaluación aprobada,
     sanción revocada—;
   - se crean los [[plan_accion_riesgo]] de los compromisos, con responsable y
     fecha;
   - se emite `evento_dominio` `comite.sesionado`.
6. Un control verifica que cada comité haya sesionado dentro de su
   `periodicidad_minima`. **No sesionar es un hallazgo**, y aparece como tal.
7. Los compromisos se siguen sesión a sesión: el primer punto del orden del día es
   siempre el estado de los pendientes de la anterior.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | No se alcanza el quórum | La sesión no se realiza; se reconvoca y **queda registrado el intento fallido**, que es información de gobierno |
| 2b | Falta un rol de la composición requerida | Los asuntos que exigen ese rol se posponen; los demás pueden tratarse |
| 3a | Decisión que excede la facultad del comité | Se eleva al directorio con la recomendación; el comité no se atribuye lo que no tiene |
| 4a | Todos los miembros tienen interés en el asunto | Se eleva a la instancia superior: un comité que no puede abstenerse no puede decidir |
| 5a | Un compromiso vence sin cumplirse | Escala en la sesión siguiente y se registra como [[hallazgo_auditoria]] |
| 6a | El comité no sesiona en su periodicidad | Hallazgo automático y alerta al directorio |
| — | Sesión extraordinaria por urgencia | Se permite con quórum reducido si la política lo prevé, y se ratifica en la ordinaria siguiente |
| — | Decisión que después resulta equivocada | No se reescribe el acta: se decide distinto en una sesión nueva, y ambas quedan |

## Postcondiciones

- Toda decisión de gobierno tiene acta con quórum, votos y fundamentos.
- Ningún efecto de una decisión se aplica sin el acta que lo respalda.

## Contrato · `openapi/cumplimiento.yaml`

```ts
export const EntradaCU94 = z.object({
  comiteTipo: z.enum(['CUMPLIMIENTO','RIESGOS','AUDITORIA','PRODUCTO','DIRECTORIO']),
  fechaSesion: z.string().datetime(),
  asistentes: z.array(z.object({
    usuarioId: z.string().uuid(), rol: z.string().max(40),
  })).min(1),
  asuntos: z.array(z.object({
    referenciaTipo: z.string().max(40),
    referenciaId: z.string().uuid(),
    resumen: z.string().max(500),
    decision: z.enum(['APROBAR','RECHAZAR','APROBAR_CON_CONDICIONES','ELEVAR','POSPONER']),
    fundamento: z.string().min(20).max(2000),
    votos: z.array(z.object({
      usuarioId: z.string().uuid(),
      voto: z.enum(['A_FAVOR','EN_CONTRA','ABSTENCION']),
      motivoAbstencion: z.string().max(200).nullable(),
    })),
    compromisos: z.array(z.object({
      descripcion: z.string().max(300),
      responsableId: z.string().uuid(),
      fechaLimite: z.string().date(),
    })),
  })).min(1),
}).strict()

export const SalidaCU94 = z.object({
  actaId: z.string().uuid(),
  quorumAlcanzado: z.boolean(),
  composicionCompleta: z.boolean(),
  asuntosResueltos: z.number().int(),
  asuntosPospuestos: z.number().int(),
  planesGenerados: z.array(z.string().uuid()),
  proximaSesionLimite: z.string().date(),
}).strict()

export const ErroresCU94 = {
  SIN_QUORUM:            'AP-CU94-01',
  COMPOSICION_INCOMPLETA:'AP-CU94-02',
  PARTE_INTERESADA:      'AP-CU94-03',
  FUERA_DE_FACULTAD:     'AP-CU94-04',
  ACTA_YA_CERRADA:       'AP-CU94-05',
  COMITE_INACTIVO:       'AP-CU94-06',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SIN_QUORUM` | Los asistentes no alcanzan `quorum_minimo` |
| `COMPOSICION_INCOMPLETA` | Falta un rol de `composicion_requerida` para ese asunto |
| `PARTE_INTERESADA` | Un votante tiene interés directo y no se abstuvo (`R-SEG-04`) |
| `FUERA_DE_FACULTAD` | El asunto excede lo que el comité puede decidir; se eleva |
| `ACTA_YA_CERRADA` | Se intenta modificar un acta cerrada (`R-AUD-01`) |
| `COMITE_INACTIVO` | El comité está dado de baja |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `verificarQuorum(asistentes, comite)` | Cantidad y composición; puro |
| Átomo | `computarVotos(votos, quorum)` | Resultado con abstenciones que no ponderan; puro |
| Átomo | `esParteInteresada(miembro, asunto)` | Regla de abstención; puro |
| Molécula | `ComiteRepositorio` · `ActaRepositorio` | Persistencia; el acta es *append-only* |
| Molécula | `EjecutorDeDecision` | Despacha el efecto según `referenciaTipo` |
| Organismo | `CU94CerrarActa` | Transacción: acta, efectos, planes y evento |
| Página | `POST /comites/:tipo/sesiones` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `comite.sesionado` | Ejecución de los efectos aprobados y creación de planes | `GOBIERNO_SESIONAR` |
| `comite.sin_quorum` | Reconvocatoria y registro para el control de periodicidad | — |
| `comite.periodicidad_incumplida` | Hallazgo de auditoría y alerta al directorio | — |
| — | Control de periodicidad y de compromisos vencidos | — |

## Interfaz

- **App:** sin pantalla.
- **Backoffice:** *Gobierno → Comités*: calendario de sesiones con su periodicidad,
  orden del día, actas históricas y el tablero de compromisos abiertos con su
  responsable y su fecha.

## Restricciones aplicables

`R-SEG-04` · `R-LIC-03` · `R-AUD-01` · `R-AUD-04` · `R-AUD-08` · `R-RIS-01`

## Evidencia que deja

[[comite_gobierno]] · [[acta_comite]] · [[plan_accion_riesgo]] ·
[[politica_interna]] · [[evaluacion_riesgo_producto]] · [[apelacion_sancion_org]] ·
[[hallazgo_auditoria]] · `evento_dominio`

## Criterios de aceptación

```gherkin
Dado un comité con quórum mínimo de tres y composición que exige cumplimiento
Cuando sesionan tres miembros sin el rol de cumplimiento
Entonces se rechaza con COMPOSICION_INCOMPLETA

Dada una sesión con quórum y una evaluación de producto aprobada
Cuando se cierra el acta
Entonces la evaluación queda VIGENTE en la misma transacción

Dado un miembro con interés directo en un asunto
Cuando emite voto a favor
Entonces se rechaza con PARTE_INTERESADA

Dado un comité que no sesiona dentro de su periodicidad mínima
Cuando corre el control
Entonces existe un hallazgo_auditoria abierto
```

## Ver también

[[CU-47 Evaluar el riesgo del producto antes de lanzarlo]] · [[CU-49 Designar al oficial de cumplimiento y capacitar]] · [[CU-54 Registrar un evento de riesgo operativo]] · [[CU-93 Sancionar al organizador y resolver su apelación]] · [[CU-98 Publicar el tablero de indicadores]]
