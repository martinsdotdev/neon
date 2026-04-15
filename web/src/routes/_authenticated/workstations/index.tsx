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
  workstationQueries,
  type Workstation,
} from "@/shared/api/workstations"
import { Badge } from "@/shared/ui/badge"
import { PageHeader } from "@/shared/ui/page-header"
import { DataGrid } from "@/shared/reui/data-grid/data-grid"
import { DataGridTable } from "@/shared/reui/data-grid/data-grid-table"
import { DataGridColumnHeader } from "@/shared/reui/data-grid/data-grid-column-header"
import { DataGridPagination } from "@/shared/reui/data-grid/data-grid-pagination"
import { StateBadge } from "@/shared/ui/state-badge"

export const Route = createFileRoute("/_authenticated/workstations/")({
  component: WorkstationsPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(workstationQueries.all()),
})

function WorkstationsPage() {
  const { data: workstations } = useSuspenseQuery(
    workstationQueries.all(),
  )
  const navigate = useNavigate()
  const [sorting, setSorting] = useState<SortingState>([])
  const [pagination, setPagination] = useState<PaginationState>({
    pageIndex: 0,
    pageSize: 20,
  })

  const columns = useMemo<ColumnDef<Workstation>[]>(
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
        accessorKey: "workstationType",
        cell: ({ row }) => (
          <Badge>{row.original.workstationType}</Badge>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Type" />
        ),
        size: 120,
      },
      {
        accessorKey: "mode",
        cell: ({ row }) => (
          <Badge variant="secondary">{row.original.mode}</Badge>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Mode" />
        ),
        size: 120,
      },
      {
        accessorKey: "slotCount",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.slotCount}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Slots" />
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
    data: workstations,
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
        description="Put walls and pack stations"
        title="Workstations"
      />
      <DataGrid
        onRowClick={(workstation) =>
          navigate({
            params: { workstationId: workstation.id },
            to: "/workstations/$workstationId",
          })
        }
        recordCount={workstations.length}
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
