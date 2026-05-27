---
status: "accepted"
date: 2026-04-10
decision-makers: project owner
consulted:
informed: future contributors
---

# Use shadcn/ui with Base UI primitives

## Context and Problem Statement

The frontend needs accessible, customizable UI components without inheriting a rigid, hard-to-restyle design system. How do we get production-quality accessibility and behaviour while keeping full control over markup and styling?

## Decision Drivers

- Accessibility out of the box (ARIA, keyboard navigation, focus management).
- Full ownership of component markup and styling — no fighting an opinionated theme.
- Type-safe, ergonomic variant management.
- A theming approach that fits the project's OKLch token system and light/dark modes.

## Considered Options

- **shadcn/ui with Base UI primitives** — copy-paste components built on Base UI, styled with Tailwind and CVA.
- **Radix UI + custom styling** — unstyled Radix primitives, styled entirely by hand.
- **Material UI** — a full, opinionated design system.

## Decision Outcome

Chosen option: **"shadcn/ui with Base UI primitives"**, because the components live in our own source tree — fully ownable and customizable — while Base UI supplies the accessibility and behaviour we would otherwise have to build. Components are styled with Tailwind CSS v4 and Class Variance Authority (CVA) for variants.

### Consequences

- **Good**, because components live in the codebase (`src/components/ui/`), fully ownable and customizable.
- **Good**, because Base UI provides accessible, unstyled primitives (ARIA, keyboard navigation).
- **Good**, because CVA enables type-safe variant definitions (size, variant props).
- **Good**, because Tailwind CSS v4 with OKLch color tokens gives perceptually uniform theming.
- **Good**, because the `cn()` utility (clsx + tailwind-merge) prevents class conflicts.
- **Bad**, because components are project code to maintain, not a package that auto-updates.
- **Bad**, because Base UI has a smaller community than Radix UI.
- **Neutral**, because Tailwind utility classes can grow verbose in complex components — the cost of styling in markup.

### Confirmation

Verified in the codebase: components reside in `src/components/ui/`, compose Base UI primitives, and are themed through the OKLch token set with working light/dark modes.

## Pros and Cons of the Options

### shadcn/ui with Base UI primitives

- **Good**, because it combines full ownership of the components with accessible primitives we do not have to write.
- **Good**, because CVA + Tailwind give type-safe variants and token-driven theming.
- **Bad**, because owned components must be maintained by hand rather than upgraded via a dependency.

### Radix UI + custom styling

- **Good**, because unstyled primitives give complete styling control.
- **Bad**, because every component must be styled and assembled from scratch, duplicating much of what shadcn/ui already provides.

### Material UI

- **Good**, because it is a complete, batteries-included design system.
- **Bad**, because its opinionated styling is hard to bend to a custom design and OKLch token system.
- **Bad**, because deep customization fights the framework rather than working with it.

## More Information

- The frontend architecture and component conventions: [Chapter 24 — Frontend](../book/05-advanced-topics/ch24-frontend.md).
- The chosen framework is the subject of [ADR-0006](0006-use-tanstack-start-react-frontend.md).
