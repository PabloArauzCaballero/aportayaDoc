---
tags:
  - caso-uso
  - modulo/10-billetera-custodia-y-dinero-electronico
codigo: CU-16
criticidad: alta
actores: [Usuario, Operador]
normas: [ASFI Consumidor Financiero, Ley 393 (conservación)]
---

# CU-16 — Cerrar billetera y devolver saldo

> **Objetivo.** Que irse sea tan posible como entrar, con el saldo devuelto y sin
> que el cierre borre la historia que la ley obliga a conservar.

## Actores y disparador

- **Actor principal:** titular.
- **Actor secundario:** operador que valida.
- **Disparadores:** solicitud del titular; decisión de la entidad de terminar la
  relación (por ejemplo, tras [[CU-44 De alerta de monitoreo a reporte de operación sospechosa]]).

## Precondiciones

1. No hay [[retencion_saldo]] vigente distinta de la del propio retiro de cierre.
2. No hay [[bloqueo_saldo]] vigente.
3. No hay obligaciones abiertas: [[obligacion_aporte]] pendientes,
   [[deuda_participante]], [[cuenta_por_cobrar_comision]] ni grupos activos.

## Flujo principal

1. Se crea [[solicitud_cierre_billetera]] con `motivo`, `saldo_al_solicitar` y
   `destino_saldo` (`RETIRO` o `TRANSFERENCIA`).
2. El sistema ejecuta las validaciones de precondición y las muestra una por una:
   qué falta y por qué (no un "no se puede" genérico).
3. Se devuelve el saldo: se dispara [[CU-11 Retirar saldo]] y se guarda
   `orden_retiro_id`.
4. Confirmado el pago, **en la misma transacción**:
   - `cuenta_billetera.estado='CERRADA'` y `fecha_cierre`;
   - `solicitud_cierre_billetera.estado='EJECUTADA'`;
   - se emite `evento_dominio` `BILLETERA_CERRADA`.
5. El [[expediente_cliente]] queda con `retencion_hasta` recalculada desde el
   **último asiento contable**, no desde la fecha de cierre.
6. Se informa al titular que sus datos financieros se conservan por el plazo legal
   y que puede pedir su extracto histórico ([[CU-15 Emitir extracto y certificado de saldo]]).

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | Hay grupo activo con turnos pendientes | Se rechaza con detalle: el cierre exige resolver la posición en el grupo primero |
| 2b | Hay deuda | Se ofrece pagar o acordar; sin eso no hay cierre |
| 2c | Hay bloqueo de autoridad | Se rechaza y se informa el número de oficio |
| 3a | El saldo es menor al costo de retiro | Se ofrece transferencia interna o se exonera el costo por política ([[exencion_comision]]) |
| — | Cierre por decisión de la entidad | Se documenta la causal, se notifica y se devuelve el saldo igual |

## Postcondiciones

- Saldo en cero y cuenta `CERRADA`, o solicitud rechazada con motivo escrito.
- La historia contable permanece intacta y consultable.

## Contrato · `openapi/nucleo-financiero.yaml`

```ts
export const EntradaCU16 = z.object({
  cuentaBilleteraId: z.string().uuid(),
  motivo: z.string().min(10).max(200),
  destinoSaldo: z.enum(['RETIRO','TRANSFERENCIA']),
  instrumentoDestinoId: z.string().uuid().optional(),
}).strict()

export const SalidaCU16 = z.object({
  solicitudId: z.string().uuid(),
  saldoADevolver: MontoSchema,
  impedimentos: z.array(z.object({ tipo: z.string(), detalle: z.string() })),
  estado: z.enum(['SOLICITADA','APROBADA','EJECUTADA','RECHAZADA']),
}).strict()

export const ErroresCU16 = {
  OBLIGACIONES_ABIERTAS: 'AP-CU16-01',
  GRUPO_ACTIVO: 'AP-CU16-02',
  RETENCION_VIGENTE: 'AP-CU16-03',
  BLOQUEO_DE_AUTORIDAD: 'AP-CU16-04',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `OBLIGACIONES_ABIERTAS` | Hay aportes, deuda o comisiones por cobrar |
| `GRUPO_ACTIVO` | Participa en un grupo en curso |
| `RETENCION_VIGENTE` | Hay saldo retenido sin resolver |
| `BLOQUEO_DE_AUTORIDAD` | Existe un oficio vigente (R-BIL-13) |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `listarImpedimentos` | Devuelve todo lo que falta, no solo el primero; puro |
| Molécula | `SolicitudCierreRepositorio` | Alta y seguimiento |
| Molécula | `CuentaBilleteraRepositorio` | Cierre y verificación de saldo cero |
| Organismo | `CU16CerrarBilletera` | Transacción: devolución del saldo y cierre |
| Página | `POST /billetera/cierre` | Traduce y delega, sin lógica |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `billetera.cierre_solicitado` | Cálculo de impedimentos y aviso | `BILLETERA_OPERAR` |
| `billetera.cerrada` | Recalculo de retención documental del expediente | — |

## Interfaz

- **App:** *Cerrar mi cuenta*: lista de lo que falta resolver, en lugar de un no genérico.
- **Backoffice:** Cola de cierres con sus impedimentos, para acompañar al usuario.

## Restricciones aplicables

`R-BIL-13` · `R-BIL-02` · `R-AUD-08` · `R-CON-05` · `R-SEG-06`

## Evidencia que deja

[[solicitud_cierre_billetera]] · [[orden_retiro]] · [[cuenta_billetera]] ·
[[expediente_cliente]] · [[bitacora_evento]]

## Criterios de aceptación

```gherkin
Dado un titular sin obligaciones ni bloqueos
Cuando solicita el cierre y se le devuelve el saldo
Entonces cuenta_billetera queda CERRADA con saldo_total = 0

Dado un titular con un bloqueo_saldo vigente
Cuando solicita el cierre
Entonces la solicitud se rechaza indicando el número de oficio

Dada una cuenta cerrada
Cuando se consulta su historial dentro del plazo de conservación
Entonces los movimientos siguen disponibles
```

## Ver también

[[CU-07 Ejercer derechos sobre datos personales]] · [[CU-09 Cambiar credenciales y solicitar la baja]] · [[CU-11 Retirar saldo]]
