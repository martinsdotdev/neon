import { Link, createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { ArrowLeft } from "lucide-react"
import { receiptQueries } from "@/shared/api/receipts"
import { Button } from "@/shared/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card"
import { PageHeader } from "@/shared/ui/page-header"
import { StateBadge } from "@/shared/ui/state-badge"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/shared/ui/table"

export const Route = createFileRoute("/_authenticated/receipts/$receiptId")({
  component: ReceiptDetailPage,
  loader: ({ context, params }) =>
    context.queryClient.ensureQueryData(
      receiptQueries.detail(params.receiptId)
    ),
})

function ReceiptDetailPage() {
  const { receiptId } = Route.useParams()
  const { data: receipt } = useSuspenseQuery(receiptQueries.detail(receiptId))

  if (!receipt) {
    return (
      <div>
        <PageHeader title="Receipt not found" />
        <Button render={<Link to="/receipts" />} variant="ghost">
          <ArrowLeft className="size-4" />
          Back to receipts
        </Button>
      </div>
    )
  }

  return (
    <div>
      <PageHeader
        actions={<StateBadge state={receipt.state} />}
        title={`Receipt ${receipt.id}`}
      />

      <div className="mb-6">
        <Button render={<Link to="/receipts" />} size="sm" variant="ghost">
          <ArrowLeft className="size-4" />
          All receipts
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
                {receipt.id}
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Delivery ID</dt>
              <dd className="font-mono font-medium">{receipt.deliveryId}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">State</dt>
              <dd>
                <StateBadge state={receipt.state} />
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Created At</dt>
              <dd className="font-medium">{receipt.createdAt}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>

      {receipt.lines && receipt.lines.length > 0 && (
        <Card className="mt-6">
          <CardHeader>
            <CardTitle className="font-heading text-sm tracking-wider">
              Receipt Lines
            </CardTitle>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>SKU</TableHead>
                  <TableHead>Packaging</TableHead>
                  <TableHead>Lot</TableHead>
                  <TableHead className="text-right">Quantity</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {receipt.lines.map((line, index) => (
                  <TableRow key={index}>
                    <TableCell className="font-mono text-xs">
                      {line.skuId}
                    </TableCell>
                    <TableCell>{line.packagingLevel}</TableCell>
                    <TableCell>{line.lot ?? "-"}</TableCell>
                    <TableCell className="text-right font-mono text-xs">
                      {line.quantity}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
