import { cn } from "@/shared/lib/utils"

type FlowStageProps = {
  label: string
  value: string | number
  sub?: string
  color?: string
  histogram?: Array<number>
  className?: string
}

function FlowStage({
  label,
  value,
  sub,
  color = "var(--color-primary)",
  histogram,
  className,
}: FlowStageProps) {
  return (
    <div
      className={cn(
        "flex min-w-0 flex-col gap-1 rounded-shape-sm border border-outline-variant bg-surface-container-lowest px-3.5 py-2.5",
        className
      )}
      data-slot="flow-stage"
    >
      <span className="text-label-s font-mono tracking-wider text-on-surface-variant uppercase">
        {label}
      </span>
      <span
        className="font-mono text-[22px] leading-none font-semibold tabular-nums"
        style={{ color }}
      >
        {value}
      </span>
      {sub ? (
        <span className="text-label-s text-on-surface-variant">{sub}</span>
      ) : null}
      {histogram && histogram.length > 0 ? (
        <FlowStageHistogram color={color} points={histogram} />
      ) : null}
    </div>
  )
}

function FlowStageHistogram({
  points,
  color,
}: {
  points: Array<number>
  color: string
}) {
  const max = Math.max(...points, 1)
  return (
    <svg
      aria-hidden="true"
      className="mt-1.5 h-3.5 w-full"
      preserveAspectRatio="none"
      viewBox={`0 0 ${points.length * 2.5} 14`}
    >
      {points.map((value, index) => {
        const height = Math.max(1.5, (value / max) * 10)
        const opacity = 0.4 + (index / points.length) * 0.6
        return (
          <rect
            fill={color}
            height={height}
            key={index}
            opacity={opacity}
            width={1.5}
            x={index * 2.5}
            y={14 - height - 1}
          />
        )
      })}
    </svg>
  )
}

function FlowArrow({ className }: { className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        "self-center px-1 font-mono text-on-surface-variant",
        className
      )}
    >
      →
    </span>
  )
}

export { FlowStage, FlowArrow, type FlowStageProps }
