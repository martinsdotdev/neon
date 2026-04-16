import { useMemo } from "react"
import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Label,
  Pie,
  PieChart,
  XAxis,
  YAxis,
} from "recharts"
import type { LucideIcon } from "lucide-react"
import {
  CheckCircle2,
  Layers,
  ShoppingCart,
  Waves,
} from "lucide-react"
import { motion } from "motion/react"
import { waveQueries } from "@/shared/api/waves"
import { taskQueries } from "@/shared/api/tasks"
import { orderQueries } from "@/shared/api/orders"
import { consolidationGroupQueries } from "@/shared/api/consolidation-groups"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/shared/ui/card"
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  ChartLegend,
  ChartLegendContent,
  type ChartConfig,
} from "@/shared/ui/chart"
import { PageHeader } from "@/shared/ui/page-header"
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
    ])
  },
})

// ---------------------------------------------------------------------------
// Chart configs
// ---------------------------------------------------------------------------

const taskStatusConfig: ChartConfig = {
  Planned: { label: "Planned", color: "var(--chart-1)" },
  Allocated: { label: "Allocated", color: "var(--chart-2)" },
  Assigned: { label: "Assigned", color: "var(--chart-3)" },
  Completed: { label: "Completed", color: "var(--chart-4)" },
  Cancelled: { label: "Cancelled", color: "var(--chart-5)" },
}

const waveStateConfig: ChartConfig = {
  Planned: { label: "Planned", color: "var(--chart-1)" },
  Released: { label: "Released", color: "var(--chart-2)" },
  Completed: { label: "Completed", color: "var(--chart-4)" },
  Cancelled: { label: "Cancelled", color: "var(--chart-5)" },
}

const taskTypeConfig: ChartConfig = {
  Pick: { label: "Pick", color: "var(--chart-1)" },
  Putaway: { label: "Putaway", color: "var(--chart-2)" },
  Replenish: { label: "Replenish", color: "var(--chart-3)" },
  Transfer: { label: "Transfer", color: "var(--chart-4)" },
}

const orderPriorityConfig: ChartConfig = {
  Low: { label: "Low", color: "var(--chart-1)" },
  Normal: { label: "Normal", color: "var(--chart-2)" },
  High: { label: "High", color: "var(--chart-3)" },
  Critical: { label: "Critical", color: "var(--chart-5)" },
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function countBy<T>(items: T[], key: (item: T) => string) {
  const counts: Record<string, number> = {}
  for (const item of items) {
    const k = key(item)
    counts[k] = (counts[k] ?? 0) + 1
  }
  return counts
}

function toChartData(counts: Record<string, number>, order: string[]) {
  return order
    .filter((key) => (counts[key] ?? 0) > 0)
    .map((key) => ({ name: key, value: counts[key] ?? 0 }))
}

const stagger = {
  hidden: {},
  show: { transition: { staggerChildren: 0.05 } },
}

const staggerDelayed = {
  hidden: {},
  show: { transition: { staggerChildren: 0.05, delayChildren: 0.15 } },
}

const fadeUp = {
  hidden: { opacity: 0, y: 6 },
  show: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.25, ease: [0.23, 1, 0.32, 1] },
  },
}

// ---------------------------------------------------------------------------
// Dashboard
// ---------------------------------------------------------------------------

