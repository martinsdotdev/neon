# Where to Go from Here

You have reached the end of the book. Over the preceding twenty-five
chapters, we built a complete Warehouse Execution System from the ground up:
domain types, state machines, events, policies, services, repositories,
actors, cluster sharding, CQRS projections, an HTTP API, authentication,
authorization, error handling, observability, serialization, and a frontend
stack. That is a lot of ground to cover. Let's take a moment to look back at
what we built, revisit the patterns that hold it together, and point toward
where you might go from here.

## What We Built

The journey began with a question: what is a Warehouse Execution System?
In Chapter 1, we explored the domain of warehouse operations, the flow of
goods from inbound receipt to outbound shipping, and the role of software in
orchestrating that flow.

In Part II, we built the domain model. We started with the common
foundation (opaque type IDs, shared enums, utility types) and then
introduced the central innovation of the system: typestate encoding. By
modeling each aggregate state as a distinct case class, we made illegal
transitions compile-time errors rather than runtime bugs. We defined events
as immutable records of state changes, policies as pure functions that
decide what should happen next, services as orchestrators that compose
policies and repositories, and repositories as abstract ports that decouple
the domain from persistence.

In Part III, we brought the domain to life. We wrapped aggregates in
event-sourced Pekko actors, distributed them across a cluster with sharding,
projected their events into read-side tables for queries, exposed the whole
thing through an HTTP API, and tested every layer from pure domain logic to
full integration.

In Part IV, we addressed system concerns. We wired everything together in a
bootstrap sequence, configured serialization for both the persistence
journal and the HTTP layer, built a three-level error handling strategy, and
instrumented the system with structured logging and MDC propagation.

In Part V, we went further: stock management, inbound and cycle counting,
handling units and workstations, the frontend stack, and the Architecture
Decision Records that document why we made the choices we made.

The result is not a toy. It is a system with fourteen event-sourced
aggregates, over a dozen services with cascading state transitions, a
complete RBAC-protected HTTP API, CQRS projections for read-side queries,
and structured observability from the HTTP boundary through actors and
projections.

## The Five Patterns Revisited

Five architectural patterns weave through the entire system. Each one
appeared in context throughout the book. Let's name them explicitly and see
how they connect.

### The Decider Pattern

Jeremie Chassaing's "Functional Event Sourcing Decider" defines three
functions: `decide` (command to events), `evolve` (state + event to new
state), and `initialState`. Our typestate aggregates are Deciders. The
transition methods (e.g., `Task.Assigned.complete`) are the `decide`
function, producing a `(NewState, Event)` tuple. The actor's event handler
is the `evolve` function, reconstructing state from events during recovery.
The `EmptyState` is the `initialState`. The Decider pattern gives us a
formal model for event-sourced domain logic.

### Railway Oriented Programming

Scott Wlaschin's "Railway Oriented Programming" provides the error handling
model. `Either[Error, Result]` is the two-track railway. Each service
operation is a switch function that can route to the success track or the
failure track. `flatMap` chains switches together, and `Left` values bypass
all remaining processing. The sealed trait ADTs enumerate every possible
failure mode, and the compiler's exhaustive matching guarantee ensures
nothing slips through. We explored this in depth in Chapter 18.

### Functional Core, Imperative Shell

Gary Bernhardt's "Boundaries" talk describes an architecture where pure,
functional code sits at the center (the "core") and impure, side-effecting
code wraps around it (the "shell"). In Neon WES, policies are the
functional core: stateless, pure functions with no dependencies, returning
`Option[(State, Event)]`. Services are the imperative shell: they read from
repositories (impure), call policies (pure), and write results back
(impure). This sandwich structure makes the business logic trivially
testable. You do not need a database or an actor system to test a policy.
You just call the function.

### The Elm Architecture

The Elm programming language popularized a pattern for managing state:
`Model`, `Update`, `View`. Our event-sourced actors follow the same
structure. The `State` sealed trait is the Model. The command handler
(which processes commands and produces events) is the Update function.
The HTTP routes and CQRS projections are the View, transforming internal
state into external representations. The actor's recovery process replays
events through the event handler, reconstructing the Model from its history.
This is the Elm Architecture applied to server-side event sourcing.

### Hexagonal Architecture

Alistair Cockburn's Hexagonal Architecture (also called Ports and Adapters)
separates the application into a core that defines port interfaces and
adapters that implement them. In Neon WES, repository traits are ports:
`TaskRepository`, `WaveRepository`, and their async counterparts define
abstract persistence contracts. In-memory implementations are adapters for
testing. `PekkoTaskRepository`, `PekkoWaveRepository`, and their siblings
are adapters for production, backed by Cluster Sharding. The HTTP routes
are adapters for the external boundary. The domain model has no knowledge of
Pekko, PostgreSQL, or HTTP. It depends only on abstract traits defined in
its own module.

## Further Reading

Each pattern above has a canonical reference. Here are the primary sources,
along with additional resources that shaped the design of Neon WES.

