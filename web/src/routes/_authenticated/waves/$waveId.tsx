import { Link, createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { ArrowLeft } from "lucide-react"
import { waveQueries } from "@/shared/api/waves"
import { Button } from "@/shared/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card"
import { DateCell } from "@/shared/ui/date-cell"
import { PageHeader } from "@/shared/ui/page-header"
import { StateBadge } from "@/shared/ui/state-badge"

export const Route = createFileRoute("/_authenticated/waves/$waveId")({
  component: WaveDetailPage,
  loader: ({ context, params }) =>
    context.queryClient.ensureQueryData(waveQueries.detail(params.waveId)),
})

function WaveDetailPage() {
  const { waveId } = Route.useParams()
  const { data: wave } = useSuspenseQuery(waveQueries.detail(waveId))

  if (!wave) {
    return (
      <div>
        <PageHeader title="Wave not found" />
        <Button render={<Link to="/waves" />} variant="ghost">
          <ArrowLeft className="size-4" />
          Back to waves
        </Button>
      </div>
    )
  }

  return (
    <div>
      <PageHeader
        actions={<StateBadge state={wave.state} />}
        title={`Wave ${wave.id}`}
      />

      <div className="mb-6">
        <Button render={<Link to="/waves" />} size="sm" variant="ghost">
          <ArrowLeft className="size-4" />
          All waves
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
                {wave.id}
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Order Grouping</dt>
              <dd className="font-medium">{wave.orderGrouping}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Order Count</dt>
              <dd className="font-mono font-medium">{wave.orderCount}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">State</dt>
              <dd>
                <StateBadge state={wave.state} />
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Created</dt>
              <dd>
                <DateCell value={wave.createdAt} variant="detail" />
              </dd>
            </div>
            <div className="col-span-2">
              <dt className="mb-1 text-muted-foreground">Order IDs</dt>
              <dd className="font-mono text-xs">
                {wave.orderIds?.join(", ") ?? "-"}
              </dd>
            </div>
          </dl>
        </CardContent>
      </Card>
    </div>
  )
}
