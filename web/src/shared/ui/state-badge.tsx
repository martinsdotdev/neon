import type { BadgeProps } from "@/shared/ui/badge"
import { Badge } from "@/shared/ui/badge"

type StateVariant = "neutral" | "active" | "success" | "destructive"

const STATE_VARIANTS: Record<string, StateVariant> = {
  Active: "active",
  Allocated: "active",
  Assigned: "active",
  Available: "neutral",
  Blocked: "destructive",
  Cancelled: "destructive",
  Closed: "success",
  Completed: "success",
  Confirmed: "success",
  Created: "neutral",
  Damaged: "destructive",
  Disabled: "destructive",
  Empty: "destructive",
  Expected: "neutral",
  Expired: "destructive",
  Idle: "active",
  InBuffer: "active",
  InProgress: "active",
  Open: "neutral",
  Packed: "active",
  PartiallyReceived: "active",
  Pending: "neutral",
  PickCreated: "neutral",
  Picked: "active",
  Planned: "neutral",
  QualityHold: "destructive",
  ReadyForWorkstation: "active",
  ReadyToShip: "active",
  Received: "success",
  Released: "active",
  Reserved: "active",
  ShipCreated: "neutral",
  Shipped: "success",
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
      className={`${VARIANT_TO_CLASSES[variant]} font-mono text-[0.6875rem] tracking-wider uppercase ${className ?? ""}`}
    >
      {state}
    </Badge>
  )
}

export { StateBadge }
