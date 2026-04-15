# ADR 0007: Use shadcn/ui with Base UI Primitives

## Status

Accepted

## Context

The frontend needs a component library that provides accessible, customizable UI components without imposing a rigid design system.

**Options considered:**

1. **Material UI**: Full design system, opinionated styling.
2. **Radix UI + custom styling**: Unstyled primitives, full control.
3. **shadcn/ui with Base UI**: Copy-paste components built on Base UI primitives, styled with Tailwind + CVA.

## Decision

Use shadcn/ui configured with Base UI primitives (not Radix), styled with Tailwind CSS v4 and Class Variance Authority (CVA) for variant management.

## Consequences

**Benefits:**

- Components live in the codebase (`src/components/ui/`), fully ownable and customizable
- Base UI provides accessible, unstyled primitives (ARIA, keyboard navigation)
- CVA enables type-safe variant definitions (size, variant props)
- Tailwind CSS v4 with OKLch color tokens for perceptually uniform theming
- `cn()` utility (clsx + tailwind-merge) prevents class conflicts

**Tradeoffs:**

- Components must be maintained as project code, not auto-updated from a package
- Base UI has a smaller community than Radix UI
- Tailwind utility classes can become verbose in complex components
