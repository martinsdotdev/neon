import {
  createColumnHelper,
  getCoreRowModel,
  useReactTable,
} from "@tanstack/react-table"
import { createFileRoute } from "@tanstack/react-router"

import type { TransportOrder } from "@/shared/types/transport-order"
import { useTransportOrders } from "@/entities/transport-order/query-hooks/use-transport-orders"
import { DataTable } from "@/shared/components/data-table"
import { PageHeader } from "@/shared/components/page-header"
import { StatusBadge } from "@/shared/components/status-badge"
import { Skeleton } from "@/components/ui/skeleton"
import { m } from "@/paraglide/messages.js"

export const Route = createFileRoute(
  "/_app/transport-orders",
)({
  component: TransportOrdersPage,
})

const columnHelper = createColumnHelper<TransportOrder>()

const columns = [
  columnHelper.accessor("id", {
    header: () => m.column_id(),
    cell: (info) => (
      <span className="font-mono text-xs">
        {info.getValue().slice(0, 8)}
      </span>
    ),
  }),
  columnHelper.accessor("status", {
    header: () => m.column_status(),
    cell: (info) => <StatusBadge status={info.getValue()} />,
  }),
  columnHelper.accessor("handlingUnitId", {
    header: "Handling Unit",
    cell: (info) => (
      <span className="font-mono text-xs">
        {info.getValue().slice(0, 8)}
      </span>
    ),
  }),
  columnHelper.accessor("destination", {
    header: "Destination",
    cell: (info) => (
      <span className="font-mono text-xs">
        {info.getValue().slice(0, 8)}
      </span>
    ),
  }),
  columnHelper.accessor("createdAt", {
    header: () => m.column_created_at(),
    cell: (info) =>
      new Date(info.getValue()).toLocaleString(),
  }),
]

function TransportOrdersPage() {
  const { data, isLoading } = useTransportOrders()

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
      <PageHeader
        title={m.page_title_transport_orders()}
      />
      <DataTable
        table={table}
        emptyTitle={m.empty_state_title({
          entity: "transport orders",
        })}
      />
    </div>
  )
}
