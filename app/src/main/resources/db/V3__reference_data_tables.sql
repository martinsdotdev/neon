-- Reference data tables for read-only entities (no event sourcing).

CREATE TABLE IF NOT EXISTS location (
  id        UUID PRIMARY KEY,
  name      VARCHAR(255) NOT NULL,
  type      VARCHAR(50)  NOT NULL,
  zone_id   UUID
);

CREATE TABLE IF NOT EXISTS carrier (
  id   UUID PRIMARY KEY,
  name VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS orders (
  id         UUID PRIMARY KEY,
  carrier_id UUID REFERENCES carrier(id),
  lines      JSONB NOT NULL DEFAULT '[]'
);

CREATE TABLE IF NOT EXISTS sku (
  id            UUID PRIMARY KEY,
  name          VARCHAR(255) NOT NULL,
  uom_hierarchy JSONB NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
  id   UUID PRIMARY KEY,
  name VARCHAR(255) NOT NULL
);
