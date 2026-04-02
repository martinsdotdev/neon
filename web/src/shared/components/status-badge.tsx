import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"

const statusColors: Record<string, string> = {
  Planned: "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200",
  Released: "bg-indigo-100 text-indigo-800 dark:bg-indigo-900 dark:text-indigo-200",
  Allocated: "bg-violet-100 text-violet-800 dark:bg-violet-900 dark:text-violet-200",
  Assigned: "bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200",
  Completed: "bg-emerald-100 text-emerald-800 dark:bg-emerald-900 dark:text-emerald-200",
  Cancelled: "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200",
  Pending: "bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200",
  Confirmed: "bg-emerald-100 text-emerald-800 dark:bg-emerald-900 dark:text-emerald-200",
  Created: "bg-slate-100 text-slate-800 dark:bg-slate-900 dark:text-slate-200",
  Picked: "bg-cyan-100 text-cyan-800 dark:bg-cyan-900 dark:text-cyan-200",
  ReadyForWorkstation: "bg-teal-100 text-teal-800 dark:bg-teal-900 dark:text-teal-200",
  Disabled: "bg-gray-100 text-gray-800 dark:bg-gray-900 dark:text-gray-200",
  Idle: "bg-sky-100 text-sky-800 dark:bg-sky-900 dark:text-sky-200",
  Active: "bg-emerald-100 text-emerald-800 dark:bg-emerald-900 dark:text-emerald-200",
  Available: "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200",
  Reserved: "bg-orange-100 text-orange-800 dark:bg-orange-900 dark:text-orange-200",
  PickCreated: "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200",
  ShipCreated: "bg-indigo-100 text-indigo-800 dark:bg-indigo-900 dark:text-indigo-200",
  InBuffer: "bg-cyan-100 text-cyan-800 dark:bg-cyan-900 dark:text-cyan-200",
  Empty: "bg-gray-100 text-gray-800 dark:bg-gray-900 dark:text-gray-200",
  Packed: "bg-violet-100 text-violet-800 dark:bg-violet-900 dark:text-violet-200",
  ReadyToShip: "bg-teal-100 text-teal-800 dark:bg-teal-900 dark:text-teal-200",
  Shipped: "bg-emerald-100 text-emerald-800 dark:bg-emerald-900 dark:text-emerald-200",
}

interface StatusBadgeProps {
  status: string
  className?: string
}

export function StatusBadge({
  status,
  className,
}: StatusBadgeProps) {
  return (
    <Badge
      variant="outline"
      className={cn(
        "border-transparent font-medium",
        statusColors[status],
        className
      )}
    >
      {status}
    </Badge>
  )
}
