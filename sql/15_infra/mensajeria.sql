-- ADR-027 · infraestructura de mensajeria por esquema de servicio.
-- Generado por scripts/generar_ddl.py — no editar a mano.
-- estado_saga solo en los esquemas que orquestan una saga (ADR-028).

-- ── aportes ──
-- Outbox del servicio: se escribe en la MISMA transaccion del caso
-- de uso; el relevo lo publica (UPDATE de estado, ADR-027/018).
CREATE TABLE IF NOT EXISTS aportes.evento_dominio (
  id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  tipo           VARCHAR(60) NOT NULL,
  version        VARCHAR(10) NOT NULL DEFAULT '1',
  agregado       VARCHAR(40) NOT NULL,
  agregado_id    UUID        NOT NULL,
  payload        JSONB       NOT NULL,
  metadatos      JSONB       NOT NULL DEFAULT '{}'::jsonb,
  correlation_id UUID        NOT NULL,
  causation_id   UUID,
  ocurrido_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
  publicado_en   TIMESTAMPTZ,
  estado         VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE'
    CONSTRAINT ck_aportes_evtdom_estado
    CHECK (estado IN ('PENDIENTE', 'PUBLICADO', 'FALLIDO')),
  intentos       SMALLINT    NOT NULL DEFAULT 0
);
-- Indice parcial de despacho: el relevo solo mira lo PENDIENTE.
CREATE INDEX IF NOT EXISTS ix_aportes_evtdom_despacho
  ON aportes.evento_dominio (ocurrido_en) WHERE estado = 'PENDIENTE';
COMMENT ON TABLE aportes.evento_dominio IS 'Outbox transaccional del servicio (ADR-027).';

-- Idempotencia de consumo: (id_evento, consumidor). Append-only de facto.
CREATE TABLE IF NOT EXISTS aportes.evento_consumido (
  id_evento    UUID        NOT NULL,
  consumidor   VARCHAR(60) NOT NULL,
  consumido_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT pk_aportes_evtcons PRIMARY KEY (id_evento, consumidor)
);
COMMENT ON TABLE aportes.evento_consumido IS 'Marca de evento ya consumido, por consumidor (ADR-027).';

-- ShedLock: un solo relevo/planificador activo entre replicas (ADR-018).
CREATE TABLE IF NOT EXISTS aportes.shedlock (
  name       VARCHAR(64)  PRIMARY KEY,
  lock_until TIMESTAMPTZ  NOT NULL,
  locked_at  TIMESTAMPTZ  NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);
COMMENT ON TABLE aportes.shedlock IS 'Bloqueo de trabajos programados entre replicas (ADR-018).';

-- Estado de saga orquestada: se persiste el paso en la MISMA
-- transaccion que el efecto local; un @Scheduled barre las atascadas (ADR-028).
CREATE TABLE IF NOT EXISTS aportes.estado_saga (
  id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  tipo_saga      VARCHAR(60) NOT NULL,
  clave_negocio  VARCHAR(120) NOT NULL,
  paso           SMALLINT    NOT NULL DEFAULT 0,
  estado         VARCHAR(15) NOT NULL DEFAULT 'INICIADA'
    CONSTRAINT ck_aportes_saga_estado
    CHECK (estado IN ('INICIADA','EN_CURSO','COMPLETADA','COMPENSANDO','COMPENSADA','FALLIDA')),
  datos          JSONB       NOT NULL DEFAULT '{}'::jsonb,
  creado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_aportes_saga_clave UNIQUE (tipo_saga, clave_negocio)
);
CREATE INDEX IF NOT EXISTS ix_aportes_saga_pendiente
  ON aportes.estado_saga (actualizado_en) WHERE estado IN ('INICIADA','EN_CURSO','COMPENSANDO');
COMMENT ON TABLE aportes.estado_saga IS 'Estado de saga orquestada por este servicio (ADR-028).';

-- ── auditoria ──
-- Outbox del servicio: se escribe en la MISMA transaccion del caso
-- de uso; el relevo lo publica (UPDATE de estado, ADR-027/018).
CREATE TABLE IF NOT EXISTS auditoria.evento_dominio (
  id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  tipo           VARCHAR(60) NOT NULL,
  version        VARCHAR(10) NOT NULL DEFAULT '1',
  agregado       VARCHAR(40) NOT NULL,
  agregado_id    UUID        NOT NULL,
  payload        JSONB       NOT NULL,
  metadatos      JSONB       NOT NULL DEFAULT '{}'::jsonb,
  correlation_id UUID        NOT NULL,
  causation_id   UUID,
  ocurrido_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
  publicado_en   TIMESTAMPTZ,
  estado         VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE'
    CONSTRAINT ck_auditoria_evtdom_estado
    CHECK (estado IN ('PENDIENTE', 'PUBLICADO', 'FALLIDO')),
  intentos       SMALLINT    NOT NULL DEFAULT 0
);
-- Indice parcial de despacho: el relevo solo mira lo PENDIENTE.
CREATE INDEX IF NOT EXISTS ix_auditoria_evtdom_despacho
  ON auditoria.evento_dominio (ocurrido_en) WHERE estado = 'PENDIENTE';
