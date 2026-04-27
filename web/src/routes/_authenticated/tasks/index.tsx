import { createFileRoute, useNavigate } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { Inbox, KanbanSquare, LayoutList, PanelRight } from "lucide-react"
import { toast } from "sonner"
import type { ColumnDef } from "@tanstack/react-table"
import type { Task, TaskState } from "@/shared/api/tasks"
import type { Filter, FilterFieldConfig } from "@/shared/reui/filters"
import type { KanbanMoveEvent } from "@/shared/ui/kanban"
import {
  ALL_TASK_STATES,
  LEGAL_TRANSITIONS,
  STATE_DOT_CLASS,
  STATE_LABEL,
  taskQueries,
} from "@/shared/api/tasks"
import { DataGrid } from "@/shared/data-grid/data-grid"
import { DataGridRowHeightMenu } from "@/shared/data-grid/data-grid-row-height-menu"
import { DataGridSelectionBar } from "@/shared/data-grid/data-grid-selection-bar"
import { getDataGridSelectColumn } from "@/shared/data-grid/data-grid-select-column"
import { DataGridSortMenu } from "@/shared/data-grid/data-grid-sort-menu"
import { DataGridViewMenu } from "@/shared/data-grid/data-grid-view-menu"
import { useDataGrid } from "@/shared/hooks/use-data-grid"
import { useIsMobile } from "@/shared/hooks/use-mobile"
import { cn } from "@/shared/lib/utils"
import { Filters } from "@/shared/reui/filters"
import { Badge } from "@/shared/ui/badge"
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/shared/ui/empty"
import {
  HoverCard,
  HoverCardContent,
  HoverCardTrigger,
} from "@/shared/ui/hover-card"
import {
  Kanban,
  KanbanBoard,
  KanbanColumn,
  KanbanColumnContent,
  KanbanItem,
  KanbanItemHandle,
  KanbanOverlay,
} from "@/shared/ui/kanban"
import { PageHeader } from "@/shared/ui/page-header"
import { KpiCard } from "@/shared/ui/stat"
import { StateBadge } from "@/shared/ui/state-badge"
import { TaskCard } from "@/shared/ui/task-card"
import { TaskDrawer } from "@/shared/ui/task-drawer"
import { TaskTypeChip } from "@/shared/ui/task-type-chip"
import { ToggleGroup, ToggleGroupItem } from "@/shared/ui/toggle-group"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/shared/ui/tooltip"

const TASK_TYPE_OPTIONS = [
  { label: "Pick", value: "Pick" },
  { label: "Putaway", value: "Putaway" },
  { label: "Replenish", value: "Replenish" },
  { label: "Transfer", value: "Transfer" },
]

const filterFields: Array<FilterFieldConfig> = [
  { key: "id", label: "ID", type: "text" },
  { key: "skuId", label: "SKU", type: "text" },
  {
    key: "state",
    label: "State",
    options: ALL_TASK_STATES.map((s) => ({ label: STATE_LABEL[s], value: s })),
    type: "select",
  },
  {
    key: "taskType",
    label: "Type",
    options: TASK_TYPE_OPTIONS,
    type: "select",
  },
]

export const Route = createFileRoute("/_authenticated/tasks/")({
  component: TasksPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(taskQueries.all()),
})

type TasksView = "table" | "split" | "kanban"

