-- regla_automatizacion · módulo 07 — Organizador y Automatización
-- clase de dominio: ReglaAutomatizacion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS organizador.regla_automatizacion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  codigo                             VARCHAR(40) NOT NULL,
  descripcion                        VARCHAR(200) NOT NULL,
  disparador                         VARCHAR(10) NOT NULL,
  expresion_disparo                  VARCHAR(80) NOT NULL,
  condicion                          VARCHAR(300) NOT NULL,
  accion                             VARCHAR(30) NOT NULL,
  requiere_confirmacion_humana       BOOLEAN DEFAULT FALSE NOT NULL,
  prioridad                          SMALLINT NOT NULL,
  activa                             BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_regla_automatizacion PRIMARY KEY (id),
  CONSTRAINT ck_regla_automatizacion_disparador CHECK (disparador IN ('CRON', 'EVENTO')),
  CONSTRAINT ck_regla_automatizacion_accion CHECK (accion IN ('APLICAR_MORA', 'EJECUTAR_ENTREGA', 'ENVIAR_RECORDATORIO', 'ESCALAR_COBRANZA', 'GENERAR_COBROS', 'LIQUIDAR_PERIODO'))
);

COMMENT ON TABLE organizador.regla_automatizacion IS 'Módulo 07 — Organizador y Automatización. Administrar es un rol, no un negocio: el organizador no cobra ni custodia';
COMMENT ON COLUMN organizador.regla_automatizacion.id IS 'PK';
COMMENT ON COLUMN organizador.regla_automatizacion.codigo IS 'UQ';
COMMENT ON COLUMN organizador.regla_automatizacion.disparador IS 'CK';
COMMENT ON COLUMN organizador.regla_automatizacion.accion IS 'CK';
