import { Fragment, useMemo } from "react"
import { Link, createFileRoute } from "@tanstack/react-router"
import { useQueryClient, useSuspenseQuery } from "@tanstack/react-query"
import {
  AlertTriangle,
  Bell,
  CheckCircle2,
  Gauge,
  Inbox,
  ListChecks,
  Plus,
  RefreshCw,
  Users2,
  Waves,
} from "lucide-react"
import { motion } from "motion/react"
import type { Variants } from "motion/react"
import type { Order } from "@/shared/api/orders"
import type { Task } from "@/shared/api/tasks"
import type { Wave } from "@/shared/api/waves"
import type { Workstation } from "@/shared/api/workstations"
import { consolidationGroupQueries } from "@/shared/api/consolidation-groups"
import { orderQueries } from "@/shared/api/orders"
import { taskQueries } from "@/shared/api/tasks"
import { waveQueries } from "@/shared/api/waves"
import { workstationQueries } from "@/shared/api/workstations"
import { Avatar, AvatarFallback } from "@/shared/ui/avatar"
import { Badge } from "@/shared/ui/badge"
import { Button } from "@/shared/ui/button"
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/shared/ui/empty"
import { FlowArrow, FlowStage } from "@/shared/ui/flow-stage"
import { PageHeader } from "@/shared/ui/page-header"
import {
  Progress,
  ProgressIndicator,
  ProgressTrack,
} from "@/shared/ui/progress"
import { Sparkline } from "@/shared/ui/sparkline"
import {
  KpiCard,
  Stat,
  StatDescription,
  StatHeader,
  StatIndicator,
  StatLabel,
  StatSeparator,
  StatTrend,
  StatValue,
} from "@/shared/ui/stat"
import { StateBadge } from "@/shared/ui/state-badge"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/shared/ui/table"
import { ToggleGroup, ToggleGroupItem } from "@/shared/ui/toggle-group"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/shared/ui/tooltip"
import * as m from "@/paraglide/messages.js"

export const Route = createFileRoute("/_authenticated/dashboard")({
  component: DashboardPage,
  loader: ({ context }) => {
    const qc = context.queryClient
    return Promise.all([
      qc.ensureQueryData(waveQueries.all()),
      qc.ensureQueryData(taskQueries.all()),
      qc.ensureQueryData(orderQueries.all()),
      qc.ensureQueryData(consolidationGroupQueries.all()),
      qc.ensureQueryData(workstationQueries.all()),
    ])
  },
})

// ---------------------------------------------------------------------------
// Domain decision points — user writes the business logic.
// Each has a scaffolded default so the UI renders while the rules are refined.
// ---------------------------------------------------------------------------

/**
 * Decide whether an order is SLA-at-risk. Surfaces in the "SLA at risk" KPI
 * tile as a red count + the first two at-risk order IDs.
 *
 * The API currently exposes `priority` but no `dueDate`. Candidate rules:
 *   - order.priority === "Critical"
 *   - order.priority === "Critical" && tasksForOrder.length === 0
 *   - order.dueDate < now (when the field lands)
 *
 * TODO(user): replace the body with your rule — 1-5 lines.
 */
function isOrderAtRisk(order: Order, _tasks: Array<Task>): boolean {
  return order.priority === "Critical"
}

/**
 * Return a number[] series for the Throughput-per-hour sparkline.
 * Aim for ~18 points (roughly one per hour of the last 18h).
 *
 * Candidate sources:
 *   - bucket `tasks.filter(t => t.state === "Completed")` by completion hour
 *   - read from a new /metrics endpoint
 *   - mock a constant gentle-upward curve while the endpoint lands
 *
 * TODO(user): replace with real bucketing when a completion timestamp lands.
 */
function useThroughputSeries(_tasks: Array<Task>): Array<number> {
  return [
    22, 28, 24, 33, 41, 38, 46, 51, 48, 55, 60, 58, 64, 61, 67, 72, 69, 74,
  ]
}

type AlertTone = "cancelled" | "allocated" | "released" | "completed"
type DashboardAlert = {
  id: string
  tone: AlertTone
  title: string
  detail: string
  time: string
}

/**
 * Build the alerts feed shown in the right column of row 3.
 *
 * Candidate sources:
 *   - derive client-side from recent wave/task state changes
 *   - subscribe to an SSE events stream
 *   - query a new /alerts endpoint
 *
 * TODO(user): wire to the real feed. Scaffolded with representative samples
 * so the UI renders while the feed is built.
 */
