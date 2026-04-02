import {
  createColumnHelper,
  getCoreRowModel,
  useReactTable,
} from "@tanstack/react-table"
import { createFileRoute } from "@tanstack/react-router"

import type { Order } from "@/shared/types/order"
import { useOrders } from "@/entities/order/query-hooks/use-orders"
import { DataTable } from "@/shared/components/data-table"
import { PageHeader } from "@/shared/components/page-header"
import { PriorityBadge } from "@/shared/components/priority-badge"
import { Skeleton } from "@/components/ui/skeleton"
import { m } from "@/paraglide/messages.js"

export const Route = createFileRoute("/_app/orders")({
  component: OrdersPage,
})

const columnHelper = createColumnHelper<Order>()

const columns = [
  columnHelper.accessor("id", {
    header: () => m.column_id(),
    cell: (info) => (
      <span className="font-mono text-xs">
        {info.getValue().slice(0, 8)}
      </span>
    ),
  }),
  columnHelper.accessor("priority", {
    header: "Priority",
    cell: (info) => (
      <PriorityBadge priority={info.getValue()} />
    ),
  }),
  columnHelper.accessor("lines", {
    header: "Lines",
    cell: (info) => info.getValue().length,
  }),
  columnHelper.accessor("carrierId", {
    header: "Carrier",
    cell: (info) => (
      <span className="font-mono text-xs">
        {info.getValue()?.slice(0, 8) ?? "-"}
      </span>
    ),
  }),
]

function OrdersPage() {
  const { data, isLoading } = useOrders()

  const table = useReactTable({
    data: data ?? [],
    columns,
    getCoreRowModel: getCoreRowModel(),
  })

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 w-full" />
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader title={m.page_title_orders()} />
      <DataTable
        table={table}
        emptyTitle={m.empty_state_title({
          entity: "orders",
        })}
      />
    </div>
  )
}
