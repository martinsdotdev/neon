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
  cycleCountQueries,
  type CycleCount,
} from "@/shared/api/cycle-counts"
import { Badge } from "@/shared/ui/badge"
import { PageHeader } from "@/shared/ui/page-header"
import { DataGrid } from "@/shared/reui/data-grid/data-grid"
import { DataGridTable } from "@/shared/reui/data-grid/data-grid-table"
import { DataGridColumnHeader } from "@/shared/reui/data-grid/data-grid-column-header"
import { DataGridPagination } from "@/shared/reui/data-grid/data-grid-pagination"
import { StateBadge } from "@/shared/ui/state-badge"

export const Route = createFileRoute("/_authenticated/cycle-counts/")({
  component: CycleCountsPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(cycleCountQueries.all()),
})

function CycleCountsPage() {
  const { data: counts } = useSuspenseQuery(cycleCountQueries.all())
  const navigate = useNavigate()
  const [sorting, setSorting] = useState<SortingState>([])
  const [pagination, setPagination] = useState<PaginationState>({
    pageIndex: 0,
    pageSize: 20,
  })

  const columns = useMemo<ColumnDef<CycleCount>[]>(
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
        accessorKey: "countType",
        cell: ({ row }) => <Badge>{row.original.countType}</Badge>,
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Type" />
        ),
        size: 120,
      },
      {
        accessorKey: "countMethod",
        cell: ({ row }) => (
          <Badge variant="secondary">
            {row.original.countMethod}
          </Badge>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Method" />
        ),
        size: 120,
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
        accessorKey: "taskCount",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.taskCount}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Tasks" />
        ),
        size: 100,
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
    data: counts,
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
        description="Inventory accuracy verification"
        title="Cycle Counts"
      />
      <DataGrid
        onRowClick={(count) =>
          navigate({
            params: { countId: count.id },
            to: "/cycle-counts/$countId",
          })
        }
        recordCount={counts.length}
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
