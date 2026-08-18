---
name: lecturas-proyecciones
description: "Separar lecturas de escrituras en AportaYa: conexión de solo lectura y réplica, cuándo una vista o materialized view vale la pena, cómo se crea (desde la bóveda, nunca al arrancar), qué endpoints de consulta pueden usarla y cuándo NO. Úsala al construir extractos, paneles, listados pesados, reportes o conciliaciones."
---

# Lecturas, réplicas y proyecciones

El sistema tiene dos perfiles de consulta muy distintos: operaciones cortas que
mueven dinero, y consultas pesadas de cumplimiento, conciliación y reportes. Mezclar
ambas en la misma conexión hace que un reporte mensual degrade un cobro.

## Escritura y lectura separadas

| Perfil | Conexión | Rol | Ejemplos |
| --- | --- | --- | --- |
| Operación | Escritura (primaria) | `rol_aplicacion` | Aportar, recargar, retirar, entregar |
| Lectura del usuario | Escritura o réplica según frescura | `rol_aplicacion` | Saldo, movimientos recientes |
| Cumplimiento y reportes | **Réplica**, transacción de solo lectura | `rol_auditor`, `rol_cumplimiento` | Extractos, conciliación, reportes UIF |

La separación es **por credencial**, aunque al principio ambas apunten al mismo
servidor: eso permite mover la lectura a una réplica sin tocar código.

Reglas:

- Todo lo que necesita **leer lo que acaba de escribir** va a la primaria. Un saldo
  después de un aporte no se lee de la réplica.
- La transacción de lectura se abre como `READ ONLY` cuando corresponde, con su
  `SET LOCAL` de contexto igual que cualquier otra ([[ADR-007 Sesión, RLS y pooling]]).
- El retraso de réplica se **mide y se alerta**; una conciliación sobre datos viejos
  produce un descuadre que no existe.
- Nunca se redirige todo a la primaria "porque la réplica falló" sin límite y sin
  alerta: eso convierte una degradación en una caída.

## Antes de crear una vista

No asumas que una vista es más rápida. En orden, evaluá:

```
1. La consulta con las columnas justas y el índice adecuado
2. Índice parcial o compuesto que falte
3. Vista normal        → solo simplifica; no acelera por sí sola
4. Materialized view   → acelera, a cambio de frescura
5. Tabla de proyección → cuando la vista materializada ya no alcanza
6. Caché               → último recurso, y solo con invalidación definida
```

Con datos representativos y `EXPLAIN (ANALYZE, BUFFERS)`. Si no medís, estás
adivinando.

## Cómo se crean

Igual que todo el esquema: **desde la bóveda, generado**. Una vista es estructura, no
código de aplicación.

- Se define en el módulo que corresponda y se regenera con `scripts/generar_ddl.py`.
- **Jamás** se crea una vista al arrancar la aplicación, ni con SQL construido desde
  entradas del usuario.
- El rol de lectura recibe `SELECT` **solo** sobre las proyecciones que necesita.
- Los repositorios de vista son de solo lectura: no ofrecen crear, actualizar ni
  borrar. Una proyección no es una tabla más.

## Materialized views

Si se usa una, se documenta —y se mide— todo esto:

| Aspecto | Qué definir |
| --- | --- |
| Refresco | Quién lo dispara: trabajo del worker, con su bloqueo por identificador |
| Concurrencia | `REFRESH … CONCURRENTLY` requiere índice único |
| Tolerancia | Cuánta desactualización acepta el negocio, por escrito |
| Métrica | **Edad de los datos** y fallos de refresco, con alerta |
| Cumplimiento | Un reporte regulatorio **no** se emite desde datos vencidos |

## Endpoints de consulta

```
Controlador → Servicio de consulta → Repositorio de lectura → vista o consulta optimizada
```

- Devuelven DTO pensado para la pantalla, no filas crudas del modelo.
- Paginan del lado del servidor, siempre.
- Filtros y orden por **lista blanca**; nunca nombres de tabla, columnas ni joins que
  vengan del cliente.
- Limitan el tamaño de respuesta; los exportes grandes se generan por trabajo.
- Respetan RLS: una proyección **no** es una puerta trasera a datos de otro usuario.
  Si la vista agrega datos de varios, su acceso se restringe por rol y se prueba.

## Antipatrones

- Crear una vista para "ordenar el código" sin medir nada.
- Materializar y olvidarse de refrescar.
- Emitir un reporte regulatorio desde una vista con retraso desconocido.
- Dar `SELECT` general al rol de lectura sobre todo el esquema.
- Exponer un endpoint que acepta el nombre de la columna a ordenar.
- Leer de la réplica algo que se acaba de escribir.

## Ver también

`datos-jooq` · `seguridad-sesion-rls` · `extraccion-de-datos` · `indicadores-tablero` ·
`resiliencia-rendimiento` · `reportes-regulatorios` · `boveda-modelo` ·
`docs/Arquitectura/ADR-011 Lecturas y réplica.md`
