import type { Task } from "@/shared/api/tasks"
import { cn } from "@/shared/lib/utils"

const TASK_TYPE_INITIALS: Record<Task["taskType"], string> = {
  Pick: "PK",
  Putaway: "PA",
  Replenish: "RP",
  Transfer: "TR",
}

// Colored mono-initials chip: Pick=emerald (state-completed), Putaway=blue
// (state-released), Replenish=amber (state-allocated), Transfer=violet
// (state-ready). Uses the warehouse state palette so the chip's hue agrees
// with how the same workflow surfaces colors elsewhere.
const TASK_TYPE_TONE: Record<Task["taskType"], { bg: string; fg: string }> = {
  Pick: {
    bg: "bg-state-completed-soft",
    fg: "text-state-completed",
  },
  Putaway: {
    bg: "bg-state-released-soft",
    fg: "text-state-released",
  },
  Replenish: {
    bg: "bg-state-allocated-soft",
    fg: "text-state-allocated",
  },
  Transfer: {
    bg: "bg-state-ready-soft",
    fg: "text-state-ready",
  },
}

const SIZE_CLASS: Record<"sm" | "md" | "lg", string> = {
  sm: "size-5 text-[9px]",
  md: "size-6 text-[10px]",
  lg: "size-7 text-[11px]",
}

type TaskTypeChipProps = {
  type: Task["taskType"]
  size?: "sm" | "md" | "lg"
  className?: string
}

function TaskTypeChip({ type, size = "md", className }: TaskTypeChipProps) {
  const tone = TASK_TYPE_TONE[type]
  return (
    <span
      aria-label={type}
      className={cn(
        "inline-flex shrink-0 items-center justify-center rounded-shape-xs font-mono font-semibold",
        SIZE_CLASS[size],
        tone.bg,
        tone.fg,
        className
      )}
      data-slot="task-type-chip"
      data-type={type}
    >
      {TASK_TYPE_INITIALS[type]}
    </span>
  )
}

export { TaskTypeChip, type TaskTypeChipProps }