COMMENT ON TABLE auditoria.evento_dominio IS 'Outbox transaccional del servicio (ADR-027).';

-- Idempotencia de consumo: (id_evento, consumidor). Append-only de facto.
CREATE TABLE IF NOT EXISTS auditoria.evento_consumido (
  id_evento    UUID        NOT NULL,
  consumidor   VARCHAR(60) NOT NULL,
  consumido_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT pk_auditoria_evtcons PRIMARY KEY (id_evento, consumidor)
);
COMMENT ON TABLE auditoria.evento_consumido IS 'Marca de evento ya consumido, por consumidor (ADR-027).';

-- ShedLock: un solo relevo/planificador activo entre replicas (ADR-018).
CREATE TABLE IF NOT EXISTS auditoria.shedlock (
  name       VARCHAR(64)  PRIMARY KEY,
  lock_until TIMESTAMPTZ  NOT NULL,
  locked_at  TIMESTAMPTZ  NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);
COMMENT ON TABLE auditoria.shedlock IS 'Bloqueo de trabajos programados entre replicas (ADR-018).';

-- ── cumplimiento ──
-- Outbox del servicio: se escribe en la MISMA transaccion del caso
-- de uso; el relevo lo publica (UPDATE de estado, ADR-027/018).
CREATE TABLE IF NOT EXISTS cumplimiento.evento_dominio (
  id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  tipo           VARCHAR(60) NOT NULL,
  version        VARCHAR(10) NOT NULL DEFAULT '1',
  agregado       VARCHAR(40) NOT NULL,
  agregado_id    UUID        NOT NULL,
  payload        JSONB       NOT NULL,
  metadatos      JSONB       NOT NULL DEFAULT '{}'::jsonb,
  correlation_id UUID        NOT NULL,
  causation_id   UUID,
  ocurrido_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
  publicado_en   TIMESTAMPTZ,
  estado         VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE'
    CONSTRAINT ck_cumplimiento_evtdom_estado
    CHECK (estado IN ('PENDIENTE', 'PUBLICADO', 'FALLIDO')),
  intentos       SMALLINT    NOT NULL DEFAULT 0
);
-- Indice parcial de despacho: el relevo solo mira lo PENDIENTE.
CREATE INDEX IF NOT EXISTS ix_cumplimiento_evtdom_despacho
  ON cumplimiento.evento_dominio (ocurrido_en) WHERE estado = 'PENDIENTE';
COMMENT ON TABLE cumplimiento.evento_dominio IS 'Outbox transaccional del servicio (ADR-027).';

-- Idempotencia de consumo: (id_evento, consumidor). Append-only de facto.
CREATE TABLE IF NOT EXISTS cumplimiento.evento_consumido (
  id_evento    UUID        NOT NULL,
  consumidor   VARCHAR(60) NOT NULL,
  consumido_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT pk_cumplimiento_evtcons PRIMARY KEY (id_evento, consumidor)
);
COMMENT ON TABLE cumplimiento.evento_consumido IS 'Marca de evento ya consumido, por consumidor (ADR-027).';

-- ShedLock: un solo relevo/planificador activo entre replicas (ADR-018).
CREATE TABLE IF NOT EXISTS cumplimiento.shedlock (
  name       VARCHAR(64)  PRIMARY KEY,
  lock_until TIMESTAMPTZ  NOT NULL,
  locked_at  TIMESTAMPTZ  NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);
COMMENT ON TABLE cumplimiento.shedlock IS 'Bloqueo de trabajos programados entre replicas (ADR-018).';

-- ── entregas ──
-- Outbox del servicio: se escribe en la MISMA transaccion del caso
-- de uso; el relevo lo publica (UPDATE de estado, ADR-027/018).
CREATE TABLE IF NOT EXISTS entregas.evento_dominio (
  id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  tipo           VARCHAR(60) NOT NULL,
  version        VARCHAR(10) NOT NULL DEFAULT '1',
  agregado       VARCHAR(40) NOT NULL,
  agregado_id    UUID        NOT NULL,
  payload        JSONB       NOT NULL,
  metadatos      JSONB       NOT NULL DEFAULT '{}'::jsonb,
  correlation_id UUID        NOT NULL,
  causation_id   UUID,
  ocurrido_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
  publicado_en   TIMESTAMPTZ,
  estado         VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE'
    CONSTRAINT ck_entregas_evtdom_estado
    CHECK (estado IN ('PENDIENTE', 'PUBLICADO', 'FALLIDO')),
  intentos       SMALLINT    NOT NULL DEFAULT 0
);
-- Indice parcial de despacho: el relevo solo mira lo PENDIENTE.
CREATE INDEX IF NOT EXISTS ix_entregas_evtdom_despacho
  ON entregas.evento_dominio (ocurrido_en) WHERE estado = 'PENDIENTE';
