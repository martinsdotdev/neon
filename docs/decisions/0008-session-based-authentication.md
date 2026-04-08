# Use Server-Side Sessions for Authentication

## Status

Proposed

## Context and Problem Statement

Neon WES has no authentication. All HTTP endpoints are publicly accessible. The
`users` table exists with basic fields (id, login, name, active) but has no
password support.

How should we authenticate users?

## Decision Drivers

- Security: follow established best practices (The Copenhagen Book, OWASP)
- Simplicity: minimize new dependencies and infrastructure
- Fit with existing architecture: Pekko HTTP directives, R2DBC/PostgreSQL,
  Scala 3
- Immediate revocation: ability to lock out users instantly (security incidents)
- Prior art: production Scala systems using session-based auth

## Considered Options

- Server-side sessions following The Copenhagen Book
- Stateless JWT tokens
- pekko-http-session library (client-side sessions)

## Decision Outcome

Chosen option: "Server-side sessions following The Copenhagen Book", because it
is the simplest correct approach for a single-cluster web application backed by
PostgreSQL.

### Consequences

#### Positive

- Sessions are stored server-side, enabling immediate revocation on logout or
  security incident
- Only one new dependency (argon2-jvm for password hashing); session tokens use
  built-in JVM crypto (SecureRandom, SHA-256)
- Follows The Copenhagen Book recommendations exactly: HttpOnly/Secure/SameSite
  cookies, SHA-256 hashed tokens, 30-day sliding expiry
- Fits naturally into existing patterns: R2dbcHelper for DB access, custom Pekko
  HTTP directives, error ADTs mapped to 401/403

#### Negative

- Requires a DB lookup per authenticated request (mitigated: PostgreSQL is
  already on the hot path for every operation)
- More custom code than using pekko-http-session library (mitigated: full
  control, no library quirks)

### Confirmation

The decision will be confirmed by:

- `PasswordHasherSuite`: Argon2id hash/verify round-trip
- `AuthenticationServiceSuite`: login, session validation, expiry, renewal,
  logout
- `AuthRoutesSuite`: login (Set-Cookie), logout (clear cookie), me (auth
  context), unauthenticated (401)
- Manual curl verification of full login/request/logout flow

## Pros and Cons of the Options

### Server-Side Sessions (The Copenhagen Book)

Session tokens generated with 160 bits of entropy (SecureRandom), SHA-256 hashed
before PostgreSQL storage. HttpOnly/Secure/SameSite=Lax cookies. 30-day expiry
with 15-day sliding renewal. Argon2id for password hashing (19 MB memory, 2
iterations, 1 parallelism).

- Good, because Copenhagen Book is a well-regarded, security-reviewed reference
- Good, because Argon2id is the top recommendation (Copenhagen Book, OWASP);
  validated in production Scala by SoftwareMill's Bootzooka
- Good, because session-based auth is the standard for Scala web apps (Lichess
  uses sessions+MongoDB, TabNews uses sessions+PostgreSQL)
- Good, because immediate session revocation is trivial (DELETE from sessions)
- Good, because minimal dependencies: only argon2-jvm (2.12, Java library)
- Neutral, because DB lookup per request, but PostgreSQL is already the hot path
- Bad, because more custom code than a library solution

### Stateless JWT Tokens

jwt-circe (11.0.3) for token encoding/decoding. Bearer token in Authorization
header. Short-lived access tokens (15min) with longer-lived refresh tokens in DB.

- Good, because no DB lookup per request (signature validation only)
- Good, because jwt-circe integrates with existing Circe stack
- Good, because stateless design scales across services
- Bad, because The Copenhagen Book explicitly advises against JWT for web apps
- Bad, because revocation requires a blocklist (negating stateless benefit) or
  short expiry windows
- Bad, because more complex: two token types, refresh flow, token rotation
- Bad, because Neon WES is a single cluster; statelessness provides no benefit

### pekko-http-session (SoftwareMill Library)

com.softwaremill.pekko-http-session 0.7.1. Client-side sessions: signed data
stored in cookies. Built-in directives, CSRF, refresh tokens.

- Good, because battle-tested library with Scala 3 support
- Good, because built-in directives reduce custom code
- Good, because CSRF protection included
- Bad, because client-side sessions (data in cookie) differ from Copenhagen
  Book's server-side model
- Bad, because the pjfanning fork was archived; SoftwareMill's maintained
  version is less active
- Bad, because still need custom code for login, passwords, roles, permissions
- Bad, because adds a dependency without removing most of the implementation work

## More Information

### Session Architecture

- **Storage**: PostgreSQL `sessions` table with `token_hash`, `user_id`,
  `expires_at`
- **Transport**: Session token in Secure, HttpOnly, SameSite=Lax cookie
- **Validation**: SHA-256 hash the cookie value, look up by hash in DB
- **Renewal**: Extend expiry to 30 days if session used within 15 days of expiry

### Database Schema

```sql
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);

CREATE TABLE sessions (
  id         UUID PRIMARY KEY,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  user_id    UUID NOT NULL REFERENCES users(id),
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
```

### References

- [The Copenhagen Book](https://thecopenhagenbook.com/): sessions, server-side
  tokens, password authentication, CSRF
- [Lucia Auth](https://lucia-auth.com/): auth patterns and educational resources
- [Lichess (lila)](https://github.com/lichess-org/lila): Scala 3 chess server
  with session-based auth
- [Bootzooka](https://github.com/softwaremill/bootzooka): Scala 3 scaffold with
  Argon2id password hashing
- [TabNews](https://github.com/filipedeschamps/tabnews.com.br): session-based
  auth with feature-flag authorization
