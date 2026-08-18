---
name: definicion-de-terminado
description: "Decidir si algo de AportaYa está realmente terminado: la matriz de gates por área con su evidencia, y la prohibición de afirmar 'listo', 'compila', 'pasa pruebas' o 'es seguro' sin haberlo ejecutado. Úsala antes de entregar, al cerrar una fase, al declarar un módulo completo o cuando alguien pregunte si esto ya se puede desplegar."
---

# Definición de terminado

Dos reglas antes que cualquier lista:

> **1. Nada se afirma sin evidencia.** "Compila", "pasa las pruebas", "está listo
> para producción" y "es seguro" solo se dicen después de haberlo ejecutado. Si algo
> no se pudo ejecutar, se declara **exactamente qué** y **por qué**.

> **2. Un gate crítico en rojo impide declarar terminado.** Se puede entregar
> parcial —es legítimo y frecuente— pero se declara como parcial, con la lista de lo
> que falta.

## Matriz de gates

Se reporta así, no en prosa:

| Área | Gate | Evidencia | Estado |
| --- | --- | --- | --- |
| Especificación | Criterios de aceptación cubiertos | `CU21Test.java`, 14/14 | Pass |
| Datos | Restricciones citadas con prueba de rechazo | 6/6 rechazos verificados | Pass |
| … | … | … | Pass/Fail |

### Especificación

- [ ] Todos los criterios de aceptación del caso de uso, como pruebas nombradas.
- [ ] La bóveda quedó al día: si el flujo cambió, se actualizó el caso de uso.
- [ ] Ninguna regla nueva quedó solo en el código.

### Datos y dinero

- [ ] Prueba de rechazo por **cada** restricción citada en el caso.
- [ ] Prueba de reintento: misma clave, misma respuesta, cero efectos nuevos.
- [ ] Prueba de concurrencia sobre el agregado que se modifica.
- [ ] Si mueve dinero: suma de movimientos `0.00` y asiento equilibrado.
- [ ] Ningún importe pasa por `number` en ninguna capa.
- [ ] Las consultas de verificación de [[Restricciones]] devuelven cero filas.

### Seguridad

- [ ] Prueba negativa de permisos: con contexto ajeno, cero filas.
- [ ] Ningún secreto en el repositorio, en la imagen ni en logs.
- [ ] Rate limit en los bordes sensibles.
- [ ] Datos personales minimizados y redactados en logs.
- [ ] Dependencias e imagen escaneadas sin críticas abiertas.

### Plazos y cumplimiento

- [ ] Si el flujo tiene plazo legal, hay prueba de vencimiento y de aviso previo.
- [ ] Los umbrales vienen de catálogo con vigencia, no de constantes.
- [ ] Queda evidencia consultable de lo que ocurrió (bitácora, intentos, acuses).

### Arquitectura y código

- [ ] Piezas declaradas por nivel; ninguna salta niveles.
- [ ] Ningún caso de uso llama a un proveedor dentro de la transacción.
- [ ] Ningún archivo manual supera el límite sin excepción justificada.
- [ ] Sin `any` ni `eslint-disable` sin justificación escrita.

### Operación

- [ ] Health y readiness responden lo que deben (readiness falla si no puede servir).
- [ ] Métricas y trazas del flujo existen y llevan `cu` y `usuario_id`.
- [ ] Apagado controlado: el trabajo en curso termina antes de morir.
- [ ] Backup vigente y **restauración probada** (`respaldos-restauracion`).

### Entrega

- [ ] Lint, tipos, pruebas y build ejecutados, con su salida citada.
- [ ] OpenAPI generado y sin diferencias con el publicado.
- [ ] Documentación de lo tocado actualizada.
- [ ] Supuestos declarados y riesgos pendientes escritos.

## Cómo se reporta un entregable

1. **Qué se construyó**, con las piezas por nivel.
2. **Qué se ejecutó y con qué resultado** — comandos y salida, no adjetivos.
3. **Qué quedó fuera** y por qué.
4. **Supuestos** declarados.
5. **Cómo verificarlo** quien reciba el trabajo.

## Frases prohibidas sin evidencia detrás

| No decir | Decir |
| --- | --- |
| "Está listo para producción" | "Pasan los 8 gates de la matriz; el de restore drill quedó pendiente" |
| "Debería funcionar" | "No lo pude ejecutar porque falta X; queda sin verificar" |
| "Ya está probado" | "14 criterios como pruebas, 6 rechazos de restricción, `./gradlew test` en verde" |
| "Es seguro" | "Prueba negativa de RLS y de permisos en verde; sin escaneo de imagen todavía" |

## Ver también

`plan-por-fases` · `ci-calidad` · `pruebas-cu` · `revision-codigo` ·
`implementar-desde-boveda`
