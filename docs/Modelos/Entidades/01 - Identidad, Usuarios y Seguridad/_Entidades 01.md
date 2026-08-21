---
tags:
  - moc
  - modulo/01-identidad-usuarios-y-seguridad
modulo: "01 — Identidad, Usuarios y Seguridad"
entidades: 25
---

# 01 — Identidad, Usuarios y Seguridad · entidades

Las **25 tablas** de este módulo. Justificación de negocio en [[01_identidad_usuarios]].

[[_Entidades|← Todas las entidades]] · [[Index]]

| Tabla | Columnas | FK sal. | FK ent. |
| --- | --: | --: | --: |
| [[usuario]] | 18 | 0 | 209 |
| [[direccion_usuario]] | 8 | 1 | 0 |
| [[perfil_financiero]] | 8 | 1 | 0 |
| [[credencial_acceso]] | 8 | 1 | 0 |
| [[historial_credencial]] | 4 | 1 | 0 |
| [[politica_token]] | 12 | 0 | 0 |
| [[token_verificacion]] | 31 | 4 | 10 |
| [[intento_validacion_token]] | 7 | 1 | 0 |
| [[factor_mfa]] | 9 | 1 | 0 |
| [[dispositivo]] | 11 | 1 | 4 |
| [[sesion]] | 11 | 2 | 1 |
| [[intento_autenticacion]] | 10 | 1 | 0 |
| [[bloqueo_cuenta]] | 7 | 2 | 0 |
| [[restriccion_usuario]] | 10 | 2 | 0 |
| [[documento_identidad]] | 14 | 1 | 1 |
| [[verificacion_kyc]] | 14 | 3 | 2 |
| [[referencia_personal]] | 8 | 1 | 0 |
| [[rol]] | 5 | 0 | 2 |
| [[permiso]] | 6 | 0 | 1 |
| [[rol_permiso]] | 2 | 2 | 0 |
| [[asignacion_rol]] | 10 | 3 | 0 |
| [[consentimiento]] | 10 | 1 | 0 |
| [[preferencia_notificacion]] | 12 | 1 | 0 |
| [[reputacion_usuario]] | 11 | 1 | 0 |
| [[solicitud_baja]] | 6 | 1 | 0 |