COMMENT ON TABLE entregas.evento_dominio IS 'Outbox transaccional del servicio (ADR-027).';

-- Idempotencia de consumo: (id_evento, consumidor). Append-only de facto.
CREATE TABLE IF NOT EXISTS entregas.evento_consumido (
  id_evento    UUID        NOT NULL,
  consumidor   VARCHAR(60) NOT NULL,
  consumido_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT pk_entregas_evtcons PRIMARY KEY (id_evento, consumidor)
);
COMMENT ON TABLE entregas.evento_consumido IS 'Marca de evento ya consumido, por consumidor (ADR-027).';

-- ShedLock: un solo relevo/planificador activo entre replicas (ADR-018).
CREATE TABLE IF NOT EXISTS entregas.shedlock (
  name       VARCHAR(64)  PRIMARY KEY,
  lock_until TIMESTAMPTZ  NOT NULL,
  locked_at  TIMESTAMPTZ  NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);
COMMENT ON TABLE entregas.shedlock IS 'Bloqueo de trabajos programados entre replicas (ADR-018).';

-- Estado de saga orquestada: se persiste el paso en la MISMA
-- transaccion que el efecto local; un @Scheduled barre las atascadas (ADR-028).
CREATE TABLE IF NOT EXISTS entregas.estado_saga (
  id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  tipo_saga      VARCHAR(60) NOT NULL,
  clave_negocio  VARCHAR(120) NOT NULL,
  paso           SMALLINT    NOT NULL DEFAULT 0,
  estado         VARCHAR(15) NOT NULL DEFAULT 'INICIADA'
    CONSTRAINT ck_entregas_saga_estado
    CHECK (estado IN ('INICIADA','EN_CURSO','COMPLETADA','COMPENSANDO','COMPENSADA','FALLIDA')),
  datos          JSONB       NOT NULL DEFAULT '{}'::jsonb,
  creado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_entregas_saga_clave UNIQUE (tipo_saga, clave_negocio)
);
CREATE INDEX IF NOT EXISTS ix_entregas_saga_pendiente
  ON entregas.estado_saga (actualizado_en) WHERE estado IN ('INICIADA','EN_CURSO','COMPENSANDO');
COMMENT ON TABLE entregas.estado_saga IS 'Estado de saga orquestada por este servicio (ADR-028).';

-- ── erp ──
-- Outbox del servicio: se escribe en la MISMA transaccion del caso
-- de uso; el relevo lo publica (UPDATE de estado, ADR-027/018).
CREATE TABLE IF NOT EXISTS erp.evento_dominio (
  id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  tipo           VARCHAR(60) NOT NULL,
  version        VARCHAR(10) NOT NULL DEFAULT '1',
  agregado       VARCHAR(40) NOT NULL,
  agregado_id    UUID        NOT NULL,
  payload        JSONB       NOT NULL,
  metadatos      JSONB       NOT NULL DEFAULT '{}'::jsonb,
  correlation_id UUID        NOT NULL,
  causation_id   UUID,
  ocurrido_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
  publicado_en   TIMESTAMPTZ,
  estado         VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE'
    CONSTRAINT ck_erp_evtdom_estado
    CHECK (estado IN ('PENDIENTE', 'PUBLICADO', 'FALLIDO')),
  intentos       SMALLINT    NOT NULL DEFAULT 0
);
-- Indice parcial de despacho: el relevo solo mira lo PENDIENTE.
CREATE INDEX IF NOT EXISTS ix_erp_evtdom_despacho
  ON erp.evento_dominio (ocurrido_en) WHERE estado = 'PENDIENTE';
COMMENT ON TABLE erp.evento_dominio IS 'Outbox transaccional del servicio (ADR-027).';

-- Idempotencia de consumo: (id_evento, consumidor). Append-only de facto.
CREATE TABLE IF NOT EXISTS erp.evento_consumido (
  id_evento    UUID        NOT NULL,
  consumidor   VARCHAR(60) NOT NULL,
  consumido_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT pk_erp_evtcons PRIMARY KEY (id_evento, consumidor)
);
COMMENT ON TABLE erp.evento_consumido IS 'Marca de evento ya consumido, por consumidor (ADR-027).';

-- ShedLock: un solo relevo/planificador activo entre replicas (ADR-018).
CREATE TABLE IF NOT EXISTS erp.shedlock (
  name       VARCHAR(64)  PRIMARY KEY,
  lock_until TIMESTAMPTZ  NOT NULL,
  locked_at  TIMESTAMPTZ  NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);
