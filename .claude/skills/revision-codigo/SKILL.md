---
name: revision-codigo
description: "Revisar un cambio de AportaYa antes de fusionarlo: qué se mira primero, qué se rechaza sin discusión y qué se deja pasar con comentario. Úsala al revisar un PR propio o ajeno, o antes de pedir revisión. Ordena la revisión por riesgo: dinero y cumplimiento primero, estilo al final."
---

# Revisar un cambio

Una revisión no es una lectura de arriba abajo: es una búsqueda ordenada por riesgo.
En este sistema el riesgo se ordena solo — **dinero, plazos legales y evidencia
primero**; estilo, al final y automatizado.

## Orden de revisión

```
1. ¿Qué caso de uso implementa? ¿Coincide con lo que dice la bóveda?
2. Frontera transaccional e idempotencia
3. Dónde quedó cada garantía: ¿base o aplicación?
4. Dinero: tipos, redondeo, cuadre
5. Pruebas: criterios, rechazos, reintento
6. Composición: niveles y dirección de dependencia
7. Nombres, legibilidad, comentarios
8. Estilo → lo resolvió la herramienta; si aparece acá, falta configuración
```

## Se rechaza sin discusión

| Hallazgo | Por qué |
| --- | --- |
| Un importe tipado como `number`, un `parseFloat` o aritmética suelta sobre dinero | `dinero-decimal`: exactitud es cumplimiento |
| `UPDATE` sobre tabla append-only, o "ajustar" un saldo en lugar de insertar movimiento | Corrección = movimiento inverso |
| Llamada a un proveedor externo dentro de la transacción | Va por outbox |
| Escritura antes de validar la clave de idempotencia | La bóveda lo exige explícitamente |
| Consulta sin `SET LOCAL` de contexto, o `SET` sin `LOCAL` | Fuga de identidad entre requests |
| Un umbral, límite o tarifa como constante en el código | Va a catálogo con vigencia |
| Una regla que protege dinero "validada solo en el backend" sin su `R-XXX-nn` | La garantía está en el lugar equivocado |
| Criterio de aceptación sin prueba, o restricción citada sin prueba de rechazo | No está terminado |
| Migración escrita a mano fuera de `sql/` | El esquema se genera desde la bóveda |
| `any` o `eslint-disable` sin justificación escrita | Silencia justo donde hay dudas |
| Cualquiera de las **dieciocho prohibiciones** de [[Seguridad]] §5 | Secreto versionado, SQL concatenado, `Math.random` para un token, hash rápido de contraseña, PII en el log, traza en la respuesta, SSRF, `dangerouslySetInnerHTML`… no se discuten |
| Una restricción, un disparador o una política de RLS desactivada para que pase una prueba | Si la prueba choca contra el motor, está mal la prueba o el diseño |
| Un gate del CI bajado para desbloquear el merge | El gate se arregla o se discute; no se apaga |

## Se comenta, no se bloquea

- Nombres mejorables que no inducen a error.
- Una molécula que podría subir a `plataforma/comun-dominio` en el futuro (recordá: **al
  tercer uso**, no al segundo).
- Consultas correctas pero optimizables sin evidencia de que sean un problema.
- Preferencias de composición dentro del mismo nivel.

## Preguntas que sacan los defectos reales

1. **¿Qué pasa si esto se ejecuta dos veces?** El reintento del usuario en mala señal
   es el caso normal, no el raro.
2. **¿Qué pasa si el proceso muere justo después del `COMMIT`?** ¿El efecto externo
   se pierde o queda en cola?
3. **¿Qué pasa si dos personas hacen esto a la vez?** ¿Qué fila se bloquea?
4. **¿Qué ve un usuario que no debería ver esto?** ¿Lo impide la política de fila o
   solo un `WHERE`?
5. **¿Dónde queda la evidencia?** Si mañana hay un reclamo o una inspección, ¿con qué
   consulta se responde?
6. **Si borro esta prueba, ¿algo falla?** Una prueba que no puede fallar no es una
   prueba.
7. **¿Esto contradice un ADR vigente?** Si sí, el ADR gana hasta que se escriba uno
   nuevo que lo supere.

## Revisión de frontend

| Se mira | Qué se espera |
| --- | --- |
| Estados | Cargando, vacío, error, éxito — los cuatro, siempre |
| Doble envío | Botón deshabilitado + misma clave de idempotencia |
| Tokens | Ningún hex ni espaciado literal fuera del archivo de tokens |
| Dominio | Ningún `fetch` dentro de un componente |
| Tipos | Vienen del paquete `contratos`, no reescritos |
| Accesibilidad | Foco visible, semántica correcta, contraste |
| Importes | Formateados por el átomo `Monto`, nunca a mano |

## Qué mirar en el diff del esquema

Si el PR toca `docs/entidades/*.puml`, `docs/Restricciones.md` o `sql/`:

- [ ] `sql/` está **regenerado**, no editado a mano.
- [ ] La prueba de humo pasa y las consultas de verificación devuelven cero filas.
- [ ] Las notas de la bóveda quedaron al día (`boveda-modelo`).
- [ ] Si hay restricción nueva, tiene código `R-XXX-nn`, norma citada y prueba de
      rechazo.
- [ ] El cambio es compatible hacia atrás, o está partido en dos despliegues.

## Cómo se comenta

- Señalá **el riesgo concreto**, no el gusto: "si esto corre dos veces se duplica el
  asiento" es útil; "no me gusta esta estructura" no.
- Citá la fuente: el caso de uso, la restricción, el ADR. En este repo casi todo
  hallazgo tiene una referencia escrita que lo respalda.
- Proponé la corrección cuando la sepas; una revisión que solo pregunta cuesta dos
  ciclos.
- Distinguí explícitamente lo que bloquea de lo que es sugerencia.

## Ver también

`git-flujo` · `glosario-dominio` · `codigo-limpio` · `arquitectura-atomica` · `pruebas-cu` · `implementar-desde-boveda` ·
`seguridad-aplicacion` · [[Seguridad]] · `docs/Arquitectura/Método de arquitectura.md`
