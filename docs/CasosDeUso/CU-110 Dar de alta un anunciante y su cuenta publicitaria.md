---
tags:
  - caso-uso
  - modulo/14-publicidad-campanas
codigo: CU-110
criticidad: media
actores: [Organizador, Socio comercial, Operaciones]
normas: [Política comercial interna, ASFI Consumidor Financiero]
---

# CU-110 — Dar de alta un anunciante y su cuenta publicitaria

> **Objetivo.** Que un organizador o un negocio externo pueda empezar a
> publicitar dentro de la app con una cuenta propia, sin que eso le dé al
> organizador ningún ingreso por su rol de administrador (RN-18).

## Actores y disparador

- **Actor principal:** quien quiere anunciar — un [[organizador]] existente o
  un negocio externo nuevo.
- **Disparadores:** solicitud de servicio publicitario dentro de la app.

## Precondiciones

1. Si el anunciante es un `organizador`, ese `organizador` existe y está
   `HABILITADO` (módulo 07).
2. Si el anunciante es un negocio externo, se cuenta con su `numero_documento`.

## Flujo principal

1. Si corresponde, se da de alta el [[socio_comercial]] con estado
   `POSTULADO`; Operaciones lo verifica y lo pasa a `ACTIVO`
   (`verificado_por`).
2. Se crea el [[anunciante]] con `tipo` (`ORGANIZADOR` o `SOCIO_COMERCIAL`) y
   exactamente una de las dos referencias (`organizador_id` o
   `socio_comercial_id`), según el CHECK del modelo.
3. **En la misma transacción** se crea su [[cuenta_publicitaria]] en estado
   `ACTIVA`, con `limite_gasto_mensual` opcional y `saldo_consumido_mes = 0`.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 1a | El `socio_comercial` no pasa la verificación | Queda `SUSPENDIDO`; no se puede crear un `anunciante` sobre él |
| — | El `organizador` pierde su habilitación (módulo 07) mientras tiene campañas activas | Sus [[campana_publicitaria]] pasan a `PAUSADA`; el gasto ya devengado se sigue cobrando igual |
| — | Se intenta crear un `anunciante` con ambas referencias (`organizador_id` y `socio_comercial_id`) a la vez | El CHECK del modelo lo rechaza |
| — | Un anunciante se da de baja (`estado = 'DADO_DE_BAJA'`) | Sus campañas activas se pausan; su cuenta publicitaria pasa a `CERRADA` |

## Postcondiciones

- Todo anunciante activo tiene una cuenta publicitaria con la que financiar
  campañas; ningún organizador queda con un ingreso por ser publicitado.

## Contrato · `openapi/publicidad.yaml`

```ts
export const EntradaCU110 = z.object({
  tipo: z.enum(['ORGANIZADOR', 'SOCIO_COMERCIAL']),
  organizadorId: z.string().uuid().optional(),
  socioComercialNuevo: z.object({
    razonSocial: z.string().max(150),
    numeroDocumento: z.string().max(30),
    emailContacto: z.string().email(),
  }).optional(),
  limiteGastoMensual: MontoSchema.optional(),
}).strict()

export const SalidaCU110 = z.object({
  anuncianteId: z.string().uuid(),
  cuentaPublicitariaId: z.string().uuid(),
}).strict()

export const ErroresCU110 = {
  ORGANIZADOR_NO_HABILITADO: 'AP-CU110-01',
  TIPO_Y_REFERENCIA_INCONSISTENTES: 'AP-CU110-02',
  SOCIO_COMERCIAL_NO_VERIFICADO: 'AP-CU110-03',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `ORGANIZADOR_NO_HABILITADO` | El `organizador_id` referenciado no está `HABILITADO` |
| `TIPO_Y_REFERENCIA_INCONSISTENTES` | `tipo` no coincide con la referencia provista |
| `SOCIO_COMERCIAL_NO_VERIFICADO` | Se intenta crear el `anunciante` antes de que el socio comercial esté `ACTIVO` |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `validarTipoAnuncianteExclusivo` | Verifica la regla del CHECK antes de escribir; puro |
| Molécula | `SocioComercialRepositorio` | Alta y verificación de negocios externos |
| Molécula | `AnuncianteRepositorio` | Alta de anunciante y su cuenta publicitaria |
| Organismo | `CU110AltaAnunciante` | Transacción de alta |
| Página | `apps/backoffice` — alta de anunciantes | Formulario con selector de tipo, verificación de socio comercial |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `anunciante.creado` | Habilita crear campañas | `PUBLICIDAD_ANUNCIANTES` |
| `socio_comercial.verificado` | Habilita darlo de alta como anunciante | `PUBLICIDAD_ANUNCIANTES` |

## Interfaz

- **App:** Un organizador ve, dentro de su panel de grupo, la opción "Promocionar
  mi grupo", que dispara este alta si todavía no tiene cuenta publicitaria.
- **Backoffice:** Alta y verificación de socios comerciales externos.

## Restricciones aplicables

`R-PUB-01` · `R-PUB-02`

Es alta administrativa de un servicio comercial nuevo; `R-PUB-01` es la que
impide, a nivel de base, que un anunciante quede sin dueño o con dos.

## Evidencia que deja

[[socio_comercial]] · [[anunciante]] · [[cuenta_publicitaria]]

## Criterios de aceptación

```gherkin
Dado un organizador habilitado sin cuenta publicitaria
Cuando solicita anunciar su grupo
Entonces se crea un anunciante tipo ORGANIZADOR con su cuenta_publicitaria en estado ACTIVA

Dado un negocio externo nuevo
Cuando se da de alta como socio_comercial y Operaciones lo verifica
Entonces puede crearse un anunciante tipo SOCIO_COMERCIAL sobre él

Dado un intento de alta de anunciante con organizador_id y socio_comercial_id a la vez
Cuando se procesa la solicitud
Entonces el sistema devuelve TIPO_Y_REFERENCIA_INCONSISTENTES
```

## Ver también

[[CU-111 Crear y aprobar una campaña publicitaria]]
