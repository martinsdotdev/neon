# Appendix F: Glossary

## Warehouse Terms

**Allocation.** The process of reserving inventory and assigning source and destination locations to a task, moving stock from the "available" bucket to the "allocated" bucket.

**Batch picking.** A picking strategy where a single operator fulfills multiple orders in one trip through the warehouse, depositing items into separate compartments of a pick handling unit.

**Buffer zone.** A staging area near workstations where pick handling units wait after transport until all units for a consolidation group have arrived.

**Carrier.** A shipping partner (e.g., freight company, parcel service) assigned to a dock location for outbound dispatch of shipped handling units.

**Consolidation.** The process of grouping multiple orders from a wave so they can be batch-picked together and later separated at a workstation.

**Cycle count.** A periodic inventory verification process that counts a subset of SKUs in a warehouse area without shutting down operations, producing count tasks for individual SKU-location checks.

**Dock.** A physical loading or unloading bay at the warehouse perimeter, used for inbound receiving and outbound shipping.

**FEFO.** First Expired, First Out. An inventory rotation strategy that prioritizes items with the earliest expiration date, common for perishable goods and pharmaceuticals.

**FIFO.** First In, First Out. An inventory rotation strategy that prioritizes the oldest stock, ensuring items are consumed in the order they were received.

**Goods receipt.** A physical receiving session against an inbound delivery, recording individual received lines (SKU, quantity, lot, packaging level) before confirmation.

**Handling unit.** A physical container (tote, carton, or pallet) that moves through the warehouse. Neon WES tracks two streams: pick handling units (storage to buffer) and ship handling units (workstation to dock).

**Inbound delivery.** An expected receipt of goods into the warehouse, tracking expected, received, and rejected quantities through the receiving process.

**Lot.** A production or supplier batch identifier attached to inventory for traceability. Used with lot attributes (expiration date, production date) for FEFO/FIFO rotation.

**Order line.** A single SKU and quantity within a customer order; the smallest unit of demand that generates picking tasks.

**Packing.** The workstation operation of placing picked items into a ship handling unit, verifying contents, and sealing the package for outbound shipping.

**Pallet.** A large handling unit, typically wooden or plastic, used for bulk storage and transport. Represents the highest packaging level in the UOM hierarchy.

**Picking.** The warehouse operation of retrieving items from storage locations to fulfill order demand, executed by operators following assigned tasks.

**Put wall.** A workstation type with numbered slots where operators sort items from pick handling units into order-specific ship handling units during deconsolidation.

**Putaway.** The warehouse operation of moving received goods from the dock area to their designated storage locations.

**Receiving.** The inbound process of unloading goods at a dock, verifying quantities against inbound delivery expectations, and recording goods receipts.

**Shortpick.** A condition where the actual quantity picked is less than the requested quantity, triggering downstream policies to create replacement tasks for the remainder.

**SKU.** Stock Keeping Unit. A unique identifier for a distinct product or item in the warehouse, the fundamental unit of inventory tracking.

**Slot.** A put-wall position within a workstation, bound to one order at a time. Operators place items into the slot's ship handling unit during deconsolidation.

**Staging area.** A designated warehouse zone where goods are temporarily stored between process steps (e.g., after picking and before shipping).

**Task.** A single atomic warehouse operation (pick, putaway, replenish, or transfer) for one SKU, assigned to an operator with source and destination locations.

**Tote.** A small-to-medium handling unit (typically a plastic bin) used for picking and transporting individual items or small batches.

**Transport order.** A directive to move a handling unit from its current location to a destination, representing the gap between task completion and operator confirmation at the target.

**UOM hierarchy.** Unit of Measure hierarchy. The packaging levels for a SKU (e.g., Each, Inner Pack, Case, Pallet), defining how items are nested in larger containers.

**Wave.** A batch of orders grouped by a strategy (Single or Multi) for coordinated processing through picking, consolidation, and shipping.

**Wave release.** The act of transitioning a planned wave to the Released state, which triggers task creation, inventory allocation, and consolidation group formation.

**Workstation.** A physical station (put wall or pack station) where consolidation and packing operations occur. Workstations cycle between Disabled, Idle, and Active states.

**Zone.** A logical subdivision of the warehouse used for organizing storage locations, routing tasks, and balancing workload across areas.

---

## Technical Terms

**Actor.** A lightweight concurrent entity in the Pekko actor model that processes messages one at a time, maintaining encapsulated state without shared memory.

