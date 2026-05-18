-- Operator role gains task:assign so the mobile picker can self-claim work
-- via POST /tasks/{id}/claim. The same permission gates the supervisor's
-- explicit /tasks/{id}/assign on behalf of others.

INSERT INTO role_permissions (role, permission) VALUES
  ('Operator', 'task:assign');