function TasksPage() {
  const { data: tasks } = useSuspenseQuery(taskQueries.all())
  const [data, setData] = useState(tasks)
  const [filters, setFilters] = useState<Array<Filter>>([])
  const [view, setView] = useState<TasksView>("split")

  const filteredData = useMemo(() => {
    if (filters.length === 0) return data
    return data.filter((row) => {
      for (const f of filters) {
        const value = String((row as Record<string, unknown>)[f.field] ?? "")
        if (f.operator === "is" || f.operator === "is_any_of") {
          if (!f.values.some((v) => value === String(v))) return false
        } else if (f.operator === "contains") {
          if (
            !f.values.some((v) =>
              value.toLowerCase().includes(String(v).toLowerCase())
            )
          )
            return false
        }
      }
      return true
    })
  }, [data, filters])

  const onFiltersChange = useCallback((newFilters: Array<Filter>) => {
    setFilters(newFilters)
  }, [])

  const columns = useMemo<Array<ColumnDef<Task>>>(
    () => [
      getDataGridSelectColumn<Task>({
        detailHref: (row) => `/tasks/${row.original.id}`,
        enableRowMarkers: true,
      }),
      {
        accessorKey: "id",
        cell: ({ row }) => (
          <span className="font-mono text-xs font-medium">
            {row.original.id}
          </span>
        ),
        header: "ID",
        meta: { cell: { variant: "short-text" as const }, label: "ID" },
        size: 120,
      },
      {
        accessorKey: "taskType",
        cell: ({ row }) => (
          <Badge variant="secondary">{row.original.taskType}</Badge>
        ),
        header: "Type",
        meta: {
          cell: {
            options: TASK_TYPE_OPTIONS,
            variant: "select" as const,
          },
          label: "Type",
        },
        size: 120,
      },
      {
        accessorKey: "skuId",
        cell: ({ row }) => (
          <span className="font-mono text-xs font-medium">
            {row.original.skuId}
          </span>
        ),
        header: "SKU",
        meta: {
          cell: { variant: "short-text" as const },
          label: "SKU",
        },
        size: 150,
      },
      {
        accessorKey: "state",
        cell: ({ row }) => <StateBadge state={row.original.state} />,
        header: "State",
        meta: {
          cell: {
            options: ALL_TASK_STATES.map((s) => ({
              label: STATE_LABEL[s],
              value: s,
            })),
            variant: "select" as const,
          },
          label: "State",
        },
        size: 130,
      },
      {
        accessorKey: "requestedQuantity",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.requestedQuantity}
          </span>
        ),
        header: "Requested Qty",
        meta: {
          cell: { variant: "number" as const },
          label: "Requested Qty",
        },
        size: 140,
      },
      {
        accessorKey: "assignedTo",
        cell: ({ row }) => (
          <span className="font-mono text-xs text-muted-foreground">
            {row.original.assignedTo ?? "—"}
          </span>
        ),
        header: "Assigned To",
        meta: {
          cell: { variant: "short-text" as const },
          label: "Assigned To",
        },
        size: 150,
      },
    ],
    []
  )

  const gridProps = useDataGrid({
    columns,
    data: filteredData,
    enableSearch: true,
    onDataChange: setData,
    readOnly: true,
    rowHeight: "short",
  })

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        description="Pick, putaway, replenish, and transfer tasks"
        title="Tasks"
      />
      <TaskStatStrip tasks={data} />
      <div className="flex flex-wrap items-center gap-2 pb-2">
        <Filters
          fields={filterFields}
          filters={filters}
          onChange={onFiltersChange}
          size="sm"
        />
        <div className="ml-auto flex items-center gap-2">
          <ViewToggle onValueChange={setView} value={view} />
          {view === "table" ? (
            <>
              <DataGridSortMenu table={gridProps.table} />
              <DataGridRowHeightMenu table={gridProps.table} />
              <DataGridViewMenu table={gridProps.table} />
            </>
          ) : null}
        </div>
      </div>
      {view === "table" ? (
        <>
          <DataGrid {...gridProps} height={500} />
          <DataGridSelectionBar table={gridProps.table} />
        </>
      ) : view === "split" ? (
        <TaskSplitView tasks={filteredData} />
      ) : (
        <TaskKanbanView tasks={filteredData} />
      )}
    </div>
  )
}

// ---------------------------------------------------------------------------
// View toggle
// ---------------------------------------------------------------------------

function ViewToggle({
  onValueChange,
  value,
}: {
  onValueChange: (value: TasksView) => void
  value: TasksView
}) {
  return (
    <ToggleGroup
      aria-label="Tasks view"
      onValueChange={(val) => {
        const next = val[0]
        // Base UI's ToggleGroup hands back string[]; we know our items only
        // emit TasksView values, so the cast is safe.
        if (next) onValueChange(next as TasksView)
      }}
      size="sm"
      value={[value]}
      variant="outline"
    >
      <ToggleGroupItem aria-label="Split view" value="split">
        <PanelRight className="size-4" />
      </ToggleGroupItem>
      <ToggleGroupItem aria-label="Table view" value="table">
        <LayoutList className="size-4" />
      </ToggleGroupItem>
      <ToggleGroupItem aria-label="Kanban view" value="kanban">
        <KanbanSquare className="size-4" />
      </ToggleGroupItem>
    </ToggleGroup>
  )
}

