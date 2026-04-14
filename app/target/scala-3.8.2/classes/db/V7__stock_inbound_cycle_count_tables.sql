-- CQRS read-side projection tables for stock, inbound, and cycle count aggregates.
-- Populated by Pekko Projection handlers consuming tagged events from the journal.

-- Stock positions indexed by (SKU, warehouse area) for allocation queries
CREATE TABLE IF NOT EXISTS stock_position_by_sku_area (
  stock_position_id  UUID NOT NULL,
  sku_id             UUID NOT NULL,
  warehouse_area_id  UUID NOT NULL,
  status             VARCHAR(50) NOT NULL,
  expiration_date    DATE,
  on_hand_quantity   INT NOT NULL DEFAULT 0,
  available_quantity INT NOT NULL DEFAULT 0,
  allocated_quantity INT NOT NULL DEFAULT 0,
  reserved_quantity  INT NOT NULL DEFAULT 0,
  blocked_quantity   INT NOT NULL DEFAULT 0,
  PRIMARY KEY (stock_position_id)
);
CREATE INDEX IF NOT EXISTS stock_position_sku_area_idx ON stock_position_by_sku_area (sku_id, warehouse_area_id);

-- Handling unit stock indexed by container
CREATE TABLE IF NOT EXISTS handling_unit_stock_by_container (
  handling_unit_stock_id UUID NOT NULL,
  sku_id                 UUID NOT NULL,
  stock_position_id      UUID NOT NULL,
  container_id           UUID NOT NULL,
  slot_code              VARCHAR(50),
  on_hand_quantity       INT NOT NULL DEFAULT 0,
  available_quantity     INT NOT NULL DEFAULT 0,
  allocated_quantity     INT NOT NULL DEFAULT 0,
  reserved_quantity      INT NOT NULL DEFAULT 0,
  blocked_quantity       INT NOT NULL DEFAULT 0,
  PRIMARY KEY (handling_unit_stock_id)
);
CREATE INDEX IF NOT EXISTS hu_stock_container_idx ON handling_unit_stock_by_container (container_id);

-- Inbound deliveries indexed by state
CREATE TABLE IF NOT EXISTS inbound_delivery_by_state (
  inbound_delivery_id UUID NOT NULL,
  sku_id              UUID NOT NULL,
  expected_quantity   INT NOT NULL,
  received_quantity   INT NOT NULL DEFAULT 0,
  rejected_quantity   INT NOT NULL DEFAULT 0,
  state               VARCHAR(50) NOT NULL,
  PRIMARY KEY (inbound_delivery_id)
);

-- Goods receipts indexed by inbound delivery
CREATE TABLE IF NOT EXISTS goods_receipt_by_delivery (
  goods_receipt_id    UUID NOT NULL,
  inbound_delivery_id UUID NOT NULL,
  state               VARCHAR(50) NOT NULL,
  PRIMARY KEY (goods_receipt_id)
);
CREATE INDEX IF NOT EXISTS goods_receipt_delivery_idx ON goods_receipt_by_delivery (inbound_delivery_id);

-- Cycle counts indexed by state
CREATE TABLE IF NOT EXISTS cycle_count_by_state (
  cycle_count_id    UUID NOT NULL,
  warehouse_area_id UUID NOT NULL,
  count_type        VARCHAR(50) NOT NULL,
  count_method      VARCHAR(50) NOT NULL,
  state             VARCHAR(50) NOT NULL,
  PRIMARY KEY (cycle_count_id)
);

-- Count tasks indexed by cycle count
CREATE TABLE IF NOT EXISTS count_task_by_cycle_count (
  count_task_id    UUID NOT NULL,
  cycle_count_id   UUID NOT NULL,
  sku_id           UUID NOT NULL,
  location_id      UUID NOT NULL,
  expected_quantity INT NOT NULL,
  actual_quantity   INT,
  variance          INT,
  state             VARCHAR(50) NOT NULL,
  PRIMARY KEY (count_task_id)
);
CREATE INDEX IF NOT EXISTS count_task_cycle_count_idx ON count_task_by_cycle_count (cycle_count_id);

-- Add mode column to workstation projection table
ALTER TABLE workstation_by_type_and_state ADD COLUMN IF NOT EXISTS mode VARCHAR(50) DEFAULT 'Picking';
