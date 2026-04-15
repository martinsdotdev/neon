import { createFileRoute, Link } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { ArrowLeft } from "lucide-react"
import { consolidationGroupQueries } from "@/shared/api/consolidation-groups"
import { Button } from "@/shared/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card"
import { PageHeader } from "@/shared/ui/page-header"
import { StateBadge } from "@/shared/ui/state-badge"

export const Route = createFileRoute(
  "/_authenticated/consolidation-groups/$groupId",
)({
  component: ConsolidationGroupDetailPage,
  loader: ({ context, params }) =>
    context.queryClient.ensureQueryData(
      consolidationGroupQueries.detail(params.groupId),
    ),
})

function ConsolidationGroupDetailPage() {
  const { groupId } = Route.useParams()
  const { data: group } = useSuspenseQuery(
    consolidationGroupQueries.detail(groupId),
  )

  if (!group) {
    return (
      <div>
        <PageHeader title="Consolidation group not found" />
        <Button
          render={<Link to="/consolidation-groups" />}
          variant="ghost"
        >
          <ArrowLeft className="size-4" />
          Back to consolidation groups
        </Button>
      </div>
    )
  }

  return (
    <div>
      <PageHeader
        actions={<StateBadge state={group.state} />}
        title={`Consolidation Group ${group.id}`}
      />

      <div className="mb-6">
        <Button
          render={<Link to="/consolidation-groups" />}
          size="sm"
          variant="ghost"
        >
          <ArrowLeft className="size-4" />
          All consolidation groups
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
                {group.id}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">Wave ID</dt>
              <dd className="font-mono font-medium">{group.waveId}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">Order Count</dt>
              <dd className="font-mono font-medium">
                {group.orderCount}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">State</dt>
              <dd>
                <StateBadge state={group.state} />
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">
                Workstation ID
              </dt>
              <dd className="font-mono font-medium">
                {group.workstationId ?? "-"}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">Created At</dt>
              <dd className="font-medium">{group.createdAt}</dd>
            </div>
            <div className="col-span-2">
              <dt className="text-muted-foreground mb-1">Order IDs</dt>
              <dd className="font-mono text-xs">
                {group.orderIds?.join(", ") ?? "-"}
              </dd>
            </div>
          </dl>
        </CardContent>
      </Card>
    </div>
  )
}
