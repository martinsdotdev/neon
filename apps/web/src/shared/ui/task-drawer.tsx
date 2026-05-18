import {
  ArrowRight,
  Check,
  ExternalLink,
  MapPin,
  User,
  UserCog,
  X,
} from "lucide-react"
import { toast } from "sonner"
import type { ReactNode } from "react"
import type { Task, TaskTimelineEvent } from "@/shared/api/tasks"
import { STATE_DOT_CLASS } from "@/shared/api/tasks"
import { cn } from "@/shared/lib/utils"
import { Badge } from "@/shared/ui/badge"
import { Button } from "@/shared/ui/button"
import { StateBadge } from "@/shared/ui/state-badge"
import { TaskTypeChip } from "@/shared/ui/task-type-chip"

// ---------------------------------------------------------------------------
// TaskDrawer — split-view detail panel for the active task.
// Shows a quantity progress bar (with shortpick highlighting), the
// source→destination move, a fields grid, the lifecycle timeline, and a
// state-aware action row. Designed to live in a sticky right column on
// desktop and as a fixed overlay on mobile.
// ---------------------------------------------------------------------------

const PRIORITY_DOT_CLASS = {
  high: "bg-priority-critical shadow-[0_0_0_3px_color-mix(in_oklch,var(--color-priority-critical)_18%,transparent)]",
  medium: "bg-priority-high",
  low: "bg-muted-foreground/40",
} as const

type DrawerAction = {
  icon: typeof Check
  label: string
  variant?: "default" | "outline" | "destructive"
}

function actionsFor(task: Task): Array<DrawerAction> {
  switch (task.state) {
    case "Planned":
      return [
        { icon: MapPin, label: "Allocate", variant: "default" },
        { icon: X, label: "Cancel task", variant: "destructive" },
      ]
    case "Allocated":
      return [
        { icon: User, label: "Assign to me", variant: "default" },
        { icon: X, label: "Cancel task", variant: "destructive" },
      ]
    case "Assigned":
      return [
        { icon: Check, label: "Mark complete", variant: "default" },
        { icon: UserCog, label: "Reassign", variant: "outline" },
        { icon: X, label: "Cancel task", variant: "destructive" },
      ]
    case "Completed":
    case "Cancelled":
      return [{ icon: ExternalLink, label: "View order", variant: "outline" }]
  }
}

type TaskDrawerProps = {
  task: Task | null
  onClose: () => void
  className?: string
}

function TaskDrawerEmpty({ className }: { className?: string }) {
  return (
    <aside
      aria-label="Task detail"
      className={cn(
        "flex flex-col rounded-shape-lg border border-outline-variant bg-card p-10 text-center text-on-surface-variant",
        className
      )}
      data-slot="task-drawer-empty"
    >
      <div className="mx-auto mb-3 flex size-9 items-center justify-center rounded-shape-sm bg-surface-container">
        <ArrowRight className="size-4 -rotate-90" />
      </div>
      <div className="text-sm font-medium text-foreground">
        No task selected
      </div>
      <p className="text-label-m mt-1">Click any row to inspect the task.</p>
    </aside>
  )
}

function TaskDrawer({ task, className, onClose }: TaskDrawerProps) {
  if (!task) return <TaskDrawerEmpty className={className} />

  const isShort =
    task.actualQuantity != null && task.actualQuantity < task.requestedQuantity
  const pct =
    task.actualQuantity != null
      ? Math.min(100, (task.actualQuantity / task.requestedQuantity) * 100)
      : 0
  const actions = actionsFor(task)

  const handleAction = (label: string) => {
    toast.success(`${label} · ${task.id}`, {
      description:
        "Wired to a stub. Plug in taskMutations.transition() when the API lands.",
    })
  }

  return (
    <aside
      aria-label={`Task ${task.id} detail`}
      className={cn(
        "flex flex-col overflow-hidden rounded-shape-lg border border-outline-variant bg-card",
        className
      )}
      data-slot="task-drawer"
    >
      <DrawerHeader onClose={onClose} task={task} />
      <div className="flex-1 overflow-y-auto px-5 py-4">
        <div className="flex flex-col gap-5">
          <QuantitySection isShort={isShort} pct={pct} task={task} />
          <MovementSection task={task} />
          <DetailsSection task={task} />
          <TimelineSection events={task.timeline ?? []} state={task.state} />
        </div>
      </div>
      {actions.length > 0 ? (
        <div className="flex flex-wrap items-center gap-2 border-t border-outline-variant bg-surface-container-low/40 px-5 py-3">
          {actions.map((action) => {
            const Icon = action.icon
            return (
              <Button
                className="flex-1"
                key={action.label}
                onClick={() => handleAction(action.label)}
                size="sm"
                variant={action.variant ?? "default"}
              >
                <Icon className="size-3.5" />
                {action.label}
              </Button>
            )
          })}
        </div>
      ) : null}
    </aside>
  )
}

