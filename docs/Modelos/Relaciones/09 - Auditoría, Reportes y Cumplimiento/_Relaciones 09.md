---
tags:
  - moc
  - modulo/09-auditoria-reportes-y-cumplimiento
modulo: "09 — Auditoría, Reportes y Cumplimiento"
relaciones_fk: 27
---

# 09 — Auditoría, Reportes y Cumplimiento · relaciones

Las **27 claves foráneas** que salen de las tablas de este módulo.

[[_Relaciones|← Todas las relaciones]] · [[Index]]

| Relación | Destino | Cruza | Opcional |
| --- | --- | :-: | :-: |
| [[alerta_cumplimiento.analista_id → usuario]] | [[usuario]] | ↗ 01 | sí |
| [[alerta_cumplimiento.grupo_id → grupo]] | [[grupo]] | ↗ 02 | sí |
| [[alerta_cumplimiento.regla_id → regla_cumplimiento]] | [[regla_cumplimiento]] | — | no |
| [[alerta_cumplimiento.reporte_sospechoso_id → reporte_operacion_sospechosa]] | [[reporte_operacion_sospechosa]] | — | sí |
| [[alerta_cumplimiento.usuario_id → usuario]] | [[usuario]] | ↗ 01 | no |
| [[bitacora_evento.actor_usuario_id → usuario]] | [[usuario]] | ↗ 01 | sí |
| [[bitacora_evento.grupo_id → grupo]] | [[grupo]] | ↗ 02 | sí |
| [[bitacora_evento.suplantando_a_usuario_id → usuario]] | [[usuario]] | ↗ 01 | sí |
| [[coincidencia_lista.lista_id → lista_restrictiva_externa]] | [[lista_restrictiva_externa]] | — | no |
| [[coincidencia_lista.revisada_por → usuario]] | [[usuario]] | ↗ 01 | sí |
| [[coincidencia_lista.usuario_id → usuario]] | [[usuario]] | ↗ 01 | no |
| [[ejecucion_reporte.definicion_id → definicion_reporte]] | [[definicion_reporte]] | — | no |
| [[ejecucion_reporte.grupo_id → grupo]] | [[grupo]] | ↗ 02 | sí |
| [[ejecucion_reporte.solicitado_por → usuario]] | [[usuario]] | ↗ 01 | no |
| [[exportacion_reporte.ejecucion_id → ejecucion_reporte]] | [[ejecucion_reporte]] | — | no |
| [[indicador_kpi.definicion_indicador_id → definicion_indicador]] | [[definicion_indicador]] | — | no |
| [[proceso_anonimizacion.solicitud_id → solicitud_datos_personales]] | [[solicitud_datos_personales]] | — | sí |
| [[proceso_anonimizacion.usuario_id → usuario]] | [[usuario]] | ↗ 01 | no |
| [[programacion_reporte.definicion_id → definicion_reporte]] | [[definicion_reporte]] | — | no |
| [[registro_acceso_datos.usuario_afectado_id → usuario]] | [[usuario]] | ↗ 01 | no |
| [[registro_acceso_datos.usuario_consultor_id → usuario]] | [[usuario]] | ↗ 01 | no |
| [[reporte_operacion_sospechosa.aprobado_por → usuario]] | [[usuario]] | ↗ 01 | sí |
| [[reporte_operacion_sospechosa.usuario_id → usuario]] | [[usuario]] | ↗ 01 | no |
| [[solicitud_datos_personales.atendida_por → usuario]] | [[usuario]] | ↗ 01 | sí |
| [[solicitud_datos_personales.usuario_id → usuario]] | [[usuario]] | ↗ 01 | no |
| [[ticket_soporte.asignado_a → usuario]] | [[usuario]] | ↗ 01 | sí |
| [[ticket_soporte.usuario_id → usuario]] | [[usuario]] | ↗ 01 | no |
