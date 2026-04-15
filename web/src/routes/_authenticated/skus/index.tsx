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
import { skuQueries, type Sku } from "@/shared/api/skus"
import { Badge } from "@/shared/ui/badge"
import { PageHeader } from "@/shared/ui/page-header"
import { DataGrid } from "@/shared/reui/data-grid/data-grid"
import { DataGridTable } from "@/shared/reui/data-grid/data-grid-table"
import { DataGridColumnHeader } from "@/shared/reui/data-grid/data-grid-column-header"
import { DataGridPagination } from "@/shared/reui/data-grid/data-grid-pagination"

export const Route = createFileRoute("/_authenticated/skus/")({
  component: SkusPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(skuQueries.all()),
})

function SkusPage() {
  const { data: skus } = useSuspenseQuery(skuQueries.all())
  const navigate = useNavigate()
  const [sorting, setSorting] = useState<SortingState>([])
  const [pagination, setPagination] = useState<PaginationState>({
    pageIndex: 0,
    pageSize: 20,
  })

  const columns = useMemo<ColumnDef<Sku>[]>(
    () => [
      {
        accessorKey: "code",
        cell: ({ row }) => (
          <span className="font-mono text-xs font-medium">
            {row.original.code}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Code" />
        ),
        size: 160,
      },
      {
        accessorKey: "description",
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Description" />
        ),
      },
      {
        accessorKey: "lotManaged",
        cell: ({ row }) =>
          row.original.lotManaged ? (
            <Badge variant="default">Lot-managed</Badge>
          ) : (
            <span className="text-muted-foreground text-xs">No</span>
          ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Lot" />
        ),
        size: 120,
      },
    ],
    [],
  )

  const table = useReactTable({
    columns,
    data: skus,
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
        description="Stock keeping units in the warehouse"
        title="SKUs"
      />
      <DataGrid
        onRowClick={(sku) =>
          navigate({ params: { skuId: sku.id }, to: "/skus/$skuId" })
        }
        recordCount={skus.length}
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
