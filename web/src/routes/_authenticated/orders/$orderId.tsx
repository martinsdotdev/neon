import { createFileRoute, Link } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { ArrowLeft } from "lucide-react"
import { orderQueries } from "@/shared/api/orders"
import { Badge } from "@/shared/ui/badge"
import { Button } from "@/shared/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card"
import { PageHeader } from "@/shared/ui/page-header"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/shared/ui/table"

const PRIORITY_VARIANT: Record<string, "default" | "secondary" | "destructive"> = {
  Critical: "destructive",
  High: "default",
  Low: "secondary",
  Normal: "secondary",
}

export const Route = createFileRoute("/_authenticated/orders/$orderId")({
  component: OrderDetailPage,
  loader: ({ context, params }) =>
    context.queryClient.ensureQueryData(orderQueries.detail(params.orderId)),
})

function OrderDetailPage() {
  const { orderId } = Route.useParams()
  const { data: order } = useSuspenseQuery(orderQueries.detail(orderId))

  if (!order) {
    return (
      <div>
        <PageHeader title="Order not found" />
        <Button render={<Link to="/orders" />} variant="ghost">
          <ArrowLeft className="size-4" />
          Back to orders
        </Button>
      </div>
    )
  }

  return (
    <div>
      <PageHeader
        actions={
          <Badge variant={PRIORITY_VARIANT[order.priority] ?? "secondary"}>
            {order.priority}
          </Badge>
        }
        title={`Order ${order.id}`}
      />

      <div className="mb-6">
        <Button render={<Link to="/orders" />} size="sm" variant="ghost">
          <ArrowLeft className="size-4" />
          All orders
        </Button>
      </div>

      <div className="space-y-6">
        <Card>
          <CardHeader>
            <CardTitle className="font-heading text-sm tracking-wider">
              Details
            </CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="grid grid-cols-2 gap-4 text-sm">
              <div>
                <dt className="text-muted-foreground mb-1">Order ID</dt>
                <dd className="font-mono font-medium">{order.id}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground mb-1">Priority</dt>
                <dd>{order.priority}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground mb-1">Carrier</dt>
                <dd className="font-mono text-xs">
                  {order.carrierId ?? "\u2014"}
                </dd>
              </div>
              <div>
                <dt className="text-muted-foreground mb-1">Lines</dt>
                <dd className="font-mono">{order.lines.length}</dd>
              </div>
            </dl>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="font-heading text-sm tracking-wider">
              Order Lines
            </CardTitle>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>SKU</TableHead>
                  <TableHead>Packaging</TableHead>
                  <TableHead className="text-right">Quantity</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {order.lines.map((line, i) => (
                  <TableRow key={i}>
                    <TableCell className="font-mono text-xs">
                      {line.skuId}
                    </TableCell>
                    <TableCell>
                      <Badge variant="secondary">{line.packagingLevel}</Badge>
                    </TableCell>
                    <TableCell className="text-right font-mono">
                      {line.quantity}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
