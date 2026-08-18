---
tags:
  - caso-uso
  - modulo/08-garantia-incumplimiento-cobranza-y-sanciones
  - modulo/03-aportes-pagos-qr-y-conciliacion
codigo: CU-67
criticidad: alta
actores: [Grupo, Organizador, Contabilidad]
normas: [Consumidor financiero, contabilidad]
---

# CU-67 — Disolver el grupo anticipadamente y liquidar

> **Objetivo.** Cerrar un grupo que no puede continuar **devolviendo a cada uno lo
> que le corresponde**, con una regla de reparto conocida de antemano y aritmética
> que cuadra al centavo. Es el peor momento del producto: si acá el número no
> cierra, no importa nada de lo anterior.

## Actores y disparador

- **Actor principal:** el grupo, por [[acuerdo]] de disolución.
- **Actores secundarios:** organizador, contabilidad, soporte.
- **Disparadores:** acuerdo aprobado; imposibilidad de reemplazo tras
  [[CU-66 Reemplazar a un participante moroso]]; mora generalizada; decisión de la
  plataforma por causa grave.

## Precondiciones

1. Existe [[acuerdo]] de tipo `DISOLUCION` aprobado con quórum, **o** una causal
   automática prevista en el [[reglamento_grupo]].
2. No hay [[entrega_fondo]] en curso: si hay una autorizada sin ejecutar, primero
   se resuelve.

## Flujo principal

1. Se crea [[disolucion_anticipada]] con `motivo`, `causal`, `acuerdo_id` y estado
   `EN_LIQUIDACION`. El grupo pasa a `SUSPENDIDO`: **deja de aceptar aportes**.
2. Se congela la posición: se calcula, por cupo, lo aportado, lo cobrado, la deuda
   viva, los recargos y lo cubierto por el fondo.
3. Se aplica el **orden de prelación** del reglamento, que se muestra desde el
   primer día del grupo y no se inventa acá:
   1. deudas exigibles de cada participante contra su propio saldo;
   2. reposición al [[fondo_garantia]] de las coberturas consumidas;
   3. costos de la disolución, si el reglamento los prevé;
   4. devolución a quienes **no cobraron** su turno, a prorrata de lo aportado;
   5. remanente, si lo hubiera, a prorrata entre todos.
4. **En una sola transacción**:
   - se crea una [[liquidacion_participante]] por cupo con su desglose;
   - se generan las [[transaccion_billetera]] de devolución;
   - se registran los [[asiento_contable]] correspondientes;
   - el grupo pasa a `DISUELTO_ANTICIPADAMENTE` y la cuenta de billetera del grupo
     queda en cero (`R-GRP-13`);
   - se emite `evento_dominio` `grupo.disuelto`.
5. Cada participante recibe su liquidación con el desglose completo y el saldo ya
   acreditado en su billetera.
6. Las deudas que no se pudieron compensar siguen vivas: pasan a
   [[gestion_cobranza]] y, si corresponde, a [[castigo_deuda]] con autorización.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 3a | Lo disponible no alcanza para devolver todo | Se aplica prorrata estricta y **se muestra el factor aplicado**: cada uno ve por qué recibe ese número |
| 2a | Hay una entrega autorizada sin ejecutar | Se ejecuta o se anula con [[CU-14 Reversar una transacción]] antes de liquidar; no se liquida con dinero en tránsito |
| 4a | La liquidación no cuadra contra el mayor | **La transacción no confirma.** Se abre incidente y se liquida solo cuando cuadra |
| — | Disolución con deudores que ya cobraron | Su devolución es cero y su deuda queda íntegra; el fondo absorbe lo que falte hasta su tope y subroga |
| — | Un participante no está de acuerdo | Puede reclamar ([[CU-52 Atender un reclamo en plazo]]); la liquidación no se detiene, pero el reclamo puede corregirla |

## Postcondiciones

- La cuenta del grupo quedó en cero y cada peso tiene destino explicado.
- Ninguna deuda desapareció por el solo hecho de disolver.

## Contrato · `openapi/garantia.yaml`

