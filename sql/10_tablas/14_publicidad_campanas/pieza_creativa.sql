-- pieza_creativa · módulo 14 — Publicidad y Campañas
-- clase de dominio: PiezaCreativa
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS publicidad.pieza_creativa (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  anunciante_id                      UUID NOT NULL,
  titulo                             VARCHAR(120) NOT NULL,
  texto                              VARCHAR(300),
  url_recurso                        VARCHAR(300) NOT NULL,
  tipo_recurso                       VARCHAR(10) NOT NULL,
  estado_moderacion                  VARCHAR(15) NOT NULL,
  creada_en                          TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_pieza_creativa PRIMARY KEY (id),
  CONSTRAINT ck_pieza_creativa_tipo_recurso CHECK (tipo_recurso IN ('IMAGEN', 'VIDEO')),
  CONSTRAINT ck_pieza_creativa_estado_moderacion CHECK (estado_moderacion IN ('APROBADA', 'PENDIENTE', 'RECHAZADA'))
);

COMMENT ON TABLE publicidad.pieza_creativa IS 'Módulo 14 — Publicidad y Campañas. Que un partner se anuncie dentro de la app sin inventar un segundo cobro';
COMMENT ON COLUMN publicidad.pieza_creativa.id IS 'PK';
COMMENT ON COLUMN publicidad.pieza_creativa.anunciante_id IS 'FK, IDX';
COMMENT ON COLUMN publicidad.pieza_creativa.texto IS 'NULL';
COMMENT ON COLUMN publicidad.pieza_creativa.tipo_recurso IS 'CK';
COMMENT ON COLUMN publicidad.pieza_creativa.estado_moderacion IS 'CK, IDX';
