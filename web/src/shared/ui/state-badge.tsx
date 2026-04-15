import { Badge } from '@/shared/ui/badge';
import type { BadgeProps } from '@/shared/ui/badge';

type StateVariant = "neutral" | "active" | "success" | "destructive"

const STATE_VARIANTS: Record<string, StateVariant> = {
  // Neutral -- initial/pending states
  Planned: "neutral",
  Pending: "neutral",
  Created: "neutral",
  Available: "neutral",
  PickCreated: "neutral",
  ShipCreated: "neutral",

  // Active -- in-progress states
  Released: "active",
  Allocated: "active",
  Assigned: "active",
  Active: "active",
  Reserved: "active",
  Picked: "active",
  ReadyForWorkstation: "active",
  InBuffer: "active",
  Packed: "active",
  ReadyToShip: "active",
  Idle: "active",

  // Success -- terminal positive states
  Completed: "success",
  Confirmed: "success",
  Shipped: "success",

  // Destructive -- terminal negative states
  Cancelled: "destructive",
  Disabled: "destructive",
  Empty: "destructive",
}

const VARIANT_TO_BADGE: Record<StateVariant, BadgeProps["variant"]> = {
  active: "default",
  destructive: "destructive",
  neutral: "secondary",
  success: "default",
}

const VARIANT_TO_CLASSES: Record<StateVariant, string> = {
  active: "bg-warning/15 text-warning-foreground border-warning/25",
  destructive: "",
  neutral: "",
  success: "bg-success/15 text-success-foreground border-success/25",
}

const StateBadge = ({
  state,
  className,
}: {
  state: string
  className?: string
}) => {
  const variant = STATE_VARIANTS[state] ?? "neutral"

  return (
    <Badge
      variant={VARIANT_TO_BADGE[variant]}
      className={`${VARIANT_TO_CLASSES[variant]} font-mono text-[0.6875rem] uppercase tracking-wider ${className ?? ""}`}
    >
      {state}
    </Badge>
  )
}

export { StateBadge }
