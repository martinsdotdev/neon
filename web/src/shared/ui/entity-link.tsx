import type { ReactNode } from "react"
import { Link } from "@tanstack/react-router"

import { cn } from "@/shared/lib/utils"

// ---------------------------------------------------------------------------
// EntityLink — single source of truth for "this cell shows an ID, route the
// click to that entity's detail page".
//
// Each branch hands TanStack Router a literal `to=` so the typed Link can
// validate the route + params shape at compile time. Adding a new entity
// kind requires updating both the union and the switch — TypeScript will
// flag any branch we forget.
//
// Callers pass the entity kind + ID; the displayed text defaults to the ID
// itself but can be overridden via `children` when the cell shows a
// different label (carrier name, SKU code, etc.).
// ---------------------------------------------------------------------------

type EntityKind =
  | "wave"
  | "task"
  | "order"
  | "carrier"
  | "user"
  | "sku"
  | "location"
  | "workstation"
  | "delivery"
  | "receipt"
  | "handling-unit"
  | "stock-position"
  | "inventory"
  | "consolidation-group"
  | "transport-order"
  | "cycle-count"

interface EntityLinkProps {
  kind: EntityKind
  id: string | null | undefined
  /** Override the displayed text (defaults to `id`). */
  children?: ReactNode
  /** Render this when `id` is null/undefined. Defaults to the em-dash glyph. */
  fallback?: ReactNode
  className?: string
  /** Useful when the link sits inside a clickable row to stop bubbling. */
  onClick?: (event: React.MouseEvent) => void
}

const LINK_CLASS = cn(
  "rounded-shape-xs font-mono underline-offset-2 hover:text-primary hover:underline",
  "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
)

function EntityLink({
  kind,
  id,
  children,
  fallback,
  className,
  onClick,
}: EntityLinkProps) {
  if (!id) {
    return (
      <span className="font-mono text-on-surface-variant">
        {fallback ?? "—"}
      </span>
    )
  }

  const label = children ?? id
  const linkClassName = cn(LINK_CLASS, className)

  // Each branch produces a typed Link that the router can validate against
  // its registered routes. The duplication here is the cost of that safety.
  switch (kind) {
    case "wave":
      return (
        <Link
          className={linkClassName}
          onClick={onClick}
          params={{ waveId: id }}
          to="/waves/$waveId"
        >
          {label}
        </Link>
      )
    case "task":
      return (
        <Link
          className={linkClassName}
          onClick={onClick}
          params={{ taskId: id }}
          to="/tasks/$taskId"
        >
          {label}
        </Link>
      )
    case "order":
      return (
        <Link
          className={linkClassName}
          onClick={onClick}
          params={{ orderId: id }}
          to="/orders/$orderId"
        >
          {label}
        </Link>
      )
    case "carrier":
      return (
        <Link
          className={linkClassName}
          onClick={onClick}
          params={{ carrierId: id }}
          to="/carriers/$carrierId"
        >
          {label}
        </Link>
      )
    case "user":
      return (
        <Link
          className={linkClassName}
          onClick={onClick}
          params={{ userId: id }}
          to="/users/$userId"
        >
          {label}
        </Link>
      )
    case "sku":
      return (
        <Link
          className={linkClassName}
          onClick={onClick}
          params={{ skuId: id }}
          to="/skus/$skuId"
        >
          {label}
        </Link>
      )
    case "location":
      return (
        <Link
          className={linkClassName}
          onClick={onClick}
          params={{ locationId: id }}
          to="/locations/$locationId"
        >
          {label}
        </Link>
      )
    case "workstation":
      return (
        <Link
          className={linkClassName}
          onClick={onClick}
          params={{ workstationId: id }}
          to="/workstations/$workstationId"
        >
          {label}
        </Link>
      )
    case "delivery":
      return (
        <Link
          className={linkClassName}
          onClick={onClick}
          params={{ deliveryId: id }}
          to="/deliveries/$deliveryId"
        >
          {label}
        </Link>
      )
    case "receipt":
      return (
        <Link
          className={linkClassName}
          onClick={onClick}
          params={{ receiptId: id }}
          to="/receipts/$receiptId"
        >
          {label}
        </Link>
      )
    case "handling-unit":
      return (
        <Link
          className={linkClassName}
          onClick={onClick}
          params={{ unitId: id }}
          to="/handling-units/$unitId"
        >
          {label}
        </Link>
      )
    case "stock-position":
      return (
        <Link
          className={linkClassName}
          onClick={onClick}
          params={{ positionId: id }}
          to="/stock-positions/$positionId"
        >
          {label}
        </Link>
      )
    case "inventory":
      return (
        <Link
          className={linkClassName}
          onClick={onClick}
          params={{ inventoryId: id }}
          to="/inventory/$inventoryId"
        >
          {label}
        </Link>
      )
    case "consolidation-group":
      return (
        <Link
          className={linkClassName}
          onClick={onClick}
          params={{ groupId: id }}
          to="/consolidation-groups/$groupId"
        >
          {label}
        </Link>
      )
    case "transport-order":
      return (
        <Link
          className={linkClassName}
          onClick={onClick}
          params={{ orderId: id }}
          to="/transport-orders/$orderId"
        >
          {label}
        </Link>
      )
    case "cycle-count":
      return (
        <Link
          className={linkClassName}
          onClick={onClick}
          params={{ countId: id }}
          to="/cycle-counts/$countId"
        >
          {label}
        </Link>
      )
    default: {
      // Exhaustiveness check: if a new kind is added to the union without a
      // matching case, this assignment errors at compile time.
      const _exhaustive: never = kind
      return _exhaustive
    }
  }
}

export { EntityLink, type EntityKind, type EntityLinkProps }
