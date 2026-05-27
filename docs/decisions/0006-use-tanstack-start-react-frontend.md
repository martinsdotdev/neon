---
status: "accepted"
date: 2026-04-10
decision-makers: project owner
consulted:
informed: future contributors
---

# Use TanStack Start with React 19 for the frontend

## Context and Problem Statement

The WES needs a web frontend for warehouse operators. The framework choice shapes developer experience, type safety, performance, and long-term maintainability. Which full-stack framework do we build on?

## Decision Drivers

- Type-safe routing and data flow end to end.
- Fast local development (dev server start, hot module replacement).
- Alignment with modern React (React 19 features, the wider TanStack ecosystem).
- Maintainability for a small team over the long term.

## Considered Options

- **TanStack Start** — full-stack React framework on Vite and Nitro, with type-safe file-based routing.
- **Next.js** — the most popular React meta-framework; large, mature ecosystem.
- **SvelteKit** — a different paradigm (Svelte) with excellent performance.

## Decision Outcome

Chosen option: **"TanStack Start with React 19"**, because it gives type-safe file-based routing and tight integration with TanStack Router and Query, on a fast Vite/Nitro foundation, while staying within React so the team's existing React knowledge carries over. The stack is TanStack Start + React 19 + TypeScript + Vite.

### Consequences

- **Good**, because file-based routing is type-safe, with an auto-generated route tree.
- **Good**, because it builds on Vite for a fast dev server and HMR.
- **Good**, because it integrates tightly with TanStack Router and TanStack Query.
- **Good**, because it adopts React 19 features (Server Components, the `use()` hook, ref as a prop).
- **Good**, because the Nitro server handles SSR and API routes.
- **Bad**, because the ecosystem and community are smaller than Next.js's.
- **Bad**, because it is less battle-tested in production and has fewer turnkey deployment integrations.

### Confirmation

Verified in practice: the app builds and runs via `bun dev:web`, the route tree generates and type-checks, and TanStack Query drives data fetching against the backend API.

## Pros and Cons of the Options

### TanStack Start

- **Good**, because routing and data are type-safe end to end, on a fast Vite/Nitro base.
- **Good**, because it stays in the React ecosystem, reusing existing skills and libraries.
- **Bad**, because it is newer and less proven, with a smaller community and fewer deployment presets.

### Next.js

- **Good**, because it is mature, widely adopted, and has the largest ecosystem and deployment story.
- **Bad**, because its routing/server model is more opinionated and heavier than this project needs.

### SvelteKit

- **Good**, because Svelte offers excellent runtime performance and a compact authoring model.
- **Bad**, because it is a non-React paradigm, discarding the team's React knowledge and the React component ecosystem.

## More Information

- The frontend architecture in depth: [Chapter 24 — Frontend](../book/05-advanced-topics/ch24-frontend.md).
- Architecture overview: [`docs/architecture.md`](../architecture.md), "Frontend Architecture".
- The component system is covered separately in [ADR-0007](0007-use-shadcn-base-ui-component-system.md).
