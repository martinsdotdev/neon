import { createFileRoute, useNavigate } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import type { ColumnDef } from "@tanstack/react-table"
import {
  getCoreRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
} from "@tanstack/react-table"
import type { PaginationState, SortingState } from "@tanstack/react-table"
import { useMemo, useState } from "react"
import { orderQueries, type Order } from "@/shared/api/orders"
import { Badge } from "@/shared/ui/badge"
import { PageHeader } from "@/shared/ui/page-header"
import { DataGrid } from "@/shared/reui/data-grid/data-grid"
import { DataGridTable } from "@/shared/reui/data-grid/data-grid-table"
import { DataGridColumnHeader } from "@/shared/reui/data-grid/data-grid-column-header"
import { DataGridPagination } from "@/shared/reui/data-grid/data-grid-pagination"

const PRIORITY_VARIANT: Record<string, "default" | "secondary" | "destructive"> = {
  Critical: "destructive",
  High: "default",
  Low: "secondary",
  Normal: "secondary",
}

export const Route = createFileRoute("/_authenticated/orders/")({
  component: OrdersPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(orderQueries.all()),
})

function OrdersPage() {
  const { data: orders } = useSuspenseQuery(orderQueries.all())
  const navigate = useNavigate()
  const [sorting, setSorting] = useState<SortingState>([])
  const [pagination, setPagination] = useState<PaginationState>({
    pageIndex: 0,
    pageSize: 20,
  })

  const columns = useMemo<ColumnDef<Order>[]>(
    () => [
      {
        accessorKey: "id",
        cell: ({ row }) => (
          <span className="font-mono text-xs font-medium">
            {row.original.id}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Order ID" />
        ),
        size: 140,
      },
      {
        accessorKey: "priority",
        cell: ({ row }) => (
          <Badge variant={PRIORITY_VARIANT[row.original.priority] ?? "secondary"}>
            {row.original.priority}
          </Badge>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Priority" />
        ),
        size: 110,
      },
      {
        accessorFn: (row) => row.lines.length,
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.lines.length}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Lines" />
        ),
        id: "lineCount",
        size: 80,
      },
      {
        accessorKey: "carrierId",
        cell: ({ row }) => (
          <span className="text-muted-foreground font-mono text-xs">
            {row.original.carrierId ?? "\u2014"}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Carrier" />
        ),
        size: 120,
      },
    ],
    [],
  )

  const table = useReactTable({
    columns,
    data: orders,
    getCoreRowModel: getCoreRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    getRowId: (row) => row.id,
    getSortedRowModel: getSortedRowModel(),
    onPaginationChange: setPagination,
    onSortingChange: setSorting,
    state: { pagination, sorting },
  })

  return (
    <div>
      <PageHeader
        description="Customer orders for fulfillment"
        title="Orders"
      />
      <DataGrid
        onRowClick={(order) =>
          navigate({ params: { orderId: order.id }, to: "/orders/$orderId" })
        }
        recordCount={orders.length}
        table={table}
        tableLayout={{ headerSticky: true }}
      >
        <div className="w-full space-y-2.5">
          <div className="rounded-lg border">
            <DataGridTable />
          </div>
          <DataGridPagination sizes={[10, 20, 50]} />
        </div>
      </DataGrid>
    </div>
  )
}