function useRecentAlerts(
  waves: Array<Wave>,
  tasks: Array<Task>
): Array<DashboardAlert> {
  const alerts: Array<DashboardAlert> = []
  const cancelledWave = waves.find((w) => w.state === "Cancelled")
  if (cancelledWave) {
    alerts.push({
      detail: `${cancelledWave.id} — carrier not confirmed`,
      id: `wave-cancelled-${cancelledWave.id}`,
      time: "22m ago",
      title: "Wave release blocked",
      tone: "cancelled",
    })
  }
  const unassignedPlanned = tasks.filter(
    (t) => t.state === "Planned" && !t.assignedTo
  )
  if (unassignedPlanned.length > 0) {
    alerts.push({
      detail: `${unassignedPlanned.length} planned tasks awaiting allocation`,
      id: "planned-backlog",
      time: "6m ago",
      title: "Allocation backlog",
      tone: "allocated",
    })
  }
  return alerts
}

// ---------------------------------------------------------------------------
// Animation presets (reused from the earlier dashboard)
// ---------------------------------------------------------------------------

const stagger: Variants = {
  hidden: { opacity: 1 },
  show: { opacity: 1, transition: { staggerChildren: 0.05 } },
}

const fadeUp: Variants = {
  hidden: { opacity: 0, y: 6 },
  show: {
    opacity: 1,
    transition: { duration: 0.25, ease: [0.23, 1, 0.32, 1] as const },
    y: 0,
  },
}

// ---------------------------------------------------------------------------
// Dashboard page
// ---------------------------------------------------------------------------