// ---------------------------------------------------------------------------
// Kanban config — WIP limits per column. State transitions + labels are
// imported from the API layer so toast text / tooltip / kanban / drawer
// agree without drift.
// ---------------------------------------------------------------------------

// Optional WIP limits per column — keep undefined to disable the feature.
// The column header surfaces "used / limit"; exceeding the cap tints the
// count chip amber and `handleMove` rejects incoming drops.
const WIP_LIMITS: Partial<Record<TaskState, number>> = {
  Allocated: 8,
  Assigned: 8,
}

function canTransition(from: TaskState, to: TaskState): boolean {
  if (from === to) return true
  return LEGAL_TRANSITIONS[from].includes(to)
}

function groupByState(tasks: Array<Task>): Record<TaskState, Array<Task>> {
  const result = Object.fromEntries(
    ALL_TASK_STATES.map((s) => [s, [] as Array<Task>])
  ) as Record<TaskState, Array<Task>>
  for (const task of tasks) result[task.state].push(task)
  return result
}

// ---------------------------------------------------------------------------
// Kanban view (desktop) and MobileTaskStack (mobile)
// ---------------------------------------------------------------------------

function TaskKanbanView({ tasks }: { tasks: Array<Task> }) {
  const isMobile = useIsMobile()
  const navigate = useNavigate()

  const [columns, setColumns] = useState<Record<TaskState, Array<Task>>>(() =>
    groupByState(tasks)
  )

  useEffect(() => {
    setColumns(groupByState(tasks))
  }, [tasks])

  const tasksById = useMemo(() => new Map(tasks.map((t) => [t.id, t])), [tasks])

  const goToTask = useCallback(
    (taskId: string) => {
      navigate({ to: "/tasks/$taskId", params: { taskId } })
    },
    [navigate]
  )

  /**
   * Drag-end handler. ReUI's Kanban skips the drag-over reorder when `onMove`
   * is provided, so this is the single place to commit (or veto) a move.
   *
   * Behavior today (a sensible default until D1 is decided):
   *   - Same-column reorder: commit local reorder.
   *   - Cross-column move: validate via canTransition + WIP limit. On pass,
   *     commit; on fail, snap back AND surface a toast so supervisors learn
   *     why the drop was rejected.
   *
   * TODO(user): swap the local `commitCrossColumn` for a server mutation
   * (e.g. taskMutations.transition) when the API lands. The toast / rollback
   * branches stay the same; only the success commit changes.
   */
  const handleMove = useCallback(
    (event: KanbanMoveEvent) => {
      const from = event.activeContainer as TaskState
      const to = event.overContainer as TaskState
      const itemId = event.event.active.id as string

      // Same-column reorder: shuffle within the array.
      if (from === to) {
        if (event.activeIndex === event.overIndex) return
        setColumns((prev) => {
          const items = [...prev[from]]
          const [moved] = items.splice(event.activeIndex, 1)
          items.splice(event.overIndex, 0, moved)
          return { ...prev, [from]: items }
        })
        return
      }

      if (!canTransition(from, to)) {
        const allowed = LEGAL_TRANSITIONS[from]
        toast.error(
          `Can't move from ${STATE_LABEL[from]} to ${STATE_LABEL[to]}.`,
          {
            description:
              allowed.length > 0
                ? `Legal next states: ${allowed.map((s) => STATE_LABEL[s]).join(", ")}.`
                : `${STATE_LABEL[from]} is a terminal state.`,
          }
        )
        setColumns(groupByState(tasks))
        return
      }

      const wipLimit = WIP_LIMITS[to]
      if (wipLimit !== undefined && columns[to].length >= wipLimit) {
        toast.error(
          `${STATE_LABEL[to]} is at WIP limit (${wipLimit} / ${wipLimit}).`,
          { description: "Finish or reassign tasks before adding more." }
        )
        setColumns(groupByState(tasks))
        return
      }

      // Cross-column move: pop from `from`, splice into `to` at the
      // drop index, and tag the task with its new state.
      setColumns((prev) => {
        const fromItems = [...prev[from]]
        const toItems = [...prev[to]]
        const itemIndex = fromItems.findIndex((t) => t.id === itemId)
        if (itemIndex === -1) return prev
        const [moved] = fromItems.splice(itemIndex, 1)
        toItems.splice(event.overIndex, 0, { ...moved, state: to })
        return { ...prev, [from]: fromItems, [to]: toItems }
      })
    },
    [columns, tasks]
  )

  if (isMobile) {
    return <MobileTaskStack columns={columns} onSelect={goToTask} />
  }

  return (
    <Kanban
      getItemValue={(task) => task.id}
      onMove={handleMove}
      onValueChange={setColumns}
      value={columns}
    >
      <KanbanBoard className="grid grid-cols-2 gap-3 lg:grid-cols-5">
        {ALL_TASK_STATES.map((state) => (
          <KanbanColumn
            className="overflow-hidden rounded-shape-sm border border-outline-variant bg-surface-container-low"
            key={state}
            value={state}
          >
            <ColumnHeader
              count={columns[state].length}
              state={state}
              wipLimit={WIP_LIMITS[state]}
            />
            <KanbanColumnContent
              className="flex min-h-32 flex-col gap-2 p-2"
              value={state}
            >
              {columns[state].map((task) => (
                <KanbanItem key={task.id} value={task.id}>
                  <KanbanItemHandle cursor={false}>
                    <KanbanCardWithPreview onSelect={goToTask} task={task} />
                  </KanbanItemHandle>
                </KanbanItem>
              ))}
              {columns[state].length === 0 ? (
                <EmptyColumn state={state} />
              ) : null}
            </KanbanColumnContent>
          </KanbanColumn>
        ))}
      </KanbanBoard>
      <KanbanOverlay>
        {({ value }) => {
          const task = tasksById.get(value as string)
          if (!task) return null
          return <TaskCard task={task} />
        }}
      </KanbanOverlay>
    </Kanban>
  )
}

