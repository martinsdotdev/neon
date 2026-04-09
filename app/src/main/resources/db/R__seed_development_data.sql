-- Repeatable Flyway migration: development seed data.
-- Re-runs whenever this file changes. All INSERTs use ON CONFLICT DO NOTHING
-- so existing rows are never overwritten.
--
-- Default password for all users: "password"

-- ============================================================================
-- Zone IDs (logical groupings for locations)
-- ============================================================================

-- No zones table exists; zone_id is an opaque UUID on location.
-- Zone A = picking zone, Zone B = shipping zone

-- ============================================================================
-- Users
-- ============================================================================

INSERT INTO users (id, login, name, password_hash, role, active) VALUES
  ('019e0000-0001-7000-8000-000000000001', 'admin',      'Alice Admin',      '$argon2id$v=19$m=19456,t=2,p=1$0/t3wl7vucB/Hk7L+MRJWQ$uHMdg84ZZl+9ZIL8PiQ2mAzVTZoUKd0ZCqKNuqLWyfs', 'Admin',      true),
  ('019e0000-0001-7000-8000-000000000002', 'supervisor', 'Sam Supervisor',   '$argon2id$v=19$m=19456,t=2,p=1$0/t3wl7vucB/Hk7L+MRJWQ$uHMdg84ZZl+9ZIL8PiQ2mAzVTZoUKd0ZCqKNuqLWyfs', 'Supervisor', true),
  ('019e0000-0001-7000-8000-000000000003', 'operator1',  'Oscar Operator',   '$argon2id$v=19$m=19456,t=2,p=1$0/t3wl7vucB/Hk7L+MRJWQ$uHMdg84ZZl+9ZIL8PiQ2mAzVTZoUKd0ZCqKNuqLWyfs', 'Operator',   true),
  ('019e0000-0001-7000-8000-000000000004', 'operator2',  'Olivia Operator',  '$argon2id$v=19$m=19456,t=2,p=1$0/t3wl7vucB/Hk7L+MRJWQ$uHMdg84ZZl+9ZIL8PiQ2mAzVTZoUKd0ZCqKNuqLWyfs', 'Operator',   true),
  ('019e0000-0001-7000-8000-000000000005', 'viewer',     'Victor Viewer',    '$argon2id$v=19$m=19456,t=2,p=1$0/t3wl7vucB/Hk7L+MRJWQ$uHMdg84ZZl+9ZIL8PiQ2mAzVTZoUKd0ZCqKNuqLWyfs', 'Viewer',     true),
  ('019e0000-0001-7000-8000-000000000006', 'inactive',   'Irene Inactive',   '$argon2id$v=19$m=19456,t=2,p=1$0/t3wl7vucB/Hk7L+MRJWQ$uHMdg84ZZl+9ZIL8PiQ2mAzVTZoUKd0ZCqKNuqLWyfs', 'Operator',   false)
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- Carriers
-- ============================================================================

INSERT INTO carrier (id, code, name, active) VALUES
  ('019e0000-0002-7000-8000-000000000001', 'FDX',  'FedEx',              true),
  ('019e0000-0002-7000-8000-000000000002', 'UPS',  'UPS',                true),
  ('019e0000-0002-7000-8000-000000000003', 'USPS', 'USPS',               true),
  ('019e0000-0002-7000-8000-000000000004', 'DHL',  'DHL Express',        false)
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- Locations
-- ============================================================================

-- Zone A: picking zone
INSERT INTO location (id, code, type, zone_id, picking_sequence) VALUES
  ('019e0000-0003-7000-8000-000000000001', 'PICK-A01', 'Pick',    '019e0000-00ff-7000-8000-00000000000a', 1),
  ('019e0000-0003-7000-8000-000000000002', 'PICK-A02', 'Pick',    '019e0000-00ff-7000-8000-00000000000a', 2),
  ('019e0000-0003-7000-8000-000000000003', 'PICK-A03', 'Pick',    '019e0000-00ff-7000-8000-00000000000a', 3),
  ('019e0000-0003-7000-8000-000000000004', 'PICK-A04', 'Pick',    '019e0000-00ff-7000-8000-00000000000a', 4),
  ('019e0000-0003-7000-8000-000000000005', 'PICK-A05', 'Pick',    '019e0000-00ff-7000-8000-00000000000a', 5),
  ('019e0000-0003-7000-8000-000000000006', 'RSV-A01',  'Reserve', '019e0000-00ff-7000-8000-00000000000a', NULL),
  ('019e0000-0003-7000-8000-000000000007', 'RSV-A02',  'Reserve', '019e0000-00ff-7000-8000-00000000000a', NULL)
ON CONFLICT (id) DO NOTHING;

