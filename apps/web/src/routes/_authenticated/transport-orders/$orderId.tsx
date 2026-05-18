import { Link, createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { ArrowLeft } from "lucide-react"
import { transportOrderQueries } from "@/shared/api/transport-orders"
import { Button } from "@/shared/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card"
import { PageHeader } from "@/shared/ui/page-header"
import { StateBadge } from "@/shared/ui/state-badge"

export const Route = createFileRoute(
  "/_authenticated/transport-orders/$orderId"
)({
  component: TransportOrderDetailPage,
  loader: ({ context, params }) =>
    context.queryClient.ensureQueryData(
      transportOrderQueries.detail(params.orderId)
    ),
})

function TransportOrderDetailPage() {
  const { orderId } = Route.useParams()
  const { data: order } = useSuspenseQuery(
    transportOrderQueries.detail(orderId)
  )

  if (!order) {
    return (
      <div>
        <PageHeader title="Transport order not found" />
        <Button render={<Link to="/transport-orders" />} variant="ghost">
          <ArrowLeft className="size-4" />
          Back to transport orders
        </Button>
      </div>
    )
  }

  return (
    <div>
      <PageHeader
        actions={<StateBadge state={order.state} />}
        title={`Transport Order ${order.id}`}
      />

      <div className="mb-6">
        <Button
          render={<Link to="/transport-orders" />}
          size="sm"
          variant="ghost"
        >
          <ArrowLeft className="size-4" />
          All transport orders
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
                {order.id}
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Handling Unit ID</dt>
              <dd className="font-mono font-medium">{order.handlingUnitId}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Destination</dt>
              <dd className="font-mono font-medium">{order.destination}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">State</dt>
              <dd>
                <StateBadge state={order.state} />
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Created At</dt>
              <dd className="font-medium">{order.createdAt}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>
    </div>
  )
}
