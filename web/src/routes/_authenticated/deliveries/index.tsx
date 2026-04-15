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
import { deliveryQueries, type Delivery } from "@/shared/api/deliveries"
import { PageHeader } from "@/shared/ui/page-header"
import { DataGrid } from "@/shared/reui/data-grid/data-grid"
import { DataGridTable } from "@/shared/reui/data-grid/data-grid-table"
import { DataGridColumnHeader } from "@/shared/reui/data-grid/data-grid-column-header"
import { DataGridPagination } from "@/shared/reui/data-grid/data-grid-pagination"
import { StateBadge } from "@/shared/ui/state-badge"

export const Route = createFileRoute("/_authenticated/deliveries/")({
  component: DeliveriesPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(deliveryQueries.all()),
})

function DeliveriesPage() {
  const { data: deliveries } = useSuspenseQuery(deliveryQueries.all())
  const navigate = useNavigate()
  const [sorting, setSorting] = useState<SortingState>([])
  const [pagination, setPagination] = useState<PaginationState>({
    pageIndex: 0,
    pageSize: 20,
  })

  const columns = useMemo<ColumnDef<Delivery>[]>(
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
          <DataGridColumnHeader column={column} title="ID" />
        ),
        size: 120,
      },
      {
        accessorKey: "skuId",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.skuId}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="SKU" />
        ),
      },
      {
        accessorKey: "expectedQuantity",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.expectedQuantity}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Expected Qty" />
        ),
        size: 130,
      },
      {
        accessorKey: "receivedQuantity",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.receivedQuantity}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Received Qty" />
        ),
        size: 130,
      },
      {
        accessorKey: "state",
        cell: ({ row }) => <StateBadge state={row.original.state} />,
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="State" />
        ),
        size: 120,
      },
    ],
    [],
  )

  const table = useReactTable({
    columns,
    data: deliveries,
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
        description="Inbound delivery scheduling and receiving"
        title="Deliveries"
      />
      <DataGrid
        onRowClick={(delivery) =>
          navigate({
            params: { deliveryId: delivery.id },
            to: "/deliveries/$deliveryId",
          })
        }
        recordCount={deliveries.length}
        table={table}
        tableLayout={{
          headerSticky: true,
        }}
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