function ColumnHeader({
  count,
  state,
  wipLimit,
}: {
  count: number
  state: TaskState
  wipLimit?: number
}) {
  const overLimit = wipLimit !== undefined && count > wipLimit
  return (
    <div className="flex items-center justify-between border-b border-outline-variant bg-surface-container-lowest px-3 py-2">
      <div className="flex items-center gap-2">
        <span
          aria-hidden="true"
          className={cn("size-2 rounded-full", STATE_DOT_CLASS[state])}
        />
        <span className="text-label-s font-mono tracking-wider uppercase">
          {state}
        </span>
      </div>
      {wipLimit === undefined ? (
        <span className="text-label-s font-mono text-on-surface-variant">
          {count}
        </span>
      ) : (
        <Tooltip>
          <TooltipTrigger
            className="text-label-s rounded-shape-xs px-1 font-mono"
            render={<span />}
          >
            <span
              className={
                overLimit ? "text-priority-high" : "text-on-surface-variant"
              }
            >
              {count} / {wipLimit}
            </span>
          </TooltipTrigger>
          <TooltipContent>
            Soft WIP limit of {wipLimit} in-progress tasks. Drops over the cap
            are rejected.
          </TooltipContent>
        </Tooltip>
      )}
    </div>
  )
}

// Shared empty-state primitive for the tasks page. Centralizes the
// Empty + EmptyHeader + EmptyMedia(Inbox) + EmptyTitle + EmptyDescription
// shape so call sites just supply title / description / size.
function TasksEmpty({
  description,
  size = "md",
  title,
}: {
  title: string
  description: string
  size?: "sm" | "md" | "lg"
}) {
  const padding = size === "sm" ? "p-4" : size === "lg" ? "p-10" : "p-8"
  return (
    <Empty className={cn("border", padding)}>
      <EmptyHeader>
        <EmptyMedia variant="icon">
          <Inbox />
        </EmptyMedia>
        <EmptyTitle className={size === "sm" ? "text-sm" : ""}>
          {title}
        </EmptyTitle>
        <EmptyDescription className={size === "sm" ? "text-xs" : ""}>
          {description}
        </EmptyDescription>
      </EmptyHeader>
    </Empty>
  )
}

function EmptyColumn({ state }: { state: TaskState }) {
  return (
    <div className="px-2 py-4">
      <TasksEmpty
        description="Drop a card here to transition."
        size="sm"
        title={`No ${STATE_LABEL[state]} tasks`}
      />
    </div>
  )
}

