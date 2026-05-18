"use client"

import {
  differenceInMinutes,
  differenceInSeconds,
  format,
  isThisYear,
  isToday,
  isYesterday,
} from "date-fns"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/shared/ui/tooltip"
import { cn } from "@/shared/lib/utils"

interface DateCellProps {
  className?: string
  value: string | Date | null | undefined
  /**
   * "compact" (default): single line + hover tooltip with full timestamp.
   *   Best for grid cells where vertical space is constrained.
   * "detail": stacked human-readable + precise timestamp.
   *   Best for detail pages where audit precision is always visible.
   */
  variant?: "compact" | "detail"
}

// Warehouse shift-work-friendly format:
// - Within the hour: relative ("5m ago") -- recency drives urgency
// - Earlier today: time-of-day ("08:00") -- anchors to shift schedule
// - Yesterday: "Yesterday, HH:mm" -- shift boundary
// - This year: "MMM d" -- absolute, scannable
// - Older: "MMM d, yyyy" -- year matters for archival
const formatDate = (date: Date, now: Date): string => {
  const seconds = differenceInSeconds(now, date)
  if (seconds < 60) return "just now"
  const minutes = differenceInMinutes(now, date)
  if (minutes < 60) return `${minutes}m ago`
  if (isToday(date)) return format(date, "HH:mm")
  if (isYesterday(date)) return `Yesterday, ${format(date, "HH:mm")}`
  if (isThisYear(date)) return format(date, "MMM d")
  return format(date, "MMM d, yyyy")
}

const Empty = () => (
  <span className="font-mono text-xs text-muted-foreground/40">{"\u2014"}</span>
)

const DateCell = ({ className, value, variant = "compact" }: DateCellProps) => {
  if (!value) return <Empty />

  const date = typeof value === "string" ? new Date(value) : value
  if (Number.isNaN(date.getTime())) {
    return (
      <span className="font-mono text-xs text-muted-foreground/40">
        invalid
      </span>
    )
  }

  const now = new Date()
  const display = formatDate(date, now)
  const precise = format(date, "yyyy-MM-dd HH:mm:ss 'UTC'")

  if (variant === "detail") {
    return (
      <div className={cn("flex flex-col gap-0.5", className)}>
        <span className="text-sm font-medium text-foreground">{display}</span>
        <span className="font-mono text-xs text-muted-foreground">
          {precise}
        </span>
      </div>
    )
  }

  // Compact: single line + tooltip
  const tooltip = format(date, "EEE, MMM d yyyy 'at' HH:mm:ss")
  return (
    <Tooltip delayDuration={300}>
      <TooltipTrigger
        render={
          <span
            className={cn(
              "cursor-default font-mono text-xs tabular-nums",
              className
            )}
          >
            {display}
          </span>
        }
      />
      <TooltipContent side="top">
        <span className="font-mono text-xs">{tooltip}</span>
      </TooltipContent>
    </Tooltip>
  )
}

export { DateCell }