COMMENT ON TABLE erp.shedlock IS 'Bloqueo de trabajos programados entre replicas (ADR-018).';

-- ── garantia ──
-- Outbox del servicio: se escribe en la MISMA transaccion del caso
-- de uso; el relevo lo publica (UPDATE de estado, ADR-027/018).
CREATE TABLE IF NOT EXISTS garantia.evento_dominio (
  id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  tipo           VARCHAR(60) NOT NULL,
  version        VARCHAR(10) NOT NULL DEFAULT '1',
  agregado       VARCHAR(40) NOT NULL,
  agregado_id    UUID        NOT NULL,
  payload        JSONB       NOT NULL,
  metadatos      JSONB       NOT NULL DEFAULT '{}'::jsonb,
  correlation_id UUID        NOT NULL,
  causation_id   UUID,
  ocurrido_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
  publicado_en   TIMESTAMPTZ,
  estado         VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE'
    CONSTRAINT ck_garantia_evtdom_estado
    CHECK (estado IN ('PENDIENTE', 'PUBLICADO', 'FALLIDO')),
  intentos       SMALLINT    NOT NULL DEFAULT 0
);
-- Indice parcial de despacho: el relevo solo mira lo PENDIENTE.
CREATE INDEX IF NOT EXISTS ix_garantia_evtdom_despacho
  ON garantia.evento_dominio (ocurrido_en) WHERE estado = 'PENDIENTE';
COMMENT ON TABLE garantia.evento_dominio IS 'Outbox transaccional del servicio (ADR-027).';

-- Idempotencia de consumo: (id_evento, consumidor). Append-only de facto.
CREATE TABLE IF NOT EXISTS garantia.evento_consumido (
  id_evento    UUID        NOT NULL,
  consumidor   VARCHAR(60) NOT NULL,
  consumido_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT pk_garantia_evtcons PRIMARY KEY (id_evento, consumidor)
);
COMMENT ON TABLE garantia.evento_consumido IS 'Marca de evento ya consumido, por consumidor (ADR-027).';

-- ShedLock: un solo relevo/planificador activo entre replicas (ADR-018).
CREATE TABLE IF NOT EXISTS garantia.shedlock (
  name       VARCHAR(64)  PRIMARY KEY,
  lock_until TIMESTAMPTZ  NOT NULL,
  locked_at  TIMESTAMPTZ  NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);
COMMENT ON TABLE garantia.shedlock IS 'Bloqueo de trabajos programados entre replicas (ADR-018).';

-- Estado de saga orquestada: se persiste el paso en la MISMA
-- transaccion que el efecto local; un @Scheduled barre las atascadas (ADR-028).
CREATE TABLE IF NOT EXISTS garantia.estado_saga (
  id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  tipo_saga      VARCHAR(60) NOT NULL,
  clave_negocio  VARCHAR(120) NOT NULL,
  paso           SMALLINT    NOT NULL DEFAULT 0,
  estado         VARCHAR(15) NOT NULL DEFAULT 'INICIADA'
    CONSTRAINT ck_garantia_saga_estado
    CHECK (estado IN ('INICIADA','EN_CURSO','COMPLETADA','COMPENSANDO','COMPENSADA','FALLIDA')),
  datos          JSONB       NOT NULL DEFAULT '{}'::jsonb,
  creado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_garantia_saga_clave UNIQUE (tipo_saga, clave_negocio)
);
CREATE INDEX IF NOT EXISTS ix_garantia_saga_pendiente
  ON garantia.estado_saga (actualizado_en) WHERE estado IN ('INICIADA','EN_CURSO','COMPENSANDO');
COMMENT ON TABLE garantia.estado_saga IS 'Estado de saga orquestada por este servicio (ADR-028).';

-- ── grupos ──
-- Outbox del servicio: se escribe en la MISMA transaccion del caso
-- de uso; el relevo lo publica (UPDATE de estado, ADR-027/018).
CREATE TABLE IF NOT EXISTS grupos.evento_dominio (
  id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  tipo           VARCHAR(60) NOT NULL,
  version        VARCHAR(10) NOT NULL DEFAULT '1',
  agregado       VARCHAR(40) NOT NULL,
  agregado_id    UUID        NOT NULL,
  payload        JSONB       NOT NULL,
  metadatos      JSONB       NOT NULL DEFAULT '{}'::jsonb,
  correlation_id UUID        NOT NULL,
  causation_id   UUID,
  ocurrido_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
  publicado_en   TIMESTAMPTZ,
  estado         VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE'
    CONSTRAINT ck_grupos_evtdom_estado
    CHECK (estado IN ('PENDIENTE', 'PUBLICADO', 'FALLIDO')),
  intentos       SMALLINT    NOT NULL DEFAULT 0
);
-- Indice parcial de despacho: el relevo solo mira lo PENDIENTE.
CREATE INDEX IF NOT EXISTS ix_grupos_evtdom_despacho
  ON grupos.evento_dominio (ocurrido_en) WHERE estado = 'PENDIENTE';