// KanbanCardWithPreview wraps TaskCard with a HoverCard popup containing the
// full task detail. The HoverCardTrigger uses asChild via render so it doesn't
// add an extra element that would interfere with dnd-kit's drag listeners.
function KanbanCardWithPreview({
  onSelect,
  task,
}: {
  onSelect: (taskId: string) => void
  task: Task
}) {
  return (
    <HoverCard>
      <HoverCardTrigger render={<div />}>
        <TaskCard onClick={() => onSelect(task.id)} task={task} />
      </HoverCardTrigger>
      <HoverCardContent className="w-72">
        <TaskPreview task={task} />
      </HoverCardContent>
    </HoverCard>
  )
}

function TaskPreview({ task }: { task: Task }) {
  return (
    <div className="flex flex-col gap-2 text-sm">
      <div className="flex items-center gap-2">
        <span className="font-mono text-xs font-semibold">{task.id}</span>
        <Badge className="px-1.5 py-0 text-[10px]" variant="secondary">
          {task.taskType}
        </Badge>
        <span className="flex-1" />
        <StateBadge state={task.state} />
      </div>
      <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-xs">
        <dt className="text-on-surface-variant">SKU</dt>
        <dd className="font-mono">{task.skuId}</dd>
        <dt className="text-on-surface-variant">Qty</dt>
        <dd className="font-mono tabular-nums">{task.requestedQuantity}</dd>
        {task.sourceLocationId || task.destinationLocationId ? (
          <>
            <dt className="text-on-surface-variant">Move</dt>
            <dd className="font-mono">
              {task.sourceLocationId ?? "—"} →{" "}
              {task.destinationLocationId ?? "—"}
            </dd>
          </>
        ) : null}
        {task.waveId ? (
          <>
            <dt className="text-on-surface-variant">Wave</dt>
            <dd className="font-mono">{task.waveId}</dd>
          </>
        ) : null}
        {task.assignedTo ? (
          <>
            <dt className="text-on-surface-variant">Operator</dt>
            <dd className="font-mono">{task.assignedTo}</dd>
          </>
        ) : null}
        <dt className="text-on-surface-variant">Created</dt>
        <dd className="font-mono">{task.createdAt}</dd>
      </dl>
    </div>
  )
}

// ---------------------------------------------------------------------------
// MobileTaskStack — single-column read-only list grouped by state, with a
// state-filter chip row for narrowing focus. No drag-and-drop here; on touch
// devices supervisors tap into a task to transition.
//
// TODO(user) D1: pick a transition mechanism for mobile. Options below.
// ---------------------------------------------------------------------------

function MobileTaskStack({
  columns,
  onSelect,
}: {
  columns: Record<TaskState, Array<Task>>
  onSelect: (taskId: string) => void
}) {
  const [activeStates, setActiveStates] = useState<Array<TaskState>>(() => [
    ...ALL_TASK_STATES,
  ])

  const flat = useMemo(() => {
    const out: Array<Task> = []
    for (const state of ALL_TASK_STATES) {
      if (!activeStates.includes(state)) continue
      for (const task of columns[state]) out.push(task)
    }
    return out
  }, [columns, activeStates])

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-1.5">
        {ALL_TASK_STATES.map((state) => {
          const active = activeStates.includes(state)
          return (
            <button
              className={
                "text-label-s flex items-center gap-1.5 rounded-full border px-2.5 py-1 font-mono transition-colors " +
                (active
                  ? "border-foreground bg-surface-container-high text-foreground"
                  : "border-dashed border-outline-variant text-on-surface-variant")
              }
              key={state}
              onClick={() =>
                setActiveStates((prev) =>
                  prev.includes(state)
                    ? prev.filter((s) => s !== state)
                    : [...prev, state]
                )
              }
              type="button"
            >
              <span
                aria-hidden="true"
                className={cn("size-1.5 rounded-full", STATE_DOT_CLASS[state])}
              />
              {state}
              <span className="text-on-surface-variant">
                {columns[state].length}
              </span>
            </button>
          )
        })}
      </div>

      {flat.length === 0 ? (
        <TasksEmpty
          description="Toggle a state chip above to widen the view."
          title="No tasks match the current filter"
        />
      ) : (
        <div className="flex flex-col gap-2">
          {flat.map((task) => (
            <TaskCard
              key={task.id}
              onClick={() => onSelect(task.id)}
              task={task}
            />
          ))}
        </div>
      )}
    </div>
  )
}

