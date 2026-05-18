-- consolidation-group:advance gates POST /consolidation-groups/{id}/
-- ready-for-workstation, which advances a Picked CG to ReadyForWorkstation
-- (the buffered HUs have arrived at the put-wall). Supervisors get it by
-- default; operators get it so the workstation operator can mark their own
-- group ready from the mobile UI.

INSERT INTO role_permissions (role, permission) VALUES
  ('Admin',      'consolidation-group:advance'),
  ('Supervisor', 'consolidation-group:advance'),
  ('Operator',   'consolidation-group:advance');
