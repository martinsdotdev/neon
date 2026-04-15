import { createFileRoute } from "@tanstack/react-router"
import { PageHeader } from "@/shared/ui/page-header"

export const Route = createFileRoute("/_authenticated/stock-positions/$positionId")({
  component: StockPositionDetailPage,
})

function StockPositionDetailPage() {
  const { positionId } = Route.useParams()

  return (
    <div>
      <PageHeader title="Stock Position Detail" description={positionId} />
      <div className="bg-muted/30 text-muted-foreground flex h-64 items-center justify-center rounded-lg border border-dashed font-mono text-sm">
        Coming soon
      </div>
    </div>
  )
}
