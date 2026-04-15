import { createFileRoute, Link } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { ArrowLeft } from "lucide-react"
import { handlingUnitQueries } from "@/shared/api/handling-units"
import { Button } from "@/shared/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card"
import { PageHeader } from "@/shared/ui/page-header"
import { StateBadge } from "@/shared/ui/state-badge"

export const Route = createFileRoute(
  "/_authenticated/handling-units/$unitId",
)({
  component: HandlingUnitDetailPage,
  loader: ({ context, params }) =>
    context.queryClient.ensureQueryData(
      handlingUnitQueries.detail(params.unitId),
    ),
})

function HandlingUnitDetailPage() {
  const { unitId } = Route.useParams()
  const { data: unit } = useSuspenseQuery(
    handlingUnitQueries.detail(unitId),
  )

  if (!unit) {
    return (
      <div>
        <PageHeader title="Handling unit not found" />
        <Button
          render={<Link to="/handling-units" />}
          variant="ghost"
        >
          <ArrowLeft className="size-4" />
          Back to handling units
        </Button>
      </div>
    )
  }

  return (
    <div>
      <PageHeader
        actions={<StateBadge state={unit.state} />}
        title={`Handling Unit ${unit.id}`}
      />

      <div className="mb-6">
        <Button
          render={<Link to="/handling-units" />}
          size="sm"
          variant="ghost"
        >
          <ArrowLeft className="size-4" />
          All handling units
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
                {unit.id}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">
                Packaging Level
              </dt>
              <dd className="font-medium">{unit.packagingLevel}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">
                Current Location
              </dt>
              <dd className="font-mono font-medium">
                {unit.currentLocation}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">Order ID</dt>
              <dd className="font-mono font-medium">
                {unit.orderId ?? "-"}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">State</dt>
              <dd>
                <StateBadge state={unit.state} />
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">Created At</dt>
              <dd className="font-medium">{unit.createdAt}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>
    </div>
  )
}
