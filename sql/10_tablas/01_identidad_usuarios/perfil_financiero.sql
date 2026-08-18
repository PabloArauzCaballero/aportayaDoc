-- perfil_financiero · módulo 01 — Identidad, Usuarios y Seguridad
-- clase de dominio: PerfilFinanciero
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS identidad.perfil_financiero (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  usuario_id                         UUID NOT NULL,
  ocupacion                          VARCHAR(80) NOT NULL,
  ingreso_mensual_declarado          NUMERIC(14,2) NOT NULL,
  capacidad_aporte_declarada         NUMERIC(14,2) NOT NULL,
  fuente_ingresos                    VARCHAR(120) NOT NULL,
  es_pep                             BOOLEAN DEFAULT FALSE NOT NULL,
  actualizado_en                     TIMESTAMPTZ NOT NULL,
  CONSTRAINT pk_perfil_financiero PRIMARY KEY (id)
);

COMMENT ON TABLE identidad.perfil_financiero IS 'Módulo 01 — Identidad, Usuarios y Seguridad. Saber con certeza a quién le estás confiando plata ajena';
COMMENT ON COLUMN identidad.perfil_financiero.id IS 'PK';
COMMENT ON COLUMN identidad.perfil_financiero.usuario_id IS 'FK, UQ';