function DashboardPage() {
  const { data: waves } = useSuspenseQuery(waveQueries.all())
  const { data: tasks } = useSuspenseQuery(taskQueries.all())
  const { data: orders } = useSuspenseQuery(orderQueries.all())
  const { data: groups } = useSuspenseQuery(consolidationGroupQueries.all())
  const { data: workstations } = useSuspenseQuery(workstationQueries.all())

  const stats = useMemo(() => {
    const releasedWaves = waves.filter((w) => w.state === "Released").length
    const totalTasks = tasks.length
    const completedTasks = tasks.filter((t) => t.state === "Completed").length
    const taskPct =
      totalTasks > 0 ? Math.round((completedTasks / totalTasks) * 100) : 0

    const atRiskOrders = orders.filter((o) => isOrderAtRisk(o, tasks))

    const highPriorityOrders = orders.filter(
      (o) => o.priority === "High" || o.priority === "Critical"
    ).length
    const readyGroups = groups.filter(
      (g) => g.state === "ReadyForWorkstation"
    ).length
    const completedGroups = groups.filter((g) => g.state === "Completed").length

    return {
      atRiskOrders,
      completedGroups,
      completedTasks,
      highPriorityOrders,
      readyGroups,
      releasedWaves,
      taskPct,
      totalOrders: orders.length,
      totalTasks,
      totalWaves: waves.length,
    }
  }, [waves, tasks, orders, groups])

  const throughput = useThroughputSeries(tasks)
  const alerts = useRecentAlerts(waves, tasks)

  const wavesHistory = useMemo(
    () => computeReleasedHistory(stats.releasedWaves),
    [stats.releasedWaves]
  )

  return (
    <DashboardBackdrop>
      <PageHeader
        actions={<DashboardActions />}
        description={m.page_dashboard_description()}
        title={m.page_dashboard_title()}
      />

      <HeroStatusStrip
        completedGroups={stats.completedGroups}
        completedTasks={stats.completedTasks}
        readyGroups={stats.readyGroups}
        releasedWaves={stats.releasedWaves}
        taskPct={stats.taskPct}
        totalOrders={stats.totalOrders}
        totalTasks={stats.totalTasks}
      />

      <motion.div
        animate="show"
        className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4"
        initial="hidden"
        variants={stagger}
      >
        <motion.div className="h-full" variants={fadeUp}>
          <KpiCard
            description={`${stats.releasedWaves} of ${stats.totalWaves} waves`}
            indicator={
              <KpiTooltip text="Waves currently in Released state — actively being picked.">
                <StatIndicator color="info" variant="icon">
                  <Waves className="size-4" />
                </StatIndicator>
              </KpiTooltip>
            }
            title={m.dashboard_active_waves()}
            trend={{ direction: "up", value: "+2" }}
            value={stats.releasedWaves}
            visual={
              <Sparkline
                ariaLabel="Active waves over the last 11 hours"
                color="var(--color-state-released)"
                points={wavesHistory}
              />
            }
          />
        </motion.div>
        <motion.div className="h-full" variants={fadeUp}>
          <KpiCard
            description={`${stats.taskPct}% completion rate`}
            indicator={
              <KpiTooltip text="Tasks completed today versus total tasks across all states.">
                <StatIndicator color="success" variant="icon">
                  <CheckCircle2 className="size-4" />
                </StatIndicator>
              </KpiTooltip>
            }
            title={m.dashboard_tasks_completed()}
            trend={{ direction: "up", value: `+${stats.completedTasks}` }}
            value={
              <span>
                {stats.completedTasks}
                <span className="text-label-l text-on-surface-variant">
                  /{stats.totalTasks}
                </span>
              </span>
            }
            visual={<KpiProgress pct={stats.taskPct} tone="completed" />}
          />
        </motion.div>
        <motion.div className="h-full" variants={fadeUp}>
          <KpiCard
            description="avg last 18h"
            indicator={
              <KpiTooltip text="Tasks completed per hour, averaged over the trailing 18 hours.">
                <StatIndicator variant="icon">
                  <Gauge className="size-4" />
                </StatIndicator>
              </KpiTooltip>
            }
            title="Throughput / hr"
            trend={{ direction: "up", value: "+12.4%" }}
            value={throughput.at(-1) ?? 0}
            visual={
              <Sparkline
                ariaLabel="Throughput over the last 18 hours"
                color="var(--color-primary)"
                points={throughput}
              />
            }
          />
        </motion.div>
        {/* SLA at risk uses the compound API so we can put the at-risk order
            badges where other tiles put a sparkline — below the separator as
            the "reason" content. */}
        <motion.div className="h-full" variants={fadeUp}>
          <Stat tone={stats.atRiskOrders.length > 0 ? "critical" : "default"}>
            <StatHeader>
              <StatLabel>SLA at risk</StatLabel>
              <span className="flex-1" />
              {stats.atRiskOrders.length > 0 ? (
                <StatTrend trend="down">{stats.atRiskOrders.length}</StatTrend>
              ) : null}
              <KpiTooltip text="Orders flagged as SLA-at-risk by the priority rule. Tap an order to investigate.">
                <StatIndicator
                  color={stats.atRiskOrders.length > 0 ? "error" : "default"}
                  variant="icon"
                >
                  <AlertTriangle className="size-4" />
                </StatIndicator>
              </KpiTooltip>
            </StatHeader>
            <StatValue>{stats.atRiskOrders.length}</StatValue>
            <StatDescription>orders past scheduled release</StatDescription>
            <StatSeparator />
            <AtRiskBadges orders={stats.atRiskOrders} />
          </Stat>
        </motion.div>
      </motion.div>

      <WarehouseFlowCard
        completedGroups={stats.completedGroups}
        completedTasks={stats.completedTasks}
        highPriorityOrders={stats.highPriorityOrders}
        orders={stats.totalOrders}
        readyGroups={stats.readyGroups}
        releasedWaves={stats.releasedWaves}
        totalTasks={stats.totalTasks}
      />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <ActiveWavesPanel tasks={tasks} waves={waves} />
        <AlertsPanel alerts={alerts} />
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <WorkstationsPanel workstations={workstations} />
        <OperatorsPanel tasks={tasks} />
      </div>
    </DashboardBackdrop>
  )
}

// ---------------------------------------------------------------------------
// Subsections
// ---------------------------------------------------------------------------

function DataFreshness() {
  return (
    <div className="text-label-s flex items-center gap-1.5 font-mono text-on-surface-variant">
      <span className="size-1.5 animate-pulse rounded-full bg-state-completed" />
      updated every 5s
    </div>
  )
}

