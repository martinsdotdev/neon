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
  consolidationGroupQueries,
  type ConsolidationGroup,
} from "@/shared/api/consolidation-groups"
import { PageHeader } from "@/shared/ui/page-header"
import { DataGrid } from "@/shared/reui/data-grid/data-grid"
import { DataGridTable } from "@/shared/reui/data-grid/data-grid-table"
import { DataGridColumnHeader } from "@/shared/reui/data-grid/data-grid-column-header"
import { DataGridPagination } from "@/shared/reui/data-grid/data-grid-pagination"
import { StateBadge } from "@/shared/ui/state-badge"

export const Route = createFileRoute(
  "/_authenticated/consolidation-groups/",
)({
  component: ConsolidationGroupsPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(
      consolidationGroupQueries.all(),
    ),
})

function ConsolidationGroupsPage() {
  const { data: groups } = useSuspenseQuery(
    consolidationGroupQueries.all(),
  )
  const navigate = useNavigate()
  const [sorting, setSorting] = useState<SortingState>([])
  const [pagination, setPagination] = useState<PaginationState>({
    pageIndex: 0,
    pageSize: 20,
  })

  const columns = useMemo<ColumnDef<ConsolidationGroup>[]>(
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
        accessorKey: "waveId",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.waveId}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Wave" />
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
        accessorKey: "workstationId",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.workstationId ?? "-"}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Workstation" />
        ),
      },
    ],
    [],
  )

  const table = useReactTable({
    columns,
    data: groups,
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
        description="Order consolidation for workstation processing"
        title="Consolidation Groups"
      />
      <DataGrid
        onRowClick={(group) =>
          navigate({
            params: { groupId: group.id },
            to: "/consolidation-groups/$groupId",
          })
        }
        recordCount={groups.length}
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
