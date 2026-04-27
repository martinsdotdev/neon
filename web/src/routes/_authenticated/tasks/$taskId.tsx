import { Link, createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { ArrowLeft, XCircle } from "lucide-react"
import type { Task } from "@/shared/api/tasks"
import { taskQueries } from "@/shared/api/tasks"
import { useIsMobile } from "@/shared/hooks/use-mobile"
import { Button } from "@/shared/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card"
import { PageHeader } from "@/shared/ui/page-header"
import { StateBadge } from "@/shared/ui/state-badge"
import {
  Stepper,
  StepperIndicator,
  StepperItem,
  StepperNav,
  StepperSeparator,
  StepperTitle,
  StepperTrigger,
} from "@/shared/ui/stepper"

export const Route = createFileRoute("/_authenticated/tasks/$taskId")({
  component: TaskDetailPage,
  loader: ({ context, params }) =>
    context.queryClient.ensureQueryData(taskQueries.detail(params.taskId)),
})

const HAPPY_PATH = ["Planned", "Allocated", "Assigned", "Completed"] as const
type HappyState = (typeof HAPPY_PATH)[number]

function TaskDetailPage() {
  const { taskId } = Route.useParams()
  const { data: task } = useSuspenseQuery(taskQueries.detail(taskId))

  if (!task) {
    return (
      <div>
        <PageHeader title="Task not found" />
        <Button render={<Link to="/tasks" />} variant="ghost">
          <ArrowLeft className="size-4" />
          Back to tasks
        </Button>
      </div>
    )
  }

  return (
    <div>
      <PageHeader
        actions={<StateBadge state={task.state} />}
        title={`Task ${task.id}`}
      />

      <div className="mb-6">
        <Button render={<Link to="/tasks" />} size="sm" variant="ghost">
          <ArrowLeft className="size-4" />
          All tasks
        </Button>
      </div>

      <TaskStepperCard task={task} />

      <Card className="mt-6">
        <CardHeader>
          <CardTitle className="font-heading text-sm tracking-wider">
            Details
          </CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <dt className="mb-1 text-muted-foreground">ID</dt>
              <dd className="font-mono text-xs text-muted-foreground">
                {task.id}
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Task Type</dt>
              <dd className="font-medium">{task.taskType}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">SKU ID</dt>
              <dd className="font-mono font-medium">{task.skuId}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Order ID</dt>
              <dd className="font-mono font-medium">{task.orderId}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Wave ID</dt>
              <dd className="font-mono font-medium">{task.waveId}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">State</dt>
              <dd>
                <StateBadge state={task.state} />
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Source Location</dt>
              <dd className="font-mono font-medium">
                {task.sourceLocationId ?? "—"}
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">
                Destination Location
              </dt>
              <dd className="font-mono font-medium">
                {task.destinationLocationId ?? "—"}
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Requested Quantity</dt>
              <dd className="font-mono font-medium">
                {task.requestedQuantity}
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Actual Quantity</dt>
              <dd className="font-mono font-medium">
                {task.actualQuantity ?? "—"}
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Assigned To</dt>
              <dd className="font-medium">{task.assignedTo ?? "—"}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Created At</dt>
              <dd className="font-medium">{task.createdAt}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>
    </div>
  )
}

function TaskStepperCard({ task }: { task: Task }) {
  const isMobile = useIsMobile()
  const isCancelled = task.state === "Cancelled"
  const activeStep = stateIndex(task.state)
  // Vertical stepper on narrow viewports — 4 steps with timestamps under
  // the label cramp horizontally below md.
  const orientation = isMobile ? "vertical" : "horizontal"

  return (
    <Card>
      <CardContent className="py-6">
        <Stepper orientation={orientation} value={activeStep}>
          <StepperNav>
            {HAPPY_PATH.map((state, i) => (
              <StepperItem
                completed={i + 1 < activeStep}
                key={state}
                step={i + 1}
              >
                <StepperTrigger className="flex items-center gap-3 px-2">
                  <StepperIndicator>{i + 1}</StepperIndicator>
                  <div className="flex flex-col items-start gap-0.5">
                    <StepperTitle>{state}</StepperTitle>
                    <span className="text-label-s font-mono text-on-surface-variant">
                      {eventAt(task, state) ?? "—"}
                    </span>
                  </div>
                </StepperTrigger>
                {i < HAPPY_PATH.length - 1 ? <StepperSeparator /> : null}
              </StepperItem>
            ))}
          </StepperNav>
        </Stepper>
        {isCancelled ? <CancelledNote task={task} /> : null}
      </CardContent>
    </Card>
  )
}

function CancelledNote({ task }: { task: Task }) {
  return (
    <div
      className="mt-4 flex items-start gap-3 rounded-shape-sm border border-state-cancelled/30 bg-state-cancelled-soft px-3 py-2.5 text-state-cancelled"
      data-slot="cancelled-note"
    >
      <XCircle className="mt-0.5 size-4 shrink-0" />
      <div className="flex flex-col">
        <span className="text-label-s font-mono tracking-wider uppercase">
          Cancelled
        </span>
        <span className="text-label-m">
          This task was cancelled. Created at {task.createdAt}.
        </span>
      </div>
    </div>
  )
}

function stateIndex(state: Task["state"]): number {
  switch (state) {
    case "Planned":
      return 1
    case "Allocated":
      return 2
    case "Assigned":
      return 3
    case "Completed":
      return 4
    case "Cancelled":
      return 1
  }
}

/**
 * Resolve a timestamp for `state`. Scaffolded to return null so the stepper
 * renders without breaking. The user wires real event sourcing when the
 * projection / event query is ready.
 *
 * TODO(user): replace with sourcing from task.events[], taskQueries.events(id),
 * or denormalized `task.<state>At` fields — 3-5 lines.
 */
function eventAt(task: Task, state: HappyState): string | null {
  if (state === "Planned") return shortTime(task.createdAt)
  return null
}

function shortTime(iso: string): string {
  const date = new Date(iso)
  return date.toLocaleTimeString(undefined, {
    hour: "2-digit",
    minute: "2-digit",
  })
}
