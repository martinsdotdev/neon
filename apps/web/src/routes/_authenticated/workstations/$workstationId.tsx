import { Link, createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { ArrowLeft } from "lucide-react"
import { workstationQueries } from "@/shared/api/workstations"
import { Button } from "@/shared/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card"
import { PageHeader } from "@/shared/ui/page-header"
import { StateBadge } from "@/shared/ui/state-badge"

export const Route = createFileRoute(
  "/_authenticated/workstations/$workstationId"
)({
  component: WorkstationDetailPage,
  loader: ({ context, params }) =>
    context.queryClient.ensureQueryData(
      workstationQueries.detail(params.workstationId)
    ),
})

function WorkstationDetailPage() {
  const { workstationId } = Route.useParams()
  const { data: workstation } = useSuspenseQuery(
    workstationQueries.detail(workstationId)
  )

  if (!workstation) {
    return (
      <div>
        <PageHeader title="Workstation not found" />
        <Button render={<Link to="/workstations" />} variant="ghost">
          <ArrowLeft className="size-4" />
          Back to workstations
        </Button>
      </div>
    )
  }

  return (
    <div>
      <PageHeader
        actions={<StateBadge state={workstation.state} />}
        title={`Workstation ${workstation.id}`}
      />

      <div className="mb-6">
        <Button render={<Link to="/workstations" />} size="sm" variant="ghost">
          <ArrowLeft className="size-4" />
          All workstations
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
                {workstation.id}
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Workstation Type</dt>
              <dd className="font-medium">{workstation.workstationType}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Mode</dt>
              <dd className="font-medium">{workstation.mode}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Slot Count</dt>
              <dd className="font-mono font-medium">{workstation.slotCount}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">State</dt>
              <dd>
                <StateBadge state={workstation.state} />
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Created At</dt>
              <dd className="font-medium">{workstation.createdAt}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>
    </div>
  )
}
