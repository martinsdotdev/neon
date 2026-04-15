"use client"

import {
  differenceInDays,
  differenceInHours,
  differenceInMinutes,
  differenceInSeconds,
  format,
  isThisYear,
  isToday,
  isYesterday,
} from "date-fns"
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/shared/ui/tooltip"
import { cn } from "@/shared/lib/utils"

interface DateCellProps {
  className?: string
  value: string | Date | null | undefined
}

const formatRelative = (date: Date, now: Date): string => {
  const seconds = differenceInSeconds(now, date)
  if (seconds < 60) return "just now"
  const minutes = differenceInMinutes(now, date)
  if (minutes < 60) return `${minutes}m ago`
  const hours = differenceInHours(now, date)
  if (hours < 24) return `${hours}h ago`
  const days = differenceInDays(now, date)
  if (days < 7) return `${days}d ago`
  return ""
}

const formatAbsolute = (date: Date): string => {
  if (isToday(date)) return `Today, ${format(date, "HH:mm")}`
  if (isYesterday(date)) return `Yesterday, ${format(date, "HH:mm")}`
  if (isThisYear(date)) return format(date, "MMM d, HH:mm")
  return format(date, "MMM d, yyyy")
}

const DateCell = ({ className, value }: DateCellProps) => {
  if (!value) {
    return (
      <span className="text-muted-foreground/40 font-mono text-xs">
        {"\u2014"}
      </span>
    )
  }

  const date = typeof value === "string" ? new Date(value) : value
  if (Number.isNaN(date.getTime())) {
    return (
      <span className="text-muted-foreground/40 font-mono text-xs">
        invalid
      </span>
    )
  }

  const now = new Date()
  const relative = formatRelative(date, now)
  const display = relative || formatAbsolute(date)
  const tooltip = format(date, "EEE, MMM d yyyy 'at' HH:mm:ss")

  return (
    <Tooltip delayDuration={300}>
      <TooltipTrigger
        render={
          <span
            className={cn(
              "font-mono text-xs tabular-nums cursor-default",
              className,
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
