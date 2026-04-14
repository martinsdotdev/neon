-- Reference data tables for read-only entities (no event sourcing).
-- Schema matches domain case classes: Carrier, Location, Order, Sku, User.

CREATE TABLE IF NOT EXISTS carrier (
  id     UUID PRIMARY KEY,
  code   VARCHAR(255) NOT NULL,
  name   VARCHAR(255) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS location (
  id               UUID PRIMARY KEY,
  code             VARCHAR(255) NOT NULL,
  type             VARCHAR(50)  NOT NULL,
  zone_id          UUID,
  picking_sequence INT
);

CREATE TABLE IF NOT EXISTS orders (
  id         UUID PRIMARY KEY,
  priority   VARCHAR(50) NOT NULL DEFAULT 'Normal',
  carrier_id UUID REFERENCES carrier(id),
  lines      JSONB NOT NULL DEFAULT '[]'
);

CREATE TABLE IF NOT EXISTS sku (
  id            UUID PRIMARY KEY,
  code          VARCHAR(255) NOT NULL,
  description   TEXT NOT NULL DEFAULT '',
  lot_managed   BOOLEAN NOT NULL DEFAULT false,
  uom_hierarchy JSONB NOT NULL DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS users (
  id     UUID PRIMARY KEY,
  login  VARCHAR(255) NOT NULL,
  name   VARCHAR(255) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT true
);