// ---------------------------------------------------------------------------
// TaskStatStrip — 5 task-specific KPI tiles above the view-area. Reuses the
// Stat compound from /shared/ui/stat so the visual language matches the
// dashboard exactly.
// ---------------------------------------------------------------------------

function TaskStatStrip({ tasks }: { tasks: Array<Task> }) {
  const stats = useMemo(() => {
    const total = tasks.length
    const inProgress = tasks.filter((t) => t.state === "Assigned").length
    const allocated = tasks.filter((t) => t.state === "Allocated").length
    const completed = tasks.filter((t) => t.state === "Completed").length
    const cancelled = tasks.filter((t) => t.state === "Cancelled").length
    const open = total - completed - cancelled
    const high = tasks.filter(
      (t) =>
        t.priority === "high" &&
        t.state !== "Completed" &&
        t.state !== "Cancelled"
    ).length
    return { allocated, completed, high, inProgress, open, total }
  }, [tasks])

  // Each tile uses the existing KpiCard from the dashboard's Stat compound,
  // so the visual language matches the dashboard's KPI row exactly.
  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
      <KpiCard
        description={`of ${stats.total}`}
        title="Open"
        value={stats.open}
      />
      <KpiCard
        description="assigned"
        title="In progress"
        value={stats.inProgress}
      />
      <KpiCard
        description="unassigned"
        title="Awaiting pick"
        value={stats.allocated}
      />
      <KpiCard
        description="open"
        title="High priority"
        tone={stats.high > 0 ? "critical" : "default"}
        value={stats.high}
      />
      <KpiCard
        description="today"
        title="Completed"
        trend={
          stats.completed > 0
            ? { direction: "up", value: `+${stats.completed}` }
            : undefined
        }
        value={stats.completed}
      />
    </div>
  )
}

// ---------------------------------------------------------------------------
// TaskSplitView — the design's signature interaction. Compact list on the
// left + sticky TaskDrawer on the right. Keyboard nav (j/k/↑/↓ to move,
// Esc to close drawer, x to toggle row selection).
// ---------------------------------------------------------------------------

function TaskSplitView({ tasks }: { tasks: Array<Task> }) {
  const isMobile = useIsMobile()
  const navigate = useNavigate()
  const [activeId, setActiveId] = useState<string | null>(
    () => tasks[0]?.id ?? null
  )
  const [selected, setSelected] = useState<Set<string>>(() => new Set())
  const listRef = useRef<HTMLDivElement>(null)

  // Reset selection when the underlying task set changes (e.g. filter change).
  useEffect(() => {
    setSelected(new Set())
  }, [tasks])

  // Keep activeId pointing at something present after filters change.
  useEffect(() => {
    if (activeId && tasks.find((t) => t.id === activeId)) return
    setActiveId(tasks[0]?.id ?? null)
  }, [tasks, activeId])

  const activeTask = useMemo(
    () => tasks.find((t) => t.id === activeId) ?? null,
    [tasks, activeId]
  )

  // Keyboard nav scoped to this view: j/k or arrow keys move the active row;
  // Esc closes the drawer; x toggles row selection. We attach to window so
  // the user doesn't have to focus the list first, but ignore events while
  // the user is typing into a search/input field.
  useEffect(() => {
    if (isMobile) return
    const onKey = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null
      if (
        target &&
        (target.tagName === "INPUT" ||
          target.tagName === "TEXTAREA" ||
          target.isContentEditable)
      ) {
        return
      }
      const idx = tasks.findIndex((t) => t.id === activeId)
      const noActive = idx === -1
      if (event.key === "ArrowDown" || event.key === "j") {
        event.preventDefault()
        // From a cleared (Esc'd) state, ArrowDown reactivates the first row.
        const next = noActive
          ? tasks[0]
          : tasks[Math.min(tasks.length - 1, idx + 1)]
        if (next) setActiveId(next.id)
      } else if (event.key === "ArrowUp" || event.key === "k") {
        event.preventDefault()
        // ArrowUp on a cleared state is a no-op so users can intentionally keep
        // the drawer empty without it snapping back to row 0.
        if (noActive) return
        const next = tasks[Math.max(0, idx - 1)]
        if (next) setActiveId(next.id)
      } else if (event.key === "Escape") {
        setActiveId(null)
      } else if (event.key === "x" && activeId) {
        event.preventDefault()
        setSelected((prev) => {
          const next = new Set(prev)
          if (next.has(activeId)) next.delete(activeId)
          else next.add(activeId)
          return next
        })
      } else if (event.key === "Enter" && activeId) {
        navigate({ params: { taskId: activeId }, to: "/tasks/$taskId" })
      }
    }
    window.addEventListener("keydown", onKey)
    return () => window.removeEventListener("keydown", onKey)
  }, [activeId, isMobile, navigate, tasks])

  // Auto-scroll the active row into view when keyboard nav moves it off screen.
  useEffect(() => {
    if (!activeId || !listRef.current) return
    const node = listRef.current.querySelector<HTMLElement>(
      `[data-row-id="${activeId}"]`
    )
    if (node) {
      node.scrollIntoView({ block: "nearest" })
    }
  }, [activeId])

  const toggleRowSelection = useCallback((id: string) => {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }, [])

  const bulkAction = useCallback(
    (label: string) => {
      toast.success(
        `${label} · ${selected.size} task${selected.size === 1 ? "" : "s"}`,
        { description: "Stub action — no transitions persisted yet." }
      )
      setSelected(new Set())
    },
    [selected]
  )

  return (
    <div className="relative">
      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_440px]">
        <SplitTaskList
          activeId={activeId}
          listRef={listRef}
          onActivate={setActiveId}
          onToggleSelection={toggleRowSelection}
          selected={selected}
          tasks={tasks}
        />
        <div className="lg:sticky lg:top-4 lg:max-h-[calc(100vh-7rem)]">
          <TaskDrawer
            className="lg:max-h-[calc(100vh-7rem)]"
            onClose={() => setActiveId(null)}
            task={activeTask}
          />
        </div>
      </div>

      {selected.size > 0 ? (
        <BulkActionBar
          count={selected.size}
          onAction={bulkAction}
          onClear={() => setSelected(new Set())}
        />
      ) : null}
    </div>
  )
}

