---
name: seguridad-aplicacion
description: "Escribir código que resista un ataque en AportaYa: denegar por omisión en las siete fronteras, las prohibiciones que se rechazan sin discusión, criptografía y secretos, validación de entrada, registro sin PII, y cómo se verifica cada control contra ISO/IEC 27001 y 27034. Úsala antes de escribir cualquier endpoint, consulta, adaptador, Dockerfile o pantalla, y al revisar un PR."
---

# Seguridad de la aplicación

`autenticacion-jwt` cubre el borde HTTP y `seguridad-sesion-rls` la política de fila. Esta
skill cubre **todo lo demás del código**: qué está prohibido, qué se exige y con qué se
comprueba. El estándar completo, con la correspondencia ISO, vive en [[Seguridad]].

## La regla de arriba

**Un control que nadie ejecuta no es un control.** Cuando escribas una regla de seguridad,
escribí en la misma línea quién la hace cumplir:

| Nivel | Qué significa | Preferilo siempre |
| --- | --- | :-: |
| **motor** | La base lo impide; no hay código que lo saltee | 1º |
| **gate** | Un comando del CI falla si no se cumple | 2º |
| **revisión** | Lo juzga una persona | último recurso |

Si tu control solo puede ser «revisión», preguntate por qué no puede ser restricción
(`restriccion`) o verificación (`scripts/verificar_seguridad.py`).

## Denegar por omisión, en las siete fronteras

Red · HTTP · autorización · fila · esquema · entrada · salida. Ver [[Seguridad]] §1. La
grieta típica no es olvidar las siete: es implementar seis y dejar una «porque es interna».

## Las dieciocho prohibiciones

No se discuten en un PR. Están enumeradas en [[Seguridad]] §5. Las que más aparecen
escribiendo código nuevo:

1. Secreto en el repositorio — ni en pruebas, ni comentado, ni «temporal».
2. SQL concatenado — aunque el dato venga de adentro.
3. Endpoint sin decisión consciente de autenticación.
4. Permiso verificado en el controlador y no contra el recurso concreto.
5. Consulta fuera de `conContexto` — es defecto de seguridad, no de estilo.
6. `Math.random()` / `java.util.Random` para algo que no se debe adivinar.
7. Hash rápido para una contraseña — **Argon2id o nada**.
8. `digest()` desnudo como hash de búsqueda sobre un dato adivinable.
9. PII, credencial o token en un log.
10. Traza, SQL o mensaje interno en la respuesta HTTP.
11. Deserialización polimórfica de datos externos.
12. URL de salida construida con datos del usuario (SSRF).
13. `dangerouslySetInnerHTML` con contenido no constante.
14. Confiar en que la interfaz esconde el botón.
15. Desactivar una restricción o una política de RLS para que pase una prueba.
16. Bajar un gate del CI para desbloquear un merge.
17. **Inventar un código de permiso** que el catálogo no tiene — es un hueco (S-8), no un detalle.
18. **Encender un canal apagado** para destrabar un flujo: reabre el vector que `R-SEG-10` cierra.

## Al escribir un endpoint

- [ ] El contrato existe **antes** (`contratos-api`), la entrada es `strict()`.
- [ ] El permiso se verifica **contra el recurso**, no contra el rol.
- [ ] La consulta corre en `conContexto`; sin contexto no hay filas.
- [ ] El error sale traducido con su código `AP-CU<NN>-<nn>` (`errores-api`), sin causa interna.
- [ ] Escritura con efecto ⇒ `clave_idempotencia` (`idempotencia-reintentos`).
- [ ] Importes en `BigDecimal`, nunca recalculados en el cliente (`dinero-decimal`).
- [ ] Prueba **negativa**: el camino feliz no prueba nada de seguridad.

## Al tocar algo de afuera

Todo lo que sale del proceso —red, disco, correo, plata, reloj, azar— es **puerto** en
`dominio/puertos/` con adaptador local primero ([[ADR-033 Puertos y adaptadores]]). Eso no
es solo arquitectura: es lo que permite que una URL de salida no se arme con datos del
usuario y que el nombre del proveedor no se filtre en un mensaje.

## Criptografía, sin margen de interpretación

| Para qué | Qué se usa | Qué está prohibido |
| --- | --- | --- |
| Contraseña y PIN | Argon2id con parámetros configurables | MD5, SHA-1, SHA-256 desnudo, bcrypt de costo bajo |
| Hash de búsqueda | HMAC con pimienta fuera de la base (`fn_seg_hash_busqueda`) | `digest()` directo sobre CI, cuenta o PAN |
| Token, código, sal | Generador criptográfico del sistema | `Math.random`, `java.util.Random`, contador, timestamp |
| Columna sensible | Cifrada, con `version_llave` declarada | Guardar en claro «porque ya está en otro lado» |

Cifrar la columna y poner al lado un `digest()` del mismo dato equivale a no cifrar: la
tabla de digests de un CI boliviano se precalcula en segundos. Por eso `R-SEG-01` **falla**
si no hay pimienta configurada.

## Registro: qué se escribe y qué no

- **Sí**: `correlation_id`, identificadores opacos, código de error, resultado, duración.
- **No**: contraseñas, tokens (ni truncados), PII, importes de terceros, cuerpos completos.
- La evidencia oponible no va al log de aplicación: va a [[bitacora_evento]], encadenada por
  hash y append-only. El log sirve para depurar; la bitácora, para responder un reclamo.

## Lo que exige la norma, sin misticismo

ISO/IEC 27001:2022 pide, en el código: **A.8.25** ciclo de desarrollo seguro, **A.8.26**
requisitos de seguridad de la aplicación, **A.8.27** ingeniería segura, **A.8.28**
codificación segura, **A.8.29** pruebas de seguridad. ISO/IEC 27034 agrega que cada control
declare su **nivel de confianza** — que acá es la columna motor / gate / revisión. La
correspondencia completa está en [[Seguridad]] §4; no la reproduzcas de memoria.

## Antes de decir que está listo

```bash
python3 scripts/verificar_seguridad.py
python3 scripts/verificar_boveda.py
```

Y la regla que las envuelve: **no se afirma «es seguro» sin haberlo ejecutado**
(`definicion-de-terminado`). Un control declarado y no corrido es peor que ninguno, porque
da confianza sin sustento.

## Ver también

[[Seguridad]] · [[Restricciones]] · [[Contrato de implementación para IA]] ·
[[ADR-038 Acceso administrativo · segundo factor y recuperación asistida]] ·
`autenticacion-jwt` · `seguridad-sesion-rls` · `roles-y-accesos` · `restriccion` ·
`errores-api` · `observabilidad` · `ci-calidad` · `revision-codigo` ·
`definicion-de-terminado`
