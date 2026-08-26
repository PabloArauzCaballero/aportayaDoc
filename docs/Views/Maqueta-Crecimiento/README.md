---
titulo: AportaYa — Maqueta de Crecimiento Financiero y Alianzas
tipo: maqueta
proyecto: AportaYa
version: 1
estado: propuesta
fecha: 2026-08-25
tags: [maqueta, producto, alianzas, mercado-de-turnos, vales, inversion, partners]
---

# Maqueta · Crecimiento Financiero y Alianzas

Prototipo HTML navegable de los cuatro pilares que llevan a AportaYa más allá de
administrar el ahorro colectivo: **mercado de turnos**, **valorización del turno**,
**alianzas comerciales con vales** e **inversión a través de entidades autorizadas**.

Abrir [`index.html`](index.html) en el navegador. Sin build, sin dependencias: usa el
mismo `estilos.css` y `script.js` del [[README|Sistema de Diseño]], más un
`maqueta.css` con los componentes que este módulo agrega.

## Qué es y qué no es

Esta maqueta define **qué se ve y con qué reglas**. No es la especificación ejecutable:
las fichas `.puml`, los casos de uso, las restricciones de base y el DDL de los módulos
nuevos (**M15–M18**) todavía no existen. Llevarlo a la bóveda es el paso siguiente.

## Cómo encaja con M13 y M14, que ya existen

La bóveda ya tiene **M14 · Publicidad y Campañas**, y se solapa con este módulo en más de
la mitad de su infraestructura. La diferencia está en el modelo de ingreso:

| | M14 · Publicidad | Este módulo · Alianzas |
| --- | --- | --- |
| Qué compra el comercio | Visibilidad | Ventas |
| Cómo se cobra | Pauta (impresión, clic) | Comisión sobre venta atribuida |
| Qué recibe el participante | Un anuncio | Un descuento real, en un vale |
| Quién asume el beneficio | Nadie: es publicidad | El comercio, en su margen |

Son complementarios, no rivales — pero **no hay que duplicar tablas**. Lo que este módulo
debe reutilizar de M14 en vez de crear de nuevo:

| Lo que la maqueta llama | Reutiliza de M14 |
| --- | --- |
| `Partner` | `socio_comercial` + `anunciante` |
| `CommercialCampaign` | `campana_publicitaria` |
| `CommercialPublication` | `anuncio` / `pieza_creativa` |
| Flujo de aprobación (§14.1) | `revision_creativa` |
| Segmento elegible | `segmento_audiencia` |
| Métricas del embudo | `impresion_anuncio` · `clic_anuncio` · `conversion_anuncio` |
| Facturación al partner | `cuenta_publicitaria` · `factura_publicidad` |

Lo que **sí es genuinamente nuevo** y no tiene dónde apoyarse: todo el mercado de turnos,
el vale como objeto con ciclo propio (`Voucher`, `VoucherInstance`, `VoucherRedemption`),
el acuerdo con comisión sobre venta (`Partnership`), el objetivo de ahorro (`SavingGoal`),
las insignias con efecto económico y el módulo de inversión completo.

> [!important] RN-18 sigue sin tocarse
> M14 es explícito: un anunciante **siempre paga, nunca cobra**, y el organizador no
> percibe ingreso por administrar. Este módulo respeta lo mismo: la comisión de una venta
> atribuida y la del mercado de turnos las cobra **la plataforma**, nunca el organizador
> del grupo.

## Las tres superficies

| Superficie | Pantallas | Quién la usa |
|---|---|---|
| App | 6 | El participante que ahorra |
| Portal Partner | 4 | El comercio aliado, en su propio dominio |
| Backoffice | 3 | El equipo interno de alianzas y de riesgo |

### App del participante

| Pantalla | Cubre |
|---|---|
| [Mi Dinero](app/mi-dinero.html) | §23 centro financiero · §24 «Tu dinero trabajando» · §36 |
| [Objetivo de ahorro](app/objetivo-ahorro.html) | §5 intención de uso · §17 · §30 privacidad |
| [Mercado de turnos](app/mercado-turnos.html) | §3 mercado · §4 valorización · §18 insignias |
| [Mis vales](app/mis-vales.html) | §9 transversalidad · §10 lado del usuario · §14 |
| [Tu turno llegó](app/que-hacer-con-tu-dinero.html) | §19 bifurcación · §27 flujo |
| [Invertir](app/invertir.html) | §20 · §21 marketplace · §22 vales financieros |

### Portal Partner

| Pantalla | Cubre |
|---|---|
| [Dashboard](partner/dashboard.html) | §13 portal · §28 flujo del partner |
| [Publicaciones](partner/publicaciones.html) | §14 · §14.1 aprobación · §15 rendimiento |
| [Validar vale](partner/vales.html) | §12 · §31 seguridad y doble canje |
| [Audiencia](partner/audiencia.html) | §16 intención agregada · §17 · §30 |

### Backoffice

| Pantalla | Cubre |
|---|---|
| [Alianzas](backoffice/alianzas.html) | §7 · §25 roles · §29 dashboard comercial |
| [Campañas y vales](backoffice/campanas.html) | §8 campañas · §11 emisión · §14.1 |
| [Riesgo de permutas](backoffice/riesgo.html) | §3.5 motor de riesgo · §32 monetización |

## Decisiones de producto que la maqueta fija

Estas no estaban en el documento de origen: son las que hubo que resolver para que las
pantallas fueran dibujables sin contradecir el modelo ya existente.

1. **Ceder se habilita antes que adelantar.** Ceder el turno no agrega riesgo al grupo;
   adelantarlo sí. Esa asimetría es la que ordena toda la tabla de niveles.
