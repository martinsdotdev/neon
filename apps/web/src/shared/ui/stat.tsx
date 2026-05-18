import { cva } from "class-variance-authority"
import { ArrowDown, ArrowRight, ArrowUp } from "lucide-react"
import type { VariantProps } from "class-variance-authority"
import type { ComponentProps, ReactNode } from "react"

import { cn } from "@/shared/lib/utils"

// ---------------------------------------------------------------------------
// Compound statistics card, inspired by DiceUI's Stat
// (https://www.diceui.com/docs/components/base/stat). Adapted to MD3 tokens
// and the warehouse state-hue palette.
//
// Composition:
//   <Stat>
//     <StatLabel>…</StatLabel>
//     <StatIndicator variant="icon">…</StatIndicator>
//     <StatValue>…</StatValue>
//     <StatTrend trend="up">+2</StatTrend>
//     <StatSeparator />
//     <StatDescription>…</StatDescription>
//   </Stat>
// ---------------------------------------------------------------------------

const statVariants = cva(
  // Pulse glow only renders for tone=critical: a soft secondary ring layered
  // outside the base ring (via ::before) draws attention without a color shift
  // on the card itself. Subtler than animating the card; activates only when
  // the tile means something urgent.
  "group/stat relative flex h-full flex-col gap-2 rounded-shape-lg bg-card px-5 py-4 shadow-md ring-1 ring-foreground/5 before:pointer-events-none before:absolute before:-inset-px before:rounded-shape-lg before:opacity-0 before:transition-opacity data-[tone=critical]:before:animate-pulse data-[tone=critical]:before:opacity-100 data-[tone=critical]:before:ring-2 data-[tone=critical]:before:ring-priority-critical/30 dark:ring-foreground/10",
  {
    variants: {
      tone: {
        default: "",
        critical: "ring-priority-critical/25",
        success: "ring-state-completed/25",
        info: "ring-state-released/25",
      },
    },
    defaultVariants: { tone: "default" },
  }
)

type StatProps = ComponentProps<"div"> & VariantProps<typeof statVariants>

function Stat({ className, tone, ...props }: StatProps) {
  return (
    <div
      className={cn(statVariants({ tone }), className)}
      data-slot="stat"
      data-tone={tone ?? "default"}
      {...props}
    />
  )
}

function StatLabel({ className, ...props }: ComponentProps<"div">) {
  return (
    <div
      className={cn(
        "text-label-s font-mono tracking-wider text-on-surface-variant uppercase",
        className
      )}
      data-slot="stat-label"
      {...props}
    />
  )
}

function StatValue({ className, ...props }: ComponentProps<"div">) {
  return (
    <div
      className={cn(
        // Console-grade weight: text-headline-l (32px) at -0.02em tracking.
        // Larger than the default Stat 28px to anchor each tile visually and
        // let supporting text (description, sparkline) recede.
        "font-mono text-[32px] leading-[36px] font-semibold tracking-[-0.02em] tabular-nums group-data-[tone=critical]/stat:text-priority-critical",
        className
      )}
      data-slot="stat-value"
      {...props}
    />
  )
}

// StatIndicator — a richer version of DiceUI's indicator. `variant` shapes the
// decoration (icon tile, badge, bare action slot); `color` tones the frame
// using our state palette.
const statIndicatorVariants = cva(
  "text-label-s inline-flex shrink-0 items-center justify-center gap-1 font-mono",
  {
    variants: {
      variant: {
        default: "",
        icon: "size-8 rounded-shape-sm",
        badge: "rounded-full px-2 py-0.5",
        action:
          "cursor-pointer rounded-shape-sm px-2 py-1 transition-colors hover:bg-surface-container-low",
      },
      color: {
        default: "bg-surface-container text-on-surface-variant",
        success: "bg-state-completed-soft text-state-completed",
        info: "bg-state-released-soft text-state-released",
        warning: "bg-state-allocated-soft text-state-allocated",
        error: "bg-state-cancelled-soft text-state-cancelled",
      },
    },
    defaultVariants: { variant: "default", color: "default" },
    compoundVariants: [
      {
        variant: "default",
        color: "default",
        class: "bg-transparent text-on-surface-variant",
      },
    ],
  }
)

type StatIndicatorProps = ComponentProps<"span"> &
  VariantProps<typeof statIndicatorVariants>

