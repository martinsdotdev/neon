import { createFileRoute, Link } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { ArrowLeft } from "lucide-react"
import { stockPositionQueries } from "@/shared/api/stock-positions"
import { Button } from "@/shared/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card"
import { PageHeader } from "@/shared/ui/page-header"

export const Route = createFileRoute(
  "/_authenticated/stock-positions/$positionId",
)({
  component: StockPositionDetailPage,
  loader: ({ context, params }) =>
    context.queryClient.ensureQueryData(
      stockPositionQueries.detail(params.positionId),
    ),
})

function StockPositionDetailPage() {
  const { positionId } = Route.useParams()
  const { data: position } = useSuspenseQuery(
    stockPositionQueries.detail(positionId),
  )

  if (!position) {
    return (
      <div>
        <PageHeader title="Stock position not found" />
        <Button
          render={<Link to="/stock-positions" />}
          variant="ghost"
        >
          <ArrowLeft className="size-4" />
          Back to stock positions
        </Button>
      </div>
    )
  }

  return (
    <div>
      <PageHeader title={`Stock Position ${position.skuId}`} />

      <div className="mb-6">
        <Button
          render={<Link to="/stock-positions" />}
          size="sm"
          variant="ghost"
        >
          <ArrowLeft className="size-4" />
          All stock positions
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
                {position.id}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">SKU ID</dt>
              <dd className="font-mono font-medium">
                {position.skuId}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">
                Warehouse Area
              </dt>
              <dd className="font-medium">
                {position.warehouseArea}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">Lot</dt>
              <dd className="font-medium">
                {position.lot ?? "-"}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">
                On Hand Quantity
              </dt>
              <dd className="font-mono font-medium">
                {position.onHandQuantity}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">
                Available Quantity
              </dt>
              <dd className="font-mono font-medium">
                {position.availableQuantity}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground mb-1">
                Blocked Quantity
              </dt>
              <dd className="font-mono font-medium">
                {position.blockedQuantity}
              </dd>
            </div>
          </dl>
        </CardContent>
      </Card>
    </div>
  )
}