2. **`EN_VALIDACION` va después de `ACEPTADA`.** Validar antes obligaría a correr el motor
   de riesgo contra cada interesado hipotético. Como consecuencia, el dinero de la
   compensación recién se mueve en `EJECUTADA`.
3. **El tope de compensación es el límite regulatorio del pilar.** Sin tope, compensar
   cinco meses de espera es una tasa de interés, y el producto pasa a ser intermediación
   crediticia.
4. **Una campaña publicada solo puede mejorar.** Extender vigencia y ampliar segmento, sí;
   recortar o cambiar el beneficio con vales emitidos, no. Para eso hay que suspender y
   proponer otra.
5. **El presupuesto corta la emisión, nunca el canje.** Un vale en manos de alguien es una
   obligación asumida.
6. **El beneficio se congela en el canje.** Si la campaña baja del 8% al 6%, el vale ya
   usado siguió valiendo lo que valía ese día — misma regla que el tarifario congelado
   por grupo.
7. **Umbral mínimo de 50 personas por segmento.** Por debajo, cruzar filtros deja de ser
   estadística y pasa a ser identificación.
8. **Quien declara «pago de deuda» no recibe oferta comercial.** Es una regla de producto,
   no un detalle de diseño.
9. **Retirar es siempre la primera opción.** La promesa de marca es «tu plata, a tu
   alcance»: si la pantalla del turno empujara a no retirar, contradiría el
   posicionamiento y se leería como retención indebida de fondos de terceros.
10. **Un vale financiero actúa sobre el precio del servicio, nunca sobre el rendimiento
    del instrumento.** Es la frontera que separa bonificar una comisión de prometer un
    retorno.
11. **El motor de riesgo no decide solo.** El 88% se resuelve automático; el resto lo mira
    una persona y deja constancia firmada del motivo, igual que el debido proceso de las
    sanciones.
12. **Comercial y riesgo separados.** Quien tiene meta de venta no aprueba límites de
    exposición.

## Métrica norte

No «cuánto dinero pasó por los pasanakus» —eso mide volumen— sino **cuánto valor económico
adicional generó la plataforma para sus usuarios**. Es la única cifra que un participante
puede comparar contra la comisión que paga.

## Entidades nuevas

`UserFinancialProfile` · `TurnMarketplaceOffer` · `TurnExchange` · `SavingGoal` ·
`Partner` · `PartnerUser` · `Partnership` · `CommercialCampaign` ·
`CommercialPublication` · `Voucher` · `VoucherInstance` · `VoucherRedemption` ·
`InvestmentPartner` · `InvestmentProduct` · `UserInvestment` · `InvestmentTransaction`

Roles nuevos: `COMMERCIAL_PARTNER` · `PARTNER_MANAGER` · `BACKOFFICE_COMMERCIAL` ·
`BACKOFFICE_RISK`

> [!warning] Corrección post-conciliación (ver `planes/21 Crecimiento Financiero y Alianzas — deltas de backend, frontend y carriles.md`)
> Esta lista tenía `Badge` y `UserBadge` como entidades nuevas. **No lo son**: el
> modelo ya tiene `insignia_logro` / `insignia_otorgada` (M06, `svc_transparencia`),
> y el nivel Bronce/Plata/Oro/Diamante de esta maqueta **es el mismo dato** que
> `puntaje_reputacion.nivel_confianza` (`EN_OBSERVACION`/`BASICO`/`CONFIABLE`/
> `MUY_CONFIABLE`/`REFERENTE`, con `SIN_HISTORIAL` y `RESTRINGIDO` fuera de la
> escala visible), con otro nombre de cara al usuario — no una escala nueva. Las
> pantallas de la maqueta no cambian; lo que cambia es de dónde sale el dato. El
> mapa exacto queda en `planes/21 Crecimiento Financiero y Alianzas — deltas de backend, frontend y carriles.md` §1.3.

## Estructura

```
Maqueta-Crecimiento/
├── README.md              ← esta nota
├── index.html             ← índice navegable, pilares, flujos, modelo y fases
├── maqueta.css            ← componentes propios (vale, oferta, medidor, embudo…)
├── maqueta.js             ← interacciones propias
├── qr.svg                 ← QR decorativo (no codifica nada)
├── app/                   ← 6 pantallas móviles
├── partner/               ← 4 pantallas del portal del comercio
└── backoffice/            ← 3 pantallas del equipo interno
```

## Dónde se abre esto

Desde [`docs/Views/index.html`](../index.html), el índice de todas las vistas del proyecto,
junto a la [[AportaYa-Maqueta|maqueta navegable]] del producto núcleo. Hasta la versión 1 de
esta maqueta no había ningún enlace hacia acá: se abría solo si alguien conocía la ruta.

## Lo que este módulo le debe al producto núcleo

§18 pide que las insignias tengan **efecto económico**, y ahí engancha con una pregunta que el
producto núcleo tenía abierta: cuánto premiar a alguien que sube de nivel. La respuesta está
en la pantalla *Tu nivel* de la maqueta navegable, y es la que habilita este módulo: un bono en
efectivo obliga a depositar el equivalente en la cuenta de custodia —el saldo es pasivo exigible
con respaldo uno a uno—, mientras que **un beneficio que paga un comercio aliado no toca la
custodia en absoluto**. Es la razón económica por la que las alianzas resuelven algo que la
plataforma sola no puede pagar.

Relacionado: [[AportaYa-Maqueta]] · [[AportaYa-Identidad]] · [[README|Sistema de Diseño]] · [[Index|Bóveda del modelo]]
