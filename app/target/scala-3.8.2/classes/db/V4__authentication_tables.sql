-- Add auth columns to existing users table.
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);
ALTER TABLE users ADD COLUMN role VARCHAR(50) NOT NULL DEFAULT 'Operator';

-- Server-side sessions (Copenhagen Book pattern).
CREATE TABLE IF NOT EXISTS sessions (
  id          UUID PRIMARY KEY,
  token_hash  VARCHAR(64) NOT NULL UNIQUE,
  user_id     UUID NOT NULL REFERENCES users(id),
  expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  ip_address  VARCHAR(45),
  user_agent  TEXT
);

CREATE INDEX idx_sessions_token_hash ON sessions(token_hash);
CREATE INDEX idx_sessions_user_id ON sessions(user_id);
CREATE INDEX idx_sessions_expires_at ON sessions(expires_at);

-- Default permissions per role.
CREATE TABLE IF NOT EXISTS role_permissions (
  role        VARCHAR(50)  NOT NULL,
  permission  VARCHAR(100) NOT NULL,
  PRIMARY KEY (role, permission)
);

-- Per-user permission overrides (allow/deny, deny wins).
CREATE TABLE IF NOT EXISTS user_permission_overrides (
  user_id    UUID         NOT NULL REFERENCES users(id),
  permission VARCHAR(100) NOT NULL,
  effect     VARCHAR(10)  NOT NULL CHECK (effect IN ('allow', 'deny')),
  PRIMARY KEY (user_id, permission)
);

-- Seed role permissions.
INSERT INTO role_permissions (role, permission) VALUES
  ('Admin', 'wave:plan'),
  ('Admin', 'wave:cancel'),
  ('Admin', 'task:complete'),
  ('Admin', 'transport-order:confirm'),
  ('Admin', 'consolidation-group:complete'),
  ('Admin', 'workstation:assign'),
  ('Admin', 'user:manage'),
  ('Supervisor', 'wave:plan'),
  ('Supervisor', 'wave:cancel'),
  ('Supervisor', 'task:complete'),
  ('Supervisor', 'transport-order:confirm'),
  ('Supervisor', 'consolidation-group:complete'),
  ('Supervisor', 'workstation:assign'),
  ('Operator', 'task:complete'),
  ('Operator', 'transport-order:confirm'),
  ('Operator', 'consolidation-group:complete');
