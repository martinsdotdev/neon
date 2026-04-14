# The Frontend

Throughout this book, we have built the entire backend of Neon WES: domain
aggregates, event sourcing, policies, services, actors, cluster sharding, CQRS
projections, and an HTTP API. Now it is time to talk about how warehouse
operators will actually interact with the system.

@:callout(info)

The frontend has not been built yet. This chapter describes the
planned technology stack and conventions. It will be expanded with
implementation details, code examples, and walkthroughs once the `web/`
directory takes shape.

@:@

## The Planned Stack

The Neon WES frontend will use **React 19** with **TanStack Start**, a
full-stack React framework built on Vite and Nitro. TypeScript is the language
for all frontend code. TanStack Start gives us type-safe file-based routing with
an auto-generated route tree, fast HMR via Vite, and tight integration with
TanStack Router and TanStack Query for data fetching.

## UI Framework

For the component layer, we will use **shadcn/ui** configured with **Base UI**
primitives (not Radix). Components are copy-pasted into `src/components/ui/` and
become fully ownable project code. Styling uses **Tailwind CSS v4** with **Class
Variance Authority (CVA)** for type-safe variant definitions.

The `cn()` utility, built from `clsx` and `tailwind-merge`, prevents Tailwind
class conflicts when composing component styles:

```ts
import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
```

## Routing and Path Aliases

Routes live in `src/routes/` and follow TanStack Start's file-based routing
convention. The path alias `@/*` maps to `src/*`, keeping imports clean and
consistent across the codebase.

## Theming

Color tokens use the **OKLch** color space for perceptually uniform theming,
with full light and dark mode support. This ensures that colors look consistent
across different hues and brightness levels.

## Formatting Conventions

The frontend follows its own formatting rules: no semicolons, double quotes, and
an 80-character line width. Prettier handles formatting; ESLint handles linting.

## What Comes Next

Once the frontend is built, this chapter will walk through the full vertical
slice: route definition, data fetching with TanStack Query, component
composition with shadcn/ui, and how the frontend connects to the HTTP API we
built in [Chapter 13](../03-the-infrastructure/ch13-http-api.md). For now, the backend is ready and
waiting.