// ---------------------------------------------------------------------------
// SplitTaskList — compact list of tasks for the Split view's left column.
// Each row: type chip, ID, SKU name, qty (red if shortpicked), state badge.
// ---------------------------------------------------------------------------

function SplitTaskList({
  activeId,
  listRef,
  onActivate,
  onToggleSelection,
  selected,
  tasks,
}: {
  activeId: string | null
  listRef: React.RefObject<HTMLDivElement | null>
  onActivate: (id: string) => void
  onToggleSelection: (id: string) => void
  selected: Set<string>
  tasks: Array<Task>
}) {
  if (tasks.length === 0) {
    return (
      <TasksEmpty
        description="Clear a filter chip above or pick a different state."
        size="lg"
        title="No tasks match the current filters"
      />
    )
  }

  return (
    <div
      className="overflow-hidden rounded-shape-lg border border-outline-variant bg-card"
      ref={listRef}
    >
      <div className="grid grid-cols-[28px_28px_minmax(0,1fr)_72px_auto] items-center gap-2 border-b border-outline-variant bg-surface-container-low px-3 py-2 font-mono text-[10px] tracking-[0.08em] text-on-surface-variant uppercase">
        <span />
        <span />
        <span>SKU</span>
        <span className="text-right">Qty</span>
        <span>State</span>
      </div>
      <div className="max-h-[calc(100vh-15rem)] overflow-y-auto">
        {tasks.map((task) => (
          <SplitTaskRow
            isActive={task.id === activeId}
            isSelected={selected.has(task.id)}
            key={task.id}
            onActivate={() => onActivate(task.id)}
            onToggleSelection={() => onToggleSelection(task.id)}
            task={task}
          />
        ))}
      </div>
    </div>
  )
}

