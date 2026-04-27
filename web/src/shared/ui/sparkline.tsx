import { cn } from "@/shared/lib/utils"

type SparklineProps = {
  points: Array<number>
  color?: string
  fill?: boolean
  strokeWidth?: number
  className?: string
  ariaLabel?: string
}

function Sparkline({
  points,
  color = "var(--color-primary)",
  fill = true,
  strokeWidth = 1.25,
  className,
  ariaLabel,
}: SparklineProps) {
  if (points.length < 2) {
    return null
  }

  const width = 120
  const height = 36
  const max = Math.max(...points)
  const min = Math.min(...points)
  const range = max - min || 1

  const coords = points.map((value, index) => {
    const x = (index / (points.length - 1)) * width
    const y = height - 2 - ((value - min) / range) * (height - 4)
    return [x, y] as const
  })

  const path = coords
    .map(([x, y], i) => `${i === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`)
    .join(" ")

  const area = `${path} L ${width},${height} L 0,${height} Z`

  return (
    <svg
      aria-label={ariaLabel ?? "sparkline"}
      className={cn("h-9 w-full", className)}
      preserveAspectRatio="none"
      role="img"
      viewBox={`0 0 ${width} ${height}`}
    >
      {fill ? <path d={area} fill={color} opacity="0.12" /> : null}
      <path
        d={path}
        fill="none"
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={strokeWidth}
      />
    </svg>
  )
}

export { Sparkline, type SparklineProps }
