# The Frontend

Throughout this book, we have built the entire backend of Neon WES: domain
aggregates, event sourcing, policies, services, actors, cluster sharding, CQRS
projections, and an HTTP API. This chapter crosses to the other side of the
wire and tours the clients that warehouse operators actually use, along with the
shared TypeScript packages that keep those clients honest with the Scala domain.

@:callout(info)

The frontend lives outside the Scala build, in a _pnpm workspace_ rooted at the
repository's `apps/` and `packages/` directories. The Scala modules and the
TypeScript workspace share one repository but build independently.

@:@

## The Workspace

Neon WES has two client applications and three shared packages, wired together
as pnpm workspaces:

```
apps/
  web/        # TanStack Start operator console (desktop browser)
  mobile/     # Expo / React Native scanner app (handheld devices)
packages/
  domain/     # @neon/domain — the TypeScript mirror of the Scala domain
  client/     # @neon/client — the typed API client
  tokens/     # @neon/tokens — OKLch design tokens
```

Each shared package publishes its modules through a per-file export map
(`"./*": "./src/*.ts"`), so an app imports exactly what it needs
(`@neon/domain/task`, `@neon/client/query`) with no barrel files.

## The Web Console (`apps/web`)

The web console is a **TanStack Start** application (v1.166) on **React 19**,
bundled by **Vite 7** and written in **TypeScript 5.9**. TanStack Start gives us
type-safe file-based routing with an auto-generated route tree, plus tight
integration with TanStack Router (v1.167) and **TanStack Query** (v5.99) for
data fetching.

Routes live under `apps/web/src/routes/`; authenticated pages sit beneath the
`_authenticated/` pathless layout route, which enforces the session before any
child route renders. The rest of the code follows a Feature-Sliced-Design-style
layering under `apps/web/src/shared/` (`api/`, `ui/`, `lib/`, `hooks/`,
`data-grid/`, `reui/`). The path alias `@/*` maps to `apps/web/src/*`.

The component layer is **shadcn/ui** built on **Base UI** primitives
(`@base-ui/react`), styled with **Tailwind CSS v4** and **Class Variance
Authority** for type-safe variants. The familiar `cn()` helper merges Tailwind
classes without conflicts:

```ts
// apps/web/src/shared/lib/utils.ts

import { clsx } from "clsx"
import { twMerge } from "tailwind-merge"
import type { ClassValue } from "clsx"

export function cn(...inputs: Array<ClassValue>) {
  return twMerge(clsx(inputs))
}
```

Formatting and linting for the web app run through **Ultracite** (Oxlint +
Oxfmt): no semicolons, double quotes, trailing commas. The standards live in
`apps/web/.claude/CLAUDE.md`.

## The Shared Packages

**`@neon/domain`** is the single source of truth for the TypeScript view of the
Scala domain: entity types (Task, Wave, ConsolidationGroup, Location, Sku, …),
their **Zod** schemas, label maps, and legal-transition tables. Both apps import
these rather than re-declaring entity shapes. The permission list is a `const`
tuple from which the `Permission` type is derived, so the type cannot drift from
the runtime list:

```ts
// packages/domain/src/auth.ts

export const PERMISSION_KEYS = [
  "wave:plan",
  "wave:cancel",
  "task:complete",
  // … twenty keys total
] as const

export type Permission = (typeof PERMISSION_KEYS)[number]
```

@:callout(info)

The Scala backend guards this mirror: `PermissionContractSuite` (in the `common`
module) reads `PERMISSION_KEYS` out of `auth.ts` and fails the build if the
TypeScript and Scala permission lists ever drift apart. Removing a stray key
here is a backend test failure, not a silent gap.

@:@

**`@neon/client`** wraps `fetch` in a `createApiClient({ baseUrl, getAuthToken })`
factory returning a **neverthrow** `ResultAsync<T, ApiError>`. Errors are
values, exactly as in the Scala services (Chapter 18). It parses RFC 9457
`application/problem+json` bodies into a structured `ApiError`, so the
problem-details responses we built in Chapter 13 arrive on the client as typed
data. Queries consume that result through one sanctioned seam, `unwrapForQuery`:

```ts
// packages/client/src/query.ts

export const unwrapForQuery = async <T>(
  result: ResultAsync<T, ApiError>,
  options: UnwrapOptions<T> = {}
): Promise<T> => {
  const settled = await result
  if (settled.isOk()) {
    return settled.value
  }
  if (options.fallback !== undefined) {
    return options.fallback
  }
  throw new ApiRequestError(settled.error)
}
```

It returns the value on success, returns an optional fallback on failure, and
otherwise throws `ApiRequestError` so TanStack Query enters its error state.

**`@neon/tokens`** holds the design tokens as a JavaScript object in the
**OKLch** color space, with light and dark modes. The web app generates CSS
variables from it; the mobile app consumes the object directly.

## How the Apps Consume the API

A query file pairs a client call with `unwrapForQuery`. The web app passes a
development mock as the fallback so production surfaces real errors while local
work stays offline:

```ts
// apps/web/src/shared/api/tasks.ts (shape)

queryFn: async () =>
  unwrapForQuery(apiClient.get<Array<Task>>("/api/tasks"), {
    fallback: import.meta.env.DEV ? MOCK_TASKS : undefined,
  })
```

Because the fallback counts only when it is not `undefined`, the same line
serves mocks in development (where `import.meta.env.DEV` is true) and throws in
production. The mobile app omits the fallback entirely, so every failure throws.

## The Mobile Scanner (`apps/mobile`)

The handheld app is built with **Expo** (SDK 54) and **Expo Router** (v6) on
React Native 0.81. Operators authenticate with a Bearer token held in
`expo-secure-store`, which `@neon/client` injects via its `getAuthToken` getter
(the web app, by contrast, relies on the session cookie). Its source is
organized into `src/{api, auth, notifications, scanner}`.

Barcode scanning currently uses `expo-camera`, wrapped in a `useScanner` hook
with a `ScannerOverlay` component (`apps/mobile/src/scanner/`).

@:callout(info)

A DataWedge path for rugged Android scanners is planned. Today there is a single
camera-based adapter, so there is no scanner abstraction seam yet; it will be
introduced when the second adapter lands and gives the seam two implementations
to justify it.

@:@

## Testing and Tooling

The TypeScript workspace tests with **Vitest**. The deepest shared modules are
covered: `@neon/client` exercises the API client's problem+json parsing, 204
handling, auth injection, and the `unwrapForQuery` seam; `@neon/domain` pins the
permission invariants and the task transition table. Run the whole workspace's
tests with `pnpm -r test`, and the web app's with `pnpm --filter web test`.

With both clients and the shared packages in place, every layer of Neon WES,
from the typestate-encoded aggregate at the center to the operator's handheld at
the edge, is now accounted for. The next chapter steps back to look at the
architecture decision records that shaped these choices.
