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
import {
  stockPositionQueries,
  type StockPosition,
} from "@/shared/api/stock-positions"
import { PageHeader } from "@/shared/ui/page-header"
import { DataGrid } from "@/shared/reui/data-grid/data-grid"
import { DataGridTable } from "@/shared/reui/data-grid/data-grid-table"
import { DataGridColumnHeader } from "@/shared/reui/data-grid/data-grid-column-header"
import { DataGridPagination } from "@/shared/reui/data-grid/data-grid-pagination"

export const Route = createFileRoute(
  "/_authenticated/stock-positions/",
)({
  component: StockPositionsPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(stockPositionQueries.all()),
})

function StockPositionsPage() {
  const { data: positions } = useSuspenseQuery(
    stockPositionQueries.all(),
  )
  const navigate = useNavigate()
  const [sorting, setSorting] = useState<SortingState>([])
  const [pagination, setPagination] = useState<PaginationState>({
    pageIndex: 0,
    pageSize: 20,
  })

  const columns = useMemo<ColumnDef<StockPosition>[]>(
    () => [
      {
        accessorKey: "skuId",
        cell: ({ row }) => (
          <span className="font-mono text-xs font-medium">
            {row.original.skuId}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="SKU" />
        ),
      },
      {
        accessorKey: "warehouseArea",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.warehouseArea}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Area" />
        ),
      },
      {
        accessorKey: "onHandQuantity",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.onHandQuantity}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="On Hand" />
        ),
        meta: { align: "right" },
        size: 120,
      },
      {
        accessorKey: "availableQuantity",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.availableQuantity}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Available" />
        ),
        meta: { align: "right" },
        size: 120,
      },
      {
        accessorKey: "blockedQuantity",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.blockedQuantity}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Blocked" />
        ),
        meta: { align: "right" },
        size: 120,
      },
    ],
    [],
  )

  const table = useReactTable({
    columns,
    data: positions,
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
        description="Stock levels by SKU and warehouse area"
        title="Stock Positions"
      />
      <DataGrid
        onRowClick={(position) =>
          navigate({
            params: { positionId: position.id },
            to: "/stock-positions/$positionId",
          })
        }
        recordCount={positions.length}
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