### Event Sourcing and Domain Modeling

- **Jeremie Chassaing, "Functional Event Sourcing Decider"**
  https://thinkbeforecoding.com/post/2021/12/17/functional-event-sourcing-decider
  The formal model behind our aggregate design. Defines `decide`, `evolve`,
  and `initialState` as the three functions of an event-sourced entity.

- **Vaughn Vernon, _Implementing Domain-Driven Design_** (Addison-Wesley, 2013).
  The standard reference for applying DDD tactically with aggregates,
  entities, value objects, domain events, and bounded contexts.

- **Scott Wlaschin, _Domain Modeling Made Functional_** (Pragmatic Bookshelf, 2018).
  Types as documentation, making illegal states unrepresentable, and modeling
  domain workflows with types and functions. The direct inspiration for our
  typestate approach, translated from F# to Scala 3.

### Error Handling and Architecture

- **Scott Wlaschin, "Railway Oriented Programming"**
  https://fsharpforfunandprofit.com/rop/
  The conceptual model behind our `Either`-based error handling. The two-track
  railway metaphor makes error propagation intuitive.

- **Gary Bernhardt, "Boundaries"**
  https://www.destroyallsoftware.com/talks/boundaries
  The talk that crystallizes the Functional Core, Imperative Shell pattern.
  Pure values at the center, side effects at the edges.

- **Alistair Cockburn, "Hexagonal Architecture"**
  https://alistair.cockburn.us/hexagonal-architecture/
  The original description of Ports and Adapters. Our repository traits are
  ports; Pekko and in-memory implementations are adapters.

### Infrastructure

- **Apache Pekko Persistence Documentation**
  https://pekko.apache.org/docs/pekko/current/typed/persistence.html
  The official guide to event-sourced actors, cluster sharding, and
  projections. Essential reference for Chapters 10, 11, and 12.

- **The Elm Architecture Guide**
  https://guide.elm-lang.org/architecture/
  Model, Update, View. The same pattern our actors follow, applied in a
  frontend context.

### Security

- **The Copenhagen Book**
  https://thecopenhagenbook.com/
  The reference for our session-based authentication: server-side tokens,
  password hashing, cookie configuration, and CSRF protection. See
  ADR-0008.

### Craft and Inspiration

- **Robert Nystrom, _Crafting Interpreters_** (Genever Benning, 2021).
  https://craftinginterpreters.com/
  Not directly about warehouse systems, but a masterclass in building
  complex software incrementally, explaining every design decision along
  the way. The pedagogical approach of this book was inspired by Nystrom's
  chapter-by-chapter construction of a complete system.

## What You Could Build Next

The patterns in this book are not locked to warehouse software. Here are
some directions you could take them:

**Your own domain.** Pick a domain you know well and model it with
typestates, event sourcing, and the policy-service-repository pattern.
Healthcare workflows, financial transactions, logistics coordination,
game state machines: any domain with well-defined state transitions and
auditability requirements is a natural fit.

**Additional warehouse aggregates.** The warehouse domain is vast. Dock
door scheduling, labor management, replenishment planning, and wave
optimization are all potential aggregate modules. Chapter 20's checklist
gives you the exact steps.

**Performance exploration.** The system is designed for correctness first.
Load testing with realistic warehouse throughput (thousands of task
completions per hour, hundreds of concurrent waves) would reveal how the
sharding and projection layers behave under pressure.

**Event schema evolution.** As systems evolve, event schemas change.
Building a real event adapter (Chapter 17 described the mechanism) is a
valuable exercise in understanding backward compatibility in event-sourced
systems.

**A frontend.** Chapter 24 described the planned stack. Building an
operator interface on top of the HTTP API (Chapter 13) is a full project
in itself, combining real-time data with complex workflow UIs.

## Closing

We started this book with a warehouse floor: pallets arriving on trucks,
workers scanning barcodes, forklifts moving goods through aisles. We end
it with a running system that models those operations as typed state
machines, records every transition as an immutable event, orchestrates
cascading logic through pure policies, distributes work across a cluster
of actors, and exposes it all through a secure, observable HTTP API.

The patterns we explored are not specific to warehouse systems. Typestate
encoding, event sourcing, the Decider pattern, Railway Oriented Programming,
Functional Core/Imperative Shell, and Hexagonal Architecture apply wherever
you need to model complex state transitions with correctness guarantees. A
healthcare system tracking patient workflows, a financial system processing
transactions, a logistics platform coordinating deliveries -- all of these
can benefit from the same approach.

The most important lesson is not any single pattern. It is the discipline of
making design decisions explicit, encoding business rules in types rather
than comments, and building systems where the compiler is your first line of
defense against bugs. When an illegal state transition is a compile error
rather than a runtime exception, when every failure mode is visible in a
type signature, when every architectural choice is documented in an ADR, you
can change the system with confidence. And in a warehouse that runs around
the clock, confidence is everything.

Thank you for reading. Now go build something.