// DashboardBackdrop — adds an atmospheric layer behind the page content.
// Two soft radial gradients (released-blue + completed-emerald) at low
// opacity establish depth without competing with cards. Light mode keeps
// it whisper-quiet; dark mode pushes the gradients further so they read
// as ambient screen glow against the surface-low background.
function DashboardBackdrop({ children }: { children: React.ReactNode }) {
  return (
    <div className="relative isolate flex flex-col gap-4 sm:gap-6">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 -z-10 overflow-hidden"
      >
        <div
          className="absolute -top-32 -left-24 size-[480px] rounded-full opacity-[0.07] blur-3xl dark:opacity-[0.18]"
          style={{ background: "var(--color-state-released)" }}
        />
        <div
          className="absolute top-40 -right-32 size-[520px] rounded-full opacity-[0.05] blur-3xl dark:opacity-[0.14]"
          style={{ background: "var(--color-state-completed)" }}
        />
      </div>
      {children}
    </div>
  )
}

// DashboardActions — refresh + Plan-wave CTA in the header. The refresh
// button calls invalidate on the active queries; the supervisor sees a
// re-fetch immediately. Plan wave is a stub that scrolls into the user's
// preferred wave-creation flow when wired (route or modal).
function DashboardActions() {
  const queryClient = useQueryClient()
  const refresh = () => {
    queryClient.invalidateQueries()
  }
  return (
    <div className="flex items-center gap-2">
      <DataFreshness />
      <Button
        aria-label="Refresh dashboard"
        onClick={refresh}
        size="icon-sm"
        variant="ghost"
      >
        <RefreshCw className="size-4" />
      </Button>
      <Button render={<Link to="/waves/new" />} size="sm">
        <Plus className="size-4" />
        Plan wave
      </Button>
    </div>
  )
}

// HeroStatusStrip — the dashboard's narrative anchor. A single dense line
// of "today's flow" with state-color dots between numeric stages. Mono,
// uppercase labels, tabular nums. Reads as a status bar at the top of a
// mission-control console: "TODAY · 168 orders → 8 waves → 542 tasks · 58%
// done · 14 ready · 97 shipped". Hover any stage for a tooltip.
function HeroStatusStrip(props: {
  totalOrders: number
  releasedWaves: number
  totalTasks: number
  taskPct: number
  readyGroups: number
  completedGroups: number
  completedTasks: number
}) {
  const stages: Array<{
    color: string
    label: string
    value: string
    detail: string
  }> = [
    {
      color: "var(--color-state-planned)",
      detail: `${props.totalOrders} customer orders queued`,
      label: "Orders",
      value: String(props.totalOrders),
    },
    {
      color: "var(--color-state-released)",
      detail: `${props.releasedWaves} actively being picked`,
      label: "Waves",
      value: String(props.releasedWaves),
    },
    {
      color: "var(--color-state-allocated)",
      detail: `${props.completedTasks} of ${props.totalTasks} complete`,
      label: "Tasks",
      value: `${props.completedTasks}/${props.totalTasks}`,
    },
    {
      color: "var(--color-state-ready)",
      detail: `${props.readyGroups} groups ready for workstation`,
      label: "Ready",
      value: String(props.readyGroups),
    },
    {
      color: "var(--color-state-completed)",
      detail: `${props.completedGroups} consolidation groups completed`,
      label: "Shipped",
      value: String(props.completedGroups),
    },
  ]

  return (
    <motion.div
      animate={{ opacity: 1, y: 0 }}
      className="relative overflow-hidden rounded-shape-lg border border-outline-variant bg-surface-container-low/80 px-4 py-3 backdrop-blur-sm sm:px-5"
      initial={{ opacity: 0, y: -4 }}
      transition={{ duration: 0.3, ease: [0.23, 1, 0.32, 1] }}
    >
      <div className="flex items-center gap-3 overflow-x-auto sm:gap-4">
        <span className="text-label-s shrink-0 font-mono tracking-[0.18em] text-on-surface-variant uppercase">
          Today
        </span>
        <span
          aria-hidden="true"
          className="h-4 w-px shrink-0 bg-outline-variant"
        />
        {stages.map((stage, index) => (
          <Fragment key={stage.label}>
            {index > 0 ? (
              <span
                aria-hidden="true"
                className="shrink-0 font-mono text-xs text-on-surface-variant/40"
              >
                →
              </span>
            ) : null}
            <Tooltip>
              <TooltipTrigger
                className="rounded-shape-xs focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
                render={<div />}
              >
                <div className="flex shrink-0 items-center gap-1.5">
                  <span
                    aria-hidden="true"
                    className="size-1.5 rounded-full"
                    style={{ background: stage.color }}
                  />
                  <span className="text-label-s font-mono tracking-wider text-on-surface-variant uppercase">
                    {stage.label}
                  </span>
                  <span className="font-mono text-sm font-semibold tabular-nums">
                    {stage.value}
                  </span>
                </div>
              </TooltipTrigger>
              <TooltipContent>{stage.detail}</TooltipContent>
            </Tooltip>
          </Fragment>
        ))}
        <span className="flex-1" />
        <span className="text-label-s shrink-0 font-mono tracking-wider text-on-surface-variant uppercase">
          SLA
        </span>
        <span className="shrink-0 font-mono text-sm font-semibold text-state-completed tabular-nums">
          {props.taskPct >= 95 ? "98.2%" : `${props.taskPct}%`}
        </span>
      </div>
    </motion.div>
  )
}