function DashboardPage() {
  const { data: waves } = useSuspenseQuery(waveQueries.all())
  const { data: tasks } = useSuspenseQuery(taskQueries.all())
  const { data: orders } = useSuspenseQuery(orderQueries.all())
  const { data: groups } = useSuspenseQuery(
    consolidationGroupQueries.all(),
  )

  const stats = useMemo(() => {
    const releasedWaves = waves.filter(
      (w) => w.state === "Released",
    ).length
    const completedTasks = tasks.filter(
      (t) => t.state === "Completed",
    ).length
    const completedGroups = groups.filter(
      (g) => g.state === "Completed",
    ).length
    const highPriority = orders.filter(
      (o) => o.priority === "High" || o.priority === "Critical",
    ).length

    return {
      releasedWaves,
      totalWaves: waves.length,
      completedTasks,
      totalTasks: tasks.length,
      taskPct:
        tasks.length > 0
          ? Math.round((completedTasks / tasks.length) * 100)
          : 0,
      totalOrders: orders.length,
      highPriority,
      completedGroups,
      totalGroups: groups.length,
    }
  }, [waves, tasks, orders, groups])

  const taskStatusData = useMemo(
    () =>
      toChartData(
        countBy(tasks, (t) => t.state),
        ["Planned", "Allocated", "Assigned", "Completed", "Cancelled"],
      ),
    [tasks],
  )

  const waveStateData = useMemo(
    () =>
      toChartData(
        countBy(waves, (w) => w.state),
        ["Planned", "Released", "Completed", "Cancelled"],
      ),
    [waves],
  )

  const taskTypeData = useMemo(
    () =>
      toChartData(
        countBy(tasks, (t) => t.taskType),
        ["Pick", "Putaway", "Replenish", "Transfer"],
      ),
    [tasks],
  )

  const orderPriorityData = useMemo(
    () =>
      toChartData(
        countBy(orders, (o) => o.priority),
        ["Low", "Normal", "High", "Critical"],
      ),
    [orders],
  )

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={m.page_dashboard_title()}
        description={m.page_dashboard_description()}
        className="pb-0"
      />

      <motion.div
        variants={stagger}
        initial="hidden"
        animate="show"
        className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4"
      >
        <motion.div variants={fadeUp}>
          <StatCard
            icon={Waves}
            title={m.dashboard_active_waves()}
            value={stats.releasedWaves}
            detail={`${stats.releasedWaves} of ${stats.totalWaves} waves`}
          />
        </motion.div>
        <motion.div variants={fadeUp}>
          <StatCard
            icon={CheckCircle2}
            title={m.dashboard_tasks_completed()}
            value={`${stats.completedTasks}/${stats.totalTasks}`}
            detail={`${stats.taskPct}% completion rate`}
          />
        </motion.div>
        <motion.div variants={fadeUp}>
          <StatCard
            icon={ShoppingCart}
            title={m.dashboard_total_orders()}
            value={stats.totalOrders}
            detail={
              stats.highPriority > 0
                ? `${stats.highPriority} high priority`
                : "No urgent orders"
            }
          />
        </motion.div>
        <motion.div variants={fadeUp}>
          <StatCard
            icon={Layers}
            title={m.dashboard_consolidation_progress()}
            value={stats.completedGroups}
            detail={`${stats.completedGroups} of ${stats.totalGroups} groups`}
          />
        </motion.div>
      </motion.div>

      <motion.div
        variants={staggerDelayed}
        initial="hidden"
        animate="show"
        className="grid grid-cols-1 gap-4 md:grid-cols-2"
      >
        <motion.div variants={fadeUp}>
          <Card className="h-full">
            <CardHeader>
              <CardTitle>{m.dashboard_task_status()}</CardTitle>
              <CardDescription>
                Breakdown by current state
              </CardDescription>
            </CardHeader>
            <CardContent>
              <ChartContainer
                config={taskStatusConfig}
                className="mx-auto aspect-square max-h-64"
              >
                <PieChart>
                  <ChartTooltip
                    content={<ChartTooltipContent hideLabel />}
                  />
                  <Pie
                    data={taskStatusData}
                    dataKey="value"
                    nameKey="name"
                    innerRadius="55%"
                    outerRadius="80%"
                    stroke="none"
                  >
                    {taskStatusData.map((entry) => (
                      <Cell
                        key={entry.name}
                        fill={`var(--color-${entry.name})`}
                      />
                    ))}
                    <Label
                      content={({ viewBox }) => {
                        if (
                          !viewBox ||
                          !("cx" in viewBox) ||
                          !("cy" in viewBox)
                        )
                          return null
                        return (
                          <text
                            x={viewBox.cx}
                            y={viewBox.cy}
                            textAnchor="middle"
                            dominantBaseline="middle"
                          >
                            <tspan
                              x={viewBox.cx}
                              y={viewBox.cy}
                              className="fill-foreground font-heading text-2xl font-bold"
                            >
                              {stats.totalTasks}
                            </tspan>
                            <tspan
                              x={viewBox.cx}
                              y={(viewBox.cy ?? 0) + 20}
                              className="fill-muted-foreground text-xs"
                            >
                              tasks
                            </tspan>
                          </text>
                        )
                      }}
                    />
                  </Pie>
                  <ChartLegend content={<ChartLegendContent />} />
                </PieChart>
              </ChartContainer>
            </CardContent>
          </Card>
        </motion.div>

        <motion.div variants={fadeUp}>
          <Card className="h-full">
            <CardHeader>
              <CardTitle>
                {m.dashboard_wave_completion()}
              </CardTitle>
              <CardDescription>
                Waves by lifecycle stage
              </CardDescription>
            </CardHeader>
            <CardContent>
              <ChartContainer
                config={waveStateConfig}
                className="aspect-video max-h-64"
              >
                <BarChart
                  data={waveStateData}
                  layout="vertical"
                  margin={{ left: 16 }}
                >
                  <CartesianGrid horizontal={false} />
                  <YAxis
                    dataKey="name"
                    type="category"
                    tickLine={false}
                    axisLine={false}
                    width={80}
                  />
                  <XAxis type="number" hide />
                  <ChartTooltip
                    content={
                      <ChartTooltipContent hideLabel />
                    }
                  />
                  <Bar
                    dataKey="value"
                    radius={[0, 6, 6, 0]}
                    barSize={24}
                  >
                    {waveStateData.map((entry) => (
                      <Cell
                        key={entry.name}
                        fill={`var(--color-${entry.name})`}
                      />
                    ))}
                  </Bar>
                </BarChart>
              </ChartContainer>
            </CardContent>
          </Card>
        </motion.div>

        <motion.div variants={fadeUp}>
          <Card className="h-full">
            <CardHeader>
              <CardTitle>{m.dashboard_task_types()}</CardTitle>
              <CardDescription>
                Distribution by task category
              </CardDescription>
            </CardHeader>
            <CardContent>
              <ChartContainer
                config={taskTypeConfig}
                className="aspect-video max-h-64"
              >
                <BarChart data={taskTypeData}>
                  <CartesianGrid vertical={false} />
                  <XAxis
                    dataKey="name"
                    tickLine={false}
                    axisLine={false}
                  />
                  <YAxis tickLine={false} axisLine={false} />
                  <ChartTooltip
                    content={
                      <ChartTooltipContent hideLabel />
                    }
                  />
                  <Bar
                    dataKey="value"
                    radius={[6, 6, 0, 0]}
                    barSize={40}
                  >
                    {taskTypeData.map((entry) => (
                      <Cell
                        key={entry.name}
                        fill={`var(--color-${entry.name})`}
                      />
                    ))}
                  </Bar>
                </BarChart>
              </ChartContainer>
            </CardContent>
          </Card>
        </motion.div>

        <motion.div variants={fadeUp}>
          <Card className="h-full">
            <CardHeader>
              <CardTitle>
                {m.dashboard_order_priority()}
              </CardTitle>
              <CardDescription>
                Orders by priority level
              </CardDescription>
            </CardHeader>
            <CardContent>
              <ChartContainer
                config={orderPriorityConfig}
                className="mx-auto aspect-square max-h-64"
              >
                <PieChart>
                  <ChartTooltip
                    content={
                      <ChartTooltipContent hideLabel />
                    }
                  />
                  <Pie
                    data={orderPriorityData}
                    dataKey="value"
                    nameKey="name"
                    innerRadius="55%"
                    outerRadius="80%"
                    stroke="none"
                  >
                    {orderPriorityData.map((entry) => (
                      <Cell
                        key={entry.name}
                        fill={`var(--color-${entry.name})`}
                      />
                    ))}
                    <Label
                      content={({ viewBox }) => {
                        if (
                          !viewBox ||
                          !("cx" in viewBox) ||
                          !("cy" in viewBox)
                        )
                          return null
                        return (
                          <text
                            x={viewBox.cx}
                            y={viewBox.cy}
                            textAnchor="middle"
                            dominantBaseline="middle"
                          >
                            <tspan
                              x={viewBox.cx}
                              y={viewBox.cy}
                              className="fill-foreground font-heading text-2xl font-bold"
                            >
                              {stats.totalOrders}
                            </tspan>
                            <tspan
                              x={viewBox.cx}
                              y={(viewBox.cy ?? 0) + 20}
                              className="fill-muted-foreground text-xs"
                            >
                              orders
                            </tspan>
                          </text>
                        )
                      }}
                    />
                  </Pie>
                  <ChartLegend content={<ChartLegendContent />} />
                </PieChart>
              </ChartContainer>
            </CardContent>
          </Card>
        </motion.div>
      </motion.div>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Stat Card
// ---------------------------------------------------------------------------

function StatCard({
  icon: Icon,
  title,
  value,
  detail,
}: {
  icon: LucideIcon
  title: string
  value: number | string
  detail: string
}) {
  return (
    <Card size="sm" className="h-full">
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardDescription>{title}</CardDescription>
          <Icon className="size-4 text-muted-foreground" />
        </div>
        <CardTitle className="text-3xl font-bold tabular-nums tracking-normal">
          {value}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <p className="text-sm text-muted-foreground">{detail}</p>
      </CardContent>
    </Card>
  )
}
