import { createFileRoute, Link } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { ArrowLeft } from "lucide-react"
import { taskQueries } from "@/shared/api/tasks"
import { Button } from "@/shared/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card"
import { PageHeader } from "@/shared/ui/page-header"
import { StateBadge } from "@/shared/ui/state-badge"

export const Route = createFileRoute("/_authenticated/tasks/$taskId")({
  component: TaskDetailPage,
  loader: ({ context, params }) =>
    context.queryClient.ensureQueryData(
      taskQueries.detail(params.taskId),
    ),
})

function TaskDetailPage() {
  const { taskId } = Route.useParams()
  const { data: task } = useSuspenseQuery(
    taskQueries.detail(taskId),
  )

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

      <Card>
        <CardHeader>
          <CardTitle className="font-heading text-sm tracking-wider">
            Details
          </CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <dt className="text-muted-foreground mb-1">ID</dt>
              <dd className="font-mono text-xs text-muted-foreground">
                {task.id}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">Task Type</dt>
              <dd className="font-medium">{task.taskType}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">SKU ID</dt>
              <dd className="font-mono font-medium">{task.skuId}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">Order ID</dt>
              <dd className="font-mono font-medium">{task.orderId}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">Wave ID</dt>
              <dd className="font-mono font-medium">{task.waveId}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">State</dt>
              <dd>
                <StateBadge state={task.state} />
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">
                Source Location
              </dt>
              <dd className="font-mono font-medium">
                {task.sourceLocation}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">
                Destination Location
              </dt>
              <dd className="font-mono font-medium">
                {task.destinationLocation}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">
                Requested Quantity
              </dt>
              <dd className="font-mono font-medium">
                {task.requestedQuantity}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">
                Actual Quantity
              </dt>
              <dd className="font-mono font-medium">
                {task.actualQuantity ?? "-"}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">Assigned To</dt>
              <dd className="font-medium">{task.assignedTo ?? "-"}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">Created At</dt>
              <dd className="font-medium">{task.createdAt}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>
    </div>
  )
}