function StatIndicator({
  className,
  color,
  variant,
  ...props
}: StatIndicatorProps) {
  return (
    <span
      className={cn(statIndicatorVariants({ color, variant }), className)}
      data-slot="stat-indicator"
      {...props}
    />
  )
}

// StatTrend — up / down / neutral arrow + value. Follows DiceUI's trend
// semantics but uses our state-completed / state-cancelled tokens so the
// direction meaning ("up = good") is tied to the warehouse palette.
const statTrendVariants = cva(
  "text-label-s inline-flex items-center gap-0.5 font-mono",
  {
    variants: {
      trend: {
        up: "text-state-completed",
        down: "text-state-cancelled",
        neutral: "text-on-surface-variant",
      },
    },
    defaultVariants: { trend: "neutral" },
  }
)

type StatTrendProps = ComponentProps<"span"> & {
  trend: "up" | "down" | "neutral"
  /** Auto-render a directional icon unless false or children already include one. */
  withIcon?: boolean
}

function StatTrend({
  children,
  className,
  trend,
  withIcon = true,
  ...props
}: StatTrendProps) {
  const Icon =
    trend === "up" ? ArrowUp : trend === "down" ? ArrowDown : ArrowRight
  return (
    <span
      className={cn(statTrendVariants({ trend }), className)}
      data-slot="stat-trend"
      data-trend={trend}
      {...props}
    >
      {withIcon ? <Icon className="size-3" aria-hidden="true" /> : null}
      {children}
    </span>
  )
}

function StatDescription({ className, ...props }: ComponentProps<"div">) {
  return (
    <div
      className={cn("text-label-m text-on-surface-variant", className)}
      data-slot="stat-description"
      {...props}
    />
  )
}

function StatSeparator({
  className,
  orientation = "horizontal",
  ...props
}: ComponentProps<"div"> & { orientation?: "horizontal" | "vertical" }) {
  return (
    <div
      aria-hidden="true"
      className={cn(
        "bg-outline-variant/60",
        orientation === "horizontal" ? "my-1 h-px w-full" : "mx-1 h-full w-px",
        className
      )}
      data-orientation={orientation}
      data-slot="stat-separator"
      {...props}
    />
  )
}

// StatHeader — convenience row that keeps label on the left and indicator /
// trend on the right without forcing every call site to wire a flex container.
function StatHeader({ children, className, ...props }: ComponentProps<"div">) {
  return (
    <div
      className={cn("flex items-start gap-2", className)}
      data-slot="stat-header"
      {...props}
    >
      {children}
    </div>
  )
}

// Tiny helper re-export so call sites don't need a separate import just to
// nest a custom visual (sparkline / progress bar) inside the card.
function StatVisual({ className, ...props }: ComponentProps<"div">) {
  return (
    <div className={cn("mt-1", className)} data-slot="stat-visual" {...props} />
  )
}

export {
  Stat,
  StatDescription,
  StatHeader,
  StatIndicator,
  StatLabel,
  StatSeparator,
  StatTrend,
  StatValue,
  StatVisual,
  type StatIndicatorProps,
  type StatProps,
  type StatTrendProps,
}

// Re-export types so value-only callers don't need a separate type import.
export type StatTone = NonNullable<VariantProps<typeof statVariants>["tone"]>

// ---------------------------------------------------------------------------
// KpiCard (compat shim) — small wrapper for the common shape used across the
// dashboard. Keeping this lets quick call sites stay short while complex tiles
// (e.g. SLA-at-risk with custom indicator content) use the compound API.
// ---------------------------------------------------------------------------

type KpiCardProps = {
  title: string
  value: ReactNode
  description?: ReactNode
  trend?: {
    direction: "up" | "down" | "neutral"
    value: string
  }
  indicator?: ReactNode
  visual?: ReactNode
  tone?: StatTone
  className?: string
}

function KpiCard({
  title,
  value,
  description,
  trend,
  indicator,
  visual,
  tone,
  className,
}: KpiCardProps) {
  return (
    <Stat className={className} tone={tone}>
      <StatHeader>
        <StatLabel>{title}</StatLabel>
        <span className="flex-1" />
        {trend ? (
          <StatTrend trend={trend.direction}>{trend.value}</StatTrend>
        ) : null}
        {indicator}
      </StatHeader>
      <StatValue>{value}</StatValue>
      {description ? <StatDescription>{description}</StatDescription> : null}
      {visual ? <StatVisual>{visual}</StatVisual> : null}
    </Stat>
  )
}

export { KpiCard, type KpiCardProps }
