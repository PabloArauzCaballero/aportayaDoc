---
name: web-backoffice
description: "Construir el backoffice web de AportaYa con React, Vite, TanStack Router y TanStack Query: composición atómica, tablas densas con filtros y exportación, pantallas de cumplimiento con plazos y evidencia, permisos y estados obligatorios. Úsala al crear o modificar cualquier pantalla de apps/backoffice."
---

# Backoffice web

Sus usuarios son el **oficial de cumplimiento, soporte y contabilidad**: gente
experta, jornada completa, pantallas densas, decisiones con consecuencia legal. No es
la app estirada.

El diseño visual lo manda `disenar-frontend`; esta skill manda la estructura
y el comportamiento.

## Estructura

```
apps/backoffice/src/
├── atomos/       Boton, Campo, Monto, ChipEstado, Badge
├── moleculas/    FiltroDeRango, FilaDeAlerta, CeldaDeMonto, useAlertas
├── organismos/   TablaDeAlertas, PanelDeReclamo, FormularioDeReporte
├── pantallas/    ruta + composición, sin lógica
├── dominio/      un cliente por caso de uso, tipado desde openapi/ del servicio
└── tokens/       único lugar con valores literales de diseño
```

TanStack Router para rutas tipadas, TanStack Query para estado de servidor. Ningún
componente hace `fetch`.

## Tablas: el organismo central

Casi todo el backoffice es una tabla con filtros. Reglas fijas:

| Aspecto | Regla |
| --- | --- |
| Paginación | Del servidor, siempre. Nada de traer todo y filtrar en el cliente |
| Orden y filtros | Por lista blanca acordada con la API; el estado va en la URL |
| Virtualización | Para listas largas; el DOM no crece sin límite |
| Columnas de dinero | Alineadas a la derecha, con el átomo `Monto`, moneda visible |
| Fechas | Con zona horaria explícita; nada de fechas ambiguas en un expediente |
| Exportación | Del servidor, con los mismos filtros aplicados, y **queda registrada**: quién exportó qué y cuándo |
| Selección múltiple | Solo si existe una acción masiva real, y con confirmación que enumera lo afectado |

El estado de la tabla vive en la URL: un oficial tiene que poder pegar el enlace de lo
que está mirando en un expediente.

## Pantallas de cumplimiento

Lo que distingue a este producto de un CRUD:

- **El plazo se muestra siempre.** Un reclamo (CU-52) o un reporte (CU-43) exhibe
  cuánto falta, y el vencimiento se destaca visualmente antes de vencer, no después.
- **La evidencia se ve.** Toda pantalla de expediente muestra la bitácora: quién hizo
  qué, cuándo, con qué resultado. Es lo que se responde ante una inspección.
- **Nada se edita.** Corregir es registrar un movimiento o una decisión nueva, con
  motivo obligatorio. Si la interfaz sugiere "editar", está mintiendo sobre el modelo.
- **Motivo obligatorio** en toda acción con consecuencia: bloquear saldo, rechazar,
  elevar, devolver comisión.
- **Doble confirmación** en lo irreversible, enumerando exactamente qué va a pasar.

## Permisos

- La interfaz oculta o deshabilita por comodidad; **la protección real es del
  servidor** y de las políticas de fila.
- Una pantalla vacía por permiso lo dice explícitamente: "no tenés acceso a esto", no
  una tabla vacía que parece un error de datos.
- La segregación de funciones del modelo se respeta en la interfaz: quien registra no
  aprueba. Si la interfaz permite ambas cosas al mismo usuario, es un defecto.

## Estados obligatorios

Cargando, vacío, error y éxito en toda vista con datos; **enviando** en toda acción.
En un backoffice el estado vacío importa el doble: "no hay alertas" y "el filtro no
devolvió nada" son mensajes distintos y se distinguen.

## Formularios

- Validación con los **tipos generados desde el OpenAPI del servicio**; nunca reglas reescritas a mano.
- Errores por campo, en el campo, diciendo cómo corregir.
- Clave de idempotencia en toda operación con efecto.
- Los formularios largos (reportes, expedientes) guardan borrador local para no
  perder trabajo por una sesión caída.

## Rendimiento

- Consultas con caché e invalidación **explícita** por acción; nada de recargar todo
  tras cada cambio.
- Las pantallas de conciliación y reportes leen de la réplica: son consultas pesadas.
- Se optimiza con medición, no por intuición.

## Antipatrones

- Traer diez mil filas para filtrar en el navegador.
- Un botón "editar" sobre algo append-only.
- Acción masiva sin confirmación que enumere lo afectado.
- Exportar sin registrar quién exportó.
- Ocultar un permiso solo en el cliente y asumir que eso protege.
- Reutilizar el layout de la app móvil para pantallas densas.

## Ver también

`errores-api` · `observabilidad` · `glosario-dominio` · `disenar-frontend` · `arquitectura-atomica` · `contratos-api` · `revision-codigo` ·
`docs/Arquitectura/ADR-004 Frontend.md` · `docs/Arquitectura/Prompts/Prompt de frontend.md`
