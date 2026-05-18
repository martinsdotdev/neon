import type { Task } from "@/shared/api/tasks"
import { cn } from "@/shared/lib/utils"
import { Badge } from "@/shared/ui/badge"

type TaskCardProps = {
  task: Task
  className?: string
  onClick?: () => void
}

function TaskCard({ task, className, onClick }: TaskCardProps) {
  const from = task.sourceLocationId
  const to = task.destinationLocationId
  const hasMovement = Boolean(from || to)

  return (
    <div
      className={cn(
        "group/task-card flex flex-col gap-2 rounded-shape-sm border border-outline-variant bg-card px-2.5 py-2 text-card-foreground shadow-sm transition-colors hover:bg-surface-container-low",
        onClick && "cursor-pointer",
        className
      )}
      data-slot="task-card"
      onClick={onClick}
      onKeyDown={(event) => {
        if (!onClick) return
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault()
          onClick()
        }
      }}
      role={onClick ? "button" : undefined}
      tabIndex={onClick ? 0 : undefined}
    >
      <div className="flex items-center gap-1.5">
        <span className="text-label-s font-mono font-medium">{task.id}</span>
        <span className="flex-1" />
        <Badge className="px-1.5 py-0 text-[10px]" variant="secondary">
          {task.taskType}
        </Badge>
      </div>
      <div className="flex items-baseline gap-2">
        <span className="text-label-s font-mono text-on-surface-variant">
          {task.skuId}
        </span>
        <span className="flex-1" />
        <span className="text-title-s font-mono font-semibold tabular-nums">
          {task.requestedQuantity}
        </span>
      </div>
      {hasMovement ? (
        <div className="text-label-s flex items-center gap-1 font-mono text-on-surface-variant">
          <span className="truncate">{from ?? "—"}</span>
          <span aria-hidden="true">→</span>
          <span className="truncate">{to ?? "—"}</span>
        </div>
      ) : null}
      {task.assignedTo ? (
        <div className="text-label-s flex items-center gap-1 text-on-surface-variant">
          <span className="size-1.5 rounded-full bg-state-assigned" />
          <span className="truncate">{task.assignedTo}</span>
        </div>
      ) : null}
    </div>
  )
}

export { TaskCard, type TaskCardProps }
