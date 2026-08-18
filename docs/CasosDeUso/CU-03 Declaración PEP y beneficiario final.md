---
tags:
  - caso-uso
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
codigo: CU-03
criticidad: alta
actores: [Usuario, Oficial de cumplimiento]
normas: [UIF EBR]
---

# CU-03 — Declaración PEP y beneficiario final

> **Objetivo.** Saber si el cliente es una persona expuesta políticamente —o
> familiar o allegado— y quién controla realmente la cuenta, y que esa respuesta
> tenga fecha, autor y evidencia.

## Actores y disparador

- **Actor principal:** usuario, al registrarse y en cada revisión periódica.
- **Actor secundario:** oficial de cumplimiento, que verifica.
- **Disparador:** alta ([[CU-01 Registro y apertura de billetera]]), revisión
  periódica ([[CU-06 Revisión periódica de conocimiento del cliente]]) o cambio
  declarado por el usuario.

## Precondiciones

1. El usuario está autenticado y su registro está en curso o vigente.

## Flujo principal

1. Se presenta la declaración con las cinco categorías: `NACIONAL`, `EXTRANJERO`,
   `ORG_INTERNACIONAL`, `FAMILIAR`, `ALLEGADO`.
2. El usuario responde. Se crea [[declaracion_pep]] con `es_pep`, y si es
   afirmativa: `tipo_pep`, `cargo`, `institucion`, `pais`, `desde`, `hasta`.
3. Si el titular es una organización o el grupo tiene personería, se declaran los
   [[beneficiario_final]] con `porcentaje_participacion` y `tipo_control`.
4. Se cotejan los nombres declarados contra [[lista_restrictiva_externa]].
5. **Si `es_pep = true`**, en la misma transacción:
   - se fuerza `debida_diligencia.tipo='REFORZADA'`;
   - la [[calificacion_riesgo_cliente]] sube a `ALTO` con `motivo_cambio='PEP'`;
   - se reduce la periodicidad de revisión;
   - se marca el usuario para monitoreo intensificado en [[regla_monitoreo_lft]].
6. El oficial de cumplimiento verifica (`verificada_por`, `evidencia_url`).

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | El usuario declara no ser PEP pero el cotejo devuelve coincidencia | Se abre [[coincidencia_lista]] y [[caso_investigacion_lft]]; la declaración falsa es en sí un factor de riesgo |
| 5a | Deja de ser PEP (`hasta` cumplido) | La condición **no se borra**: se cierra con `hasta` y la recalificación se evalúa en la próxima revisión |
| 3a | El beneficiario final no coincide con el titular | Se exige documentación adicional antes de habilitar operaciones |
| 4a | La coincidencia con la lista resulta homónima | Se descarta con evidencia y firma del oficial de cumplimiento. **La descartada también se guarda**: la próxima vez no se investiga desde cero |
| — | El beneficiario final es a su vez PEP | La condición se propaga al titular: el riesgo se hereda hacia arriba, no se diluye en la estructura |

## Postcondiciones

- Existe una declaración PEP fechada por usuario, con su histórico completo.
- Un PEP nunca queda con debida diligencia menor a reforzada.

## Contrato · `openapi/cumplimiento.yaml`

```ts
export const EntradaCU03 = z.object({
  usuarioId:  z.string().uuid(),
  esPep:      z.boolean(),
  tipoPep:    z.enum(['NACIONAL','EXTRANJERO','ORG_INTERNACIONAL','FAMILIAR','ALLEGADO']).optional(),
  cargo:      z.string().max(120).optional(),
  institucion:z.string().max(120).optional(),
  beneficiariosFinales: z.array(z.object({ nombre: z.string(), documento: z.string(), porcentaje: z.string() })).optional(),
}).strict()

export const SalidaCU03 = z.object({
  declaracionId: z.string().uuid(),
  exigeDiligenciaReforzada: z.boolean(),
  nivelRiesgo:   z.enum(['BAJO','MEDIO','ALTO']),
}).strict()

export const ErroresCU03 = {
  DECLARACION_INCOMPLETA: 'AP-CU03-01',
  BENEFICIARIO_SIN_DOCUMENTO: 'AP-CU03-02',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `DECLARACION_INCOMPLETA` | Dice ser PEP pero no informa cargo ni institución |
| `BENEFICIARIO_SIN_DOCUMENTO` | Falta identificar a un beneficiario final |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `clasificarPep` | Traduce la declaración a nivel de riesgo; puro |
| Molécula | `DeclaracionPepRepositorio` | Persistencia de la declaración y sus vínculos |
| Molécula | `ListaRestrictivaRepositorio` | Cotejo del nombre declarado |
| Organismo | `CU03DeclararPep` | Transacción: declaración, recalificación y marcado de monitoreo |
| Página | `POST /usuarios/:id/pep` | Traduce y delega, sin lógica |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `pep.declarado` | Recalificación de riesgo y monitoreo intensificado | `PARTICIPANTE` |
| `pep.verificado` | Cierre de la verificación por cumplimiento | `OFICIAL_CUMPLIMIENTO` |

## Interfaz

- **App:** Formulario con las cinco categorías explicadas en lenguaje llano, no en jerga normativa.
- **Backoffice:** Panel de PEP con su verificación y la fecha de la próxima revisión.

## Restricciones aplicables

`R-UIF-10` · `R-UIF-11` · `R-SEG-04` · `R-AUD-08`

## Evidencia que deja

[[declaracion_pep]] · [[beneficiario_final]] · [[coincidencia_lista]] ·
[[calificacion_riesgo_cliente]] · [[bitacora_evento]]

## Criterios de aceptación

```gherkin
Dado un usuario que declara ser PEP nacional
Cuando se guarda la declaración
Entonces su debida_diligencia queda en tipo REFORZADA
Y su calificacion_riesgo_cliente vigente tiene nivel ALTO

Dado un usuario que declaró no ser PEP
Y existe una coincidencia_lista confirmada con su nombre
Cuando se evalúa su perfil
Entonces se abre un caso_investigacion_lft

Dado un usuario que declara ser PEP sin informar cargo ni institución
Cuando intenta guardar la declaración
Entonces se rechaza con DECLARACION_INCOMPLETA
Y no se crea ninguna declaracion_pep

Dado un beneficiario_final declarado que es PEP extranjero
Cuando se guarda la estructura de control
Entonces la debida_diligencia del titular queda en REFORZADA
```

## Ver también

[[CU-01 Registro y apertura de billetera]] · [[CU-06 Revisión periódica de conocimiento del cliente]] · [[CU-44 De alerta de monitoreo a reporte de operación sospechosa]]
