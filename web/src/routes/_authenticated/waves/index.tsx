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
import { waveQueries, type Wave } from "@/shared/api/waves"
import { PageHeader } from "@/shared/ui/page-header"
import { DataGrid } from "@/shared/reui/data-grid/data-grid"
import { DataGridTable } from "@/shared/reui/data-grid/data-grid-table"
import { DataGridColumnHeader } from "@/shared/reui/data-grid/data-grid-column-header"
import { DataGridPagination } from "@/shared/reui/data-grid/data-grid-pagination"
import { StateBadge } from "@/shared/ui/state-badge"

export const Route = createFileRoute("/_authenticated/waves/")({
  component: WavesPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(waveQueries.all()),
})

function WavesPage() {
  const { data: waves } = useSuspenseQuery(waveQueries.all())
  const navigate = useNavigate()
  const [sorting, setSorting] = useState<SortingState>([])
  const [pagination, setPagination] = useState<PaginationState>({
    pageIndex: 0,
    pageSize: 20,
  })

  const columns = useMemo<ColumnDef<Wave>[]>(
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
        accessorKey: "orderGrouping",
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Order Grouping" />
        ),
      },
      {
        accessorKey: "orderCount",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.orderCount}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Order Count" />
        ),
        size: 120,
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
      {
        accessorKey: "createdAt",
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Created At" />
        ),
      },
    ],
    [],
  )

  const table = useReactTable({
    columns,
    data: waves,
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
        description="Wave planning and release management"
        title="Waves"
      />
      <DataGrid
        onRowClick={(wave) =>
          navigate({
            params: { waveId: wave.id },
            to: "/waves/$waveId",
          })
        }
        recordCount={waves.length}
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