COMMENT ON TABLE grupos.evento_dominio IS 'Outbox transaccional del servicio (ADR-027).';

-- Idempotencia de consumo: (id_evento, consumidor). Append-only de facto.
CREATE TABLE IF NOT EXISTS grupos.evento_consumido (
  id_evento    UUID        NOT NULL,
  consumidor   VARCHAR(60) NOT NULL,
  consumido_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT pk_grupos_evtcons PRIMARY KEY (id_evento, consumidor)
);
COMMENT ON TABLE grupos.evento_consumido IS 'Marca de evento ya consumido, por consumidor (ADR-027).';

-- ShedLock: un solo relevo/planificador activo entre replicas (ADR-018).
CREATE TABLE IF NOT EXISTS grupos.shedlock (
  name       VARCHAR(64)  PRIMARY KEY,
  lock_until TIMESTAMPTZ  NOT NULL,
  locked_at  TIMESTAMPTZ  NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);
COMMENT ON TABLE grupos.shedlock IS 'Bloqueo de trabajos programados entre replicas (ADR-018).';

-- ── identidad ──
-- Outbox del servicio: se escribe en la MISMA transaccion del caso
-- de uso; el relevo lo publica (UPDATE de estado, ADR-027/018).
CREATE TABLE IF NOT EXISTS identidad.evento_dominio (
  id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  tipo           VARCHAR(60) NOT NULL,
  version        VARCHAR(10) NOT NULL DEFAULT '1',
  agregado       VARCHAR(40) NOT NULL,
  agregado_id    UUID        NOT NULL,
  payload        JSONB       NOT NULL,
  metadatos      JSONB       NOT NULL DEFAULT '{}'::jsonb,
  correlation_id UUID        NOT NULL,
  causation_id   UUID,
  ocurrido_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
  publicado_en   TIMESTAMPTZ,
  estado         VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE'
    CONSTRAINT ck_identidad_evtdom_estado
    CHECK (estado IN ('PENDIENTE', 'PUBLICADO', 'FALLIDO')),
  intentos       SMALLINT    NOT NULL DEFAULT 0
);
-- Indice parcial de despacho: el relevo solo mira lo PENDIENTE.
CREATE INDEX IF NOT EXISTS ix_identidad_evtdom_despacho
  ON identidad.evento_dominio (ocurrido_en) WHERE estado = 'PENDIENTE';
COMMENT ON TABLE identidad.evento_dominio IS 'Outbox transaccional del servicio (ADR-027).';

-- Idempotencia de consumo: (id_evento, consumidor). Append-only de facto.
CREATE TABLE IF NOT EXISTS identidad.evento_consumido (
  id_evento    UUID        NOT NULL,
  consumidor   VARCHAR(60) NOT NULL,
  consumido_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT pk_identidad_evtcons PRIMARY KEY (id_evento, consumidor)
);
COMMENT ON TABLE identidad.evento_consumido IS 'Marca de evento ya consumido, por consumidor (ADR-027).';

-- ShedLock: un solo relevo/planificador activo entre replicas (ADR-018).
CREATE TABLE IF NOT EXISTS identidad.shedlock (
  name       VARCHAR(64)  PRIMARY KEY,
  lock_until TIMESTAMPTZ  NOT NULL,
  locked_at  TIMESTAMPTZ  NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);
COMMENT ON TABLE identidad.shedlock IS 'Bloqueo de trabajos programados entre replicas (ADR-018).';

-- ── notificaciones ──
-- Outbox del servicio: se escribe en la MISMA transaccion del caso
-- de uso; el relevo lo publica (UPDATE de estado, ADR-027/018).
CREATE TABLE IF NOT EXISTS notificaciones.evento_dominio (
  id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  tipo           VARCHAR(60) NOT NULL,
  version        VARCHAR(10) NOT NULL DEFAULT '1',
  agregado       VARCHAR(40) NOT NULL,
  agregado_id    UUID        NOT NULL,
  payload        JSONB       NOT NULL,
  metadatos      JSONB       NOT NULL DEFAULT '{}'::jsonb,
  correlation_id UUID        NOT NULL,
  causation_id   UUID,
  ocurrido_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
  publicado_en   TIMESTAMPTZ,
  estado         VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE'
    CONSTRAINT ck_notificaciones_evtdom_estado
    CHECK (estado IN ('PENDIENTE', 'PUBLICADO', 'FALLIDO')),
  intentos       SMALLINT    NOT NULL DEFAULT 0
);
-- Indice parcial de despacho: el relevo solo mira lo PENDIENTE.
CREATE INDEX IF NOT EXISTS ix_notificaciones_evtdom_despacho
  ON notificaciones.evento_dominio (ocurrido_en) WHERE estado = 'PENDIENTE';
