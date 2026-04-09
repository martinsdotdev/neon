-- Pekko Persistence R2DBC: journal, snapshot, and durable state tables.
-- Schema follows pekko-persistence-r2dbc 1.1.x DDL for PostgreSQL.

CREATE TABLE IF NOT EXISTS event_journal (
  slice          INT         NOT NULL,
  entity_type    VARCHAR(255) NOT NULL,
  persistence_id VARCHAR(255) NOT NULL,
  seq_nr         BIGINT      NOT NULL,
  db_timestamp   TIMESTAMP WITH TIME ZONE NOT NULL,
  writer         VARCHAR(255) NOT NULL,
  adapter_manifest VARCHAR(255),
  event_ser_id   INT         NOT NULL,
  event_ser_manifest VARCHAR(255),
  event_payload  BYTEA       NOT NULL,
  meta_ser_id    INT,
  meta_ser_manifest VARCHAR(255),
  meta_payload   BYTEA,
  deleted        BOOLEAN     DEFAULT false,
  tags           TEXT[],
  PRIMARY KEY (persistence_id, seq_nr)
);

CREATE INDEX IF NOT EXISTS event_journal_slice_idx
  ON event_journal (slice, entity_type, db_timestamp, seq_nr);

CREATE TABLE IF NOT EXISTS snapshot (
  slice          INT         NOT NULL,
  entity_type    VARCHAR(255) NOT NULL,
  persistence_id VARCHAR(255) NOT NULL,
  seq_nr         BIGINT      NOT NULL,
  write_timestamp BIGINT     NOT NULL,
  db_timestamp   TIMESTAMP WITH TIME ZONE NOT NULL,
  ser_id         INT         NOT NULL,
  ser_manifest   VARCHAR(255),
  snapshot       BYTEA       NOT NULL,
  meta_ser_id    INT,
  meta_ser_manifest VARCHAR(255),
  meta_payload   BYTEA,
  deleted        BOOLEAN     DEFAULT false,
  PRIMARY KEY (persistence_id)
);

CREATE TABLE IF NOT EXISTS durable_state (
  slice          INT         NOT NULL,
  entity_type    VARCHAR(255) NOT NULL,
  persistence_id VARCHAR(255) NOT NULL,
  revision       BIGINT      NOT NULL,
  db_timestamp   TIMESTAMP WITH TIME ZONE NOT NULL,
  state_ser_id   INT         NOT NULL,
  state_ser_manifest VARCHAR(255),
  state_payload  BYTEA       NOT NULL,
  tags           TEXT[],
  PRIMARY KEY (persistence_id)
);

CREATE INDEX IF NOT EXISTS durable_state_slice_idx
  ON durable_state (slice, entity_type, db_timestamp);
