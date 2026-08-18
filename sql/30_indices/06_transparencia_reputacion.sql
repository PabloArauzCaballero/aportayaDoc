-- Índices y restricciones de unicidad del módulo 06 — Transparencia y Reputación
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE UNIQUE INDEX IF NOT EXISTS uq_modelo_scoring_version
  ON transparencia.modelo_scoring (version);

CREATE INDEX IF NOT EXISTS ix_peso_factor_modelo_id
  ON transparencia.peso_factor (modelo_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_peso_factor_modelo_id_codigo_factor
  ON transparencia.peso_factor (modelo_id, codigo_factor);

CREATE INDEX IF NOT EXISTS ix_regla_impacto_evento_modelo_id
  ON transparencia.regla_impacto_evento (modelo_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_regla_impacto_evento_modelo_id_tipo_evento
  ON transparencia.regla_impacto_evento (modelo_id, tipo_evento);

CREATE INDEX IF NOT EXISTS ix_evento_reputacion_usuario_id
  ON transparencia.evento_reputacion (usuario_id);

CREATE INDEX IF NOT EXISTS ix_evento_reputacion_grupo_id
  ON transparencia.evento_reputacion (grupo_id);

CREATE INDEX IF NOT EXISTS ix_evento_reputacion_tipo
  ON transparencia.evento_reputacion (tipo);

CREATE INDEX IF NOT EXISTS ix_evento_reputacion_ocurrido_en
  ON transparencia.evento_reputacion (ocurrido_en);

CREATE UNIQUE INDEX IF NOT EXISTS uq_puntaje_reputacion_usuario_id
  ON transparencia.puntaje_reputacion (usuario_id);

CREATE INDEX IF NOT EXISTS ix_puntaje_reputacion_puntaje
  ON transparencia.puntaje_reputacion (puntaje);

CREATE INDEX IF NOT EXISTS ix_puntaje_reputacion_nivel_confianza
  ON transparencia.puntaje_reputacion (nivel_confianza);

CREATE INDEX IF NOT EXISTS ix_puntaje_reputacion_proximo_recalculo_en
  ON transparencia.puntaje_reputacion (proximo_recalculo_en);

CREATE INDEX IF NOT EXISTS ix_componente_score_puntaje_id
  ON transparencia.componente_score (puntaje_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_componente_score_puntaje_id_codigo_factor
  ON transparencia.componente_score (puntaje_id, codigo_factor);

CREATE INDEX IF NOT EXISTS ix_snapshot_reputacion_usuario_id
  ON transparencia.snapshot_reputacion (usuario_id);

CREATE INDEX IF NOT EXISTS ix_snapshot_reputacion_tomado_en
  ON transparencia.snapshot_reputacion (tomado_en);

CREATE INDEX IF NOT EXISTS ix_certificado_reputacion_usuario_id
  ON transparencia.certificado_reputacion (usuario_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_certificado_reputacion_snapshot_id
  ON transparencia.certificado_reputacion (snapshot_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_certificado_reputacion_codigo_verificacion
  ON transparencia.certificado_reputacion (codigo_verificacion);

CREATE UNIQUE INDEX IF NOT EXISTS uq_insignia_logro_codigo
  ON transparencia.insignia_logro (codigo);

CREATE INDEX IF NOT EXISTS ix_insignia_otorgada_usuario_id
  ON transparencia.insignia_otorgada (usuario_id);

CREATE INDEX IF NOT EXISTS ix_metrica_grupo_grupo_id
  ON transparencia.metrica_grupo (grupo_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_metrica_grupo_grupo_id_periodo_id_codigo
  ON transparencia.metrica_grupo (grupo_id, periodo_id, codigo);

CREATE INDEX IF NOT EXISTS ix_metrica_grupo_en_alerta
  ON transparencia.metrica_grupo (en_alerta);

CREATE INDEX IF NOT EXISTS ix_bloque_transparencia_grupo_id
  ON transparencia.bloque_transparencia (grupo_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_bloque_transparencia_grupo_id_numero_bloque
  ON transparencia.bloque_transparencia (grupo_id, numero_bloque);

CREATE UNIQUE INDEX IF NOT EXISTS uq_bloque_transparencia_hash_bloque
  ON transparencia.bloque_transparencia (hash_bloque);

CREATE INDEX IF NOT EXISTS ix_registro_sellado_bloque_id
  ON transparencia.registro_sellado (bloque_id);

CREATE INDEX IF NOT EXISTS ix_registro_sellado_entidad_id
  ON transparencia.registro_sellado (entidad_id);

CREATE INDEX IF NOT EXISTS ix_verificacion_publica_referencia_id
  ON transparencia.verificacion_publica (referencia_id);

CREATE INDEX IF NOT EXISTS ix_resena_participante_grupo_id
  ON transparencia.resena_participante (grupo_id);

CREATE INDEX IF NOT EXISTS ix_resena_participante_evaluado_usuario_id
  ON transparencia.resena_participante (evaluado_usuario_id);

CREATE INDEX IF NOT EXISTS ix_alerta_riesgo_ambito_id
  ON transparencia.alerta_riesgo (ambito_id);

CREATE INDEX IF NOT EXISTS ix_alerta_riesgo_estado
  ON transparencia.alerta_riesgo (estado);