COMMENT ON TABLE notificaciones.evento_dominio IS 'Outbox transaccional del servicio (ADR-027).';

-- Idempotencia de consumo: (id_evento, consumidor). Append-only de facto.
CREATE TABLE IF NOT EXISTS notificaciones.evento_consumido (
  id_evento    UUID        NOT NULL,
  consumidor   VARCHAR(60) NOT NULL,
  consumido_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT pk_notificaciones_evtcons PRIMARY KEY (id_evento, consumidor)
);
COMMENT ON TABLE notificaciones.evento_consumido IS 'Marca de evento ya consumido, por consumidor (ADR-027).';

-- ShedLock: un solo relevo/planificador activo entre replicas (ADR-018).
CREATE TABLE IF NOT EXISTS notificaciones.shedlock (
  name       VARCHAR(64)  PRIMARY KEY,
  lock_until TIMESTAMPTZ  NOT NULL,
  locked_at  TIMESTAMPTZ  NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);
COMMENT ON TABLE notificaciones.shedlock IS 'Bloqueo de trabajos programados entre replicas (ADR-018).';

-- ── nucleo_financiero ──
-- Outbox del servicio: se escribe en la MISMA transaccion del caso
-- de uso; el relevo lo publica (UPDATE de estado, ADR-027/018).
CREATE TABLE IF NOT EXISTS nucleo_financiero.evento_dominio (
  id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  tipo           VARCHAR(60) NOT NULL,
  version        VARCHAR(10) NOT NULL DEFAULT '1',
  agregado       VARCHAR(40) NOT NULL,
  agregado_id    UUID        NOT NULL,
  payload        JSONB       NOT NULL,
  metadatos      JSONB       NOT NULL DEFAULT '{}'::jsonb,
  correlation_id UUID        NOT NULL,
  causation_id   UUID,
  ocurrido_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
  publicado_en   TIMESTAMPTZ,
  estado         VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE'
    CONSTRAINT ck_nucleo_financiero_evtdom_estado
    CHECK (estado IN ('PENDIENTE', 'PUBLICADO', 'FALLIDO')),
  intentos       SMALLINT    NOT NULL DEFAULT 0
);
-- Indice parcial de despacho: el relevo solo mira lo PENDIENTE.
CREATE INDEX IF NOT EXISTS ix_nucleo_financiero_evtdom_despacho
  ON nucleo_financiero.evento_dominio (ocurrido_en) WHERE estado = 'PENDIENTE';
COMMENT ON TABLE nucleo_financiero.evento_dominio IS 'Outbox transaccional del servicio (ADR-027).';

-- Idempotencia de consumo: (id_evento, consumidor). Append-only de facto.
CREATE TABLE IF NOT EXISTS nucleo_financiero.evento_consumido (
  id_evento    UUID        NOT NULL,
  consumidor   VARCHAR(60) NOT NULL,
  consumido_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT pk_nucleo_financiero_evtcons PRIMARY KEY (id_evento, consumidor)
);
COMMENT ON TABLE nucleo_financiero.evento_consumido IS 'Marca de evento ya consumido, por consumidor (ADR-027).';

-- ShedLock: un solo relevo/planificador activo entre replicas (ADR-018).
CREATE TABLE IF NOT EXISTS nucleo_financiero.shedlock (
  name       VARCHAR(64)  PRIMARY KEY,
  lock_until TIMESTAMPTZ  NOT NULL,
  locked_at  TIMESTAMPTZ  NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);
COMMENT ON TABLE nucleo_financiero.shedlock IS 'Bloqueo de trabajos programados entre replicas (ADR-018).';

-- ── organizador ──
-- Outbox del servicio: se escribe en la MISMA transaccion del caso
-- de uso; el relevo lo publica (UPDATE de estado, ADR-027/018).
CREATE TABLE IF NOT EXISTS organizador.evento_dominio (
  id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  tipo           VARCHAR(60) NOT NULL,
  version        VARCHAR(10) NOT NULL DEFAULT '1',
  agregado       VARCHAR(40) NOT NULL,
  agregado_id    UUID        NOT NULL,
  payload        JSONB       NOT NULL,
  metadatos      JSONB       NOT NULL DEFAULT '{}'::jsonb,
  correlation_id UUID        NOT NULL,
  causation_id   UUID,
  ocurrido_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
  publicado_en   TIMESTAMPTZ,
  estado         VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE'
    CONSTRAINT ck_organizador_evtdom_estado
    CHECK (estado IN ('PENDIENTE', 'PUBLICADO', 'FALLIDO')),
  intentos       SMALLINT    NOT NULL DEFAULT 0
);
-- Indice parcial de despacho: el relevo solo mira lo PENDIENTE.
CREATE INDEX IF NOT EXISTS ix_organizador_evtdom_despacho
  ON organizador.evento_dominio (ocurrido_en) WHERE estado = 'PENDIENTE';