function SplitTaskRow({
  isActive,
  isSelected,
  onActivate,
  onToggleSelection,
  task,
}: {
  isActive: boolean
  isSelected: boolean
  onActivate: () => void
  onToggleSelection: () => void
  task: Task
}) {
  const isShort =
    task.actualQuantity != null && task.actualQuantity < task.requestedQuantity
  const isDone = task.state === "Completed" || task.state === "Cancelled"

  return (
    // role="button" instead of `<button>` so the nested checkbox doesn't
    // violate the HTML spec (interactive elements can't nest inside buttons).
    // Activation handlers handle Enter/Space explicitly.
    <div
      aria-pressed={isActive}
      className={cn(
        "grid w-full cursor-pointer grid-cols-[28px_28px_minmax(0,1fr)_72px_auto] items-center gap-2 border-b border-outline-variant/40 px-3 py-2 text-left transition-colors last:border-b-0 hover:bg-surface-container-low focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-ring",
        isActive && "bg-primary/[0.06] shadow-[inset_3px_0_0_0] shadow-primary",
        isSelected && "bg-primary/[0.04]",
        isDone && "opacity-60"
      )}
      data-row-id={task.id}
      onClick={onActivate}
      onKeyDown={(event) => {
        if (event.key === "Enter") {
          event.preventDefault()
          onActivate()
        } else if (event.key === " ") {
          event.preventDefault()
          onToggleSelection()
        }
      }}
      role="button"
      tabIndex={0}
    >
      <span className="flex items-center justify-center">
        <input
          aria-label="Select task"
          checked={isSelected}
          className="size-3.5 accent-primary"
          onChange={onToggleSelection}
          onClick={(event) => event.stopPropagation()}
          type="checkbox"
        />
      </span>
      <TaskTypeChip size="md" type={task.taskType} />
      <span className="flex min-w-0 flex-col gap-0">
        <span className="truncate text-sm">{task.skuName ?? task.skuId}</span>
        <span className="truncate font-mono text-[11px] text-on-surface-variant">
          {task.id} · {task.skuId}
        </span>
      </span>
      <span
        className={cn(
          "text-right font-mono text-[13px] tabular-nums",
          isShort && "font-semibold text-state-cancelled"
        )}
      >
        {task.actualQuantity != null
          ? `${task.actualQuantity}/${task.requestedQuantity}`
          : task.requestedQuantity}
      </span>
      <StateBadge state={task.state} />
    </div>
  )
}

// ---------------------------------------------------------------------------
// BulkActionBar — floating pill at the bottom of the split view when one or
// more tasks are checked.
// ---------------------------------------------------------------------------

function BulkActionBar({
  count,
  onAction,
  onClear,
}: {
  count: number
  onAction: (label: string) => void
  onClear: () => void
}) {
  return (
    // Toolbar landmark for the bulk-action region. The live count is exposed
    // via a separate sr-only span so the toolbar's button labels aren't
    // re-announced on every selection toggle.
    <div
      aria-label="Bulk actions"
      className="fixed bottom-6 left-1/2 z-40 flex -translate-x-1/2 items-center gap-2 rounded-full bg-foreground px-3 py-1.5 text-background shadow-2xl"
      role="toolbar"
    >
      <span
        aria-hidden="true"
        className="font-mono text-sm font-semibold tabular-nums"
      >
        {count}
      </span>
      <span aria-hidden="true" className="text-label-m text-background/70">
        selected
      </span>
      <span aria-atomic="true" aria-live="polite" className="sr-only">
        {count} task{count === 1 ? "" : "s"} selected
      </span>
      <span aria-hidden="true" className="h-5 w-px bg-background/20" />
      <BulkActionButton label="Assign" onClick={() => onAction("Assigned")} />
      <BulkActionButton
        label="Allocate"
        onClick={() => onAction("Allocated")}
      />
      <BulkActionButton
        label="Cancel"
        onClick={() => onAction("Cancelled")}
        tone="danger"
      />
      <span aria-hidden="true" className="h-5 w-px bg-background/20" />
      <BulkActionButton label="Clear" onClick={onClear} tone="ghost" />
    </div>
  )
}

function BulkActionButton({
  label,
  onClick,
  tone = "default",
}: {
  label: string
  onClick: () => void
  tone?: "default" | "danger" | "ghost"
}) {
  return (
    <button
      className={cn(
        "text-label-m rounded-full px-3 py-1 font-medium transition-colors",
        tone === "default" && "bg-background/10 hover:bg-background/20",
        tone === "danger" &&
          "bg-background/10 hover:bg-state-cancelled hover:text-background",
        tone === "ghost" && "hover:bg-background/15"
      )}
      onClick={onClick}
      type="button"
    >
      {label}
    </button>
  )
}
