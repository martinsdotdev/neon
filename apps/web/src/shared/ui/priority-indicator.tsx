import { cn } from "@/shared/lib/utils"

type Priority = "Critical" | "High" | "Normal" | "Low"

const FILLED_BARS: Record<Priority, number> = {
  Critical: 4,
  High: 3,
  Normal: 2,
  Low: 1,
}

const COLOR_CLASS: Record<Priority, string> = {
  Critical: "text-priority-critical",
  High: "text-priority-high",
  Normal: "text-priority-normal",
  Low: "text-priority-low",
}

type PriorityIndicatorProps = {
  level: Priority
  hideLabel?: boolean
  className?: string
}

function PriorityIndicator({
  level,
  hideLabel = false,
  className,
}: PriorityIndicatorProps) {
  const filled = FILLED_BARS[level]

  return (
    <span
      className={cn(
        "text-label-s inline-flex items-center gap-1 font-mono",
        COLOR_CLASS[level],
        className
      )}
      data-slot="priority-indicator"
    >
      <span className="inline-flex items-end gap-0.5" aria-hidden="true">
        {[0, 1, 2, 3].map((index) => (
          <span
            className="block h-2.5 w-0.5 rounded-[1px] bg-current"
            key={index}
            style={{ opacity: index < filled ? 1 : 0.2 }}
          />
        ))}
      </span>
      {hideLabel ? (
        <span className="sr-only">{level}</span>
      ) : (
        <span>{level}</span>
      )}
    </span>
  )
}

export { PriorityIndicator, type Priority, type PriorityIndicatorProps }