COMMENT ON TABLE organizador.evento_dominio IS 'Outbox transaccional del servicio (ADR-027).';

-- Idempotencia de consumo: (id_evento, consumidor). Append-only de facto.
CREATE TABLE IF NOT EXISTS organizador.evento_consumido (
  id_evento    UUID        NOT NULL,
  consumidor   VARCHAR(60) NOT NULL,
  consumido_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT pk_organizador_evtcons PRIMARY KEY (id_evento, consumidor)
);
COMMENT ON TABLE organizador.evento_consumido IS 'Marca de evento ya consumido, por consumidor (ADR-027).';

-- ShedLock: un solo relevo/planificador activo entre replicas (ADR-018).
CREATE TABLE IF NOT EXISTS organizador.shedlock (
  name       VARCHAR(64)  PRIMARY KEY,
  lock_until TIMESTAMPTZ  NOT NULL,
  locked_at  TIMESTAMPTZ  NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);
COMMENT ON TABLE organizador.shedlock IS 'Bloqueo de trabajos programados entre replicas (ADR-018).';

-- ── publicidad ──
-- Outbox del servicio: se escribe en la MISMA transaccion del caso
-- de uso; el relevo lo publica (UPDATE de estado, ADR-027/018).
CREATE TABLE IF NOT EXISTS publicidad.evento_dominio (
  id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  tipo           VARCHAR(60) NOT NULL,
  version        VARCHAR(10) NOT NULL DEFAULT '1',
  agregado       VARCHAR(40) NOT NULL,
  agregado_id    UUID        NOT NULL,
  payload        JSONB       NOT NULL,
  metadatos      JSONB       NOT NULL DEFAULT '{}'::jsonb,
  correlation_id UUID        NOT NULL,
  causation_id   UUID,
  ocurrido_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
  publicado_en   TIMESTAMPTZ,
  estado         VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE'
    CONSTRAINT ck_publicidad_evtdom_estado
    CHECK (estado IN ('PENDIENTE', 'PUBLICADO', 'FALLIDO')),
  intentos       SMALLINT    NOT NULL DEFAULT 0
);
-- Indice parcial de despacho: el relevo solo mira lo PENDIENTE.
CREATE INDEX IF NOT EXISTS ix_publicidad_evtdom_despacho
  ON publicidad.evento_dominio (ocurrido_en) WHERE estado = 'PENDIENTE';
COMMENT ON TABLE publicidad.evento_dominio IS 'Outbox transaccional del servicio (ADR-027).';

-- Idempotencia de consumo: (id_evento, consumidor). Append-only de facto.
CREATE TABLE IF NOT EXISTS publicidad.evento_consumido (
  id_evento    UUID        NOT NULL,
  consumidor   VARCHAR(60) NOT NULL,
  consumido_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT pk_publicidad_evtcons PRIMARY KEY (id_evento, consumidor)
);
COMMENT ON TABLE publicidad.evento_consumido IS 'Marca de evento ya consumido, por consumidor (ADR-027).';

-- ShedLock: un solo relevo/planificador activo entre replicas (ADR-018).
CREATE TABLE IF NOT EXISTS publicidad.shedlock (
  name       VARCHAR(64)  PRIMARY KEY,
  lock_until TIMESTAMPTZ  NOT NULL,
  locked_at  TIMESTAMPTZ  NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);
COMMENT ON TABLE publicidad.shedlock IS 'Bloqueo de trabajos programados entre replicas (ADR-018).';

-- ── tarifas ──
-- Outbox del servicio: se escribe en la MISMA transaccion del caso
-- de uso; el relevo lo publica (UPDATE de estado, ADR-027/018).
CREATE TABLE IF NOT EXISTS tarifas.evento_dominio (
  id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  tipo           VARCHAR(60) NOT NULL,
  version        VARCHAR(10) NOT NULL DEFAULT '1',
  agregado       VARCHAR(40) NOT NULL,
  agregado_id    UUID        NOT NULL,
  payload        JSONB       NOT NULL,
  metadatos      JSONB       NOT NULL DEFAULT '{}'::jsonb,
  correlation_id UUID        NOT NULL,
  causation_id   UUID,
  ocurrido_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
  publicado_en   TIMESTAMPTZ,
  estado         VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE'
    CONSTRAINT ck_tarifas_evtdom_estado
    CHECK (estado IN ('PENDIENTE', 'PUBLICADO', 'FALLIDO')),
  intentos       SMALLINT    NOT NULL DEFAULT 0
);
-- Indice parcial de despacho: el relevo solo mira lo PENDIENTE.
CREATE INDEX IF NOT EXISTS ix_tarifas_evtdom_despacho
  ON tarifas.evento_dominio (ocurrido_en) WHERE estado = 'PENDIENTE';
