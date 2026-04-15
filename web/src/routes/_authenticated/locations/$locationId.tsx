import { createFileRoute, Link } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { ArrowLeft } from "lucide-react"
import { locationQueries } from "@/shared/api/locations"
import { Badge } from "@/shared/ui/badge"
import { Button } from "@/shared/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card"
import { PageHeader } from "@/shared/ui/page-header"

export const Route = createFileRoute("/_authenticated/locations/$locationId")({
  component: LocationDetailPage,
  loader: ({ context, params }) =>
    context.queryClient.ensureQueryData(
      locationQueries.detail(params.locationId),
    ),
})

function LocationDetailPage() {
  const { locationId } = Route.useParams()
  const { data: location } = useSuspenseQuery(
    locationQueries.detail(locationId),
  )

  if (!location) {
    return (
      <div>
        <PageHeader title="Location not found" />
        <Button render={<Link to="/locations" />} variant="ghost">
          <ArrowLeft className="size-4" />
          Back to locations
        </Button>
      </div>
    )
  }

  return (
    <div>
      <PageHeader
        actions={<Badge variant="secondary">{location.locationType}</Badge>}
        title={location.code}
      />

      <div className="mb-6">
        <Button render={<Link to="/locations" />} size="sm" variant="ghost">
          <ArrowLeft className="size-4" />
          All locations
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
              <dt className="text-muted-foreground mb-1">Code</dt>
              <dd className="font-mono font-medium">{location.code}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">Type</dt>
              <dd>{location.locationType}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">Zone</dt>
              <dd className="font-mono text-xs">
                {location.zoneId ?? "\u2014"}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">Picking Sequence</dt>
              <dd className="font-mono">
                {location.pickingSequence ?? "\u2014"}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">ID</dt>
              <dd className="font-mono text-xs text-muted-foreground">
                {location.id}
              </dd>
            </div>
          </dl>
        </CardContent>
      </Card>
    </div>
  )
}
