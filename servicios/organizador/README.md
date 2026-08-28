# Servicio `organizador`

Modulo 07 de la boveda — Organizador y Automatización.

> **Este README enlaza, no repite.** La especificacion esta en `docs/CasosDeUso/`;
> un dato en dos lugares diverge.

| | |
| --- | --- |
| **Esquema** | `organizador` |
| **Rol de base** | `svc_organizador` |
| **Prefijos de ruta** | `/organizadores` · `/automatizacion` |
| **Contrato** | [`openapi/organizador.yaml`](src/main/resources/openapi/organizador.yaml) |
| **Paquete** | `bo.aportaya.organizador` |

## Casos de uso

| CU | Nombre | Estado |
| --- | --- | --- |
| CU-90 | Postular a organizador y habilitarse | Implementado |
| CU-91 | Firmar y rescindir el contrato de organizador | Implementado |
| CU-92 | Evaluar el desempeno del organizador | Implementado |
| CU-93 | Sancionar al organizador y resolver su apelacion | Implementado |
| CU-95 | Definir una regla de automatizacion | Implementado |
| CU-96 | Programar y ejecutar una tarea automatizada | Implementado |

CU-94 pertenece a `cumplimiento`, no a este servicio.

## Lo que hay que saber antes de tocar esto

**Una apelacion abierta no se puede guardar.** `ck_apelacion_org_resuelta` exige que el
estado sea `PENDIENTE` —que `ck_apelacion_sancion_org_estado` **no admite**— o que los
tres campos de resolucion esten presentes. Las dos restricciones juntas hacen imposible
registrar una apelacion sin resolver. Mientras esta abierta, lo que la registra es el
estado `APELADA` de la sancion y el evento con el argumento; la fila se escribe entera
al resolverla. Es el hueco H-7 del informe.

**Las acciones sensibles se rechazan, no se corrigen en silencio.** Una regla que
ejecuta una entrega sin `requiere_confirmacion_humana` **no se guarda**. Forzar la
bandera calladamente deja a quien la escribio creyendo que definio otra cosa.

**El ascenso de nivel salta un escalon por vez; el descenso no tiene limite.** El nivel
define cuanta plata ajena puede tener en curso: subir dos de golpe entrega un limite que
esa persona nunca sostuvo. Bajar de golpe si, porque cuando algo sale mal esperar no
mejora nada.

## Lo que este servicio NO puede hacer

- Leer el esquema de otro servicio. No tiene `GRANT`, y jOOQ no le genero las
  clases (invariante 11).
- Escribir el libro contable, salvo que sea `nucleo-financiero` (invariante 12).
- Usar JPA (ADR-016).
- Publicar a Kafka dentro de una transaccion: para eso esta el outbox (ADR-018).

## Como se trabaja acá

```bash
docker compose --profile base up -d --wait
./gradlew :servicios:organizador:generateJooq
./gradlew :servicios:organizador:bootRun
```

Skills: `arrancar-carril` primero, despues las diecinueve de todo carril de backend.
