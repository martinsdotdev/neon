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
  inventoryQueries,
  type InventoryRecord,
} from "@/shared/api/inventory"
import { PageHeader } from "@/shared/ui/page-header"
import { DataGrid } from "@/shared/reui/data-grid/data-grid"
import { DataGridTable } from "@/shared/reui/data-grid/data-grid-table"
import { DataGridColumnHeader } from "@/shared/reui/data-grid/data-grid-column-header"
import { DataGridPagination } from "@/shared/reui/data-grid/data-grid-pagination"
import { StateBadge } from "@/shared/ui/state-badge"

export const Route = createFileRoute("/_authenticated/inventory/")({
  component: InventoryPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(inventoryQueries.all()),
})

function InventoryPage() {
  const { data: records } = useSuspenseQuery(inventoryQueries.all())
  const navigate = useNavigate()
  const [sorting, setSorting] = useState<SortingState>([])
  const [pagination, setPagination] = useState<PaginationState>({
    pageIndex: 0,
    pageSize: 20,
  })

  const columns = useMemo<ColumnDef<InventoryRecord>[]>(
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
        accessorKey: "locationId",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.locationId}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Location" />
        ),
      },
      {
        accessorKey: "status",
        cell: ({ row }) => (
          <StateBadge state={row.original.status} />
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Status" />
        ),
        size: 120,
      },
      {
        accessorKey: "onHand",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.onHand}
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
        accessorKey: "available",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.available}
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
        accessorKey: "reserved",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.reserved}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Reserved" />
        ),
        meta: { align: "right" },
        size: 120,
      },
    ],
    [],
  )

  const table = useReactTable({
    columns,
    data: records,
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
        description="Location-level inventory records"
        title="Inventory"
      />
      <DataGrid
        onRowClick={(record) =>
          navigate({
            params: { inventoryId: record.id },
            to: "/inventory/$inventoryId",
          })
        }
        recordCount={records.length}
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