**Aggregate.** A cluster of domain objects treated as a single unit for data changes and consistency, with one root entity that controls access.

**CBOR.** Concise Binary Object Representation. A binary serialization format used by Neon WES (via Jackson CBOR) for compact, fast serialization of events, commands, and snapshots.

**Cluster sharding.** A Pekko mechanism that distributes actors across cluster nodes, routing messages to the correct node by entity ID. Each aggregate instance is a sharded entity.

**Command.** A message sent to an actor requesting a state change. Commands are validated against the current state; if valid, they produce events.

**CQRS.** Command Query Responsibility Segregation. A pattern that separates the write model (event-sourced actors) from the read model (projection tables), optimizing each for its workload.

**Domain event.** An immutable record of something that happened in the domain. Events are the source of truth in an event-sourced system, persisted to the journal.

**Either.** A Scala type representing a value that is one of two possibilities: `Left` (typically an error) or `Right` (typically a success). Used throughout Neon WES for error handling without exceptions.

**Event handler.** A pure function that applies an event to the current state, producing the next state. Used during recovery to reconstruct actor state from the event journal.

**Event sourcing.** A persistence pattern where state changes are captured as an append-only sequence of events rather than overwriting current state. The current state is derived by replaying events.

**EventSourcedBehavior.** The Pekko Typed API for defining an event-sourced actor with command handlers, event handlers, and retention policies. Neon WES uses `withEnforcedReplies` for type-safe responses.

**Functional core.** The pure domain logic layer (aggregates, policies) that contains no side effects, I/O, or framework dependencies. Easily testable in isolation.

**Hexagonal architecture.** An architectural pattern (also called ports and adapters) where domain logic is at the center, with external concerns (databases, HTTP) connected through abstract ports and concrete adapters.

**Imperative shell.** The outer layer that orchestrates side effects (database calls, actor messages, HTTP responses) around the functional core. Services and actors form this shell.

**MDC.** Mapped Diagnostic Context. A logging mechanism that attaches contextual key-value pairs (entity type, entity ID) to log entries for structured, filterable logging.

**Opaque type.** A Scala 3 feature that creates a new type backed by an existing type without runtime overhead. Used in Neon WES for type-safe ID wrappers (e.g., `WaveId` wrapping `UUID`).

**Pekko.** Apache Pekko, the open-source fork of Akka. Provides the actor model, cluster sharding, persistence, projections, and HTTP server used by Neon WES.

**Policy.** A stateless decision object in the core module that encodes business rules. Policies return `Option[(State, Event)]`, making them pure functions that are trivially testable.

**Port.** An abstract trait defining an interface between the domain and external systems (e.g., `WaveRepository`). Ports are implemented by in-memory mocks in tests and Pekko repositories in production.

**Projection.** A CQRS read-side process that consumes tagged events from the journal and writes denormalized views to query tables. Projections run via `ShardedDaemonProcess` with `exactlyOnce` delivery.

**R2DBC.** Reactive Relational Database Connectivity. A non-blocking database driver API used by Pekko Persistence for journal, snapshot, and projection storage against PostgreSQL.

**Repository.** An abstraction over data access that provides `findById`, `save`, and query operations. Neon WES uses sync repositories (in-memory for tests) and async repositories (Pekko-backed for production).

**Retention criteria.** Configuration that controls snapshot frequency and cleanup. Neon WES takes a snapshot every 100 events and keeps the 2 most recent snapshots.

**Sealed trait.** A Scala trait that restricts its implementations to the same file, enabling exhaustive pattern matching. Used in Neon WES for aggregate state hierarchies and error ADTs.

**Service.** An orchestrator in the core module that injects repositories and policies, coordinates cross-aggregate operations, and returns `Either[Error, Result]` or `Future`.

**Snapshot.** A serialized copy of an actor's state at a specific sequence number, used to speed up recovery by avoiding full event replay from the beginning of the journal.

**Typestate.** A pattern that encodes valid state transitions in the type system. Each state is a separate case class with transition methods that only exist on valid source states, making illegal transitions a compile-time error.

**UUID v7.** A UUID variant that embeds a Unix timestamp in the most significant bits, providing both uniqueness and natural chronological ordering. Generated via the uuid-creator library.

**Wide event.** An event that carries enough context for downstream consumers (projections, services) to act without loading additional aggregates. Neon WES events include related IDs (wave, order, handling unit) to support projection independence.