function DrawerHeader({ onClose, task }: { onClose: () => void; task: Task }) {
  const priority = task.priority ?? "medium"
  return (
    <header className="flex flex-col gap-2 border-b border-outline-variant px-5 pt-4 pb-3">
      <div className="flex items-center justify-between gap-2">
        <span className="text-label-s font-mono text-on-surface-variant">
          {task.id}
        </span>
        <Button
          aria-label="Close drawer"
          onClick={onClose}
          size="icon-sm"
          variant="ghost"
        >
          <X className="size-4" />
        </Button>
      </div>
      <div className="flex items-center gap-2">
        <TaskTypeChip size="lg" type={task.taskType} />
        <h2 className="font-heading text-base leading-snug font-semibold">
          {task.taskType}
          {task.skuName ? (
            <>
              <span className="text-on-surface-variant"> · </span>
              {task.skuName}
            </>
          ) : null}
        </h2>
      </div>
      <div className="flex flex-wrap items-center gap-1.5">
        <StateBadge state={task.state} />
        {task.packagingLevel ? (
          <Badge variant="secondary">{task.packagingLevel}</Badge>
        ) : null}
        <span className="text-label-s inline-flex items-center gap-1.5 rounded-full bg-muted/70 px-2 py-0.5">
          <span
            aria-hidden="true"
            className={cn(
              "size-1.5 rounded-full",
              PRIORITY_DOT_CLASS[priority]
            )}
          />
          <span className="capitalize">{priority} priority</span>
        </span>
      </div>
    </header>
  )
}

function QuantitySection({
  isShort,
  pct,
  task,
}: {
  isShort: boolean
  pct: number
  task: Task
}) {
  return (
    <section data-slot="task-drawer-quantity">
      <div className="flex items-center justify-between gap-2">
        <SectionLabel>Quantity</SectionLabel>
        <span className="text-label-l font-mono">
          {task.actualQuantity != null ? (
            <>
              <span className="font-semibold tabular-nums">
                {task.actualQuantity} / {task.requestedQuantity}
              </span>
              {isShort ? (
                <span className="ml-2 text-state-cancelled">
                  · short {task.requestedQuantity - task.actualQuantity}
                </span>
              ) : null}
            </>
          ) : (
            <>
              <span className="font-semibold tabular-nums">
                {task.requestedQuantity}
              </span>{" "}
              <span className="text-on-surface-variant">requested</span>
            </>
          )}
        </span>
      </div>
      <div className="mt-1.5 h-2 w-full overflow-hidden rounded-full bg-muted">
        <div
          className={cn(
            "h-full rounded-full transition-[width]",
            isShort ? "bg-state-cancelled" : "bg-primary"
          )}
          style={{ width: `${task.actualQuantity != null ? pct : 0}%` }}
        />
      </div>
    </section>
  )
}

function MovementSection({ task }: { task: Task }) {
  const source = task.sourceLocationId ?? "—"
  const destination = task.destinationLocationId ?? "—"
  return (
    <section data-slot="task-drawer-movement">
      <SectionLabel>Movement</SectionLabel>
      <div className="mt-2 grid grid-cols-[1fr_auto_1fr] items-stretch gap-3 rounded-shape-md bg-surface-container-low p-3.5">
        <MovementNode
          aisle={aisleLabel(source)}
          label="Source"
          location={source}
        />
        <div className="flex items-center justify-center text-on-surface-variant">
          <ArrowRight className="size-5" />
        </div>
        <MovementNode
          aisle={aisleLabel(destination)}
          label="Destination"
          location={destination}
        />
      </div>
    </section>
  )
}

function MovementNode({
  aisle,
  label,
  location,
}: {
  aisle: string
  label: string
  location: string
}) {
  return (
    <div className="flex min-w-0 flex-col gap-0.5">
      <span className="font-mono text-[9px] tracking-[0.1em] text-on-surface-variant uppercase">
        {label}
      </span>
      <span className="truncate font-mono text-sm font-semibold">
        {location}
      </span>
      <span className="text-label-s truncate font-mono text-on-surface-variant">
        {aisle}
      </span>
    </div>
  )
}

