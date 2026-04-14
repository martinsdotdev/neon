-- CQRS read-side projection tables for cross-entity queries.
-- Populated by Pekko Projection handlers consuming tagged events from the journal.

-- Tasks indexed by wave (for TaskRepository.findByWaveId)
CREATE TABLE IF NOT EXISTS task_by_wave (
  task_id          UUID NOT NULL,
  wave_id          UUID NOT NULL,
  order_id         UUID NOT NULL,
  handling_unit_id UUID,
  state            VARCHAR(50) NOT NULL,
  PRIMARY KEY (task_id)
);

CREATE INDEX IF NOT EXISTS task_by_wave_wave_id_idx
  ON task_by_wave (wave_id);

-- Tasks indexed by handling unit (for TaskRepository.findByHandlingUnitId)
CREATE TABLE IF NOT EXISTS task_by_handling_unit (
  task_id          UUID NOT NULL,
  handling_unit_id UUID NOT NULL,
  wave_id          UUID,
  order_id         UUID NOT NULL,
  state            VARCHAR(50) NOT NULL,
  PRIMARY KEY (task_id)
);

CREATE INDEX IF NOT EXISTS task_by_handling_unit_hu_id_idx
  ON task_by_handling_unit (handling_unit_id);

-- Consolidation groups indexed by wave (for ConsolidationGroupRepository.findByWaveId)
CREATE TABLE IF NOT EXISTS consolidation_group_by_wave (
  consolidation_group_id UUID NOT NULL,
  wave_id                UUID NOT NULL,
  order_ids              UUID[] NOT NULL,
  state                  VARCHAR(50) NOT NULL,
  PRIMARY KEY (consolidation_group_id)
);

CREATE INDEX IF NOT EXISTS consolidation_group_by_wave_wave_id_idx
  ON consolidation_group_by_wave (wave_id);

-- Transport orders indexed by handling unit (for TransportOrderRepository.findByHandlingUnitId)
CREATE TABLE IF NOT EXISTS transport_order_by_handling_unit (
  transport_order_id UUID NOT NULL,
  handling_unit_id   UUID NOT NULL,
  destination        UUID NOT NULL,
  state              VARCHAR(50) NOT NULL,
  PRIMARY KEY (transport_order_id)
);

CREATE INDEX IF NOT EXISTS transport_order_by_hu_id_idx
  ON transport_order_by_handling_unit (handling_unit_id);

-- Workstations indexed by type and state (for WorkstationRepository.findIdleByType)
CREATE TABLE IF NOT EXISTS workstation_by_type_and_state (
  workstation_id   UUID NOT NULL,
  workstation_type VARCHAR(50) NOT NULL,
  slot_count       INT NOT NULL,
  state            VARCHAR(50) NOT NULL,
  PRIMARY KEY (workstation_id)
);

CREATE INDEX IF NOT EXISTS workstation_by_type_state_idx
  ON workstation_by_type_and_state (workstation_type, state);

-- Handling units for batch lookup (for HandlingUnitRepository.findByIds)
CREATE TABLE IF NOT EXISTS handling_unit_lookup (
  handling_unit_id  UUID NOT NULL,
  packaging_level   VARCHAR(50) NOT NULL,
  current_location  UUID,
  state             VARCHAR(50) NOT NULL,
  PRIMARY KEY (handling_unit_id)
);

-- Slots indexed by workstation (for SlotRepository.findByWorkstationId)
CREATE TABLE IF NOT EXISTS slot_by_workstation (
  slot_id        UUID NOT NULL,
  workstation_id UUID NOT NULL,
  order_id       UUID,
  state          VARCHAR(50) NOT NULL,
  PRIMARY KEY (slot_id)
);

CREATE INDEX IF NOT EXISTS slot_by_workstation_ws_id_idx
  ON slot_by_workstation (workstation_id);

-- Inventory indexed by (location, sku, lot) triad
-- (for InventoryRepository.findByLocationSkuLot)
CREATE TABLE IF NOT EXISTS inventory_by_location_sku_lot (
  inventory_id UUID NOT NULL,
  location_id  UUID NOT NULL,
  sku_id       UUID NOT NULL,
  lot          VARCHAR(255),
  on_hand      INT NOT NULL DEFAULT 0,
  reserved     INT NOT NULL DEFAULT 0,
  PRIMARY KEY (inventory_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS inventory_location_sku_lot_idx
  ON inventory_by_location_sku_lot (location_id, sku_id, lot);

-- Wave dispatch assignments (for WaveDispatchAssignmentRepository)
CREATE TABLE IF NOT EXISTS wave_dispatch_assignment (
  wave_id    UUID NOT NULL,
  dock_id    UUID NOT NULL,
  carrier_id UUID NOT NULL,
  active     BOOLEAN NOT NULL DEFAULT true,
  PRIMARY KEY (wave_id, dock_id)
);

CREATE INDEX IF NOT EXISTS wave_dispatch_by_dock_idx
  ON wave_dispatch_assignment (dock_id) WHERE active = true;
