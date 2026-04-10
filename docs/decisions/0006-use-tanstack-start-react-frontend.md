# ADR 0006: Use TanStack Start with React 19 for Frontend

## Status

Accepted

## Context

The WES needs a web frontend for warehouse operators. The choice of framework affects developer experience, performance, and long-term maintainability.

**Options considered:**
1. **Next.js**: Most popular React meta-framework. Large ecosystem, mature.
2. **TanStack Start**: Full-stack React framework built on Vite and Nitro. Type-safe routing, newer but aligned with modern React patterns.
3. **SvelteKit**: Different paradigm (Svelte), excellent performance.

## Decision

Use TanStack Start with React 19, TypeScript, and Vite.

## Consequences

**Benefits:**
- Type-safe file-based routing with auto-generated route tree
- Built on Vite for fast dev server and HMR
- Tight integration with TanStack Router and TanStack Query
- React 19 features (Server Components, use() hook, ref as prop)
- Nitro server for SSR and API routes

**Tradeoffs:**
- Smaller ecosystem and community compared to Next.js
- Less battle-tested in production environments
- Fewer deployment platform integrations out of the box
