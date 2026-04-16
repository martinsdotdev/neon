import { cn } from "@/shared/lib/utils"

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

const DOT_CLASSES: Record<StateVariant, string> = {
  neutral: "bg-muted-foreground/50",
  active: "bg-warning",
  success: "bg-success",
  destructive: "bg-destructive",
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
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full bg-muted/60 px-2 py-0.5 text-xs font-medium text-foreground",
        className
      )}
    >
      <span
        className={cn("size-1.5 shrink-0 rounded-full", DOT_CLASSES[variant])}
      />
      {state}
    </span>
  )
}

export { StateBadge }
