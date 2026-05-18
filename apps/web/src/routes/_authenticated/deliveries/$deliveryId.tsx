import { Link, createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { ArrowLeft } from "lucide-react"
import { deliveryQueries } from "@/shared/api/deliveries"
import { Button } from "@/shared/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card"
import { PageHeader } from "@/shared/ui/page-header"
import { StateBadge } from "@/shared/ui/state-badge"

export const Route = createFileRoute("/_authenticated/deliveries/$deliveryId")({
  component: DeliveryDetailPage,
  loader: ({ context, params }) =>
    context.queryClient.ensureQueryData(
      deliveryQueries.detail(params.deliveryId)
    ),
})

function DeliveryDetailPage() {
  const { deliveryId } = Route.useParams()
  const { data: delivery } = useSuspenseQuery(
    deliveryQueries.detail(deliveryId)
  )

  if (!delivery) {
    return (
      <div>
        <PageHeader title="Delivery not found" />
        <Button render={<Link to="/deliveries" />} variant="ghost">
          <ArrowLeft className="size-4" />
          Back to deliveries
        </Button>
      </div>
    )
  }

  return (
    <div>
      <PageHeader
        actions={<StateBadge state={delivery.state} />}
        title={`Delivery ${delivery.id}`}
      />

      <div className="mb-6">
        <Button render={<Link to="/deliveries" />} size="sm" variant="ghost">
          <ArrowLeft className="size-4" />
          All deliveries
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
                {delivery.id}
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">SKU ID</dt>
              <dd className="font-mono font-medium">{delivery.skuId}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Packaging Level</dt>
              <dd className="font-medium">{delivery.packagingLevel}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Lot</dt>
              <dd className="font-medium">{delivery.lot ?? "-"}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Expected Quantity</dt>
              <dd className="font-mono font-medium">
                {delivery.expectedQuantity}
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Received Quantity</dt>
              <dd className="font-mono font-medium">
                {delivery.receivedQuantity}
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Rejected Quantity</dt>
              <dd className="font-mono font-medium">
                {delivery.rejectedQuantity}
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">State</dt>
              <dd>
                <StateBadge state={delivery.state} />
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Created At</dt>
              <dd className="font-medium">{delivery.createdAt}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>
    </div>
  )
}