function DetailsSection({ task }: { task: Task }) {
  const fields: Array<{ label: string; value: ReactNode; mono?: boolean }> = [
    { label: "SKU", mono: true, value: task.skuId },
    { label: "Order", mono: true, value: task.orderId },
    { label: "Wave", mono: true, value: task.waveId ?? "—" },
    {
      label: "Handling Unit",
      mono: true,
      value: task.handlingUnitId ?? "—",
    },
    { label: "Assigned To", value: task.assignedTo ?? "—" },
    { label: "Created", value: shortDateTime(task.createdAt) },
  ]
  return (
    <section data-slot="task-drawer-details">
      <SectionLabel>Details</SectionLabel>
      <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-3">
        {fields.map((field) => (
          <div key={field.label}>
            <dt className="font-mono text-[9px] tracking-[0.1em] text-on-surface-variant uppercase">
              {field.label}
            </dt>
            <dd
              className={cn(
                "mt-0.5 text-sm",
                field.mono ? "text-label-l font-mono" : ""
              )}
            >
              {field.value}
            </dd>
          </div>
        ))}
      </dl>
    </section>
  )
}

const HAPPY_PATH: ReadonlyArray<Task["state"]> = [
  "Planned",
  "Allocated",
  "Assigned",
  "Completed",
]

function TimelineSection({
  events,
  state,
}: {
  events: Array<TaskTimelineEvent>
  state: Task["state"]
}) {
  const isTerminal = state === "Completed" || state === "Cancelled"
  // Synthesize a Planned event if the timeline is empty so the lifecycle
  // strip never renders without its starting dot. Defensive against API
  // shapes where `events` is missing or empty for fresh tasks.
  const seedEvents: Array<TaskTimelineEvent> =
    events.length === 0 ? [{ at: "", by: "system", state: "Planned" }] : events
  const futureStates = HAPPY_PATH.filter((s) => {
    if (seedEvents.find((e) => e.state === s)) return false
    if (isTerminal) return false
    const lastDone = seedEvents.at(-1)?.state ?? "Planned"
    return HAPPY_PATH.indexOf(s) > HAPPY_PATH.indexOf(lastDone)
  })
  const all: Array<TimelineRow> = [
    ...seedEvents.map((event) => ({ event, future: false })),
    ...futureStates.map((s) => ({
      event: { at: "", by: "", state: s },
      future: true,
    })),
  ]
  return (
    <section data-slot="task-drawer-timeline">
      <SectionLabel>Lifecycle</SectionLabel>
      <ol className="relative mt-2.5 flex flex-col">
        {all.map((row, index) => (
          <TimelineRow
            isLast={index === all.length - 1}
            key={`${row.event.state}-${index}`}
            row={row}
          />
        ))}
      </ol>
    </section>
  )
}

type TimelineRow = { event: TaskTimelineEvent; future: boolean }

function TimelineRow({ isLast, row }: { isLast: boolean; row: TimelineRow }) {
  const { event, future } = row
  return (
    <li
      className={cn(
        "relative grid grid-cols-[20px_1fr] gap-3 pb-3 last:pb-0",
        future && "opacity-65"
      )}
    >
      {!isLast ? (
        <span
          aria-hidden="true"
          className="absolute top-4 bottom-0 left-[9px] -z-0 w-px bg-outline-variant"
        />
      ) : null}
      <span
        aria-hidden="true"
        className={cn(
          "relative z-10 mt-1 ml-1 size-2.5 self-start rounded-full ring-4 ring-card",
          future
            ? "border border-outline-variant bg-card"
            : STATE_DOT_CLASS[event.state]
        )}
      />
      <div className="flex min-w-0 flex-col gap-0.5">
        <span
          className={cn(
            "text-sm font-medium",
            future && "text-on-surface-variant"
          )}
        >
          {event.state}
        </span>
        {future ? (
          <span className="text-label-s font-mono text-on-surface-variant">
            Pending
          </span>
        ) : (
          <span className="text-label-s font-mono text-on-surface-variant">
            {shortDateTime(event.at)} · {event.by}
          </span>
        )}
        {event.note ? (
          <span className="text-label-s mt-1 self-start rounded-shape-xs bg-surface-container-low px-2 py-1 text-on-surface-variant">
            {event.note}
          </span>
        ) : null}
      </div>
    </li>
  )
}

function SectionLabel({ children }: { children: ReactNode }) {
  return (
    <span className="text-label-s font-mono tracking-wider text-on-surface-variant uppercase">
      {children}
    </span>
  )
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function aisleLabel(loc: string): string {
  if (!loc || loc === "—") return ""
  if (loc.startsWith("WS")) return "Pack workstation"
  if (loc.startsWith("RECV")) return "Receiving dock"
  const parts = loc.split("-")
  return parts.length >= 2 ? `Aisle ${parts[1]}` : ""
}

function shortDateTime(iso: string): string {
  if (!iso) return "—"
  const date = new Date(iso)
  return date.toLocaleString(undefined, {
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    month: "short",
  })
}

export { TaskDrawer, type TaskDrawerProps }
