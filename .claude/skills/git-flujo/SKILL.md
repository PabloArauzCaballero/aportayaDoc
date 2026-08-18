---
name: git-flujo
description: "Trabajar con el repositorio de AportaYa: ramas, mensajes de commit en español, qué entra en un PR, qué se verifica antes de abrirlo y qué nunca se commitea. Úsala antes de commitear, al abrir un PR, al elegir el nombre de una rama, o cuando un cambio toque a la vez la bóveda y el código."
---

# Ramas, commits y PR

```
main   ← lo estable
dev    ← integración; acá apuntan los PR
<usuario>/<tipo>/<tema>   ← el trabajo
```

Tipos de rama: `feature`, `fix`, `docs`, `chore`, `modelo`.
Ejemplos reales: `pablo/feature/boveda-obsidian`, `pablo/fix/verificacion-postgres`.

**Nunca se commitea directo a `main` ni a `dev`.** Si el trabajo empezó en una de
esas, se crea la rama antes de commitear.

## Mensajes de commit

En **español**, con prefijo, en imperativo o descriptivo breve, y **explicando el
porqué cuando no es obvio**:

```
docs: bóveda de Obsidian con el modelo de datos navegable
modelo: el organizador no cobra comisión (RN-18)
fix: tres defectos que aparecieron al verificar contra PostgreSQL sembrado
feat: billetera con custodia, comisión de plataforma y cumplimiento ASFI
chore: ignorar ruido local y versionar la config de plugins de Obsidian
```

| Prefijo | Para |
| --- | --- |
| `feat` | Funcionalidad nueva |
| `fix` | Corrección |
| `modelo` | Cambio en `docs/entidades/*.puml` y lo que se genera de ahí |
| `docs` | Bóveda, casos de uso, restricciones, cumplimiento |
| `chore` | Herramientas, configuración, scripts |
| `test` | Pruebas solas |

Un commit que dice `fix: arreglos varios` es un commit que nadie va a poder
revertir con confianza. Si hace falta esa frase, son varios commits.

## Qué entra en un PR

**Un cambio, completo.** Completo significa que el vínculo especificación ↔ código
no queda roto:

| Si el PR toca… | También trae |
| --- | --- |
| Un `.puml` | La bóveda y el SQL regenerados, y la ficha del módulo actualizada |
| Un caso de uso | Sus restricciones citadas, si son nuevas |
| Una restricción | Su prueba de rechazo y su consulta de verificación |
| Un catálogo | El JSON en `seeders/`, el manifiesto y el README |
| Código de un caso de uso | Su contrato en `openapi/ del servicio/` y sus pruebas |
| Un cambio de precio o de plazo regulatorio | La fila con vigencia y su cita normativa |

Un PR que cambia el código y deja la bóveda vieja crea dos verdades. En este
proyecto la bóveda **es** la especificación: si divergen, gana la bóveda y el
código está mal.

## Antes de abrir el PR

```bash
python3 scripts/generar_boveda.py     # "sin_resolver": []
python3 scripts/generar_ddl.py        # "Sin pendientes a nivel de datos."
```

Y contra una base real, recién creada:

```bash
psql -d aportaya -v ON_ERROR_STOP=1 -f sql/aplicar.sql
psql -d aportaya -v ON_ERROR_STOP=1 -f sql/60_semillas/sembrar.sql
psql -d aportaya -f sql/50_verificacion/prueba_humo.sql    # todo OK, cero FALLA
```

- [ ] Los generadores terminan sin pendientes.
- [ ] La prueba de humo da todo OK sobre base nueva.
- [ ] Las consultas de verificación de `docs/Restricciones.md` devuelven cero filas.
- [ ] Los enlaces `[[...]]` de la bóveda resuelven (verificación de `boveda-modelo`).
- [ ] `git status` no muestra archivos generados sin querer ni basura local.

## Descripción del PR

Corta y en español, con:

1. **Qué cambia**, en una frase.
2. **Por qué**, si no es evidente: la norma, el incidente o la decisión que lo
   originó.
3. **Qué se verificó**, con el resultado real —no "probado localmente", sino "68 OK,
   0 FALLA sobre PostgreSQL 16 recién sembrado".
4. **Qué queda pendiente**, si algo queda. Explícito, no implícito.

Los números que se ponen ahí son los que salieron, no los que se esperaban.

## Qué nunca se commitea

| Nunca | Por qué |
| --- | --- |
| Credenciales, tokens, claves de proveedor | Aunque sean de prueba: se normaliza y un día una es real |
| Datos personales reales, aunque sean del equipo | El repositorio no tiene el control de acceso del sistema |
| Volcados de base con datos de producción | Lo mismo, peor |
| `.env` con valores | Va el `.env.example` con las claves y sin los valores |
| Archivos generados que el generador produce | Salvo los que el proyecto versiona a propósito (`sql/`, `docs/Modelos/`) |
| Configuración personal del editor | Ruido para todos los demás |

Si una credencial se coló en un commit, no alcanza con borrarla en el siguiente:
**se rota la credencial**. El historial de git es público para quien tenga el repo.

## Ver también

`implementar-desde-boveda` · `boveda-modelo` · `revision-codigo` · `pruebas-cu` ·
`semillas-catalogos`
