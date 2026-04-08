# Use RBAC with Per-User Permission Overrides for Authorization

## Status

Proposed

## Context and Problem Statement

Neon WES needs authorization to control access to warehouse operations. The
system has clear role hierarchies (Admin, Supervisor, Operator, Viewer) but
occasionally requires per-user exceptions: an operator promoted to handle wave
planning, or a supervisor denied access to user management.

How should we model and enforce authorization?

## Decision Drivers

- WES domain: clear role hierarchies with occasional per-user exceptions
- Simplicity: avoid external services or complex graph-based models
- Expressiveness: support both granting and revoking individual permissions
- Deny wins: security-conservative default when overrides conflict
- Prior art: production monorepo's credential/power model with deny-wins
  overrides

## Considered Options

- RBAC with per-user permission overrides (deny wins)
- Plain RBAC (roles only, no overrides)
- Zanzibar/ReBAC (SpiceDB or OpenFGA)

## Decision Outcome

Chosen option: "RBAC with per-user permission overrides (deny wins)", because it
provides the right balance between simplicity and flexibility for WES operations.

### Consequences

#### Positive

- Roles provide sensible defaults; overrides handle exceptions without creating
  new roles
- Deny-wins semantics prevent accidental privilege escalation
- Two small tables (`role_permissions`, `user_permission_overrides`) with no
  external dependencies
- Effective permissions computed at session validation: no per-request overhead
  beyond the session lookup
- Custom Pekko HTTP directive (`requirePermission`) integrates cleanly with
  existing route structure

#### Negative

- Per-user overrides add complexity vs plain RBAC (mitigated: the override table
  is typically sparse)
- No per-resource ACLs (acceptable: WES authorization is on resource types, not
  individual entities)

### Confirmation

The decision will be confirmed by:

- `AuthenticationServiceSuite`: effective permissions with role defaults,
  allow overrides, deny overrides (deny wins)
- `TaskRoutesSuite`: 401 on unauthenticated, 403 on insufficient permissions
- Route-level permission guards on all mutation endpoints

## Pros and Cons of the Options

### RBAC with Per-User Permission Overrides

Roles define default permission sets. Per-user overrides add (allow) or remove
(deny) individual permissions. Effective permissions = role defaults +/- user
overrides, deny always wins.

- Good, because handles the common case (role-based) and exceptions (per-user)
  in one model
- Good, because deny-wins is security-conservative
- Good, because no external service; two PostgreSQL tables
- Good, because inspired by production systems (production credential/power
  model)
- Neutral, because override table adds a join; mitigated by computing at session
  validation
- Bad, because more complex than plain RBAC

### Plain RBAC (Roles Only)

Each role has a fixed set of permissions. No per-user exceptions.

- Good, because simplest model
- Good, because no override table or deny-wins logic
- Bad, because any exception requires creating a new role
- Bad, because role proliferation for edge cases (e.g., "OperatorWithWavePlan")

### Zanzibar/ReBAC (SpiceDB or OpenFGA)

External authorization service queried via gRPC or HTTP. Relationship tuples
model permissions as a graph.

- Good, because scales to fine-grained per-resource ACLs
- Good, because clean separation of authorization logic from application
- Bad, because requires deploying and maintaining a separate service
- Bad, because adds gRPC dependency (grpc-java or ScalaPB)
- Bad, because must keep authorization tuples in sync with domain state
- Bad, because Neon WES has no per-resource sharing; authorization is role-based
  on resource types
- Bad, because massive infrastructure overhead for current requirements

## More Information

### Authorization Model

```
roles (enum)          : Admin, Supervisor, Operator, Viewer
permissions (enum)    : wave:plan, wave:cancel, task:complete, ...
role_permissions      : role -> Set[permission]  (defaults)
user_permission_overrides : user -> permission -> allow|deny  (overrides)
```

Effective permissions = role defaults +/- user overrides, deny wins.

### Database Schema

```sql
ALTER TABLE users ADD COLUMN role VARCHAR(50) NOT NULL DEFAULT 'Operator';

CREATE TABLE role_permissions (
  role       VARCHAR(50) NOT NULL,
  permission VARCHAR(100) NOT NULL,
  PRIMARY KEY (role, permission)
);

CREATE TABLE user_permission_overrides (
  user_id    UUID NOT NULL REFERENCES users(id),
  permission VARCHAR(100) NOT NULL,
  effect     VARCHAR(10) NOT NULL CHECK (effect IN ('allow', 'deny')),
  PRIMARY KEY (user_id, permission)
);
```

### Permission Matrix

| Permission                      | Admin | Supervisor | Operator | Viewer |
| ------------------------------- | ----- | ---------- | -------- | ------ |
| `wave:plan`                     | x     | x          |          |        |
| `wave:cancel`                   | x     | x          |          |        |
| `task:complete`                 | x     | x          | x        |        |
| `transport-order:confirm`       | x     | x          | x        |        |
| `consolidation-group:complete`  | x     | x          | x        |        |
| `workstation:assign`            | x     | x          |          |        |
| `user:manage`                   | x     |            |          |        |

### References

- production monorepo: role/power model with credential overrides and
  deny-wins semantics
