# transparencia

**Que nadie tenga que creernos.** Este servicio existe para que las afirmaciones de la
plataforma —«el sorteo fue limpio», «esta persona cumple», «la historia del grupo no se
tocó»— se puedan comprobar sin pedirnos permiso y sin acceso a la base.

De ahí las dos únicas familias de rutas sin sesión de todo el sistema: `/publico` y
`/verificar`.

| Dato | Valor |
| --- | --- |
| Esquema | `transparencia` · rol `svc_transparencia` |
| Prefijos | `/reputacion` · `/publico` · `/verificar` |
| Casos de uso | CU-61, CU-70, CU-71, CU-72, CU-73, CU-74, CU-75, CU-76, CU-97 |
| Contrato | `src/main/resources/openapi/transparencia.yaml` — 17 rutas, 35 esquemas |
| Informe del carril | `planes/informes/carril-3B.md` — 10 huecos declarados |

## Las cinco decisiones que explican el resto

**1 · El átomo de verificación es el mismo que el de generación.** CU-61 verifica el
sorteo con `SorteoVerificable`, la clase que CU-60 usó para sortear. Si la verificación
tuviera su propia implementación, estaríamos comprobando que dos códigos nuestros
coinciden entre sí, no que el sorteo es correcto. Lo mismo entre CU-72 y CU-73 con
`CadenaDeBloques`.

**2 · La forma canónica es parte del contrato.** `ContenidoCanonico` ordena las claves,
escribe los importes como cadena y las fechas en UTC. Un hash solo sirve si dos
implementaciones producen el mismo; si el orden dependiera del `HashMap` de turno, dos
verificadores honestos obtendrían hashes distintos del mismo hecho y la cadena diría
que el grupo fue alterado cuando no lo fue.

**3 · Sin historial no hay castigo.** Por debajo del mínimo de eventos del modelo, el
nivel es `SIN_HISTORIAL` y el puntaje es el de arranque — no cero. Y en CU-97 el nivel
es `SIN_DATOS`, nunca riesgo alto. Tratar a quien recién llega como probable
incumplidor es la exclusión que este producto existe para no repetir.

**4 · Al participante nunca se le muestra un puntaje de riesgo.** El mensaje habla de
hechos con números: «te vencen dos aportes el viernes». Un número de riesgo no le sirve
para nada, lo etiqueta, y quien lo conoce lo puede jugar.

**5 · Nada se borra.** Un evento de reputación se compensa con su inverso, nunca se
edita. Una insignia revocada conserva su fila con el motivo. Una reseña rechazada
también. Borrar el reconocimiento —o el reproche— sería borrar la razón por la que se
dio.

## Los nueve casos de uso

| CU | Qué hace | Lo que no se negocia |
| --- | --- | --- |
| **CU-61** | Verificar públicamente el sorteo | Antes del revelado no hay nada que verificar. Una verificación fallida abre incidente: si falla, el problema es nuestro |
| **CU-70** | Registrar un evento de reputación | Un hecho puntúa una sola vez (R-REP-01). **Sin regla en el modelo no se puntúa** |
| **CU-71** | Recalcular el puntaje | Un solo puntaje vigente (R-REP-02) y el total es la suma de sus componentes (R-REP-03) |
| **CU-72** | Sellar el bloque de transparencia | Con excepciones de conciliación abiertas no se sella: un bloque con datos provisorios miente con firma |
| **CU-73** | Verificar la cadena | Devuelve **el primer bloque que falla y qué componente**. Un grupo sin bloques no es un error de integridad |
| **CU-74** | Otorgar y revocar una insignia | No hay otorgamiento manual. Revocar no borra (R-REP-05) |
| **CU-75** | Emitir un certificado verificable | El titular elige campo por campo. Un código inexistente responde igual que uno revocado |
| **CU-76** | Reseñar y moderar | Solo reseña quien convivió (R-REP-06). Una opinión pesa menos que un pago |
| **CU-97** | Alertas tempranas | Una alerta abierta por causa, y **no se cierra sin desenlace** (R-GAR-07) |

## Cómo se corre

```bash
docker compose --profile base up -d --wait
./gradlew bd:reset
./gradlew :servicios:transparencia:build
./gradlew :servicios:transparencia:integrationTest   # 118 pruebas
```

Las pruebas de CU cablean el modelo de scoring desde `sql/60_semillas/16-reputacion-y-scoring.sql`,
porque `sql/aplicar.sql` monta el esquema pero no las semillas y el modelo es catálogo
(invariante 10). Se lee el archivo de la bóveda en vez de reescribir sus valores, para
que ninguna prueba pueda pasar contra números que la bóveda ya no tiene.

## Lo que este servicio NO hace

- **No escribe el libro contable.** Solo `nucleo-financiero` lo hace (invariante 12).
- **No lee el esquema de nadie** (invariante 11). El paquete del sorteo, los hechos a
  sellar, el estado de la conciliación y los hechos que sustentan una insignia llegan
  **resueltos** por quien los posee.
- **No escribe `auditoria.incidente_operativo` ni `garantia.alerta_temprana`.** Los
  pide por evento de dominio, con su severidad y su taxonomía puestas.