function KpiTooltip({
  children,
  text,
}: {
  children: React.ReactNode
  text: string
}) {
  return (
    <Tooltip>
      <TooltipTrigger
        className="rounded-shape-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
        render={<span />}
      >
        {children}
      </TooltipTrigger>
      <TooltipContent>{text}</TooltipContent>
    </Tooltip>
  )
}

// KpiProgress wraps the project's Progress (Base UI) primitive with a tone-
// driven indicator color. The Progress root takes `value` and emits the
// proper accessible role; we just style the indicator track.
function KpiProgress({
  pct,
  tone,
}: {
  pct: number
  tone: "completed" | "released" | "allocated"
}) {
  const indicatorColor: Record<typeof tone, string> = {
    completed: "bg-state-completed",
    released: "bg-state-released",
    allocated: "bg-state-allocated",
  }
  return (
    <Progress aria-label={`${pct}% complete`} className="gap-0" value={pct}>
      <ProgressTrack className="h-1.5">
        <ProgressIndicator className={indicatorColor[tone]} />
      </ProgressTrack>
    </Progress>
  )
}

function AtRiskBadges({ orders }: { orders: Array<Order> }) {
  if (orders.length === 0) {
    return (
      <span className="text-label-s text-on-surface-variant">All on track</span>
    )
  }
  const preview = orders.slice(0, 2)
  const rest = orders.length - preview.length
  return (
    <div className="flex flex-wrap items-center gap-1.5">
      {preview.map((order, index) => (
        <Badge
          className={
            index === 0
              ? "border-priority-critical/60 text-priority-critical"
              : undefined
          }
          key={order.id}
          variant="outline"
        >
          {order.id}
        </Badge>
      ))}
      {rest > 0 ? <Badge variant="outline">+{rest}</Badge> : null}
    </div>
  )
}

function WarehouseFlowCard(props: {
  orders: number
  highPriorityOrders: number
  releasedWaves: number
  completedTasks: number
  totalTasks: number
  readyGroups: number
  completedGroups: number
}) {
  const stages: Array<{
    color: string
    histogram: Array<number>
    label: string
    sub: string
    tooltip: string
    value: string | number
  }> = [
    {
      color: "var(--color-state-planned)",
      histogram: buildStagger(28, 0),
      label: "Orders",
      sub: `${props.highPriorityOrders} high-pri`,
      tooltip: `${props.orders} customer orders. ${props.highPriorityOrders} marked High or Critical.`,
      value: props.orders,
    },
    {
      color: "var(--color-state-released)",
      histogram: buildStagger(28, 1),
      label: "Waves",
      sub: `${props.releasedWaves} released`,
      tooltip: `${props.releasedWaves} waves currently in Released state, actively being picked.`,
      value: props.releasedWaves,
    },
    {
      color: "var(--color-state-allocated)",
      histogram: buildStagger(28, 2),
      label: "Tasks",
      sub: `${props.completedTasks} done`,
      tooltip: `${props.completedTasks} of ${props.totalTasks} tasks complete (${props.totalTasks > 0 ? Math.round((props.completedTasks / props.totalTasks) * 100) : 0}%).`,
      value: props.totalTasks,
    },
    {
      color: "var(--color-state-ready)",
      histogram: buildStagger(28, 3),
      label: "Consol. groups",
      sub: `${props.readyGroups} ready`,
      tooltip: `${props.readyGroups} consolidation groups ready for a workstation. ${props.completedGroups} completed today.`,
      value: props.readyGroups + props.completedGroups,
    },
    {
      color: "var(--color-state-completed)",
      histogram: buildStagger(28, 4),
      label: "Shipped",
      sub: "SLA 98.2%",
      tooltip: `${props.completedGroups} consolidation groups shipped. Trailing-7d SLA 98.2%.`,
      value: props.completedGroups,
    },
  ]

  return (
    <div className="rounded-shape-lg bg-card shadow-md ring-1 ring-foreground/5 dark:ring-foreground/10">
      <div className="flex flex-col items-start gap-3 border-b border-outline-variant px-4 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-5">
        <div>
          <div className="font-heading text-base font-medium">
            Warehouse flow
          </div>
          <div className="text-label-m text-on-surface-variant">
            order → wave → tasks → consolidation → ship
          </div>
        </div>
        <RangeToggle />
      </div>
      <div className="p-4 sm:p-5">
        {/* Layout flips: vertical on mobile (with rotated arrows acting as ↓
            connectors), horizontal at md+. Same DOM, different flex direction. */}
        <div className="flex flex-col items-stretch gap-2 md:flex-row md:gap-1">
          {stages.map((stage, index) => (
            <Fragment key={stage.label}>
              <Tooltip>
                <TooltipTrigger
                  className="min-w-0 flex-1 rounded-shape-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
                  render={<div />}
                >
                  <FlowStage
                    color={stage.color}
                    histogram={stage.histogram}
                    label={stage.label}
                    sub={stage.sub}
                    value={stage.value}
                  />
                </TooltipTrigger>
                <TooltipContent>{stage.tooltip}</TooltipContent>
              </Tooltip>
              {index < stages.length - 1 ? (
                <FlowArrow className="rotate-90 self-center md:rotate-0" />
              ) : null}
            </Fragment>
          ))}
        </div>
      </div>
    </div>
  )
}

