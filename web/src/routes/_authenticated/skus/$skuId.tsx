import { Link, createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { ArrowLeft } from "lucide-react"
import { skuQueries } from "@/shared/api/skus"
import { Badge } from "@/shared/ui/badge"
import { Button } from "@/shared/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card"
import { PageHeader } from "@/shared/ui/page-header"

export const Route = createFileRoute("/_authenticated/skus/$skuId")({
  component: SkuDetailPage,
  loader: ({ context, params }) =>
    context.queryClient.ensureQueryData(skuQueries.detail(params.skuId)),
})

function SkuDetailPage() {
  const { skuId } = Route.useParams()
  const { data: sku } = useSuspenseQuery(skuQueries.detail(skuId))

  if (!sku) {
    return (
      <div>
        <PageHeader title="SKU not found" />
        <Button render={<Link to="/skus" />} variant="ghost">
          <ArrowLeft className="size-4" />
          Back to SKUs
        </Button>
      </div>
    )
  }

  return (
    <div>
      <PageHeader
        actions={sku.lotManaged ? <Badge>Lot-managed</Badge> : null}
        title={sku.code}
      />

      <div className="mb-6">
        <Button render={<Link to="/skus" />} size="sm" variant="ghost">
          <ArrowLeft className="size-4" />
          All SKUs
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
              <dt className="mb-1 text-muted-foreground">Code</dt>
              <dd className="font-mono font-medium">{sku.code}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Description</dt>
              <dd>{sku.description}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Lot Managed</dt>
              <dd>{sku.lotManaged ? "Yes" : "No"}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">ID</dt>
              <dd className="font-mono text-xs text-muted-foreground">
                {sku.id}
              </dd>
            </div>
          </dl>
        </CardContent>
      </Card>
    </div>
  )
}