COMMENT ON TABLE tarifas.evento_dominio IS 'Outbox transaccional del servicio (ADR-027).';

-- Idempotencia de consumo: (id_evento, consumidor). Append-only de facto.
CREATE TABLE IF NOT EXISTS tarifas.evento_consumido (
  id_evento    UUID        NOT NULL,
  consumidor   VARCHAR(60) NOT NULL,
  consumido_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT pk_tarifas_evtcons PRIMARY KEY (id_evento, consumidor)
);
COMMENT ON TABLE tarifas.evento_consumido IS 'Marca de evento ya consumido, por consumidor (ADR-027).';

-- ShedLock: un solo relevo/planificador activo entre replicas (ADR-018).
CREATE TABLE IF NOT EXISTS tarifas.shedlock (
  name       VARCHAR(64)  PRIMARY KEY,
  lock_until TIMESTAMPTZ  NOT NULL,
  locked_at  TIMESTAMPTZ  NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);
COMMENT ON TABLE tarifas.shedlock IS 'Bloqueo de trabajos programados entre replicas (ADR-018).';

-- Estado de saga orquestada: se persiste el paso en la MISMA
-- transaccion que el efecto local; un @Scheduled barre las atascadas (ADR-028).
CREATE TABLE IF NOT EXISTS tarifas.estado_saga (
  id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  tipo_saga      VARCHAR(60) NOT NULL,
  clave_negocio  VARCHAR(120) NOT NULL,
  paso           SMALLINT    NOT NULL DEFAULT 0,
  estado         VARCHAR(15) NOT NULL DEFAULT 'INICIADA'
    CONSTRAINT ck_tarifas_saga_estado
    CHECK (estado IN ('INICIADA','EN_CURSO','COMPLETADA','COMPENSANDO','COMPENSADA','FALLIDA')),
  datos          JSONB       NOT NULL DEFAULT '{}'::jsonb,
  creado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_tarifas_saga_clave UNIQUE (tipo_saga, clave_negocio)
);
CREATE INDEX IF NOT EXISTS ix_tarifas_saga_pendiente
  ON tarifas.estado_saga (actualizado_en) WHERE estado IN ('INICIADA','EN_CURSO','COMPENSANDO');
COMMENT ON TABLE tarifas.estado_saga IS 'Estado de saga orquestada por este servicio (ADR-028).';

-- ── transparencia ──
-- Outbox del servicio: se escribe en la MISMA transaccion del caso
-- de uso; el relevo lo publica (UPDATE de estado, ADR-027/018).
CREATE TABLE IF NOT EXISTS transparencia.evento_dominio (
  id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  tipo           VARCHAR(60) NOT NULL,
  version        VARCHAR(10) NOT NULL DEFAULT '1',
  agregado       VARCHAR(40) NOT NULL,
  agregado_id    UUID        NOT NULL,
  payload        JSONB       NOT NULL,
  metadatos      JSONB       NOT NULL DEFAULT '{}'::jsonb,
  correlation_id UUID        NOT NULL,
  causation_id   UUID,
  ocurrido_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
  publicado_en   TIMESTAMPTZ,
  estado         VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE'
    CONSTRAINT ck_transparencia_evtdom_estado
    CHECK (estado IN ('PENDIENTE', 'PUBLICADO', 'FALLIDO')),
  intentos       SMALLINT    NOT NULL DEFAULT 0
);
-- Indice parcial de despacho: el relevo solo mira lo PENDIENTE.
CREATE INDEX IF NOT EXISTS ix_transparencia_evtdom_despacho
  ON transparencia.evento_dominio (ocurrido_en) WHERE estado = 'PENDIENTE';
COMMENT ON TABLE transparencia.evento_dominio IS 'Outbox transaccional del servicio (ADR-027).';

-- Idempotencia de consumo: (id_evento, consumidor). Append-only de facto.
CREATE TABLE IF NOT EXISTS transparencia.evento_consumido (
  id_evento    UUID        NOT NULL,
  consumidor   VARCHAR(60) NOT NULL,
  consumido_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT pk_transparencia_evtcons PRIMARY KEY (id_evento, consumidor)
);
COMMENT ON TABLE transparencia.evento_consumido IS 'Marca de evento ya consumido, por consumidor (ADR-027).';

-- ShedLock: un solo relevo/planificador activo entre replicas (ADR-018).
CREATE TABLE IF NOT EXISTS transparencia.shedlock (
  name       VARCHAR(64)  PRIMARY KEY,
  lock_until TIMESTAMPTZ  NOT NULL,
  locked_at  TIMESTAMPTZ  NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);
COMMENT ON TABLE transparencia.shedlock IS 'Bloqueo de trabajos programados entre replicas (ADR-018).';
