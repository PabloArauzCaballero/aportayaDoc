-- campana_publicitaria · módulo 14 — Publicidad y Campañas
-- clase de dominio: CampanaPublicitaria
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS publicidad.campana_publicitaria (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  cuenta_publicitaria_id             UUID NOT NULL,
  nombre                             VARCHAR(120) NOT NULL,
  objetivo                           VARCHAR(25) NOT NULL,
  presupuesto_total                  NUMERIC(14,2) NOT NULL,
  presupuesto_consumido              NUMERIC(14,2) DEFAULT 0 NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  fecha_inicio                       TIMESTAMPTZ NOT NULL,
  fecha_fin                          TIMESTAMPTZ,
  estado                             VARCHAR(15) NOT NULL,
  aprobada_por                       UUID,
  CONSTRAINT pk_campana_publicitaria PRIMARY KEY (id),
  CONSTRAINT ck_campana_publicitaria_objetivo CHECK (objetivo IN ('CONVERSION', 'DESCARGA_APP', 'POSTULACION_GRUPO', 'TRAFICO', 'VISIBILIDAD_MARCA')),
  CONSTRAINT ck_campana_publicitaria_presupuesto_total CHECK (presupuesto_total > 0),
  CONSTRAINT ck_campana_publicitaria_estado CHECK (estado IN ('ACTIVA', 'BORRADOR', 'EN_REVISION', 'FINALIZADA', 'PAUSADA', 'RECHAZADA'))
);

COMMENT ON TABLE publicidad.campana_publicitaria IS 'Módulo 14 — Publicidad y Campañas. Que un partner se anuncie dentro de la app sin inventar un segundo cobro';
COMMENT ON COLUMN publicidad.campana_publicitaria.id IS 'PK';
COMMENT ON COLUMN publicidad.campana_publicitaria.cuenta_publicitaria_id IS 'FK, IDX';
COMMENT ON COLUMN publicidad.campana_publicitaria.objetivo IS 'CK';
COMMENT ON COLUMN publicidad.campana_publicitaria.presupuesto_total IS 'CK: > 0';
COMMENT ON COLUMN publicidad.campana_publicitaria.fecha_fin IS 'NULL';
COMMENT ON COLUMN publicidad.campana_publicitaria.estado IS 'CK, IDX';
COMMENT ON COLUMN publicidad.campana_publicitaria.aprobada_por IS 'FK, NULL';
