import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"
import type { Priority } from "@/shared/types/enums"

const priorityColors: Record<Priority, string> = {
  Low: "bg-slate-100 text-slate-700 dark:bg-slate-900 dark:text-slate-300",
  Normal: "bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300",
  High: "bg-orange-100 text-orange-700 dark:bg-orange-900 dark:text-orange-300",
  Critical: "bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-300",
}

interface PriorityBadgeProps {
  priority: Priority
  className?: string
}

export function PriorityBadge({
  priority,
  className,
}: PriorityBadgeProps) {
  return (
    <Badge
      variant="outline"
      className={cn(
        "border-transparent font-medium",
        priorityColors[priority],
        className
      )}
    >
      {priority}
    </Badge>
  )
}
