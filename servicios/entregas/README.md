# Servicio `entregas`

Modulo 04 de la boveda — Entregas de Fondo.

> **Este README enlaza, no repite.** La especificacion esta en `docs/CasosDeUso/`;
> un dato en dos lugares diverge.

| | |
| --- | --- |
| **Esquema** | `entregas` |
| **Rol de base** | `svc_entregas` |
| **Prefijos de ruta** | `/entregas` · `/desembolsos` · `/cuentas-bancarias` |
| **Contrato** | [`openapi/entregas.yaml`](src/main/resources/openapi/entregas.yaml) |
| **Paquete** | `bo.aportaya.entregas` |

## Casos de uso

| CU | Nombre | Estado |
| --- | --- | --- |
| CU-18 | Registrar y verificar una cuenta bancaria de destino | Implementado |
| CU-22 | Liquidar y entregar el fondo | Implementado |
| CU-28 | Emitir la orden de desembolso y ejecutar el intento | Implementado |

Los tres del modulo 04.

## Lo que hay que saber antes de tocar esto

**El numero de cuenta no existe en claro en ninguna parte.** Ni en una columna, ni en
un evento, ni en un log. Lo que se guarda es el cifrado, un hash con **pimienta** —que
vive en el almacen de secretos, no junto al hash— y un enmascarado con cuatro digitos.
Sin pimienta el hash es adivinable: el espacio de numeros de cuenta posibles es chico.

**Verificada no es utilizable.** Tras verificar corre una ventana de enfriamiento, y su
fin **se guarda** en `bloqueada_hasta`. Si se recalculara al consultar, acortar la
politica liberaria de golpe todas las cuentas que estaban enfriando.

**Los totales de la entrega los recalcula la base.** `tg_deduccion_recalcula` los
actualiza en cada deduccion. Escribirlos a mano permitiria que un neto y sus
deducciones dejaran de coincidir sin que nada avise.

**Un intento de desembolso es una fila.** La que se abre al enviar la orden es la misma
que se cierra con la respuesta del proveedor. Agregar otra al contestar convertiria un
intento en dos, y el conteo que decide si se reintenta dejaria de significar algo.

## Lo que este servicio NO puede hacer

- Leer el esquema de otro servicio. No tiene `GRANT`, y jOOQ no le genero las
  clases (invariante 11).
- Escribir el libro contable, salvo que sea `nucleo-financiero` (invariante 12).
- Usar JPA (ADR-016).
- Publicar a Kafka dentro de una transaccion: para eso esta el outbox (ADR-018).

## Como se trabaja acá

```bash
docker compose --profile base up -d --wait
./gradlew :servicios:entregas:generateJooq
./gradlew :servicios:entregas:bootRun
```

Skills: `arrancar-carril` primero, despues las diecinueve de todo carril de backend.