```ts
export const EntradaCU67 = z.object({
  claveIdempotencia: z.string().uuid(),
  grupoId:  z.string().uuid(),
  acuerdoId: z.string().uuid().optional(),
  causal:   z.enum(['ACUERDO','SIN_REEMPLAZO','MORA_GENERALIZADA','CAUSA_GRAVE']),
  motivo:   z.string().min(20).max(1000),
}).strict()

export const SalidaCU67 = z.object({
  disolucionId: z.string().uuid(),
  factorProrrata: z.string(),                 // "0.8734" si no alcanza
  liquidaciones: z.array(z.object({
    cupoNumero: z.number().int(),
    aportado: MontoSchema, cobrado: MontoSchema,
    deuda: MontoSchema, devuelto: MontoSchema,
  })),
  totalDevuelto: MontoSchema,
  saldoGrupoFinal: MontoSchema,               // debe ser "0.00"
}).strict()

export const ErroresCU67 = {
  SIN_ACUERDO_NI_CAUSAL:   'AP-CU67-01',
  ENTREGA_EN_CURSO:        'AP-CU67-02',
  LIQUIDACION_NO_CUADRA:   'AP-CU67-03',
  GRUPO_YA_DISUELTO:       'AP-CU67-04',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SIN_ACUERDO_NI_CAUSAL` | No hay acuerdo aprobado ni causal del reglamento que habilite disolver |
| `ENTREGA_EN_CURSO` | Hay una entrega autorizada sin ejecutar: no se liquida con dinero en tránsito |
| `LIQUIDACION_NO_CUADRA` | La suma de liquidaciones no coincide con la bolsa. **La transacción no confirma** (`R-GRP-13`) |
| `GRUPO_YA_DISUELTO` | Reintento sobre un grupo cerrado; se devuelve la liquidación existente |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `calcularPrelacion(posiciones, disponible, reglamento)` | El reparto completo; **puro y con pruebas de propiedad**: la suma de lo repartido nunca supera lo disponible |
| Átomo | `factorProrrata(disponible, aDevolver)` | Con redondeo declarado y residuo asignado, sin centavos perdidos |
| Molécula | `LiquidacionRepositorio` · `TransaccionBilleteraRepositorio` | |
| Organismo | `CU67DisolverGrupo` | Una transacción para toda la liquidación |
| Página | `POST /grupos/:id/disolucion` | |

> El átomo de prelación es el candidato número uno a pruebas de propiedad: para
> cualquier combinación de aportes, cobros y deudas, **lo repartido debe ser igual
> a lo disponible, ni un centavo más**.

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `grupo.en_liquidacion` | Aviso al grupo y bloqueo de aportes | `GRUPO_ADMINISTRAR` |
| `grupo.disuelto` | Liquidación individual a cada participante + cierre contable | `ADMIN_PLATAFORMA` |
| — | Trabajo de cobranza para las deudas que quedaron vivas | — |

## Interfaz

- **App:** *Grupo → Liquidación*, con el desglose personal y el factor de prorrata
  explicado en una línea.
- **Backoffice:** pantalla de disolución con el cuadre total antes de confirmar;
  no se puede ejecutar si el cuadre falla.

## Restricciones aplicables

`R-GRP-13` · `R-AUD-05` · `R-BIL-01` · `R-BIL-12`

## Evidencia que deja

[[disolucion_anticipada]] · [[liquidacion_participante]] por cupo ·
[[transaccion_billetera]] de devolución · [[asiento_contable]] ·
[[deuda_participante]] que sobrevive

## Criterios de aceptación

```gherkin
Dado un grupo con Bs 12.000 en la bolsa y seis cupos, tres ya cobrados
Cuando se disuelve
Entonces la suma de las devoluciones más las compensaciones de deuda es Bs 12.000
Y el saldo de la cuenta del grupo queda en 0.00

Dado que lo disponible no alcanza para devolver todo lo aportado
Cuando se liquida
Entonces se aplica un factor de prorrata único
Y la suma repartida es exactamente lo disponible, sin centavos sobrantes

Dada una liquidación que no cuadra contra el mayor
Cuando se intenta confirmar
Entonces la transacción no confirma y se abre un incidente
```

## Ver también

[[CU-24 Registrar el asiento contable de una operación]] · [[CU-29 Devolver los aportes del fondo de garantía]] · [[CU-63 Proponer y votar un acuerdo]] · [[CU-65 Retirarse de un grupo]] · [[CU-66 Reemplazar a un participante moroso]]
