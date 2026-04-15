import { createFileRoute, Link } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { ArrowLeft } from "lucide-react"
import { carrierQueries } from "@/shared/api/carriers"
import { Badge } from "@/shared/ui/badge"
import { Button } from "@/shared/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card"
import { PageHeader } from "@/shared/ui/page-header"

export const Route = createFileRoute("/_authenticated/carriers/$carrierId")({
  component: CarrierDetailPage,
  loader: ({ context, params }) =>
    context.queryClient.ensureQueryData(
      carrierQueries.detail(params.carrierId),
    ),
})

function CarrierDetailPage() {
  const { carrierId } = Route.useParams()
  const { data: carrier } = useSuspenseQuery(
    carrierQueries.detail(carrierId),
  )

  if (!carrier) {
    return (
      <div>
        <PageHeader title="Carrier not found" />
        <Button render={<Link to="/carriers" />} variant="ghost">
          <ArrowLeft className="size-4" />
          Back to carriers
        </Button>
      </div>
    )
  }

  return (
    <div>
      <PageHeader
        actions={
          <Badge variant={carrier.active ? "default" : "secondary"}>
            {carrier.active ? "Active" : "Inactive"}
          </Badge>
        }
        title={carrier.name}
      />

      <div className="mb-6">
        <Button render={<Link to="/carriers" />} size="sm" variant="ghost">
          <ArrowLeft className="size-4" />
          All carriers
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
              <dd className="font-mono font-medium">{carrier.code}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">Name</dt>
              <dd className="font-medium">{carrier.name}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">ID</dt>
              <dd className="font-mono text-xs text-muted-foreground">
                {carrier.id}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">Status</dt>
              <dd>{carrier.active ? "Active" : "Inactive"}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>
    </div>
  )
}
