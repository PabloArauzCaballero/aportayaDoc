-- Claves foráneas del módulo 06 — Transparencia y Reputación
-- Generado por scripts/generar_ddl.py — no editar a mano.
-- Se aplican después de crear todas las tablas: el modelo tiene
-- referencias circulares entre módulos.
--
-- Cada una se borra si existe antes de crearse: PostgreSQL no tiene
-- ADD CONSTRAINT IF NOT EXISTS, y sql/aplicar.sql se aplica también
-- sobre una base que ya lo tiene. Borrar y volver a crear —en vez de
-- saltear si ya está— es lo que hace que un ON DELETE cambiado en el
-- modelo quede corregido al reaplicar.

ALTER TABLE transparencia.bloque_transparencia DROP CONSTRAINT IF EXISTS fk_bloque_transparencia_grupo_id;
ALTER TABLE transparencia.bloque_transparencia
  ADD CONSTRAINT fk_bloque_transparencia_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE transparencia.certificado_reputacion DROP CONSTRAINT IF EXISTS fk_certificado_reputacion_snapshot_id;
ALTER TABLE transparencia.certificado_reputacion
  ADD CONSTRAINT fk_certificado_reputacion_snapshot_id
  FOREIGN KEY (snapshot_id) REFERENCES transparencia.snapshot_reputacion (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE transparencia.certificado_reputacion DROP CONSTRAINT IF EXISTS fk_certificado_reputacion_usuario_id;
ALTER TABLE transparencia.certificado_reputacion
  ADD CONSTRAINT fk_certificado_reputacion_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE transparencia.componente_score DROP CONSTRAINT IF EXISTS fk_componente_score_puntaje_id;
ALTER TABLE transparencia.componente_score
  ADD CONSTRAINT fk_componente_score_puntaje_id
  FOREIGN KEY (puntaje_id) REFERENCES transparencia.puntaje_reputacion (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE transparencia.evento_reputacion DROP CONSTRAINT IF EXISTS fk_evento_reputacion_grupo_id;
ALTER TABLE transparencia.evento_reputacion
  ADD CONSTRAINT fk_evento_reputacion_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE transparencia.evento_reputacion DROP CONSTRAINT IF EXISTS fk_evento_reputacion_participante_id;
ALTER TABLE transparencia.evento_reputacion
  ADD CONSTRAINT fk_evento_reputacion_participante_id
  FOREIGN KEY (participante_id) REFERENCES grupos.participante (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE transparencia.evento_reputacion DROP CONSTRAINT IF EXISTS fk_evento_reputacion_revertido_por_id;
ALTER TABLE transparencia.evento_reputacion
  ADD CONSTRAINT fk_evento_reputacion_revertido_por_id
  FOREIGN KEY (revertido_por_id) REFERENCES transparencia.evento_reputacion (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE transparencia.evento_reputacion DROP CONSTRAINT IF EXISTS fk_evento_reputacion_usuario_id;
ALTER TABLE transparencia.evento_reputacion
  ADD CONSTRAINT fk_evento_reputacion_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE transparencia.insignia_otorgada DROP CONSTRAINT IF EXISTS fk_insignia_otorgada_insignia_id;
ALTER TABLE transparencia.insignia_otorgada
  ADD CONSTRAINT fk_insignia_otorgada_insignia_id
  FOREIGN KEY (insignia_id) REFERENCES transparencia.insignia_logro (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE transparencia.insignia_otorgada DROP CONSTRAINT IF EXISTS fk_insignia_otorgada_usuario_id;
ALTER TABLE transparencia.insignia_otorgada
  ADD CONSTRAINT fk_insignia_otorgada_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE transparencia.metrica_grupo DROP CONSTRAINT IF EXISTS fk_metrica_grupo_grupo_id;
ALTER TABLE transparencia.metrica_grupo
  ADD CONSTRAINT fk_metrica_grupo_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE transparencia.metrica_grupo DROP CONSTRAINT IF EXISTS fk_metrica_grupo_periodo_id;
ALTER TABLE transparencia.metrica_grupo
  ADD CONSTRAINT fk_metrica_grupo_periodo_id
  FOREIGN KEY (periodo_id) REFERENCES grupos.periodo (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE transparencia.peso_factor DROP CONSTRAINT IF EXISTS fk_peso_factor_modelo_id;
ALTER TABLE transparencia.peso_factor
  ADD CONSTRAINT fk_peso_factor_modelo_id
  FOREIGN KEY (modelo_id) REFERENCES transparencia.modelo_scoring (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE transparencia.puntaje_reputacion DROP CONSTRAINT IF EXISTS fk_puntaje_reputacion_modelo_id;
ALTER TABLE transparencia.puntaje_reputacion
  ADD CONSTRAINT fk_puntaje_reputacion_modelo_id
  FOREIGN KEY (modelo_id) REFERENCES transparencia.modelo_scoring (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE transparencia.puntaje_reputacion DROP CONSTRAINT IF EXISTS fk_puntaje_reputacion_usuario_id;
ALTER TABLE transparencia.puntaje_reputacion
  ADD CONSTRAINT fk_puntaje_reputacion_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE transparencia.registro_sellado DROP CONSTRAINT IF EXISTS fk_registro_sellado_bloque_id;
ALTER TABLE transparencia.registro_sellado
  ADD CONSTRAINT fk_registro_sellado_bloque_id
  FOREIGN KEY (bloque_id) REFERENCES transparencia.bloque_transparencia (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE transparencia.regla_impacto_evento DROP CONSTRAINT IF EXISTS fk_regla_impacto_evento_modelo_id;
ALTER TABLE transparencia.regla_impacto_evento
  ADD CONSTRAINT fk_regla_impacto_evento_modelo_id
  FOREIGN KEY (modelo_id) REFERENCES transparencia.modelo_scoring (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE transparencia.resena_participante DROP CONSTRAINT IF EXISTS fk_resena_participante_autor_participante_id;
ALTER TABLE transparencia.resena_participante
  ADD CONSTRAINT fk_resena_participante_autor_participante_id
  FOREIGN KEY (autor_participante_id) REFERENCES grupos.participante (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE transparencia.resena_participante DROP CONSTRAINT IF EXISTS fk_resena_participante_evaluado_usuario_id;
ALTER TABLE transparencia.resena_participante
  ADD CONSTRAINT fk_resena_participante_evaluado_usuario_id
  FOREIGN KEY (evaluado_usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE transparencia.resena_participante DROP CONSTRAINT IF EXISTS fk_resena_participante_grupo_id;
ALTER TABLE transparencia.resena_participante
  ADD CONSTRAINT fk_resena_participante_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE transparencia.resena_participante DROP CONSTRAINT IF EXISTS fk_resena_participante_moderada_por;
ALTER TABLE transparencia.resena_participante
  ADD CONSTRAINT fk_resena_participante_moderada_por
  FOREIGN KEY (moderada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE transparencia.snapshot_reputacion DROP CONSTRAINT IF EXISTS fk_snapshot_reputacion_usuario_id;
ALTER TABLE transparencia.snapshot_reputacion
  ADD CONSTRAINT fk_snapshot_reputacion_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;
