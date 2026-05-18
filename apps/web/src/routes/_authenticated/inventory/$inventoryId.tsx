import { Link, createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { ArrowLeft } from "lucide-react"
import { inventoryQueries } from "@/shared/api/inventory"
import { Button } from "@/shared/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card"
import { PageHeader } from "@/shared/ui/page-header"
import { StateBadge } from "@/shared/ui/state-badge"

export const Route = createFileRoute("/_authenticated/inventory/$inventoryId")({
  component: InventoryDetailPage,
  loader: ({ context, params }) =>
    context.queryClient.ensureQueryData(
      inventoryQueries.detail(params.inventoryId)
    ),
})

function InventoryDetailPage() {
  const { inventoryId } = Route.useParams()
  const { data: record } = useSuspenseQuery(
    inventoryQueries.detail(inventoryId)
  )

  if (!record) {
    return (
      <div>
        <PageHeader title="Inventory record not found" />
        <Button render={<Link to="/inventory" />} variant="ghost">
          <ArrowLeft className="size-4" />
          Back to inventory
        </Button>
      </div>
    )
  }

  return (
    <div>
      <PageHeader
        actions={<StateBadge state={record.status} />}
        title={`Inventory ${record.skuId}`}
      />

      <div className="mb-6">
        <Button render={<Link to="/inventory" />} size="sm" variant="ghost">
          <ArrowLeft className="size-4" />
          All inventory
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
                {record.id}
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">SKU ID</dt>
              <dd className="font-mono font-medium">{record.skuId}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Location ID</dt>
              <dd className="font-mono font-medium">{record.locationId}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Packaging Level</dt>
              <dd className="font-medium">{record.packagingLevel}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Lot</dt>
              <dd className="font-medium">{record.lot ?? "-"}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Status</dt>
              <dd>
                <StateBadge state={record.status} />
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">On Hand</dt>
              <dd className="font-mono font-medium">{record.onHand}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Available</dt>
              <dd className="font-mono font-medium">{record.available}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Reserved</dt>
              <dd className="font-mono font-medium">{record.reserved}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>
    </div>
  )
}