-- Zone B: shipping zone
INSERT INTO location (id, code, type, zone_id, picking_sequence) VALUES
  ('019e0000-0003-7000-8000-000000000010', 'BUF-B01',  'Buffer',  '019e0000-00ff-7000-8000-00000000000b', NULL),
  ('019e0000-0003-7000-8000-000000000011', 'BUF-B02',  'Buffer',  '019e0000-00ff-7000-8000-00000000000b', NULL),
  ('019e0000-0003-7000-8000-000000000012', 'STG-B01',  'Staging', '019e0000-00ff-7000-8000-00000000000b', NULL),
  ('019e0000-0003-7000-8000-000000000013', 'STG-B02',  'Staging', '019e0000-00ff-7000-8000-00000000000b', NULL),
  ('019e0000-0003-7000-8000-000000000014', 'PACK-B01', 'Packing', '019e0000-00ff-7000-8000-00000000000b', NULL)
ON CONFLICT (id) DO NOTHING;

-- Docks
INSERT INTO location (id, code, type, zone_id, picking_sequence) VALUES
  ('019e0000-0003-7000-8000-000000000020', 'DOCK-01', 'Dock', NULL, NULL),
  ('019e0000-0003-7000-8000-000000000021', 'DOCK-02', 'Dock', NULL, NULL),
  ('019e0000-0003-7000-8000-000000000022', 'DOCK-03', 'Dock', NULL, NULL)
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- SKUs
-- ============================================================================

INSERT INTO sku (id, code, description, lot_managed, uom_hierarchy) VALUES
  ('019e0000-0004-7000-8000-000000000001', 'WIDGET-001',  'Standard Widget',       false, '{"Case": 12, "Pallet": 144}'::jsonb),
  ('019e0000-0004-7000-8000-000000000002', 'WIDGET-002',  'Premium Widget',        false, '{"Case": 6, "Pallet": 72}'::jsonb),
  ('019e0000-0004-7000-8000-000000000003', 'GADGET-001',  'Electronic Gadget',     false, '{"Case": 4, "InnerPack": 2, "Pallet": 48}'::jsonb),
  ('019e0000-0004-7000-8000-000000000004', 'PHARMA-001',  'Pharmaceutical Item A', true,  '{"Case": 24, "Pallet": 240}'::jsonb),
  ('019e0000-0004-7000-8000-000000000005', 'PHARMA-002',  'Pharmaceutical Item B', true,  '{"Case": 10}'::jsonb),
  ('019e0000-0004-7000-8000-000000000006', 'BULK-001',    'Bulk Material',         false, '{"Pallet": 1}'::jsonb)
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- Orders
-- ============================================================================

-- FedEx orders (normal and high priority)
INSERT INTO orders (id, priority, carrier_id, lines) VALUES
  ('019e0000-0005-7000-8000-000000000001', 'Normal', '019e0000-0002-7000-8000-000000000001',
   '[{"skuId":"019e0000-0004-7000-8000-000000000001","packagingLevel":"Case","quantity":3},
     {"skuId":"019e0000-0004-7000-8000-000000000002","packagingLevel":"Each","quantity":5}]'::jsonb),
  ('019e0000-0005-7000-8000-000000000002', 'High', '019e0000-0002-7000-8000-000000000001',
   '[{"skuId":"019e0000-0004-7000-8000-000000000003","packagingLevel":"Each","quantity":2}]'::jsonb),
  ('019e0000-0005-7000-8000-000000000003', 'Normal', '019e0000-0002-7000-8000-000000000001',
   '[{"skuId":"019e0000-0004-7000-8000-000000000001","packagingLevel":"Each","quantity":10}]'::jsonb)
ON CONFLICT (id) DO NOTHING;

-- UPS orders
INSERT INTO orders (id, priority, carrier_id, lines) VALUES
  ('019e0000-0005-7000-8000-000000000004', 'Normal', '019e0000-0002-7000-8000-000000000002',
   '[{"skuId":"019e0000-0004-7000-8000-000000000002","packagingLevel":"Case","quantity":1},
     {"skuId":"019e0000-0004-7000-8000-000000000003","packagingLevel":"InnerPack","quantity":3}]'::jsonb),
  ('019e0000-0005-7000-8000-000000000005', 'Critical', '019e0000-0002-7000-8000-000000000002',
   '[{"skuId":"019e0000-0004-7000-8000-000000000004","packagingLevel":"Each","quantity":20}]'::jsonb)
ON CONFLICT (id) DO NOTHING;

-- USPS orders
INSERT INTO orders (id, priority, carrier_id, lines) VALUES
  ('019e0000-0005-7000-8000-000000000006', 'Low', '019e0000-0002-7000-8000-000000000003',
   '[{"skuId":"019e0000-0004-7000-8000-000000000005","packagingLevel":"Case","quantity":2}]'::jsonb),
  ('019e0000-0005-7000-8000-000000000007', 'Normal', '019e0000-0002-7000-8000-000000000003',
   '[{"skuId":"019e0000-0004-7000-8000-000000000001","packagingLevel":"Pallet","quantity":1},
     {"skuId":"019e0000-0004-7000-8000-000000000006","packagingLevel":"Pallet","quantity":2}]'::jsonb)
ON CONFLICT (id) DO NOTHING;