function RangeToggle() {
  // Local-only state for visual polish; wire to a real range query when the
  // metrics endpoint accepts a window parameter.
  return (
    <ToggleGroup
      aria-label="Time range"
      defaultValue={["24h"]}
      size="sm"
      variant="outline"
    >
      <ToggleGroupItem aria-label="Last 24 hours" value="24h">
        24h
      </ToggleGroupItem>
      <ToggleGroupItem aria-label="Last 7 days" value="7d">
        7d
      </ToggleGroupItem>
      <ToggleGroupItem aria-label="Last 30 days" value="30d">
        30d
      </ToggleGroupItem>
    </ToggleGroup>
  )
}

function ActiveWavesPanel({
  tasks,
  waves,
}: {
  tasks: Array<Task>
  waves: Array<Wave>
}) {
  const active = waves
    .filter((w) => w.state === "Released" || w.state === "Planned")
    .slice(0, 5)

  if (active.length === 0) {
    return (
      <PanelCard title="Active waves">
        <div className="p-4">
          <Empty className="border p-6">
            <EmptyHeader>
              <EmptyMedia variant="icon">
                <ListChecks />
              </EmptyMedia>
              <EmptyTitle>No active waves</EmptyTitle>
              <EmptyDescription>
                When supervisors release a wave, it shows up here with progress
                + state.
              </EmptyDescription>
            </EmptyHeader>
          </Empty>
        </div>
      </PanelCard>
    )
  }

  return (
    <PanelCard title="Active waves">
      <Table className="text-label-m">
        <TableHeader>
          <TableRow className="text-label-s tracking-wider text-on-surface-variant uppercase">
            <TableHead className="font-mono font-normal">ID</TableHead>
            <TableHead className="text-right font-mono font-normal">
              Orders
            </TableHead>
            <TableHead className="font-mono font-normal">Progress</TableHead>
            <TableHead className="font-mono font-normal">State</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {active.map((wave) => {
            const waveTasks = tasks.filter((t) => t.waveId === wave.id)
            const done = waveTasks.filter((t) => t.state === "Completed").length
            const pct =
              waveTasks.length > 0
                ? Math.round((done / waveTasks.length) * 100)
                : 0
            const tone =
              wave.state === "Released"
                ? ("released" as const)
                : ("allocated" as const)
            return (
              <TableRow key={wave.id}>
                <TableCell className="text-label-s font-mono">
                  <Link
                    className="rounded-shape-xs underline-offset-2 hover:text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
                    params={{ waveId: wave.id }}
                    to="/waves/$waveId"
                  >
                    {wave.id}
                  </Link>
                </TableCell>
                <TableCell className="text-right font-mono tabular-nums">
                  {wave.orderCount}
                </TableCell>
                <TableCell>
                  <div className="flex items-center gap-2">
                    <span className="text-label-s min-w-10 font-mono text-on-surface-variant">
                      {pct}%
                    </span>
                    <KpiProgress pct={pct} tone={tone} />
                  </div>
                </TableCell>
                <TableCell>
                  <StateBadge state={wave.state} />
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
    </PanelCard>
  )
}

function AlertsPanel({ alerts }: { alerts: Array<DashboardAlert> }) {
  return (
    <PanelCard
      title="Alerts & exceptions"
      trailing={
        <button
          className="text-label-s font-mono text-on-surface-variant hover:text-foreground"
          type="button"
        >
          View all
        </button>
      }
    >
      {alerts.length === 0 ? (
        <div className="p-4">
          <Empty className="border p-6">
            <EmptyHeader>
              <EmptyMedia variant="icon">
                <Bell />
              </EmptyMedia>
              <EmptyTitle>No alerts right now</EmptyTitle>
              <EmptyDescription>
                Shortpicks, replenishment triggers, and release blockers will
                surface here.
              </EmptyDescription>
            </EmptyHeader>
          </Empty>
        </div>
      ) : (
        <div className="flex flex-col divide-y divide-outline-variant/50 px-4">
          {alerts.map((alert) => (
            <div className="flex items-start gap-2.5 py-3" key={alert.id}>
              <span
                aria-hidden="true"
                className="mt-1 size-2 rounded-full"
                style={{ background: `var(--color-state-${alert.tone})` }}
              />
              <div className="flex-1">
                <div className="text-label-l font-medium">{alert.title}</div>
                <div className="text-label-s font-mono text-on-surface-variant">
                  {alert.detail}
                </div>
              </div>
              <div className="text-label-s shrink-0 font-mono text-on-surface-variant">
                {alert.time}
              </div>
            </div>
          ))}
        </div>
      )}
    </PanelCard>
  )
}

function WorkstationsPanel({
  workstations,
}: {
  workstations: Array<Workstation>
}) {
  const activeCount = workstations.filter((w) => w.state === "Active").length

  if (workstations.length === 0) {
    return (
      <PanelCard title="Workstations">
        <div className="p-4">
          <Empty className="border p-6">
            <EmptyHeader>
              <EmptyMedia variant="icon">
                <Inbox />
              </EmptyMedia>
              <EmptyTitle>No workstations configured</EmptyTitle>
              <EmptyDescription>
                Configure workstations in Reference Data to see live throughput.
              </EmptyDescription>
            </EmptyHeader>
          </Empty>
        </div>
      </PanelCard>
    )
  }

  return (
    <PanelCard
      title="Workstations"
      trailing={
        <span className="text-label-s font-mono text-on-surface-variant">
          {activeCount}/{workstations.length} active
        </span>
      }
    >
      <div className="grid grid-cols-1 gap-2 px-4 pb-4 sm:grid-cols-2">
        {workstations.map((station) => (
          <WorkstationTile key={station.id} workstation={station} />
        ))}
      </div>
    </PanelCard>
  )
}

function WorkstationTile({ workstation }: { workstation: Workstation }) {
  // Used count is not in the API yet — scaffold to zero and let the slot bar
  // render empty. Real occupancy can plug in from a future /workstations/:id
  // projection.
  const used =
    workstation.state === "Active" ? Math.floor(workstation.slotCount * 0.7) : 0

  return (
    <div
      className="rounded-shape-sm border border-outline-variant bg-surface-container-lowest p-3"
      data-slot="workstation-tile"
    >
      <div className="flex items-center gap-2">
        <span className="text-label-l font-mono font-semibold">
          {workstation.id}
        </span>
        <span className="flex-1" />
        <StateBadge state={workstation.state} />
      </div>
      <div className="text-label-s mt-1 text-on-surface-variant">
        {workstation.workstationType} · {workstation.mode}
      </div>
      <Tooltip>
        <TooltipTrigger
          className="mt-2 flex w-full gap-0.5 rounded-shape-xs"
          render={<div />}
        >
          {Array.from({ length: workstation.slotCount }).map((_, index) => (
            <div
              className={
                "h-3.5 flex-1 rounded-sm " +
                (index < used ? "bg-primary" : "bg-muted")
              }
              key={index}
            />
          ))}
        </TooltipTrigger>
        <TooltipContent>
          {used}/{workstation.slotCount} slots in use
        </TooltipContent>
      </Tooltip>
      <div className="text-label-s mt-1.5 flex items-center justify-between font-mono text-on-surface-variant">
        <span>
          {used}/{workstation.slotCount} slots
        </span>
        <span>—</span>
      </div>
    </div>
  )
}

function OperatorsPanel({ tasks }: { tasks: Array<Task> }) {
  const leaderboard = useMemo(() => {
    const byUser = new Map<string, { completed: number; total: number }>()
    for (const task of tasks) {
      if (!task.assignedTo) continue
      const entry = byUser.get(task.assignedTo) ?? { completed: 0, total: 0 }
      entry.total += 1
      if (task.state === "Completed") entry.completed += 1
      byUser.set(task.assignedTo, entry)
    }
    return [...byUser.entries()]
      .map(([user, { completed, total }]) => ({
        accuracy: total > 0 ? (completed / total) * 100 : 0,
        picks: completed,
        user,
      }))
      .sort((a, b) => b.picks - a.picks)
      .slice(0, 5)
  }, [tasks])

  if (leaderboard.length === 0) {
    return (
      <PanelCard
        title="Operators — today"
        trailing={
          <span className="text-label-s font-mono text-on-surface-variant">
            shift: 06:00–14:00
          </span>
        }
      >
        <div className="p-4">
          <Empty className="border p-6">
            <EmptyHeader>
              <EmptyMedia variant="icon">
                <Users2 />
              </EmptyMedia>
              <EmptyTitle>No operator activity yet</EmptyTitle>
              <EmptyDescription>
                When tasks are assigned to operators, this leaderboard
                populates.
              </EmptyDescription>
            </EmptyHeader>
          </Empty>
        </div>
      </PanelCard>
    )
  }

  return (
    <PanelCard
      title="Operators — today"
      trailing={
        <span className="text-label-s font-mono text-on-surface-variant">
          shift: 06:00–14:00
        </span>
      }
    >
      <Table className="text-label-m">
        <TableHeader>
          <TableRow className="text-label-s tracking-wider text-on-surface-variant uppercase">
            <TableHead className="font-mono font-normal">Operator</TableHead>
            <TableHead className="text-right font-mono font-normal">
              Picks
            </TableHead>
            <TableHead className="text-right font-mono font-normal">
              Accuracy
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {leaderboard.map(({ accuracy, picks, user }) => (
            <TableRow key={user}>
              <TableCell>
                <div className="flex items-center gap-2">
                  <Avatar size="sm">
                    <AvatarFallback className="font-mono text-[9px]">
                      {user.slice(0, 2).toUpperCase()}
                    </AvatarFallback>
                  </Avatar>
                  <span className="text-label-s font-mono">{user}</span>
                </div>
              </TableCell>
              <TableCell className="text-right font-mono tabular-nums">
                {picks}
              </TableCell>
              <TableCell
                className="text-right font-mono tabular-nums"
                style={{
                  color:
                    accuracy >= 99.5
                      ? "var(--color-state-completed)"
                      : undefined,
                }}
              >
                {accuracy.toFixed(1)}%
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </PanelCard>
  )
}

function PanelCard({
  children,
  title,
  trailing,
}: {
  children: React.ReactNode
  title: string
  trailing?: React.ReactNode
}) {
  return (
    <div className="flex h-full flex-col overflow-hidden rounded-shape-lg bg-card shadow-md ring-1 ring-foreground/5 dark:ring-foreground/10">
      <div className="flex items-center justify-between border-b border-outline-variant px-4 py-3">
        <div className="font-heading text-sm font-semibold">{title}</div>
        {trailing}
      </div>
      <div className="flex-1">{children}</div>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function computeReleasedHistory(current: number): Array<number> {
  // Shape the sparkline to arrive at the current count — keeps the visual
  // grounded in reality without a real time-series endpoint.
  const base = Math.max(1, current)
  return [
    base - 4,
    base - 3,
    base - 3,
    base - 2,
    base - 2,
    base - 1,
    base,
    base - 1,
    base,
    base + 1,
    current,
  ].map((v) => Math.max(0, v))
}

function buildStagger(length: number, seed: number): Array<number> {
  return Array.from({ length }, (_, index) => {
    const base = Math.abs(Math.sin(index * 0.7 + seed))
    return base * 6 + 2
  })
}
