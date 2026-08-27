-- Índices y restricciones de unicidad del módulo 02 — Grupos, Cupos, Turnos y Gobernanza
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE UNIQUE INDEX IF NOT EXISTS uq_grupo_codigo_publico
  ON grupos.grupo (codigo_publico);

CREATE INDEX IF NOT EXISTS ix_grupo_estado
  ON grupos.grupo (estado);

CREATE INDEX IF NOT EXISTS ix_reglamento_grupo_grupo_id
  ON grupos.reglamento_grupo (grupo_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_reglamento_grupo_grupo_id_version
  ON grupos.reglamento_grupo (grupo_id, version);

CREATE INDEX IF NOT EXISTS ix_historial_estado_grupo_grupo_id
  ON grupos.historial_estado_grupo (grupo_id);

CREATE INDEX IF NOT EXISTS ix_participante_grupo_id
  ON grupos.participante (grupo_id);

CREATE INDEX IF NOT EXISTS ix_participante_usuario_id
  ON grupos.participante (usuario_id);

CREATE INDEX IF NOT EXISTS ix_participante_estado
  ON grupos.participante (estado);

CREATE INDEX IF NOT EXISTS ix_cupo_grupo_id
  ON grupos.cupo (grupo_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_cupo_grupo_id_numero
  ON grupos.cupo (grupo_id, numero);

CREATE INDEX IF NOT EXISTS ix_traspaso_cupo_cupo_id
  ON grupos.traspaso_cupo (cupo_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_solicitud_retiro_participante_id
  ON grupos.solicitud_retiro (participante_id);

CREATE INDEX IF NOT EXISTS ix_solicitud_ingreso_grupo_id
  ON grupos.solicitud_ingreso (grupo_id);

CREATE INDEX IF NOT EXISTS ix_solicitud_ingreso_usuario_id
  ON grupos.solicitud_ingreso (usuario_id);

CREATE INDEX IF NOT EXISTS ix_invitacion_grupo_id
  ON grupos.invitacion (grupo_id);

CREATE INDEX IF NOT EXISTS ix_invitacion_telefono_invitado
  ON grupos.invitacion (telefono_invitado);

CREATE INDEX IF NOT EXISTS ix_periodo_grupo_id
  ON grupos.periodo (grupo_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_periodo_grupo_id_numero
  ON grupos.periodo (grupo_id, numero);

CREATE INDEX IF NOT EXISTS ix_periodo_fecha_limite_pago
  ON grupos.periodo (fecha_limite_pago);

CREATE INDEX IF NOT EXISTS ix_periodo_estado
  ON grupos.periodo (estado);

CREATE INDEX IF NOT EXISTS ix_turno_grupo_id
  ON grupos.turno (grupo_id);

CREATE INDEX IF NOT EXISTS ix_sorteo_turnos_estado
  ON grupos.sorteo_turnos (estado);

CREATE INDEX IF NOT EXISTS ix_solicitud_permuta_turno_origen_id
  ON grupos.solicitud_permuta (turno_origen_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_dia_no_habil_alcance_grupo_id_fecha
  ON catalogo.dia_no_habil (alcance, grupo_id, fecha);

CREATE INDEX IF NOT EXISTS ix_postulacion_emparejamiento_usuario_id
  ON grupos.postulacion_emparejamiento (usuario_id);

CREATE INDEX IF NOT EXISTS ix_postulacion_emparejamiento_estado
  ON grupos.postulacion_emparejamiento (estado);

CREATE INDEX IF NOT EXISTS ix_acuerdo_grupo_id
  ON grupos.acuerdo (grupo_id);

CREATE INDEX IF NOT EXISTS ix_voto_participante_acuerdo_id
  ON grupos.voto_participante (acuerdo_id);
