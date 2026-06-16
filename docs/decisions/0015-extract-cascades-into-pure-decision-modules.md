---
status: "accepted"
date: 2026-06-11
decision-makers: project owner
consulted:
informed: future contributors
---

# Extract multi-step cascades into pure decision modules behind thin service shells

## Context and Problem Statement

The core module orchestrates cascades: completing a task can consume stock,
create a shortpick replacement, route a handling unit, and complete the wave
and consolidation group. Each such operation has historically been offered as
two services — a synchronous one returning `Either[Error, Result]` (testable
against in-memory repositories) and an asynchronous one returning `Future`
(Pekko-backed, used in production). Encoding the same cascade twice let the two
drift: the async production path silently lost stock consumption entirely, and
because it evaluated wave/picking completion over the projection-backed read
model, a shortpicked completion could complete a wave while its replacement
task was still open. How do we keep one cascade's logic authoritative while
still exposing both a sync and an async surface?

## Decision Drivers

- Cascade logic must not be able to drift between the sync and async services.
- Both surfaces are needed: a sync `Either` surface for in-memory service tests
  and the warehouse-day simulation, and an async `Future` surface for
  production.
- The cascade decision must be unit-testable without a database or actor
  system, and must not depend on read freshness (the production read model lags
  the write side within a request).

## Considered Options

- **Pure cascade decision module + thin shells** — extract the validation and
  the cascade into a pure module (`validate` then `decide`) over pre-loaded
  state; the sync and async services become load/decide/persist shells.
- **Keep duplicated cascades** — one full implementation per service.
- **Single async service only** — delete the sync variant.

## Decision Outcome

Chosen option: **"Pure cascade decision module + thin shells"**. The decision is
pure (no I/O), so it is exhaustively testable and the two shells cannot diverge
in logic — they only differ in how they load inputs and persist outputs. The
module normalises its loaded task set (substituting the just-completed task and
appending the shortpick replacement) so the sync shell (in-memory reads) and the
async shell (projection-backed reads) reach the same decision regardless of read
staleness.

### Consequences

- **Good**, because logic drift between the shells is impossible — there is one
  decision, tested directly as a pure function.
- **Good**, because the production stock-consumption gap and the projection-lag
  wave-completion bug are fixed and pinned by regression tests.
- **Good**, because both surfaces are preserved: the sync `Either` service still
  backs in-memory service suites and the warehouse-day simulation; the async
  service remains the production path.
- **Bad**, because each cascade gains a third type (the decision module) and an
  `Outcome` carrying ordered side-effect data (e.g. the consume-then-deallocate
  stock writes) that the public result type does not expose.
- **Neutral**, because the shells must load every input before calling `decide`
  and must not read their own writes — a contract the module documents.

### Confirmation

`TaskCompletionCascadeSuite` exercises the decision directly, including
stale-read and lag-read normalisation. `AsyncTaskCompletionServiceSuite` pins
the stock-consumption and wave/group divergence regressions. The sync service
suite and `WarehouseDaySimulationSuite` exercise the shell end to end against
in-memory repositories.

## More Information

This extends [ADR-0002](0002-use-policy-service-repository-pattern.md) rather
than superseding it: the policy-service-repository pattern keeps single business
rules pure and testable; this applies the same principle to the multi-step
cascade that wires those rules together. The cascade module is the unit; the
sync and async services are adapters at the seam (see `CONTEXT.md`,
"Completion cascade").
