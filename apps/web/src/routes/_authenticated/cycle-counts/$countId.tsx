import { Link, createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { ArrowLeft } from "lucide-react"
import { cycleCountQueries } from "@/shared/api/cycle-counts"
import { Button } from "@/shared/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card"
import { PageHeader } from "@/shared/ui/page-header"
import { StateBadge } from "@/shared/ui/state-badge"

export const Route = createFileRoute("/_authenticated/cycle-counts/$countId")({
  component: CycleCountDetailPage,
  loader: ({ context, params }) =>
    context.queryClient.ensureQueryData(
      cycleCountQueries.detail(params.countId)
    ),
})

function CycleCountDetailPage() {
  const { countId } = Route.useParams()
  const { data: count } = useSuspenseQuery(cycleCountQueries.detail(countId))

  if (!count) {
    return (
      <div>
        <PageHeader title="Cycle count not found" />
        <Button render={<Link to="/cycle-counts" />} variant="ghost">
          <ArrowLeft className="size-4" />
          Back to cycle counts
        </Button>
      </div>
    )
  }

  return (
    <div>
      <PageHeader
        actions={<StateBadge state={count.state} />}
        title={`Cycle Count ${count.id}`}
      />

      <div className="mb-6">
        <Button render={<Link to="/cycle-counts" />} size="sm" variant="ghost">
          <ArrowLeft className="size-4" />
          All cycle counts
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
              <dt className="mb-1 text-muted-foreground">ID</dt>
              <dd className="font-mono text-xs text-muted-foreground">
                {count.id}
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Count Type</dt>
              <dd className="font-medium">{count.countType}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Count Method</dt>
              <dd className="font-medium">{count.countMethod}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Warehouse Area</dt>
              <dd className="font-medium">{count.warehouseArea}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Task Count</dt>
              <dd className="font-mono font-medium">{count.taskCount}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">State</dt>
              <dd>
                <StateBadge state={count.state} />
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Created At</dt>
              <dd className="font-medium">{count.createdAt}</dd>
            </div>
            <div className="col-span-2">
              <dt className="mb-1 text-muted-foreground">SKU IDs</dt>
              <dd className="font-mono text-xs">
                {count.skuIds?.join(", ") ?? "-"}
              </dd>
            </div>
          </dl>
        </CardContent>
      </Card>
    </div>
  )
}
