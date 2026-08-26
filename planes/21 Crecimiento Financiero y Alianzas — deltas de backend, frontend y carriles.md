---
tags:
  - plan
  - saneamiento
  - maqueta
  - crecimiento-financiero
titulo: "Crecimiento Financiero y Alianzas — deltas de backend, frontend y carriles"
fecha: 2026-08-25
alcance: docs/Views/Maqueta-Crecimiento/* · módulos M15–M18 (aún no creados) · planes/18 · planes/informe.md
depende_de: [M06, M14]
---

# Crecimiento Financiero y Alianzas · deltas de backend, frontend y carriles

> **Qué es este documento.** El resultado de conciliar la
> [[README|Maqueta de Crecimiento Financiero y Alianzas]] contra el modelo ya
> existente en la bóveda, hecho el 2026-08-25. La maqueta listaba `Badge` y
> `UserBadge` como entidades nuevas de los futuros módulos **M15–M18**. No lo son:
> el módulo **06 · Transparencia y Reputación** ya resuelve exactamente eso desde
> antes de que existiera esta maqueta. `docs/Views/Maqueta-Crecimiento/README.md` e
> `index.html` ya llevan la corrección puntual (advertencia en la lista de
> entidades, fila de la tabla del modelo). Este documento es el delta completo:
> qué cambia en el backend cuando M15–M18 se especifiquen, qué cambia en el
> frontend (nada) y qué cambia en la planificación de carriles.

> [!important] Esto no es la especificación de M15–M18
> Las fichas `.puml`, los casos de uso, las restricciones de base y el DDL de
> **M15 · Mercado de turnos**, **M16 · Alianzas comerciales**, **M17 · Vales** y
> **M18 · Inversión** siguen sin existir — eso es trabajo aparte, posterior a este
> documento (el propio README de la maqueta lo dice: "llevarlo a la bóveda es el
> paso siguiente"). Lo que fija acá es **con qué del modelo actual tienen que
> encajar** cuando se escriban, para que nadie repita la reconciliación.

---

## 1 · Delta de backend

### 1.1 · La lista de entidades nuevas, corregida

| Antes (maqueta v1) | Después | Motivo |
| --- | --- | --- |
| `Badge` | — (no se crea) | Ya existe `insignia_logro` en M06 |
| `UserBadge` | — (no se crea) | Ya existe `insignia_otorgada` en M06 |

El resto de la lista de `index.html §26` no cambia: `UserFinancialProfile`,
`TurnMarketplaceOffer`, `TurnExchange`, `SavingGoal`, `Partner`, `PartnerUser`,
`Partnership`, `CommercialCampaign`, `CommercialPublication`, `Voucher`,
`VoucherInstance`, `VoucherRedemption`, `InvestmentPartner`, `InvestmentProduct`,
`UserInvestment`, `InvestmentTransaction` — ninguna de estas tiene con qué
solaparse en el modelo actual, así que sí son altas genuinas cuando se escriba
M15–M18.

`Partner`, `CommercialCampaign` y `CommercialPublication` ya tenían su fila de
reutilización de M14 en `index.html §modelo` (`socio_comercial` + `anunciante`,
`campana_publicitaria`, `anuncio`/`pieza_creativa`); esa tabla queda igual, no la
toca esta corrección.

### 1.2 · El error de precisión en la advertencia ya aplicada

El texto de advertencia agregado a `README.md` dice que el nivel Bronce/Plata/
Oro/Diamante "es el mismo dato que `puntaje_reputacion.nivel`". Esa columna no
existe: se llama **`nivel_confianza`** ([[puntaje_reputacion]]), con siete
valores (`SIN_HISTORIAL`, `EN_OBSERVACION`, `BASICO`, `CONFIABLE`,
`MUY_CONFIABLE`, `REFERENTE`, `RESTRINGIDO`), no cinco. Se corrige en este mismo
commit (§4).

### 1.3 · El mapa de niveles — la decisión que la advertencia no resolvía

Decir "es el mismo dato" no alcanza: la maqueta tiene **cuatro** insignias de
nivel (Bronce/Plata/Oro/Diamante) y `nivel_confianza` tiene **siete** valores.
Hace falta el mapa explícito, y quedó sin escribir. Se resuelve con el criterio
que ya está dibujado en `app/mercado-turnos.html` (la tabla de umbrales del
mercado de turnos) y `backoffice/riesgo.html` (la misma tabla, vista de riesgo):

| `nivel_confianza` | Insignia visible | Por qué |
| --- | :-: | --- |
| `SIN_HISTORIAL` | *(ninguna)* | Cuenta nueva (D-9 de [[20 Maqueta de referencia · deltas del frontend]]): sin insignia hasta el primer evento de reputación |
| `EN_OBSERVACION` | **Bronce** | Coincide con el criterio de la maqueta: "primer pasanaku en curso, sin mora" — todavía en observación |
| `BASICO` | **Plata** | Coincide con "1 pasanaku completado" — primera prueba de cumplimiento cerrada |
| `CONFIABLE` | **Oro** | Coincide con "3 pasanakus sin incumplimientos" |
| `MUY_CONFIABLE` | **Oro** | Mismo tramo visible que `CONFIABLE`; la insignia es deliberadamente más gruesa que la escala interna — no todo salto de `nivel_confianza` necesita su propio ícono |
| `REFERENTE` | **Diamante** | Coincide con "alto cumplimiento y volumen acumulado" |
| `RESTRINGIDO` | *(ninguna, sin acceso al mercado)* | Es un estado de sanción, no un peldaño de progreso; alguien restringido no debe poder ceder ni adelantar turnos |

> [!warning] Esto es una decisión de producto, no un hecho verificable en el modelo
> `nivel_confianza` no tiene documentado a qué corresponde cada valor en
> términos de pasanakus completados — la correspondencia de arriba se dedujo de
> los criterios que la propia maqueta ya dibujó. Cuando se escriba M15–M18, quien
> lo especifique tiene que confirmar el mapa contra la definición real de
> `ModeloScoring` (M06) antes de fijarlo en código.

**Dónde vive el mapa, cuando se implemente.** No es una tabla ni una columna
nueva: es una función de proyección, del mismo lado que hoy expone
`esElegiblePara(grupo)`. Vive en `svc_transparencia`, no se duplica en
`svc_organizador` ni en el cliente móvil — la app pide el nivel visible, no
`nivel_confianza` crudo.

**Las comisiones por nivel son tarifa, no código.** `app/mercado-turnos.html` y
`backoffice/riesgo.html` fijan 10% (Bronce, Plata), 8% (Oro) y 6% (Diamante) de
comisión de intercambio. Eso es política de cobro — sigue la misma regla que
[[concepto_tarifa]] en M11: se define en un seeder, se versiona, y un cambio de
comisión no es un despliegue. Cuando se especifique M15, el concepto de tarifa
de intercambio de turno debe darse de alta ahí, no como constante en
`TurnExchange`.

---

## 2 · Delta de frontend

**Ninguno.** `docs/Views/Maqueta-Crecimiento/README.md` ya lo dice: "las
pantallas de la maqueta no cambian; lo que cambia es de dónde sale el dato". Se
verificó además que no hay una segunda confusión escondida:

- `Atomos.md` y `.claude/skills/web-backoffice/SKILL.md` también mencionan
  "Badge", pero es el átomo de UI genérico (`ok`/`warn`/`err`/`info`/`neutral`),
  sin relación con la entidad — **no requiere corrección**.
- Ninguna pantalla de `app/`, `partner/` ni `backoffice/` referencia `Badge` ni
  `UserBadge` directamente; todas usan las clases CSS `.nivel bronce/plata/oro/
  diamante`, que son presentación pura y ya reciben el nivel como si viniera de
  cualquier fuente. No hay nada que tocar cuando el backend real exista.

---

## 3 · Delta de carriles

**No existe ningún carril para M15–M18 todavía**, a diferencia de M13 y M14, que
sí tienen sus carriles reservados (`5A`/`F13` contabilidad ERP, `5B`/`F14`
publicidad — [[17 Plan de acción secuencial · coordinación de cinco máquinas]]
defecto 5, aplicado en [[18 Fichas de carril · las 38 unidades de trabajo]] y
`planes/informe.md`). Este documento **no** reserva carriles nuevos: hacerlo
ahora sería planificar trabajo sobre un módulo que todavía no tiene ni una
`.puml`, y el propio alcance de la maqueta dice explícitamente que eso es "el
paso siguiente", no este.

Lo que sí deja escrito, para cuando llegue ese paso siguiente:

1. **Dependencia dura con el carril de M06.** El carril `B` (ola 3, T4 —
   "06 transparencia", puesto P3 · Legion en `planes/informe.md`) tiene que
   estar **cerrado con gate ejecutado** antes de que un carril de M15 pueda
   consumir `insignia_logro`, `insignia_otorgada` o `puntaje_reputacion`. No es
   una dependencia de fase de papel: es una dependencia de tabla — sin
   `nivel_confianza` poblado no hay insignia de mercado que mostrar.
2. **Dependencia dura con el carril de M14.** Igual razonamiento para `5B`/`F14`
   (publicidad): un carril de M16 que reutilice `socio_comercial`, `anunciante`,
   `campana_publicitaria`, `anuncio`/`pieza_creativa`, `revision_creativa`,
   `segmento_audiencia` o `cuenta_publicitaria` no puede abrir antes de que M14
   esté cerrado.
3. **Cuando se reserven los carriles de M15–M18**, deben nombrarse siguiendo la
   convención ya usada para el hueco anterior (`5A`/`5B` backend,
   `F13`/`F14` frontend): los siguientes libres son `5C`–`5D`... o `6A`–`6D` para
   backend y `F15`–`F18` para frontend, a elección de quien las abra — este
   documento no fija el número, solo dice que **no puede ser antes** de los
   carriles de M06 y M14.
4. **La ficha de cada carril nuevo debe citar este documento** en su sección de
   dependencias, para que la reconciliación de niveles (§1.3) no se repita ni se
   contradiga entre M15 y M16.

---

## Ver también

[[README|Maqueta de Crecimiento Financiero y Alianzas]] ·
[[06_transparencia_reputacion|06 — Transparencia y Reputación]] ·
[[14_publicidad_campanas|14 — Publicidad y Campañas]] ·
[[20 Saneamiento del plan · huecos de la migración a microservicios]] ·
[[20 Maqueta de referencia · deltas del frontend]] ·
[[17 Plan de acción secuencial · coordinación de cinco máquinas]] ·
[[18 Fichas de carril · las 38 unidades de trabajo]]
